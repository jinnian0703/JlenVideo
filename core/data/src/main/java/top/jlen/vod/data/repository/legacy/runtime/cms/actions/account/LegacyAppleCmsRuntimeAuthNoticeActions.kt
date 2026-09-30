package top.jlen.vod.data

import android.net.Uri
import android.os.Build
import com.google.gson.JsonParser
import java.io.IOException
import java.util.UUID
import okhttp3.FormBody
import okhttp3.Request
import top.jlen.vod.AppRuntimeInfo

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyReportHeartbeat(
    route: String,
    userId: String = currentSession().userId,
    vodId: String = "",
    sid: Int? = null,
    nid: Int? = null
) {
    val request = Request.Builder()
        .url(
            Uri.parse(runtimeAppCenterApiUrl())
                .buildUpon()
                .appendQueryParameter("action", "heartbeat")
                .build()
                .toString()
        )
        .post(
            FormBody.Builder()
                .add("device_id", legacyEnsureHeartbeatDeviceId())
                .add("platform", "android")
                .add("app_version", AppRuntimeInfo.versionName)
                .add("android_release", Build.VERSION.RELEASE.orEmpty())
                .add("android_sdk", Build.VERSION.SDK_INT.toString())
                .add("manufacturer", Build.MANUFACTURER.orEmpty().trim())
                .add("model", Build.MODEL.orEmpty().trim())
                .add("route", route.trim().ifBlank { "home" })
                .apply {
                    userId.trim()
                        .takeIf(String::isNotBlank)
                        ?.let { add("user_id", it) }
                    vodId.trim()
                        .takeIf(String::isNotBlank)
                        ?.let { add("vod_id", it) }
                    sid?.takeIf { it > 0 }?.let { add("sid", it.toString()) }
                    nid?.takeIf { it > 0 }?.let { add("nid", it.toString()) }
                }
                .build()
        )
        .build()

    runtimeHttpClient().newCall(request).await().use { response ->
        if (!response.isSuccessful) {
            throw IOException("心跳上报失败：HTTP ${response.code}")
        }
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLogin(
    userName: String,
    password: String
): AuthSession {
    val payload = FormBody.Builder()
        .add("user_name", userName.trim())
        .add("user_pwd", password)
        .build()
    val request = Request.Builder()
        .url("${runtimeBaseUrl()}/index.php/user/login")
        .header("Referer", "${runtimeBaseUrl()}/index.php/user/login.html")
        .header("X-Requested-With", "XMLHttpRequest")
        .post(payload)
        .build()

    val response = runtimeHttpClient().newCall(request).await()
    response.use {
        if (!it.isSuccessful) {
            throw IOException("登录失败：HTTP ${it.code}")
        }
        val body = it.body?.string().orEmpty()
        val authResponse = runtimeParseAuthResponse(body)

        val session = currentSession()
        if (session.isLoggedIn) {
            return session
        }

        val failureMessage = authResponse?.msg
            ?.takeIf(String::isNotBlank)
            ?.let(::legacyNormalizeLoginFailureMessage)

        throw IOException(
            failureMessage
                ?: "登录失败，请检查账号或密码"
        )
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadNotices(
    appVersion: String = AppRuntimeInfo.versionName,
    userId: String = "",
    forceRefresh: Boolean = false
): List<AppNotice> {
    if (!forceRefresh) {
        currentNoticeCacheEntry()
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeNoticeCacheTtlMs()) }
            ?.value
            ?.let { return it }
    }

    val requestKey = buildString {
        append("notices:")
        append(appVersion.trim())
        append(':')
        append(userId.trim())
    }

    return if (forceRefresh) {
        legacyLoadFreshNotices(appVersion = appVersion, userId = userId)
    } else {
        runtimeAwaitSharedRequest(requestKey) {
            currentNoticeCacheEntry()
                ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeNoticeCacheTtlMs()) }
                ?.value
                ?: legacyLoadFreshNotices(appVersion = appVersion, userId = userId)
        }
    }
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyPickPendingNotice(
    notices: List<AppNotice>
): AppNotice? {
    val dismissedIds = runtimeNoticePrefs()
        .getStringSet(runtimeDismissedNoticeIdsKey(), emptySet())
        .orEmpty()
    return notices.firstOrNull { notice ->
        notice.isActive &&
            notice.id.isNotBlank() &&
            (notice.alwaysShowDialog || !dismissedIds.contains(notice.id))
    }
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyUnreadActiveNoticeIds(
    notices: List<AppNotice>
): Set<String> {
    val dismissedIds = runtimeNoticePrefs()
        .getStringSet(runtimeDismissedNoticeIdsKey(), emptySet())
        .orEmpty()
    return notices.asSequence()
        .filter { notice ->
            notice.isActive &&
                notice.id.isNotBlank() &&
                (notice.alwaysShowDialog || !dismissedIds.contains(notice.id))
        }
        .map { it.id }
        .toSet()
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyMarkNoticeDismissed(notice: AppNotice) {
    if (notice.alwaysShowDialog) return
    legacyMarkNoticeDismissed(notice.id)
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyMarkNoticeDismissed(noticeId: String) {
    val normalized = noticeId.trim()
    if (normalized.isBlank()) return
    val currentIds = runtimeNoticePrefs()
        .getStringSet(runtimeDismissedNoticeIdsKey(), emptySet())
        .orEmpty()
    if (currentIds.contains(normalized)) return
    runtimeNoticePrefs().edit()
        .putStringSet(runtimeDismissedNoticeIdsKey(), currentIds + normalized)
        .apply()
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFreshNotices(
    appVersion: String,
    userId: String
): List<AppNotice> {
    val url = Uri.parse(runtimeAppCenterApiUrl())
        .buildUpon()
        .appendQueryParameter("action", "notices")
        .appendQueryParameter("app_version", appVersion.trim().ifBlank { AppRuntimeInfo.versionName })
        .apply {
            userId.trim()
                .takeIf(String::isNotBlank)
                ?.let { appendQueryParameter("user_id", it) }
        }
        .build()
        .toString()

    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .build()

    runtimeHttpClient().newCall(request).await().use { response ->
        if (!response.isSuccessful) {
            throw IOException("公告加载失败：HTTP ${response.code}")
        }

        val body = response.body?.string().orEmpty()
        val json = runCatching { JsonParser.parseString(body).safeObject() }.getOrNull()
        val items = json?.let { runtimeExtractNoticeItems(it) }.orEmpty()
        val notices = items.mapNotNull { element ->
            runCatching { element.safeObject()?.let { runtimeParseNoticeItem(it) } }.getOrNull()
        }
            .sortedWith(
                compareByDescending<AppNotice> { it.isPinned }
                    .thenByDescending { it.isActive }
                    .thenByDescending {
                        runtimeParseNoticeTimeToMillis(it.updatedAt)
                            ?: runtimeParseNoticeTimeToMillis(it.createdAt)
                            ?: 0L
                    }
                    .thenByDescending { it.id }
            )

        updateNoticeCacheEntry(
            CachedValue(
                value = notices,
                timestampMs = System.currentTimeMillis()
            )
        )
        runtimeCleanupCachesIfNeeded()
        return notices
    }
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyEnsureHeartbeatDeviceId(): String {
    runtimeHeartbeatPrefs().getString(runtimeHeartbeatDeviceIdKey(), null)
        ?.takeIf(String::isNotBlank)
        ?.let { return it }
    val generated = "android-${UUID.randomUUID()}"
    runtimeHeartbeatPrefs().edit().putString(runtimeHeartbeatDeviceIdKey(), generated).apply()
    return generated
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyNormalizeLoginFailureMessage(
    rawMessage: String
): String {
    val message = rawMessage.trim()
    return when {
        message.isBlank() -> ""
        message.contains("获取用户信息失败") -> "用户名不存在或密码错误"
        else -> message
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadRegisterPage(): RegisterPage {
    val document = runtimeFetchDocument("${runtimeBaseUrl()}/index.php/user/reg.html")
    val channel = document.selectFirst("input[name=ac]")?.attr("value")?.trim().orEmpty()
        .ifBlank { "email" }
    val requiresCode = document.selectFirst("input[name=code]") != null
    val requiresVerify = document.selectFirst("input[name=verify]") != null
    val captchaUrl = document.selectFirst("img.ewave-verify-img, img[src*=/verify/]")
        ?.attr("src")
        .orEmpty()

    return RegisterPage(
        channel = channel,
        contactLabel = if (channel == "phone") "手机号" else "邮箱",
        codeLabel = if (channel == "phone") "手机验证码" else "邮箱验证码",
        requiresCode = requiresCode,
        requiresVerify = requiresVerify,
        captchaUrl = runtimeResolveUrl(captchaUrl),
        captchaBytes = null
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadRegisterCaptcha(
    captchaUrl: String
): ByteArray {
    val request = Request.Builder()
        .url(runtimeAppendTimestamp(captchaUrl))
        .header("Referer", "${runtimeBaseUrl()}/index.php/user/reg.html")
        .build()

    runtimeHttpClient().newCall(request).await().use { response ->
        if (!response.isSuccessful) {
            throw IOException("加载验证码失败：HTTP ${response.code}")
        }
        return response.body?.bytes() ?: throw IOException("加载验证码失败")
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFindPasswordPage(): FindPasswordPage {
    val document = runtimeFetchDocument("${runtimeBaseUrl()}/index.php/user/findpass.html")
    val requiresVerify = document.selectFirst("input[name=verify]") != null
    val captchaUrl = document.selectFirst("img[src*=/verify/], img.mac_verify_img")
        ?.attr("src")
        .orEmpty()

    return FindPasswordPage(
        requiresVerify = requiresVerify,
        captchaUrl = runtimeResolveUrl(captchaUrl),
        captchaBytes = null
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFindPasswordCaptcha(
    captchaUrl: String
): ByteArray {
    val request = Request.Builder()
        .url(runtimeAppendTimestamp(captchaUrl))
        .header("Referer", "${runtimeBaseUrl()}/index.php/user/findpass.html")
        .build()

    runtimeHttpClient().newCall(request).await().use { response ->
        if (!response.isSuccessful) {
            throw IOException("加载验证码失败：HTTP ${response.code}")
        }
        return response.body?.bytes() ?: throw IOException("加载验证码失败")
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyFindPassword(
    editor: FindPasswordEditor
): String {
    val form = FormBody.Builder()
        .add("to", editor.email.trim())
        .add("code", editor.code.trim())
        .add("user_pwd", editor.password)
        .add("user_pwd2", editor.confirmPassword)
        .add("ac", "email")
        .build()
    return runtimeSubmitPublicAction(
        url = "${runtimeBaseUrl()}/index.php/user/findpass_reset",
        referer = "${runtimeBaseUrl()}/index.php/user/findpass.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacySendRegisterCode(
    channel: String,
    contact: String
): String {
    val form = FormBody.Builder()
        .add("ac", channel.trim())
        .add("to", contact.trim())
        .build()
    return runtimeSubmitPublicAction(
        url = runtimeNormalizeUrl("/user/reg_msg/"),
        referer = "${runtimeBaseUrl()}/index.php/user/reg.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyRegister(
    editor: RegisterEditor
): String {
    val form = FormBody.Builder()
        .add("user_name", editor.userName.trim())
        .add("user_pwd", editor.password)
        .add("user_pwd2", editor.confirmPassword)
        .add("ac", editor.channel.trim())
        .add("to", editor.contact.trim())
        .add("code", editor.code.trim())
        .add("verify", editor.verify.trim())
        .build()
    return runtimeSubmitPublicAction(
        url = runtimeNormalizeUrl("/user/reg/"),
        referer = "${runtimeBaseUrl()}/index.php/user/reg.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLogout() {
    val request = Request.Builder()
        .url("${runtimeBaseUrl()}/index.php/user/logout")
        .header("Referer", "${runtimeBaseUrl()}/index.php/user")
        .post(FormBody.Builder().build())
        .build()

    runtimeHttpClient().newCall(request).await().use {
        if (!it.isSuccessful && it.code != 302) {
            throw IOException("退出登录失败：HTTP ${it.code}")
        }
    }
    clearSession()
}
