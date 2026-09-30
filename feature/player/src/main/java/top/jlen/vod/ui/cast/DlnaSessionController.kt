package top.jlen.vod.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface DlnaTransport {
    suspend fun play(device: DlnaDevice, url: String, title: String): DlnaCastResult
    suspend fun seek(device: DlnaDevice, positionMs: Long): Boolean
    suspend fun stop(device: DlnaDevice)
    suspend fun transportState(device: DlnaDevice): String?
    suspend fun positionMs(device: DlnaDevice): Long?
}

internal data class DlnaCastState(
    val device: DlnaDevice? = null,
    val connectingDevice: DlnaDevice? = null,
    val url: String? = null,
    val pendingUrl: String? = null,
    val disconnecting: Boolean = false
) {
    val isActive: Boolean get() = device != null || connectingDevice != null || disconnecting
}

// 保留最近一次结束信息，控制栏隐藏或页面重建时也能恢复对应影片的进度。
internal data class DlnaCastStopped(val id: Long, val url: String?, val positionMs: Long?)

internal class DlnaSessionController(
    private val scope: CoroutineScope,
    private val transport: DlnaTransport,
    private val onMessage: suspend (String) -> Unit = {}
) {
    private val lock = Any()
    private val operations = Mutex()
    private var revision = 0L
    private var castJob: Job? = null
    private var seekJob: Job? = null
    private var monitorJob: Job? = null
    private var lastPositionMs: Long? = null
    private val mutableState = MutableStateFlow(DlnaCastState())
    val state = mutableState.asStateFlow()
    private val mutableStopped = MutableStateFlow<DlnaCastStopped?>(null)
    val stopped = mutableStopped.asStateFlow()

    fun acknowledgeStopped(id: Long) {
        synchronized(lock) {
            if (mutableStopped.value?.id == id) mutableStopped.value = null
        }
    }

    fun castTo(device: DlnaDevice, url: String, title: String, positionMs: Long = 0L) {
        synchronized(lock) { startCast(device, url, title, positionMs, automatic = false) }
    }

    fun pushIfConnected(url: String, title: String) {
        synchronized(lock) {
            val current = mutableState.value
            val device = current.device ?: current.connectingDevice ?: return
            startCast(device, url, title, 0L, automatic = true)
        }
    }

    private fun startCast(device: DlnaDevice, url: String, title: String, positionMs: Long, automatic: Boolean) {
        val current = mutableState.value
        if (url.isBlank() || current.disconnecting) return
        if (current.pendingUrl == url && current.connectingDevice == device) return
        if (current.pendingUrl == null && current.url == url && current.device == device) return
        val token = ++revision
        val previous = castJob
        previous?.cancel()
        seekJob?.cancel()
        monitorJob?.cancel()
        lastPositionMs = null
        mutableState.value = current.copy(connectingDevice = device, pendingUrl = url)
        castJob = scope.launch {
            try {
                previous?.cancelAndJoin()
                operations.withLock {
                    val result = transport.play(device, url, title)
                    val accepted = synchronized(lock) {
                        if (token != revision) false else {
                            if (result == DlnaCastResult.Success) {
                                mutableState.value = DlnaCastState(device = device, url = url)
                                if (positionMs > 0L) scheduleSeek(token, device, positionMs)
                                startMonitor(token, device)
                            } else {
                                finish(token, url, null)
                            }
                            true
                        }
                    }
                    if (!accepted) return@withLock
                    when (result) {
                        DlnaCastResult.Success -> if (!automatic) onMessage("已投屏到 ${device.name}")
                        else -> {
                            val reason = if (result == DlnaCastResult.Rejected) {
                                "设备拒绝播放该地址，可能不支持格式或需要特定请求头"
                            } else {
                                "设备无响应，请确认设备在线且与手机处于同一 Wi-Fi"
                            }
                            onMessage(if (automatic) "切换剧集投屏失败，已断开：$reason" else "投屏失败：$reason")
                        }
                    }
                }
            } finally {
                synchronized(lock) {
                    if (token == revision) {
                        mutableState.value = mutableState.value.copy(connectingDevice = null, pendingUrl = null)
                    }
                }
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            val current = mutableState.value
            if (current.disconnecting) return
            val device = current.device ?: current.connectingDevice ?: return
            val token = ++revision
            val previous = castJob
            previous?.cancel()
            seekJob?.cancel()
            monitorJob?.cancel()
            // Stop 完成前不接受新连接，防止旧 Stop 结束掉刚推送的新影片。
            mutableState.value = current.copy(disconnecting = true)
            castJob = scope.launch {
                previous?.cancelAndJoin()
                operations.withLock {
                    val position = if (current.pendingUrl == null) transport.positionMs(device) ?: lastPositionMs else null
                    try {
                        transport.stop(device)
                    } finally {
                        synchronized(lock) { finish(token, current.pendingUrl ?: current.url, position) }
                    }
                }
            }
        }
    }

    private fun scheduleSeek(token: Long, device: DlnaDevice, positionMs: Long) {
        seekJob = scope.launch {
            repeat(3) {
                delay(1_500L)
                if (!isCurrent(token)) return@launch
                if (transport.seek(device, positionMs)) {
                    synchronized(lock) { if (token == revision) lastPositionMs = positionMs }
                    return@launch
                }
            }
        }
    }

    private fun startMonitor(token: Long, device: DlnaDevice) {
        monitorJob = scope.launch {
            var failures = 0
            var stoppedCount = 0
            var hasPlayed = false
            while (isCurrent(token)) {
                delay(3_000L)
                val transportState = transport.transportState(device)
                if (!isCurrent(token)) return@launch
                if (transportState == null) {
                    if (++failures < 3) continue
                    synchronized(lock) { finish(token, mutableState.value.url, lastPositionMs) }
                    onMessage("投屏设备无响应，已断开")
                    return@launch
                }
                failures = 0
                if (transportState == "PLAYING" || transportState == "PAUSED_PLAYBACK") {
                    hasPlayed = true
                    stoppedCount = 0
                    val position = transport.positionMs(device)
                    synchronized(lock) { if (token == revision && position != null) lastPositionMs = position }
                } else if (transportState == "STOPPED" || transportState == "NO_MEDIA_PRESENT") {
                    // Play 返回成功不代表解码成功，连续停在 STOPPED 也需要结束连接。
                    if (!hasPlayed && ++stoppedCount < 3) continue
                    val position = transport.positionMs(device) ?: lastPositionMs
                    synchronized(lock) { finish(token, mutableState.value.url, position) }
                    onMessage("电视端已停止播放，投屏已断开")
                    return@launch
                }
            }
        }
    }

    private fun isCurrent(token: Long): Boolean = synchronized(lock) { token == revision }

    private fun finish(token: Long, url: String?, positionMs: Long?) {
        if (token != revision) return
        seekJob?.cancel()
        mutableState.value = DlnaCastState()
        mutableStopped.value = DlnaCastStopped(token, url, positionMs)
    }
}
