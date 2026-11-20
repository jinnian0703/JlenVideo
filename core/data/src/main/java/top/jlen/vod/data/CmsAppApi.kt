package top.jlen.vod.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CmsApiError(val code: Int, message: String) : IOException(message)

class LoginCaptchaRejected(message: String) : IOException(message)

class CmsAppApi internal constructor(client: OkHttpClient, base: String) {
    private val http = client.newBuilder().followRedirects(false).build()
    private val api = (base.trimEnd('/') + "/api.php/").toHttpUrl()
    private val configLock = Mutex()
    private var bootstrap: JsonObject? = null
    private var fetchedAt = 0L

    private suspend fun config(): JsonObject = configLock.withLock {
        val now = System.nanoTime()
        bootstrap?.takeIf { now - fetchedAt < TimeUnit.MINUTES.toNanos(5) } ?: (
            request("app/bootstrap").firstObject("data") ?: throw IOException("站点未返回启动配置")
        ).also { bootstrap = it; fetchedAt = now }
    }

    suspend fun cookieLoginEnabled() = config().firstObject("auth")?.firstInt("cookie_enabled") == 1

    private suspend fun request(path: String, query: Map<String, String> = emptyMap(),
                                form: Map<String, String>? = null, csrf: String = ""): JsonObject {
        val url = api.newBuilder().addPathSegments(path).apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).apply {
            if (csrf.isNotEmpty()) header("X-CSRF-Token", csrf)
            if (form != null) post(FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build())
        }.build()
        return http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("站点请求失败（HTTP ${response.code}）")
            val json = JsonParser.parseReader(response.body!!.charStream()).asJsonObject
            if (json.firstInt("code") != 1) throw CmsApiError(json.firstInt("code") ?: 0, json.firstString("msg").ifBlank { "站点请求失败" })
            json
        }
    }

    private suspend fun token(user: String): String {
        val info = request("auth/me").firstObject("info")
        if (info?.firstString("user_id") != user) throw IOException("登录状态已变更，请重新登录")
        return info.firstString("csrf_token").ifBlank { throw IOException("登录已失效，请重新登录") }
    }

    suspend fun loginCaptchaRequired() = request("auth/config").firstObject("info")?.firstInt("login_captcha") == 1

    suspend fun captcha(): ByteArray = http.newCall(Request.Builder().url(api.resolve("auth/captcha")!!).build()).await().use {
        if (!it.isSuccessful || it.body?.contentType()?.type != "image") throw IOException("验证码加载失败，请重试")
        it.body!!.bytes()
    }

    suspend fun login(name: String, password: String, verify: String) {
        try {
            request("user/login", form = mapOf("user_name" to name, "user_pwd" to password, "verify" to verify))
        } catch (error: CmsApiError) {
            if (error.code == 1002) throw LoginCaptchaRejected(error.message.orEmpty())
            throw error
        }
    }

    suspend fun unlock(vod: String, type: Int, password: String) {
        if (config().firstObject("access")?.firstInt("public_api_enabled") != 1) throw IOException("站点尚未开放影片密码验证，请联系管理员")
        try { request("vod/verify_pwd", form = mapOf("id" to vod, "type" to "$type", "pwd" to password)) }
        catch (error: CmsApiError) { if (error.code != 1002) throw error }
    }

    suspend fun favorite(vod: String): Boolean? {
        if (config().firstObject("access")?.firstInt("public_api_enabled") != 1) return null
        return request("user/get_favorites_status", mapOf("vod_ids" to vod, "mid" to "1", "ulog_type" to "2"))
            .firstObject("info")?.getAsJsonArray("rows")?.map { it.asJsonObject }?.find { it.firstString("rid") == vod }?.let { it.firstInt("is_fav") == 1 }
    }

    suspend fun resume(vod: String, sources: List<PlaySource>): PlaybackResumeRecord? {
        val info = request("ulog/get_progress", mapOf("vod_id" to vod)).firstObject("info") ?: return null
        val source = (info.firstInt("sid") ?: return null) - 1
        val episode = (info.firstInt("nid") ?: return null) - 1
        val group = sources.getOrNull(source) ?: return null
        if (episode !in group.episodes.indices) return null
        val point = info.firstInt("point") ?: return null
        val duration = info.firstInt("duration") ?: 0
        return PlaybackResumeRecord(vodId = vod, sourceKey = normalizePlaybackSourceKey(group.name, source), sourceName = group.name,
            sourceIndex = source, episodeIndex = episode, positionMs = if (duration > 0 && point >= duration) 0L else point.coerceAtLeast(0) * 1000L,
            updatedAt = (info.firstInt("time") ?: 0) * 1000L)
    }

    suspend fun progress(user: String, vod: String, source: Int, episode: Int, position: Long, duration: Long) {
        request("ulog/progress", form = mapOf("vod_id" to vod, "sid" to "${source + 1}", "nid" to "${episode + 1}",
            "point" to "${position.coerceIn(0L, duration) / 1000}", "duration" to "${duration / 1000}"), csrf = token(user))
    }

    suspend fun reportFailure(user: String, vod: String, source: Int, episode: Int, sourceName: String) {
        request("ulog/report_fail", form = mapOf("vod_id" to vod, "sid" to "${source + 1}", "nid" to "${episode + 1}", "play_from" to sourceName),
            csrf = if (user.isBlank()) "" else token(user))
    }

    suspend fun tasksEnabled() = config().firstObject("features")?.firstInt("tasks") == 1

    suspend fun signIn(user: String): String = request("task/daily_sign", form = emptyMap(), csrf = token(user)).firstString("msg").ifBlank { "签到成功" }
}
