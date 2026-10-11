package top.jlen.vod.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

// 只接受名称以 .apk 结尾的附件，不把源码归档或校验文件当作安装包。
internal fun parseGithubApkDownloadUrl(json: JsonObject): String =
    json.get("assets").safeArray()
        ?.asSequence()
        ?.mapNotNull { it.safeObject() }
        ?.filter { it.get("name").safeString().trim().endsWith(".apk", ignoreCase = true) }
        ?.map { it.get("browser_download_url").safeString().trim() }
        ?.firstOrNull { it.toHttpUrlOrNull() != null }
        .orEmpty()

internal fun findGithubApkDownloadUrl(document: Document): String =
    document.select("a[href]")
        .asSequence()
        .map { it.absUrl("href").trim() }
        .firstOrNull { it.toHttpUrlOrNull()?.encodedPath?.endsWith(".apk", ignoreCase = true) == true }
        .orEmpty()

// 上层按 HTML 渲染更新说明；优先使用 GitHub 渲染的 HTML，缺失时转义原文并保留换行。
internal fun parseGithubReleaseNotes(json: JsonObject): String =
    json.get("body_html").safeString().trim().ifBlank {
        Entities.escape(json.get("body").safeString().trim()).replace("\n", "<br>")
    }

internal fun parseAppCenterUpdateResponse(
    json: JsonObject,
    currentVersion: String,
    currentVersionCode: Int,
    expectedPackageName: String
): AppUpdateInfo {
    val code = json.firstInt("code", "status")
    if (code != null && code !in setOf(1, 200)) {
        throw IOException(json.firstString("msg", "message").ifBlank { "检查更新失败" })
    }
    val data = json.firstObject("data") ?: throw IOException("检查更新失败：版本信息为空")
    val hasUpdate = data.firstBoolean("has_update")
        ?: throw IOException("检查更新失败：响应缺少 has_update")
    if (!hasUpdate) {
        if (data.firstBoolean("force_update") == true || data.get("release")?.isJsonNull == false) {
            throw IOException("检查更新失败：无更新响应字段无效")
        }
        return AppUpdateInfo(
            currentVersion = currentVersion.trim(),
            currentVersionCode = currentVersionCode,
            hasUpdate = false
        )
    }
    val release = data.firstObject("release") ?: throw IOException("检查更新失败：版本信息为空")
    val packageName = release.firstString("package_name")
    if (packageName.isNotBlank() && packageName != expectedPackageName) {
        throw IOException("检查更新失败：安装包不匹配")
    }
    val latestVersion = release.firstString("version_name")
    val latestVersionCode = release.firstInt("version_code")
        ?: throw IOException("检查更新失败：版本号无效")
    if (latestVersion.isBlank() || latestVersionCode <= currentVersionCode) {
        throw IOException("检查更新失败：版本信息无效")
    }
    val releasePageUrl = release.firstString("release_url")
    val directUrl = release.firstString("direct_url")
    val declaredDownloadUrl = release.firstString("download_url")
    listOf(releasePageUrl, directUrl, declaredDownloadUrl)
        .filter(String::isNotBlank)
        .forEach { value ->
            val parsed = value.toHttpUrlOrNull()
            if (parsed == null || parsed.scheme !in setOf("http", "https")) {
                throw IOException("检查更新失败：下载地址无效")
            }
        }
    val downloadUrl = declaredDownloadUrl
        .ifBlank { directUrl }
        .ifBlank { releasePageUrl }
    if (downloadUrl.isBlank() && releasePageUrl.isBlank()) {
        throw IOException("检查更新失败：下载地址为空")
    }
    val notesHtml = release.firstString("notes_html")
    return AppUpdateInfo(
        currentVersion = currentVersion.trim(),
        currentVersionCode = currentVersionCode,
        latestVersion = latestVersion,
        latestVersionCode = latestVersionCode,
        minSupportedVersionCode = release.firstInt("min_supported_code") ?: 0,
        forceUpdate = data.firstBoolean("force_update") ?: false,
        releasePageUrl = releasePageUrl,
        downloadUrl = downloadUrl,
        notes = notesHtml.ifBlank { release.firstString("notes") },
        hasUpdate = true
    )
}

internal fun parseUserCenterVod(
    row: JsonObject,
    gson: Gson,
    baseUrl: String
): VodItem? {
    val vodObject = row.firstObject("vod", "video", "item") ?: row
    val rawItem = runCatching { gson.fromJson(vodObject, VodItem::class.java) }.getOrNull() ?: return null
    val resolvedVodId = rawItem.vodId.ifBlank { vodObject.firstString("vod_id", "id") }
    val resolvedTypeName = rawItem.typeName.orEmpty().ifBlank {
        vodObject.firstObject("type")?.firstString("type_name").orEmpty()
    }
    if (resolvedVodId.isBlank() && rawItem.vodName.isBlank() && vodObject === row) {
        return null
    }
    return rawItem.copy(
        vodId = resolvedVodId,
        vodName = decodeSiteText(rawItem.vodName.ifBlank { vodObject.firstString("vod_name", "name", "title") }),
        vodPic = rawItem.vodPic?.let { normalizeAgainst(it, "$baseUrl/", baseUrl) },
        typeName = resolvedTypeName,
        siteVodId = rawItem.siteVodId.ifBlank { resolvedVodId },
        detailUrl = buildVodDetailUrl(rawItem.copy(vodId = resolvedVodId), baseUrl)
    )
}

internal fun extractVideoApiMessage(json: JsonObject, fallbackMessage: String): String {
    val code = json.firstInt("code", "status")
    val message = json.firstString("msg", "message")
    if (code != null && code !in setOf(1, 200)) {
        throw IOException(message.ifBlank { fallbackMessage })
    }
    return message.ifBlank { fallbackMessage }
}

internal fun normalizeLoginFailureMessage(rawMessage: String): String {
    val message = rawMessage.trim()
    return when {
        message.isBlank() -> ""
        message.contains("获取用户信息失败") -> "用户名不存在或密码错误"
        else -> message
    }
}

internal fun parsePortraitUploadResult(json: JsonObject): String {
    val code = json.firstInt("code", "status")
    val message = json.firstString("msg", "message")
    if (code == 401 || message.equals("login required", ignoreCase = true) || isLoginMessage(message)) {
        throw IOException("请先登录")
    }
    if (code != null && code !in setOf(1, 200)) {
        throw IOException(message.ifBlank { "头像上传失败" })
    }
    val payload = unwrapApiPayload(json)
    val portrait = payload?.firstString("user_portrait_with_version", "user_portrait", "portrait", "avatar")
        .orEmpty()
    return if (portrait.isNotBlank() || message.isBlank() || message.equals("ok", ignoreCase = true)) {
        "头像已更新"
    } else {
        message
    }
}

internal fun parseVideoApiResponseBody(body: String): JsonObject {
    val trimmed = body.trim()
    runCatching {
        return JsonParser.parseString(trimmed).asJsonObject
    }
    if (trimmed.equals("closed", ignoreCase = true)) {
        return JsonObject().apply {
            addProperty("code", 0)
            addProperty("msg", "api closed")
            add("data", com.google.gson.JsonNull.INSTANCE)
        }
    }
    if (
        trimmed.contains("login required", ignoreCase = true) ||
            trimmed.contains("/index.php/user/login", ignoreCase = true) ||
            trimmed.contains("user/login", ignoreCase = true)
    ) {
        return JsonObject().apply {
            addProperty("code", 401)
            addProperty("msg", "login required")
            add("data", com.google.gson.JsonNull.INSTANCE)
        }
    }
    throw IOException("视频 API 响应格式异常")
}

internal fun JsonObject.primitiveString(name: String): String =
    get(name)
        ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.let { primitive ->
            runCatching {
                val value = primitive.asJsonPrimitive
                when {
                    value.isString -> value.asString
                    value.isNumber -> value.asNumber.toString()
                    value.isBoolean -> value.asBoolean.toString()
                    else -> ""
                }
            }.getOrNull()
        }
        ?.trim()
        .orEmpty()
