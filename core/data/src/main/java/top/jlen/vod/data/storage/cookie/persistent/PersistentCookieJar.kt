package top.jlen.vod.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import java.util.WeakHashMap
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

class PersistentCookieJar internal constructor(
    private val prefs: SharedPreferences,
    private val keyProvider: (createIfMissing: Boolean) -> SecretKey? = ::loadOrCreateSecretKey
) : CookieJar {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val gson = Gson()
    // All clients (API, fullscreen player, avatars) share one atomic read/merge/write lock.
    private val lock = synchronized(storeLocks) { storeLocks.getOrPut(prefs) { Any() } }
    private val cookies = mutableListOf<Cookie>()
    private var loaded = false
    private var loadedEncrypted: String? = null
    private var loadedLegacy: String? = null
    private var pendingWrite = false

    init {
        synchronized(lock) { refreshFromStorage() }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            // Never overwrite another client's login, or ciphertext we cannot currently read.
            if (!refreshFromStorage()) return
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
            if (!refreshFromStorage()) return emptyList()
            val removed = pruneExpiredCookies()
            if (removed) {
                persistCookies()
            }
            // 保留 OkHttp 的 domain、hostOnly、path、secure 匹配规则，不限定登录或公告域名。
            return cookies.filter { it.matches(url) }
        }
    }

    fun snapshot(): List<Cookie> = synchronized(lock) {
        if (!refreshFromStorage()) return@synchronized emptyList()
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

    fun hasPendingRestore(): Boolean = synchronized(lock) {
        !loaded && prefs.contains(KEY_COOKIES_ENCRYPTED)
    }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            // 注销不依赖密钥可用，避免加密失败后旧登录态在下次启动恢复。
            clearPersistedCookies()
        }
    }

    private fun refreshFromStorage(): Boolean {
        val encrypted = prefs.getString(KEY_COOKIES_ENCRYPTED, null)
        val legacy = prefs.getString(KEY_COOKIES, null)
        if (loaded && encrypted == loadedEncrypted && legacy == loadedLegacy) {
            if (pendingWrite) persistCookies()
            return true
        }
        val restored = loadCookies() ?: return false
        cookies.clear()
        cookies.addAll(restored)
        pendingWrite = false
        rememberStoredVersion()
        return true
    }

    private fun rememberStoredVersion() {
        loadedEncrypted = prefs.getString(KEY_COOKIES_ENCRYPTED, null)
        loadedLegacy = prefs.getString(KEY_COOKIES, null)
        loaded = true
    }

    private fun loadCookies(): List<Cookie>? {
        return try {
            if (prefs.contains(KEY_COOKIES_ENCRYPTED)) {
                val restored = parseCookies(decrypt(prefs.getString(KEY_COOKIES_ENCRYPTED, null).orEmpty()))
                if (prefs.contains(KEY_COOKIES)) prefs.edit().remove(KEY_COOKIES).commit()
                restored
            } else {
                val legacyJson = prefs.getString(KEY_COOKIES, null).orEmpty()
                if (legacyJson.isBlank()) return emptyList()
                val restored = parseCookies(legacyJson)
                // Migrate only after encryption succeeds; keep old data when the key is unavailable.
                runCatching {
                    val encrypted = encrypt(legacyJson)
                    prefs.edit()
                        .putString(KEY_COOKIES_ENCRYPTED, encrypted)
                        .remove(KEY_COOKIES)
                        .commit()
                }
                restored
            }
        } catch (error: Exception) {
            when (error) {
                is LostCookieKeyException, is AEADBadTagException,
                is IllegalArgumentException, is JsonParseException -> {
                    // Only confirmed key loss/corruption invalidates credentials; never restore stale plaintext.
                    clearPersistedCookies()
                    emptyList()
                }
                else -> {
                    // A temporarily unavailable Android Keystore must not log out an app update.
                    loaded = false
                    null
                }
            }
        }
    }

    private fun parseCookies(json: String): List<Cookie> {
        val type = object : TypeToken<List<StoredCookie?>>() {}.type
        return gson.fromJson<List<StoredCookie?>>(json, type)
            .orEmpty()
            .filterNotNull()
            .mapNotNull { it.toCookieOrNull() }
    }

    private fun persistCookies() {
        pendingWrite = true
        if (cookies.isEmpty()) {
            clearPersistedCookies()
            return
        }
        runCatching {
            val payload = cookies.map { it.toStoredCookie() }
            val encrypted = encrypt(gson.toJson(payload))
            val saved = prefs.edit()
                .putString(KEY_COOKIES_ENCRYPTED, encrypted)
                .remove(KEY_COOKIES)
                .commit()
            rememberStoredVersion()
            pendingWrite = !saved
        }
    }

    private fun clearPersistedCookies() {
        runCatching {
            val saved = prefs.edit().remove(KEY_COOKIES_ENCRYPTED).remove(KEY_COOKIES).commit()
            rememberStoredVersion()
            pendingWrite = !saved
        }
    }

    // 每次加密生成新的 IV，存储格式为 Base64(IV 长度 + IV + 带认证标签的密文)。
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider(true) ?: error("Cookie 密钥不可用"))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return (byteArrayOf(iv.size.toByte()) + iv + cipherText).toByteString().base64()
    }

    private fun decrypt(encoded: String): String {
        val combined = encoded.decodeBase64()?.toByteArray() ?: throw IllegalArgumentException("Cookie 密文格式异常")
        require(combined.isNotEmpty()) { "Cookie 密文为空" }
        val ivSize = combined[0].toInt() and 0xFF
        require(ivSize == 12 && combined.size >= 1 + ivSize + GCM_TAG_BITS / 8) { "Cookie 密文长度异常" }
        val key = keyProvider(false) ?: throw LostCookieKeyException()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, combined.copyOfRange(1, 1 + ivSize)))
        return String(cipher.doFinal(combined, 1 + ivSize, combined.size - 1 - ivSize), Charsets.UTF_8)
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
        private val storeLocks = WeakHashMap<SharedPreferences, Any>()
        const val PREFS_NAME = "jlen_cookie_store"
        const val KEY_COOKIES = "cookies"
        const val KEY_COOKIES_ENCRYPTED = "cookies_encrypted_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jlen_cookie_store_key"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        private val keyLock = Any()

        private fun loadOrCreateSecretKey(createIfMissing: Boolean): SecretKey? = synchronized(keyLock) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?.let { return@synchronized it }
            if (!createIfMissing) return@synchronized null

            // 多个仓库实例并发初始化时也只生成一次，避免覆盖密钥导致旧密文无法解密。
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
    }

    private class LostCookieKeyException : IllegalStateException("Cookie 密钥丢失")
}
