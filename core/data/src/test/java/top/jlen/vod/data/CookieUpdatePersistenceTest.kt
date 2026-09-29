package top.jlen.vod.data

import android.content.SharedPreferences
import javax.crypto.spec.SecretKeySpec
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class CookieUpdatePersistenceTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val url = "https://cms.jlen.top/".toHttpUrl()

    private fun cookie(name: String, value: String) = Cookie.Builder()
        .name(name).value(value).hostOnlyDomain(url.host).path("/").secure().httpOnly().build()

    @Test
    fun upgradeMigratesLegacyPlaintextAndRestoresEncryptedSession() {
        val prefs = CookieUpdatePreferences()
        val legacy = """[{"name":"user_check","value":"old-secret","domain":"cms.jlen.top","path":"/","expiresAt":253402300799999,"secure":true,"httpOnly":true,"hostOnly":true,"persistent":false}]"""
        prefs.edit().putString("cookies", legacy).commit()
        val updatedApp = PersistentCookieJar(prefs) { key }
        assertEquals("old-secret", updatedApp.snapshot().single().value)
        assertFalse(prefs.contains("cookies"))
        assertFalse(prefs.getString("cookies_encrypted_v1", "")!!.contains("old-secret"))
        assertEquals(updatedApp.snapshot(), PersistentCookieJar(prefs) { key }.snapshot())
    }

    @Test
    fun staleAvatarOrPlayerClientCannotOverwriteNewLogin() {
        val prefs = CookieUpdatePreferences()
        val account = PersistentCookieJar(prefs) { key }
        val oldPlayer = PersistentCookieJar(prefs) { key }
        val login = listOf(cookie("user_id", "42"), cookie("user_name", "test"), cookie("user_check", "secret"))
        account.saveFromResponse(url, login)
        oldPlayer.saveFromResponse(url, listOf(cookie("PHPSESSID", "player-session")))
        val updatedApp = PersistentCookieJar(prefs) { key }
        assertTrue(updatedApp.loadForRequest(url).containsAll(login))
        assertEquals(4, updatedApp.snapshot().size)
        assertTrue(account.snapshot().any { it.name == "PHPSESSID" })
    }

    @Test
    fun emptyResponseDoesNotRewriteStoredCredentials() {
        val prefs = CookieUpdatePreferences()
        val jar = PersistentCookieJar(prefs) { key }
        jar.saveFromResponse(url, listOf(cookie("user_check", "secret")))
        val saved = prefs.getString("cookies_encrypted_v1", null)
        jar.saveFromResponse(url, emptyList())
        assertEquals(saved, prefs.getString("cookies_encrypted_v1", null))
    }

    @Test
    fun temporaryKeystoreFailureSurvivesUpdateAndRetriesWithoutOverwriting() {
        val prefs = CookieUpdatePreferences()
        val login = listOf(cookie("user_check", "secret"))
        PersistentCookieJar(prefs) { key }.saveFromResponse(url, login)
        val saved = prefs.getString("cookies_encrypted_v1", null)
        var available = false
        val updatedApp = PersistentCookieJar(prefs) {
            if (!available) throw java.security.KeyStoreException("temporarily unavailable")
            key
        }
        assertTrue(updatedApp.snapshot().isEmpty())
        assertTrue(updatedApp.hasPendingRestore())
        updatedApp.saveFromResponse(url, listOf(cookie("PHPSESSID", "anonymous")))
        assertEquals(saved, prefs.getString("cookies_encrypted_v1", null))
        available = true
        assertEquals(login, updatedApp.snapshot())
        assertFalse(updatedApp.hasPendingRestore())
    }

    @Test
    fun logoutCannotBeUndoneByAnOlderClient() {
        val prefs = CookieUpdatePreferences()
        val account = PersistentCookieJar(prefs) { key }
        account.saveFromResponse(url, listOf(cookie("user_check", "secret")))
        val player = PersistentCookieJar(prefs) { key }
        account.clear()
        player.saveFromResponse(url, listOf(cookie("PHPSESSID", "anonymous")))
        assertFalse(PersistentCookieJar(prefs) { key }.snapshot().any { it.name == "user_check" })
    }

    @Test
    fun temporaryEncryptionFailureRetriesBeforeNextRestart() {
        val prefs = CookieUpdatePreferences()
        var available = false
        val jar = PersistentCookieJar(prefs) {
            if (!available) throw java.security.KeyStoreException("temporarily unavailable")
            key
        }
        jar.saveFromResponse(url, listOf(cookie("user_check", "secret")))
        assertFalse(prefs.contains("cookies_encrypted_v1"))
        available = true
        assertEquals("secret", jar.snapshot().single().value)
        assertEquals("secret", PersistentCookieJar(prefs) { key }.snapshot().single().value)
    }

    @Test
    fun credentialsAreWrittenSynchronouslyBeforeLoginCompletes() {
        val prefs = CookieUpdatePreferences()
        val jar = PersistentCookieJar(prefs) { key }
        jar.saveFromResponse(url, listOf(cookie("user_check", "secret")))
        assertEquals(0, prefs.asynchronousWrites)
        assertTrue(prefs.synchronousWrites > 0)
        assertEquals("secret", PersistentCookieJar(prefs) { key }.snapshot().single().value)
    }
}

private class CookieUpdatePreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()
    var synchronousWrites = 0
    var asynchronousWrites = 0
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun contains(key: String) = values.containsKey(key)
    override fun getString(key: String, defValue: String?) = (values[key] ?: defValue) as String?
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = (values[key] ?: defValues) as MutableSet<String>?
    override fun getInt(key: String, defValue: Int) = (values[key] ?: defValue) as Int
    override fun getLong(key: String, defValue: Long) = (values[key] ?: defValue) as Long
    override fun getFloat(key: String, defValue: Float) = (values[key] ?: defValue) as Float
    override fun getBoolean(key: String, defValue: Boolean) = (values[key] ?: defValue) as Boolean
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun apply() { asynchronousWrites++; writeChanges() }
        override fun commit(): Boolean { synchronousWrites++; writeChanges(); return true }
        private fun writeChanges() {
            if (clearAll) values.clear()
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
