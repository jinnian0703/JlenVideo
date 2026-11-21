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

    @Test fun loginSeparatesCaptchaRejectionFromCredentialErrors() = runBlocking {
        val codes = ArrayDeque(listOf(1002, 1003, 1))
        val client = api { """{"code":${codes.removeFirst()},"msg":"response"}""" }
        assertTrue(runCatching { client.login("user", "password", "old") }.exceptionOrNull() is LoginCaptchaRejected)
        assertEquals(1003, (runCatching { client.login("user", "password", "new") }.exceptionOrNull() as CmsApiError).code)
        client.login("user", "correct", "next")
    }

    @Test fun viewingFeaturesRequirePublicAccessAndIndividualFlags() = runBlocking {
        fun configured(access: Boolean) = api { """{"code":1,"data":{"access":{"public_api_enabled":$access},"features":{"live":1,"danmaku":true,"chatroom":false}}}""" }
        assertEquals(ViewingFeatures(true, false, true), configured(true).viewingFeatures())
        assertEquals(ViewingFeatures(), configured(false).viewingFeatures())
    }

    @Test fun viewingRequestsKeepFilmChatSeparateFromTimedEpisodeComments() = runBlocking {
        val writes = mutableListOf<String>()
        val client = api { request ->
            val path = request.url.encodedPath.substringAfter("api.php/")
            when (path) {
                "auth/me" -> """{"code":1,"info":{"user_id":7,"csrf_token":"token"}}"""
                "chatroom/get_list" -> {
                    assertEquals("42", request.url.queryParameter("vod_id"))
                    assertEquals("99", request.url.queryParameter("after_id"))
                    """{"code":1,"info":{"rows":[{"chat_id":100,"user_name":"观众","chat_content":"好看"}],"last_id":100}}"""
                }
                "danmaku/get_list" -> {
                    assertEquals("2", request.url.queryParameter("sid"))
                    assertEquals("3", request.url.queryParameter("nid"))
                    """{"code":1,"info":{"rows":[{"danmaku_id":4,"danmaku_time":"15.25","danmaku_text":"好看"}]}}"""
                }
                else -> {
                    assertEquals("token", request.header("X-CSRF-Token"))
                    val body = request.body as FormBody
                    val form = (0 until body.size).associate { body.name(it) to body.value(it) }
                    when (path) {
                        "danmaku/send" -> assertEquals(mapOf("vod_id" to "42", "sid" to "2", "nid" to "3", "time" to "15.25", "text" to "好看"), form)
                        "chatroom/send" -> assertEquals(mapOf("vod_id" to "42", "content" to "好看"), form)
                        "chatroom/report" -> assertEquals(mapOf("chat_id" to "100"), form)
                        else -> error("Unexpected path $path")
                    }
                    writes += path
                    """{"code":1}"""
                }
            }
        }
        val episode = EpisodeThread("42", 1, 2)
        assertEquals(100, client.chat("42", 99).single().id)
        assertEquals(15250L, client.danmaku(episode).single().timeMs)
        client.sendDanmaku("7", episode, 15250, "好看")
        client.sendChat("7", "42", "好看")
        client.reportChat("7", 100)
        assertEquals(3, writes.size)
    }

    @Test fun liveBrowserUsesCategoryOffsetAndReadsNamedStreamsFromDetail() = runBlocking {
        val channel = """{"live_id":17,"live_name":"新闻","cate_name":"电视","live_url_list":[{"name":"高清","url":"https://example.com/news.m3u8"}]}"""
        val client = api { request -> when (request.url.encodedPath.substringAfter("api.php/")) {
            "live/get_category" -> """{"code":1,"list":[{"cate_id":2,"cate_name":"电视"}]}"""
            "live/get_list" -> {
                assertEquals("2", request.url.queryParameter("cate_id"))
                assertEquals("30", request.url.queryParameter("offset"))
                """{"code":1,"list":[$channel],"total":31}"""
            }
            "live/get_detail" -> { assertEquals("17", request.url.queryParameter("live_id")); """{"code":1,"info":$channel}""" }
            else -> error("Unexpected request")
        } }
        val page = client.liveChannels(client.liveCategories().single().id, 30)
        assertNull(page.nextOffset)
        assertEquals("高清", client.liveChannel(page.channels.single().id).sources.single().name)
    }

    @Test fun failureReportKeepsLineAndEpisodeNumbersOneBased() = runBlocking {
        val reports = mutableListOf<List<String>>()
        val client = api { request ->
            assertEquals("/site/api.php/ulog/report_fail", request.url.encodedPath)
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            reports += listOf("vod_id", "sid", "nid", "play_from").map(fields::getValue)
            """{"code":1}"""
        }
        client.reportFailure("", "42", 0, 0, "A")
        client.reportFailure("", "42", 1, 2, "B")
        assertEquals(listOf(listOf("42", "1", "1", "A"), listOf("42", "2", "3", "B")), reports)
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
