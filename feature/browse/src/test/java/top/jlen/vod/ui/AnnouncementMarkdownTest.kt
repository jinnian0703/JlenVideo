package top.jlen.vod.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementMarkdownTest {
    @Test
    fun markdownUpdateNotesBecomeRichHtml() {
        val markdown = """## 更新日志

- 修复头像闪动
- **支持 Markdown** 更新说明

[下载地址](https://example.com/app.apk)
"""

        val html = normalizeAnnouncementContent(markdown)

        assertTrue(html.contains("<h2>更新日志</h2>"))
        assertTrue(html.contains("<ul><li>修复头像闪动</li>"))
        assertTrue(html.contains("<strong>支持 Markdown</strong>"))
        assertTrue(html.contains("href=\"https://example.com/app.apk\""))
    }

    @Test
    fun existingRichHtmlIsPreserved() {
        val html = "<p><strong>更新</strong></p>"

        assertFalse(looksLikeAnnouncementMarkdown(html))
        assertTrue(normalizeAnnouncementContent(html) == html)
    }
}
