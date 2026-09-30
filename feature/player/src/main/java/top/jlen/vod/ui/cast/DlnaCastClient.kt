package top.jlen.vod.ui

import android.content.Context
import android.net.wifi.WifiManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.jlen.vod.data.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element

// 局域网内支持 DLNA 渲染（AVTransport）的设备
internal data class DlnaDevice(
    val location: String,
    val name: String,
    val controlUrl: String,
    val serviceType: String = "urn:schemas-upnp-org:service:AVTransport:1"
)

// 投屏推送结果：区分设备无响应与设备拒绝播放
internal enum class DlnaCastResult {
    Success,
    NoResponse,
    Rejected
}

// 与 runCatching 相同，但不吞掉协程取消异常
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

// SOAP 请求返回 HTTP 错误（设备拒绝该操作）
private class DlnaRejectedException(action: String, code: Int) : IOException("$action rejected: $code")

// 应用内 DLNA 投屏客户端：SSDP 发现设备 + SOAP 控制播放
internal object DlnaCastClient : DlnaTransport {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun discover(context: Context, timeoutMs: Int = 4000): List<DlnaDevice> =
        withContext(Dispatchers.IO) {
            val locations = searchLocations(context, timeoutMs)
            // 限制并发与总时长；慢设备超时后仍保留已发现的设备。
            val devices = ConcurrentLinkedQueue<DlnaDevice>()
            val permits = Semaphore(4)
            withTimeoutOrNull(DESCRIPTION_TOTAL_TIMEOUT_MS) {
                coroutineScope {
                    locations.map { location ->
                        async {
                            permits.withPermit {
                                runCatchingCancellable { fetchDevice(location) }.getOrNull()?.let(devices::add)
                            }
                        }
                    }.awaitAll()
                }
            }
            devices.toList()
                .distinctBy { it.controlUrl }
                .sortedBy { it.name }
        }

    // 推送并开始播放；Play 成功即返回，Seek 由调用方在后台处理
    override suspend fun play(
        device: DlnaDevice,
        url: String,
        title: String
    ): DlnaCastResult = withContext(Dispatchers.IO) {
        runCatchingCancellable { soap(device, "Stop", "") }
        val metadata = buildDidlMetadata(url, title.ifBlank { "JlenVideo" })
        val loaded = runCatchingCancellable {
            soap(
                device,
                "SetAVTransportURI",
                "<CurrentURI>${escapeXml(url)}</CurrentURI>" +
                    "<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>"
            )
        }
        loaded.exceptionOrNull()?.let { return@withContext it.toCastResult() }
        val playing = runCatchingCancellable { soap(device, "Play", "<Speed>1</Speed>") }
        playing.exceptionOrNull()?.let { return@withContext it.toCastResult() }
        DlnaCastResult.Success
    }

    override suspend fun seek(device: DlnaDevice, positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            soap(device, "Seek", "<Unit>REL_TIME</Unit><Target>${formatTime(positionMs)}</Target>")
        }.isSuccess
    }

    override suspend fun stop(device: DlnaDevice): Unit = withContext(Dispatchers.IO) {
        runCatchingCancellable { soap(device, "Stop", "") }
        Unit
    }

    // 查询传输状态（PLAYING / STOPPED / NO_MEDIA_PRESENT 等），失败返回 null
    override suspend fun transportState(device: DlnaDevice): String? = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            soap(device, "GetTransportInfo", "").let { extractTag(it, "CurrentTransportState") }
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    // 查询电视当前播放进度（RelTime），失败返回 null
    override suspend fun positionMs(device: DlnaDevice): Long? = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            soap(device, "GetPositionInfo", "").let { extractTag(it, "RelTime") }
        }.getOrNull()?.let(::parseTime)
    }

    private suspend fun searchLocations(context: Context, timeoutMs: Int): Set<String> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatchingCancellable {
            wifiManager?.createMulticastLock("jlen-dlna")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName(SSDP_ADDRESS)
                SEARCH_TARGETS.forEach { target ->
                    val payload = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 3\r\n" +
                            "ST: $target\r\n\r\n"
                        ).toByteArray()
                    repeat(2) {
                        runCatchingCancellable { socket.send(DatagramPacket(payload, payload.size, group, SSDP_PORT)) }
                    }
                }
                val buffer = ByteArray(8 * 1024)
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
                while (System.nanoTime() < deadline && locations.size < 48) {
                    // 页面关闭或弹窗取消后及时退出接收循环
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    String(packet.data, 0, packet.length).lineSequence()
                        .firstOrNull { it.startsWith("location:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                        ?.takeIf { isTrustedLocation(it, packet.address) }
                        ?.let(locations::add)
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
        return locations
    }

    // 只接受 http/https、私网/链路本地地址，且必须与 SSDP 响应来源地址一致
    internal fun isTrustedLocation(location: String, source: InetAddress?): Boolean {
        if (source == null) return false
        val url = runCatching { URL(location) }.getOrNull() ?: return false
        if (url.protocol != "http" && url.protocol != "https") return false
        if (url.userInfo != null) return false
        val host = url.host.removePrefix("[").removeSuffix("]").takeIf(String::isNotBlank) ?: return false
        // 仅接受数字 IP，避免域名再次解析时换成外网地址，也不在 SSDP 循环中等待 DNS。
        if (':' !in host && !host.matches(Regex("[0-9]+(?:\\.[0-9]+){3}"))) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return address.isPrivateLan() && address == source
    }

    private fun InetAddress.isPrivateLan(): Boolean {
        if (isSiteLocalAddress || isLinkLocalAddress) return true
        // IPv6 唯一本地地址 fc00::/7
        return this is Inet6Address && (address[0].toInt() and 0xFE) == 0xFC
    }

    private suspend fun fetchDevice(location: String): DlnaDevice? {
        val bytes = httpClient.newCall(Request.Builder().url(location).build()).await().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_DESCRIPTION_BYTES) return null
            body.byteStream().readLimited(MAX_DESCRIPTION_BYTES) ?: return null
        }
        return parseDeviceDescription(location, bytes)
    }

    internal fun parseDeviceDescription(location: String, bytes: ByteArray): DlnaDevice? {
        if (bytes.size > MAX_DESCRIPTION_BYTES) return null
        val document = parseXml(bytes)
        val root = document.documentElement
        val baseUrl = root.getElementsByTagNameNS("*", "URLBase").item(0)?.textContent?.trim()
            ?.takeIf(String::isNotBlank) ?: location
        val locationHost = URL(location).host
        val services = root.getElementsByTagNameNS("*", "service")
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val type = service.directChildText("serviceType")
            if (!type.matches(Regex("urn:schemas-upnp-org:service:AVTransport:[1-9][0-9]*"))) continue
            val control = service.directChildText("controlURL").takeIf(String::isNotBlank) ?: continue
            val controlUrl = runCatching { URL(URL(baseUrl), control) }.getOrNull() ?: continue
            // 控制地址必须仍指向发现到的那台设备
            if (controlUrl.host != locationHost || controlUrl.userInfo != null) continue
            if (controlUrl.protocol != "http" && controlUrl.protocol != "https") continue
            // friendlyName 取自包含 AVTransport 服务的 <device> 节点，避免拿到根设备或其他子设备的名称
            val deviceNode = service.parentNode?.parentNode as? Element
            val name = deviceNode?.directChildText("friendlyName")
                .orEmpty()
                .ifBlank { locationHost }
            return DlnaDevice(
                location = location,
                name = name,
                controlUrl = controlUrl.toString(),
                serviceType = type
            )
        }
        return null
    }

    // 禁止 DOCTYPE 与外部实体，防止 XXE
    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            isExpandEntityReferences = false
            isXIncludeAware = false
        }
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw org.xml.sax.SAXException("不允许外部实体") }
        }.parse(ByteArrayInputStream(bytes)).also {
            require(it.doctype == null) { "不允许 DOCTYPE" }
        }
    }

    // 读取不超过 limit 字节，超出返回 null
    private fun java.io.InputStream.readLimited(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            if (output.size() + read > limit) return null
            output.write(chunk, 0, read)
        }
        return output.toByteArray()
    }

    // 成功返回响应体；HTTP 错误抛 DlnaRejectedException；网络错误抛 IOException
    private suspend fun soap(device: DlnaDevice, action: String, arguments: String): String {
        kotlin.coroutines.coroutineContext.ensureActive()
        val serviceType = device.serviceType
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$action xmlns:u=\"$serviceType\"><InstanceID>0</InstanceID>$arguments</u:$action>" +
            "</s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(device.controlUrl)
            .header("SOAPACTION", "\"$serviceType#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        return httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw DlnaRejectedException(action, response.code)
            val bytes = response.body?.byteStream()?.readLimited(MAX_DESCRIPTION_BYTES)
                ?: throw IOException("投屏设备响应为空或过大")
            kotlin.coroutines.coroutineContext.ensureActive()
            val xml = bytes.toString(Charsets.UTF_8)
            if (Regex("<(?:[\\w.-]+:)?Fault(?:\\s|>)").containsMatchIn(xml)) {
                throw DlnaRejectedException(action, response.code)
            }
            xml
        }
    }

    private fun Throwable.toCastResult(): DlnaCastResult =
        if (this is DlnaRejectedException) DlnaCastResult.Rejected else DlnaCastResult.NoResponse

    // SOAP 响应结构简单，直接按标签名截取，忽略命名空间前缀
    private fun extractTag(xml: String, tag: String): String? {
        val match = Regex("<(?:\\w+:)?$tag(?:\\s[^>]*)?>([^<]*)</(?:\\w+:)?$tag>").find(xml) ?: return null
        return match.groupValues[1].trim()
    }

    private fun buildDidlMetadata(url: String, title: String): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escapeXml(title)}</dc:title>" +
            "<upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${mimeType(url)}:*\">${escapeXml(url)}</res>" +
            "</item></DIDL-Lite>"

    private fun mimeType(url: String): String {
        val normalized = url.substringBefore('#').substringBefore('?').lowercase()
        return when {
            // application/x-mpegURL 在电视/盒子上的兼容性比 vnd.apple.mpegurl 更好
            normalized.endsWith(".m3u8") -> "application/x-mpegURL"
            normalized.endsWith(".mpd") -> "application/dash+xml"
            else -> "video/mp4"
        }
    }

    internal fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60)
    }

    // 解析 H+:MM:SS[.F]，拒绝负数、越界和溢出。
    internal fun parseTime(value: String): Long? {
        val parts = value.trim().substringBefore('.').split(':')
        if (parts.size != 3 || parts.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } }) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
        val seconds = parts[2].toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
        val remainder = minutes * 60 + seconds
        if (hours > (Long.MAX_VALUE / 1000 - remainder) / 3600) return null
        return (hours * 3600 + remainder) * 1000
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun Element.directChildText(tag: String): String {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            val name = child.localName ?: child.tagName.substringAfter(':')
            if (name == tag) return child.textContent?.trim().orEmpty()
        }
        return ""
    }

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val MAX_DESCRIPTION_BYTES = 256 * 1024
    private const val DESCRIPTION_TOTAL_TIMEOUT_MS = 6_000L
    private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    private val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        AV_TRANSPORT_SERVICE
    )
}
