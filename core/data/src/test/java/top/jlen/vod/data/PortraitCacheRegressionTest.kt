package top.jlen.vod.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitCacheRegressionTest {
    private val site = "https://example.test"

    @Test
    fun repeatedAccountReadsKeepTheSameAvatarUrl() {
        val store = PortraitVersionStore(InMemoryPreferences(), site)
        repeat(10) {
            assertEquals(
                "$site/upload/avatar.jpg",
                normalizePortraitUrl("$site/", "/upload/avatar.jpg", store.version("alice"))
            )
        }
        assertEquals("https://cdn.example.test/avatar.png?v=7", normalizePortraitUrl(site, "//cdn.example.test/avatar.png?v=7"))
        assertEquals("", normalizePortraitUrl(site, " "))
        assertEquals("", normalizePortraitUrl(site, "deleted", 1L))
    }

    @Test
    fun replacingAnAvatarAtTheSameUrlRefreshesItOnceAndSurvivesRestart() {
        val prefs = InMemoryPreferences()
        val store = PortraitVersionStore(prefs, site)
        val original = normalizePortraitUrl(site, "/avatar.jpg", store.version("alice"))

        store.markUpdated("alice")
        val updated = normalizePortraitUrl(site, "/avatar.jpg", store.version("alice"))
        assertNotEquals(original, updated)
        repeat(10) {
            assertEquals(updated, normalizePortraitUrl(site, "/avatar.jpg", store.version("alice")))
        }

        val restarted = PortraitVersionStore(prefs, "$site/")
        assertEquals(updated, normalizePortraitUrl(site, "/avatar.jpg", restarted.version("alice")))
    }

    @Test
    fun rapidReplacementsDoNotReuseThePreviousCacheVersion() {
        val prefs = InMemoryPreferences()
        val first = PortraitVersionStore(prefs, site)
        val second = PortraitVersionStore(prefs, site)
        first.markUpdated("alice")
        var previous = first.version("alice")
        repeat(10) {
            second.markUpdated("alice")
            val current = first.version("alice")
            assertTrue(current > previous)
            previous = current
        }
    }

    @Test
    fun updatingOneAccountDoesNotInvalidateOtherAccountsOrSites() {
        val prefs = InMemoryPreferences()
        val store = PortraitVersionStore(prefs, site)
        store.markUpdated("alice")
        assertEquals(0L, store.version("bob"))
        assertEquals(0L, PortraitVersionStore(prefs, "https://another.test").version("alice"))
        val before = prefs.all
        store.markUpdated(" ")
        assertEquals(before, prefs.all)
        assertEquals(0L, store.version(""))
    }

    @Test
    fun localVersionPreservesServerVersionAndSignedQueryParameters() {
        val original = "$site/avatar.jpg?version=7&token=a%2Bb&t=123#portrait"
        assertEquals(original, normalizePortraitUrl(site, original))
        val updated = normalizePortraitUrl(site, original, 42L)
        val url = updated.toHttpUrl()
        assertEquals("7", url.queryParameter("version"))
        assertEquals("a+b", url.queryParameter("token"))
        assertEquals("123", url.queryParameter("t"))
        assertEquals("42", url.queryParameter("_jlen_portrait"))
        assertEquals("portrait", url.fragment)
        assertEquals(updated, normalizePortraitUrl(site, updated, 42L))
        assertEquals(listOf("43"), normalizePortraitUrl(site, updated, 43L).toHttpUrl().queryParameterValues("_jlen_portrait"))
    }
}
