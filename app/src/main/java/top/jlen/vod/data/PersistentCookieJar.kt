package top.jlen.vod.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()
    private val cookies = loadCookies().toMutableList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            pruneExpiredCookies()
            cookies.forEach { incoming ->
                this.cookies.removeAll { saved ->
                    saved.name == incoming.name &&
                        saved.domain == incoming.domain &&
                        saved.path == incoming.path
                }

                if (incoming.expiresAt >= System.currentTimeMillis()) {
                    this.cookies += incoming
                }
            }
            persistCookies()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val removed = pruneExpiredCookies()
            if (removed) {
                persistCookies()
            }
            return cookies.filter { it.matches(url) }
        }
    }

    fun snapshot(): List<Cookie> = synchronized(lock) {
        val removed = pruneExpiredCookies()
        if (removed) {
            persistCookies()
        }
        cookies.toList()
    }

    fun cookieHeader(url: HttpUrl): String =
        loadForRequest(url).joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            persistCookies()
        }
    }

    private fun loadCookies(): List<Cookie> {
        val json = prefs.getString(KEY_COOKIES, null).orEmpty()
        if (json.isBlank()) return emptyList()

        val type = object : TypeToken<List<StoredCookie>>() {}.type
        return runCatching {
            gson.fromJson<List<StoredCookie>>(json, type)
                .orEmpty()
                .mapNotNull { it.toCookieOrNull() }
        }.getOrDefault(emptyList())
    }

    private fun persistCookies() {
        val payload = cookies.map { it.toStoredCookie() }
        prefs.edit().putString(KEY_COOKIES, gson.toJson(payload)).apply()
    }

    private fun pruneExpiredCookies(): Boolean {
        val beforeSize = cookies.size
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return beforeSize != cookies.size
    }

    private fun Cookie.toStoredCookie(): StoredCookie =
        StoredCookie(
            name = name,
            value = value,
            domain = domain,
            path = path,
            expiresAt = expiresAt,
            secure = secure,
            httpOnly = httpOnly,
            hostOnly = hostOnly,
            persistent = persistent
        )

    private fun StoredCookie.toCookieOrNull(): Cookie? =
        runCatching {
            Cookie.Builder()
                .name(name)
                .value(value)
                .apply {
                    if (hostOnly) {
                        hostOnlyDomain(domain)
                    } else {
                        domain(domain)
                    }
                    path(path)
                    if (secure) secure()
                    if (httpOnly) httpOnly()
                    if (persistent) expiresAt(expiresAt)
                }
                .build()
        }.getOrNull()

    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean
    )

    private companion object {
        const val PREFS_NAME = "jlen_cookie_store"
        const val KEY_COOKIES = "cookies"
    }
}
