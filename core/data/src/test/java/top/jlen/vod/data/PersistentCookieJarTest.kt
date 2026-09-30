package top.jlen.vod.data

import com.google.gson.Gson
import javax.crypto.spec.SecretKeySpec
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import top.jlen.vod.RuntimeEndpoints

class PersistentCookieJarTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val cmsUrl = "https://cms.jlen.top/".toHttpUrl()
    private val noticeUrl = "https://user.jlen.top/".toHttpUrl()

    @Test
    fun migratesLegacyCookiesWithoutChangingDomainOrSessionRules() {
        assertEquals("cms.jlen.top", RuntimeEndpoints.appleCmsBaseUrl.toHttpUrl().host)
        assertEquals("user.jlen.top", RuntimeEndpoints.appCenterApiUrl.toHttpUrl().host)
        assertTrue(RuntimeEndpoints.appleCmsBaseUrl.toHttpUrl().isHttps)
        assertTrue(RuntimeEndpoints.appCenterApiUrl.toHttpUrl().isHttps)
        val cookies = listOf(
            cookie("user_id", "42", "cms.jlen.top"),
            cookie("user_name", "existing-user", "cms.jlen.top"),
            cookie("user_check", "existing-login-secret", "cms.jlen.top"),
            cookie("notice_session", "notice-secret", "user.jlen.top", persistent = false),
            cookie("shared", "parent-domain", "jlen.top", hostOnly = false),
            cookie("path_only", "private-path", "cms.jlen.top", path = "/index.php/user")
        )
        val prefs = InMemoryPreferences()
        val legacy = legacyJson(cookies)
        prefs.edit().putString("cookies", legacy).commit()

        val migrated = PersistentCookieJar(prefs) { key }
        assertEquals(cookies, migrated.snapshot())
        assertFalse(prefs.contains("cookies"))
        val encrypted = prefs.getString("cookies_encrypted_v1", null)!!
        assertNotEquals(legacy, encrypted)
        assertFalse(encrypted.contains("existing-login-secret"))

        val restored = PersistentCookieJar(prefs) { key }
        val urls = listOf(
            cmsUrl, noticeUrl,
            "https://child.cms.jlen.top/".toHttpUrl(),
            "https://cms.jlen.top/index.php/user/info".toHttpUrl(),
            "http://cms.jlen.top/".toHttpUrl(),
            "https://other.example/".toHttpUrl()
        )
        urls.forEach { url ->
            assertEquals(url.toString(), cookies.filter { it.matches(url) }, restored.loadForRequest(url))
        }
        val cmsCookie = cookie("cms_extra", "cms-value", "cms.jlen.top")
        val noticeCookie = cookie("notice_extra", "notice-value", "user.jlen.top")
        restored.saveFromResponse(cmsUrl, listOf(cmsCookie))
        restored.saveFromResponse(noticeUrl, listOf(noticeCookie))
        val afterSave = PersistentCookieJar(prefs) { key }
        assertTrue(afterSave.loadForRequest(cmsUrl).contains(cmsCookie))
        assertTrue(afterSave.loadForRequest(noticeUrl).contains(noticeCookie))
        assertEquals("existing-login-secret", afterSave.snapshot().first { it.name == "user_check" }.value)
        assertNotEquals(encrypted, prefs.getString("cookies_encrypted_v1", null))
    }

    @Test
    fun corruptCiphertextOrLostKeyClearsStorageWithoutFallingBackToPlaintext() {
        listOf(false, true).forEach { lostKey ->
            val prefs = InMemoryPreferences()
            val legacy = legacyJson(listOf(cookie("user_check", "old-secret", "cms.jlen.top")))
            prefs.edit().putString("cookies", legacy).commit()
            PersistentCookieJar(prefs) { key }
            // 残留明文不能在密文损坏时重新恢复过期的会话。
            prefs.edit().putString("cookies", legacy).apply()
            if (!lostKey) prefs.edit().putString("cookies_encrypted_v1", "broken payload").apply()
            val restored = PersistentCookieJar(prefs) { if (lostKey) null else key }
            assertTrue(restored.snapshot().isEmpty())
            assertFalse(prefs.contains("cookies"))
            assertFalse(prefs.contains("cookies_encrypted_v1"))
        }
    }

    @Test
    fun unavailableKeyKeepsLegacyLoginForRetryButCannotPreventLogout() {
        val prefs = InMemoryPreferences()
        val cookies = listOf(cookie("user_check", "existing-secret", "cms.jlen.top"))
        val legacy = legacyJson(cookies)
        prefs.edit().putString("cookies", legacy).commit()
        val unavailable = PersistentCookieJar(prefs) { error("测试密钥暂不可用") }
        assertEquals(cookies, unavailable.snapshot())
        assertEquals(legacy, prefs.getString("cookies", null))
        assertFalse(prefs.contains("cookies_encrypted_v1"))

        // 下次密钥恢复后可正常完成迁移，随后即便密钥又不可用也必须能清除登录态。
        var keyAvailable = true
        val restored = PersistentCookieJar(prefs) { if (keyAvailable) key else error("测试密钥不可用") }
        assertEquals(cookies, restored.snapshot())
        keyAvailable = false
        restored.clear()
        assertTrue(restored.snapshot().isEmpty())
        assertFalse(prefs.contains("cookies"))
        assertFalse(prefs.contains("cookies_encrypted_v1"))
    }

    private fun cookie(
        name: String,
        value: String,
        domain: String,
        hostOnly: Boolean = true,
        persistent: Boolean = true,
        path: String = "/"
    ): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
        .path(path)
        .secure()
        .httpOnly()
        .apply { if (persistent) expiresAt(253402300799999L) }
        .build()

    private fun legacyJson(cookies: List<Cookie>): String = Gson().toJson(cookies.map {
        mapOf(
            "name" to it.name, "value" to it.value, "domain" to it.domain, "path" to it.path,
            "expiresAt" to it.expiresAt, "secure" to it.secure, "httpOnly" to it.httpOnly,
            "hostOnly" to it.hostOnly, "persistent" to it.persistent
        )
    })
}
