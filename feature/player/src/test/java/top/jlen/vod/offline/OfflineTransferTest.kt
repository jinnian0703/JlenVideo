package top.jlen.vod.offline

import okhttp3.mockwebserver.*
import java.io.File
import okio.Buffer
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class OfflineTransferTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun serve(block: (String) -> ByteArray): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(Buffer().write(block(request.requestUrl!!.encodedPath)))
        }; start()
    }
    private fun transfer(dir: File) = OfflineTransfer(OkHttpClient(), dir, {}, { _,_,_ -> })
    @Test fun lastSegmentIsLocalAfterServerStops() {
        val server = serve { when(it) {
            "/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=10,RESOLUTION=640x360\nsub/list.m3u8"
            "/sub/list.m3u8" -> "#EXTM3U\n#EXTINF:1,\na.ts\n#EXTINF:1,\nb.ts\n#EXT-X-ENDLIST"
            else -> it
        }.toByteArray() }; val dir = tmp.newFolder()
        val result = try { transfer(dir).download("http://127.0.0.1:${server.port}/master.m3u8") } finally { server.shutdown() }
        assertEquals("360p", result.quality)
        val child = File(dir, result.file.readLines().last()); val last = child.readLines().last { !it.startsWith("#") }
        assertEquals("/sub/b.ts", File(dir,last).readText())
    }
    @Test fun rejectsLiveEncryptedAndWebPagesBeforePublishing() {
        for (content in listOf("#EXTM3U\n#EXTINF:1,\na.ts", "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES\n#EXTINF:1,\na.ts\n#EXT-X-ENDLIST", "<html>login required</html>")) {
            val server = serve { content.toByteArray() }; val dir = tmp.newFolder()
            try { assertThrows(OfflineProblem::class.java) { transfer(dir).download("http://127.0.0.1:${server.port}/video." + if (content.startsWith("#")) "m3u8" else "mp4") } } finally { server.shutdown() }
            assertFalse(File(dir,"index.m3u8").exists()); assertFalse(File(dir,"video.mp4").exists())
        }
    }
    @Test fun pausedMp4ResumesOnlyWithMatchingByteRange() {
        val bytes = ByteArray(192 * 1024) { (it % 127).toByte() }
        "ftyp".toByteArray().copyInto(bytes, 4)
        val dir = tmp.newFolder()
        val server = MockWebServer()
        server.use {
            server.enqueue(MockResponse().setHeader("ETag", "\"v1\"").setBody(Buffer().write(bytes)))
            val url = server.url("/clip.mp4").toString()
            val pausing = OfflineTransfer(OkHttpClient(), dir, {}, { _, _, _ -> throw java.util.concurrent.CancellationException() })
            assertThrows(java.util.concurrent.CancellationException::class.java) { pausing.download(url) }
            val offset = File(dir, "video.part").length().toInt()
            assertTrue(offset in 1 until bytes.size)
            assertFalse(File(dir, "video.mp4").exists())
            server.enqueue(MockResponse().setResponseCode(206).setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                .setBody(Buffer().write(bytes, offset, bytes.size - offset)))
            assertArrayEquals(bytes, transfer(dir).download(url).file.readBytes())
            server.takeRequest()
            val resumed = server.takeRequest()
            assertEquals("bytes=$offset-", resumed.getHeader("Range"))
            assertEquals("\"v1\"", resumed.getHeader("If-Range"))
        }
    }

    @Test fun expiredRangeFallsBackToFreshMp4() {
        val dir = tmp.newFolder()
        MockWebServer().use { server ->
            val url = server.url("/clip.mp4").toString()
            val replacement = "0000ftypnew video".toByteArray()
            File(dir, "video.validator").writeText("$url\n\"old\"")
            File(dir, "video.part").writeBytes(ByteArray(256))
            server.enqueue(MockResponse().setResponseCode(416))
            server.enqueue(MockResponse().setBody(Buffer().write(replacement)))
            assertArrayEquals(replacement, transfer(dir).download(url).file.readBytes())
            assertEquals("bytes=256-", server.takeRequest().getHeader("Range"))
            assertNull(server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun retryHlsKeepsCompletedSegments() {
        val dir = tmp.newFolder()
        MockWebServer().use { server ->
            val playlist = "#EXTM3U\n#EXTINF:1,\na.ts\n#EXTINF:1,\nb.ts\n#EXT-X-ENDLIST"
            val url = server.url("/clip.m3u8").toString()
            server.enqueue(MockResponse().setBody(playlist))
            server.enqueue(MockResponse().setBody("first segment"))
            server.enqueue(MockResponse().setResponseCode(503))
            assertThrows(OfflineProblem::class.java) { transfer(dir).download(url) }
            assertFalse(File(dir, "index.m3u8").exists())
            server.enqueue(MockResponse().setBody(playlist))
            server.enqueue(MockResponse().setBody("last segment"))
            assertTrue(transfer(dir).download(url).file.isFile)
            assertEquals(listOf("/clip.m3u8", "/a.ts", "/b.ts", "/clip.m3u8", "/b.ts"), List(server.requestCount) { server.takeRequest().path })
        }
    }
    /** Optional integration run: create real short fixtures with ffmpeg, then decode the tail with the HTTP server shut down. */
    @Test fun decodeOfflineTailForBothFormats() {
        val tool = System.getenv("JLEN_TEST_FFMPEG"); org.junit.Assume.assumeTrue(!tool.isNullOrBlank())
        fun run(vararg args: String) { val proc = ProcessBuilder(listOf(tool!!,"-v","error","-y") + args).redirectErrorStream(true).start(); val log = proc.inputStream.bufferedReader().readText(); assertEquals(log,0,proc.waitFor()) }
        val input = tmp.newFolder(); val original = File(input,"clip.mp4")
        run("-f","lavfi","-i","testsrc2=size=128x72:rate=10","-t","4","-c:v","libx264","-g","10",original.path)
        run("-i",original.path,"-c","copy","-hls_time","1","-hls_list_size","0",File(input,"clip.m3u8").path)
        val server = serve { File(input,it.removePrefix("/")).readBytes() }
        val clips = try { listOf("mp4","m3u8").map { transfer(tmp.newFolder()).download("http://127.0.0.1:${server.port}/clip.$it").file } } finally {server.shutdown()}
        for (clip in clips) { val frame = File(tmp.newFolder(),"last.png"); run("-protocol_whitelist","file,crypto,data","-ss","3.6","-i",clip.path,"-frames:v","1",frame.path); assertTrue(frame.length()>0) }
    }
}
