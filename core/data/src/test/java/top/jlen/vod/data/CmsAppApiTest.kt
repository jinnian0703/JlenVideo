package top.jlen.vod.data

import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CmsAppApiTest {
    private fun api(reply: (Request) -> String): CmsAppApi {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(request).toResponseBody()).build()
        }.build()
        return CmsAppApi(http, "https://cms.example/site/")
    }

    @Test fun progressUsesSessionCsrfAndServerUnits() = runBlocking {
        val writes = mutableListOf<Request>()
        val client = api { request ->
            when (request.url.encodedPath) {
                "/site/api.php/auth/me" -> """{"code":1,"info":{"user_id":7,"csrf_token":"session-token"}}"""
                "/site/api.php/ulog/progress" -> { writes += request; """{"code":1}""" }
                else -> error("Unexpected route")
            }
        }
        client.progress("7", "42", 1, 2, 12_999, 90_000)
        val write = writes.single()
        assertEquals("session-token", write.header("X-CSRF-Token"))
        val form = write.body as FormBody
        assertEquals(mapOf("vod_id" to "42", "sid" to "2", "nid" to "3", "point" to "12", "duration" to "90"),
            (0 until form.size).associate { form.name(it) to form.value(it) })
        try { client.progress("8", "42", 0, 0, 3000, 9000); fail("Account changed") } catch (_: IOException) {}
        assertEquals(1, writes.size)
    }

    @Test fun restoreMapsToExistingEpisodeOnly() = runBlocking {
        val sources = listOf(PlaySource("线路", listOf(Episode("一", "a"), Episode("二", "b"))))
        val replies = ArrayDeque(listOf("null", """{"sid":1,"nid":2,"point":34,"time":123}""", """{"sid":2,"nid":1,"point":8}"""))
        val client = api { """{"code":1,"info":${replies.removeFirst()}}""" }
        assertNull(client.resume("42", sources))
        val resume = client.resume("42", sources)!!
        assertEquals(1, resume.episodeIndex)
        assertEquals(34_000L, resume.positionMs)
        assertEquals(123_000L, resume.updatedAt)
        assertNull(client.resume("42", sources))
    }

    @Test fun disabledPublicApiDoesNotAttemptRestrictedFavoriteRequest() = runBlocking {
        val routes = mutableListOf<String>()
        val client = api { routes += it.url.encodedPath; """{"code":1,"data":{"access":{"public_api_enabled":false},"features":{"tasks":false},"auth":{"cookie_enabled":false}}}""" }
        assertFalse(client.cookieLoginEnabled())
        assertNull(client.favorite("42"))
        assertFalse(client.tasksEnabled())
        assertEquals(listOf("/site/api.php/app/bootstrap"), routes)
    }

    @Test fun structuredSourcesRespectSeparateDownloadPassword() {
        val item = Gson().fromJson("""{"vod_id":"42","play_sources":[{"from":"A","episodes":[{"name":"一","url":"v.mp4?token=t"}]}],"download_sources":[],"vod_pwd_down":1}""", VodItem::class.java)
        assertEquals("/v.mp4?token=t", item.playGroups!!.toSources { "/$it" }.single().episodes.single().url)
        assertTrue(item.downloadGroups!!.isEmpty())
        assertTrue(item.passwordProtected)
    }
}
