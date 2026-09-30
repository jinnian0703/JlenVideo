package top.jlen.vod.data

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ParsingRegressionTest {
    @Test
    fun updateAssetsSkipSourceArchivesNullsAndNonApkNames() {
        val json = JsonParser.parseString(
            """{"assets":[null,42,{"name":"source.zip","browser_download_url":"https://example.test/source.zip"},{"name":"app.apk.sha256","browser_download_url":"https://example.test/checksum"},{"name":"broken.apk","browser_download_url":null},{"name":"app.APK","browser_download_url":"https://example.test/app.APK"}]}"""
        ).asJsonObject
        assertEquals("https://example.test/app.APK", parseGithubApkDownloadUrl(json))
        listOf("null", "3", "[]", "{}").forEach { assets ->
            assertEquals("", parseGithubApkDownloadUrl(JsonParser.parseString("{\"assets\":$assets}").asJsonObject))
        }
    }

    @Test
    fun releasePageOnlyReturnsApkLinksAndNotesStayHtml() {
        val page = Jsoup.parse(
            """<a href="/source.zip">Source</a><a href="/releases/download/v2/app.apk?download=1">APK</a>""",
            "https://github.com/example/project/releases/tag/v2"
        )
        assertEquals("https://github.com/releases/download/v2/app.apk?download=1", findGithubApkDownloadUrl(page))
        assertEquals("", findGithubApkDownloadUrl(Jsoup.parse("<a href='/source.zip'>Source</a>")))
        val html = JsonParser.parseString("""{"body_html":"<h2>更新</h2><p>修复登录</p>","body":"ignored"}""").asJsonObject
        assertEquals("<h2>更新</h2><p>修复登录</p>", parseGithubReleaseNotes(html))
        val plain = JsonParser.parseString("""{"body_html":null,"body":"第一行 <test>\n第二行"}""").asJsonObject
        assertEquals("第一行 &lt;test&gt;<br>第二行", parseGithubReleaseNotes(plain))
    }

    @Test
    fun noticeAndHotSearchJsonHelpersAcceptWrongShapes() {
        listOf("null", "3", "true", "[]").forEach { raw ->
            assertNull(JsonParser.parseString(raw).safeObject())
        }
        listOf("null", "3", "true", "{}").forEach { raw ->
            assertNull(JsonParser.parseString(raw).safeArray())
        }
        assertEquals("", JsonParser.parseString("null").safeString())
        assertEquals("", JsonParser.parseString("{}").safeString())
        val notice = JsonParser.parseString("""{"data":null,"items":false,"list":[{"id":1}]}""").asJsonObject
        assertEquals(1, notice.extractNoticeItems().size)
        assertTrue(JsonParser.parseString("""{"data":1,"items":null}""").asJsonObject.extractNoticeItems().isEmpty())
    }

    @Test
    fun chineseLoginMessageAndHistoricalTokenRepairRemainEffective() {
        assertEquals("用户名不存在或密码错误", normalizeLoginFailureMessage("获取用户信息失败"))
        assertEquals("类型", sanitizeUserFacingToken("绫诲瀷"))
        assertEquals("语言", sanitizeUserFacingToken("璇█"))
        assertEquals("年份", sanitizeUserFacingToken("年份"))
    }
}
