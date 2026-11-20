package top.jlen.vod.offline

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

internal fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

internal class OfflineProblem(message: String) : IOException(message)
internal data class LocalVideo(val file: File, val quality: String)

internal data class TransferProgress(
    val bytes: Long, val totalBytes: Long, val receivedBytes: Long, val percent: Int, val quality: String
)

/** Bytes already on disk never enter the rate calculation. Sample even during a stalled read. */
internal class DownloadRate(private var timeMs: Long) {
    private var previousBytes = 0L
    fun sample(receivedBytes: Long, nowMs: Long): Long {
        val interval = nowMs - timeMs
        if (interval <= 0) return 0
        val rate = ((receivedBytes - previousBytes).coerceAtLeast(0) * 1000.0 / interval).toLong()
        previousBytes = receivedBytes
        timeMs = nowMs
        return rate
    }
}

/** Publishes the local entry point only after every referenced byte has been saved. */
internal class OfflineTransfer(
    private val client: OkHttpClient, private val dir: File,
    private val checkActive: () -> Unit,
    private val progress: (TransferProgress) -> Unit
) {
    private val assets = linkedMapOf<HttpUrl, String>()
    private var quality = "原画质"
    private var saved = 0L
    private var received = 0L
    private fun report(bytes: Long, percent: Int, total: Long = -1) =
        progress(TransferProgress(bytes, total, received, percent, quality))
    private fun checkSpace(needed: Long = 0) {
        if (dir.usableSpace - needed < 32L * 1024 * 1024) throw OfflineProblem("存储空间不足，请释放空间后重试")
    }
    private fun get(url: HttpUrl, range: String? = null, validator: String? = null): Response {
        checkActive()
        val req = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
            if (range != null) header("Range", range)
            if (validator != null) header("If-Range", validator)
        }.build()
        val response = client.newCall(req).execute()
        if (!response.isSuccessful && !(range != null && response.code == 416)) {
            response.close()
            throw OfflineProblem(when (response.code) {
                401, 403 -> "链接已过期或需要在线授权，请重试或更换线路"
                else -> "片源返回 HTTP ${response.code}，请稍后重试"
            })
        }
        return response
    }
    fun download(url: String): LocalVideo {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("无法创建下载目录")
        checkSpace()
        val uri = url.toHttpUrl()
        return if (uri.encodedPath.endsWith(".m3u8", true)) hls(uri) else mp4(uri)
    }
    private fun mp4(url: HttpUrl): LocalVideo {
        val temp = File(dir, "video.part")
        val tagFile = File(dir, "video.validator")
        val tag = tagFile.takeIf { it.exists() }?.readLines().orEmpty()
        val offset = temp.length().takeIf { tag.size == 2 && tag[0] == url.toString() } ?: 0L
        val response = if (offset > 0) {
            val resumed = get(url, "bytes=$offset-", tag[1])
            if (resumed.code == 416) {
                // The representation may have shrunk, or all bytes arrived before a process restart.
                resumed.close()
                tagFile.delete()
                get(url)
            } else resumed
        } else get(url)
        response.use { r ->
            val body = r.body ?: throw IOException("片源为空")
            val partial = r.code == 206
            val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(r.header("Content-Range").orEmpty())
            if (partial && (range == null || range.groupValues[1].toLong() != offset || range.groupValues[2].toLong() != range.groupValues[3].toLong() - 1)) {
                tagFile.delete(); throw OfflineProblem("片源返回的续传位置不一致，请重试")
            }
            val start = if (partial) offset else 0L
            val total = if (partial) range!!.groupValues[3].toLong() else body.contentLength()
            if (total > start) checkSpace(total - start)
            val validator = r.header("ETag")?.takeUnless { it.startsWith("W/") } ?: r.header("Last-Modified")
            RandomAccessFile(temp, "rw").use { out ->
                out.setLength(start); out.seek(start)
                if (validator != null) tagFile.writeText("$url\n$validator") else tagFile.delete()
                val stream = body.source()
                if (start == 0L && (!stream.request(8) || stream.buffer.clone().apply { skip(4) }.readUtf8(4) !in setOf("ftyp", "moov", "mdat", "wide", "free")))
                    throw OfflineProblem("该线路没有返回可下载的 MP4 视频")
                var nbytes = start
                report(start, if (total > 0) (100.0 * start / total).toInt().coerceAtMost(99) else -1, total)
                pump(stream.inputStream()) { buf, n ->
                    out.write(buf, 0, n); nbytes += n
                    report(nbytes, if (total > 0) (100.0 * nbytes / total).toInt().coerceAtMost(99) else -1, total)
                }
                if (total >= 0 && nbytes != total) throw IOException("视频下载不完整")
            }
        }
        checkActive()
        val entry = File(dir, "video.mp4")
        publish(temp, entry); tagFile.delete()
        return LocalVideo(entry, quality)
    }
    private fun pump(input: java.io.InputStream, write: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(64 * 1024); var sinceCheck = Long.MAX_VALUE
        while (true) {
            checkActive()
            if (sinceCheck >= 1024 * 1024) { checkSpace(); sinceCheck = 0 }
            val n = input.read(buf); if (n < 0) break
            received += n
            write(buf, n); sinceCheck += n
        }
    }
    private fun publish(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("无法保存离线文件")
    }
    private data class Manifest(val base: HttpUrl, val lines: List<String>)
    private fun manifest(url: HttpUrl): Manifest = get(url).use { r ->
        val source = r.body!!.source()
        if (source.request(4L * 1024 * 1024 + 1)) throw OfflineProblem("播放列表过大")
        val lines = source.readUtf8().trimStart('\uFEFF').lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != "#EXTM3U") throw OfflineProblem("该线路没有返回 HLS 播放列表")
        Manifest(r.request.url, lines)
    }
    private fun resolve(base: HttpUrl, value: String) = base.resolve(value)
        ?: throw OfflineProblem("播放列表引用了不支持的地址")
    private val uriAttr = Regex("""(?:^|[:,])URI="([^"]+)"""")
    private fun localAsset(url: HttpUrl): String = assets.getOrPut(url) {
        val ext = url.encodedPath.substringAfterLast('.', "").lowercase().takeIf { it in setOf("ts", "m4s", "mp4", "aac", "vtt", "mp3", "ac3") } ?: "bin"
        "${digest(url.toString())}.$ext"
    }
    private fun media(m: Manifest): String {
        if ("#EXT-X-ENDLIST" !in m.lines) throw OfflineProblem("直播或尚未结束的 HLS 不支持离线")
        if (m.lines.none { it.startsWith("#EXTINF:") }) throw OfflineProblem("播放列表没有可下载的分片")
        return m.lines.joinToString("\n", postfix = "\n") { line -> when {
            line.startsWith("#EXT-X-KEY:") -> if (line == "#EXT-X-KEY:METHOD=NONE") line else throw OfflineProblem("此线路使用加密播放，请更换线路")
            line.startsWith("#EXT-X-MAP:") -> {
                val group = uriAttr.find(line)?.groups?.get(1) ?: throw OfflineProblem("初始化分片地址无效")
                line.replaceRange(group.range, localAsset(resolve(m.base, group.value)))
            }
            line.startsWith("#EXT-X-GAP") || line.startsWith("#EXT-X-DEFINE:") -> throw OfflineProblem("此 HLS 播放列表暂不支持离线")
            line.startsWith("#") && (line.contains("URI=") || line.startsWith("#EXT-X-PART")) -> throw OfflineProblem("此 HLS 含有无法离线的附加资源")
            line.startsWith("#") -> line
            else -> localAsset(resolve(m.base, line))
        } }
    }
    private fun attr(line: String, key: String): String? = Regex("(?:[:,])$key=(?:\"([^\"]*)\"|([^,]*))").find(line)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
    private fun hls(url: HttpUrl): LocalVideo {
        val root = manifest(url); val lists = linkedMapOf<String, String>()
        if (root.lines.any { it.startsWith("#EXT-X-SESSION-KEY") }) throw OfflineProblem("此线路需要授权播放，无法离线")
        val variants = root.lines.withIndex().filter { it.value.startsWith("#EXT-X-STREAM-INF:") }
        val entry = if (variants.isEmpty()) media(root) else {
            val v = variants.maxBy { attr(it.value, "BANDWIDTH")?.toLongOrNull() ?: 0L }
            val path = root.lines.getOrNull(v.index + 1)?.takeUnless { it.startsWith("#") } ?: throw OfflineProblem("清晰度线路地址为空")
            val resolution = attr(v.value, "RESOLUTION")?.substringAfter('x')?.toIntOrNull()
            quality = resolution?.let { "${it}p" } ?: "原画质"
            lists["picture.m3u8"] = media(manifest(resolve(root.base, path)))
            val audioId = attr(v.value, "AUDIO")
            val audio = root.lines.filter { it.startsWith("#EXT-X-MEDIA:") && attr(it, "TYPE") == "AUDIO" && attr(it, "GROUP-ID") == audioId }.maxByOrNull { attr(it,"DEFAULT") == "YES" }
            if (attr(v.value, "VIDEO") != null || (audioId != null && audio == null)) throw OfflineProblem("此线路使用不支持的外部音视频轨道")
            val audioLine = audio?.let { line -> uriAttr.find(line)?.groups?.get(1)?.let { group ->
                lists["audio.m3u8"] = media(manifest(resolve(root.base, group.value)))
                line.replaceRange(group.range, "audio.m3u8")
            } ?: line }
            val stream = v.value.replace(Regex(""",(?:SUBTITLES|CLOSED-CAPTIONS)=(?:"[^"]*"|[^,]+)"""), "")
            listOfNotNull("#EXTM3U", audioLine, stream, "picture.m3u8").joinToString("\n", postfix = "\n")
        }
        for ((index, pair) in assets.entries.withIndex()) {
            checkActive(); val file = File(dir, pair.value)
            if (!file.exists()) get(pair.key).use { r ->
                if (r.code != 200 || r.header("Content-Type").orEmpty().contains("html", true)) throw OfflineProblem("分片不可下载，请更换线路")
                val body = r.body!!; val tmp = File(dir, "segment.part"); val len = body.contentLength()
                if (len > 0) checkSpace(len)
                tmp.outputStream().use { out -> pump(body.byteStream()) { buf, n -> out.write(buf,0,n); report(saved + tmp.length(), index * 99 / assets.size) } }
                if (tmp.length() == 0L || (len >= 0 && len != tmp.length())) throw IOException("分片下载不完整")
                publish(tmp, file)
            }
            saved += file.length(); report(saved, (index + 1) * 99 / assets.size)
        }
        checkActive(); lists["index.m3u8"] = entry
        lists.forEach { (name, text) -> val tmp = File(dir,"playlist.part"); tmp.writeText(text); publish(tmp,File(dir,name)) }
        val keep = assets.values.toSet() + lists.keys
        dir.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
        return LocalVideo(File(dir,"index.m3u8"), quality)
    }
}
