package top.jlen.vod.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException

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
