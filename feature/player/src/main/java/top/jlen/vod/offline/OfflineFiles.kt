package top.jlen.vod.offline

import android.graphics.BitmapFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

internal fun albumFolder(vod: String, title: String): String {
    val safe = title.replace(Regex("""[\p{Cntrl}/\\:*?"<>|]+"""), "_").trim().trim('.').take(48).ifBlank { "未命名影片" }
    return "$safe-${digest(vod).take(16)}"
}

internal class OfflineFiles(private val root: File) {
    fun episode(folder: String, id: String) = File(File(root, folder), id)
    fun cover(folder: String) = File(File(root, folder), "cover.image")

    fun fetchCover(client: OkHttpClient, url: String, target: File): Boolean {
        val uri = url.toHttpUrlOrNull() ?: return false
        val call = client.newCall(Request.Builder().url(uri).build())
        call.timeout().timeout(10, TimeUnit.SECONDS)
        call.execute().use { response ->
            val body = response.body ?: return false
            val max = 8L * 1024 * 1024
            if (!response.isSuccessful || body.contentLength() > max) return false
            target.parentFile!!.mkdirs()
            val pending = File(target.parentFile, "cover.part")
            try {
                val input = body.byteStream()
                pending.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        if (count > max) return false
                        out.write(buffer, 0, n)
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(pending.path, bounds)
                return bounds.outWidth > 0 && bounds.outHeight > 0 && pending.renameTo(target)
            } finally { pending.delete() }
        }
    }
}
