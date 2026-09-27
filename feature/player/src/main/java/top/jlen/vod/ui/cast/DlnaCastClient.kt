package top.jlen.vod.ui

import android.content.Context
import android.net.wifi.WifiManager
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element

// 局域网内支持 DLNA 渲染（AVTransport）的设备
internal data class DlnaDevice(
    val location: String,
    val name: String,
    val controlUrl: String
)

// 应用内 DLNA 投屏客户端：SSDP 发现设备 + SOAP 控制播放
internal object DlnaCastClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun discover(context: Context, timeoutMs: Int = 4000): List<DlnaDevice> =
        withContext(Dispatchers.IO) {
            val locations = searchLocations(context, timeoutMs)
            locations.mapNotNull { location -> runCatching { fetchDevice(location) }.getOrNull() }
                .distinctBy { it.controlUrl }
                .sortedBy { it.name }
        }

    suspend fun play(
        device: DlnaDevice,
        url: String,
        title: String,
        positionMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching { soap(device, "Stop", "") }
        val metadata = buildDidlMetadata(url, title.ifBlank { "JlenVideo" })
        val loaded = runCatching {
            soap(
                device,
                "SetAVTransportURI",
                "<CurrentURI>${escapeXml(url)}</CurrentURI>" +
                    "<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>"
            )
        }.getOrDefault(false)
        if (!loaded) return@withContext false
        val playing = runCatching { soap(device, "Play", "<Speed>1</Speed>") }.getOrDefault(false)
        if (playing && positionMs > 5_000L) {
            // 部分设备需要等待媒体加载后才接受 Seek，失败不影响投屏结果
            repeat(3) {
                kotlinx.coroutines.delay(1500)
                val sought = runCatching {
                    soap(
                        device,
                        "Seek",
                        "<Unit>REL_TIME</Unit><Target>${formatTime(positionMs)}</Target>"
                    )
                }.getOrDefault(false)
                if (sought) return@withContext true
            }
        }
        playing
    }

    suspend fun stop(device: DlnaDevice) = withContext(Dispatchers.IO) {
        runCatching { soap(device, "Stop", "") }
    }

    private fun searchLocations(context: Context, timeoutMs: Int): Set<String> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching {
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
                        runCatching { socket.send(DatagramPacket(payload, payload.size, group, SSDP_PORT)) }
                    }
                }
                val buffer = ByteArray(8 * 1024)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
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
                        ?.takeIf(String::isNotBlank)
                        ?.let(locations::add)
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
        return locations
    }

    private fun fetchDevice(location: String): DlnaDevice? {
        val body = httpClient.newCall(Request.Builder().url(location).build()).execute().use {
            if (!it.isSuccessful) return null
            it.body?.string()
        } ?: return null
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(body.toByteArray()))
        val root = document.documentElement
        val name = root.getElementsByTagName("friendlyName").item(0)?.textContent?.trim()
            .orEmpty()
            .ifBlank { URL(location).host }
        val baseUrl = root.getElementsByTagName("URLBase").item(0)?.textContent?.trim()
            ?.takeIf(String::isNotBlank) ?: location
        val services = root.getElementsByTagName("service")
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val type = service.childText("serviceType")
            if (!type.startsWith(AV_TRANSPORT_PREFIX)) continue
            val control = service.childText("controlURL").takeIf(String::isNotBlank) ?: continue
            return DlnaDevice(
                location = location,
                name = name,
                controlUrl = URL(URL(baseUrl), control).toString()
            )
        }
        return null
    }

    private fun soap(device: DlnaDevice, action: String, arguments: String): Boolean {
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$action xmlns:u=\"$AV_TRANSPORT_SERVICE\"><InstanceID>0</InstanceID>$arguments</u:$action>" +
            "</s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(device.controlUrl)
            .header("SOAPACTION", "\"$AV_TRANSPORT_SERVICE#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        return httpClient.newCall(request).execute().use { it.isSuccessful }
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
            normalized.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            normalized.endsWith(".mpd") -> "application/dash+xml"
            normalized.endsWith(".mp4") -> "video/mp4"
            else -> "video/*"
        }
    }

    private fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        return "%02d:%02d:%02d".format(totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60)
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun Element.childText(tag: String): String =
        getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val AV_TRANSPORT_PREFIX = "urn:schemas-upnp-org:service:AVTransport"
    private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    private val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        AV_TRANSPORT_SERVICE
    )
}
