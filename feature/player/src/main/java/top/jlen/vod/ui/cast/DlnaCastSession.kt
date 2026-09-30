package top.jlen.vod.ui

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

// 进程级会话：内联播放器和全屏页共享；网络任务不随按钮隐藏或 Activity 销毁而取消。
internal object DlnaCastSession {
    private var applicationContext: Context? = null
    private val controller = DlnaSessionController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        transport = DlnaCastClient,
        onMessage = { message ->
            withContext(Dispatchers.Main) {
                applicationContext?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
            }
        }
    )
    val state = controller.state
    val stopped = controller.stopped
    val isCasting: Boolean get() = state.value.isActive

    fun castTo(context: Context, device: DlnaDevice, url: String, title: String, startPositionMs: Long) {
        applicationContext = context.applicationContext
        controller.castTo(device, url, title, startPositionMs)
    }

    fun pushIfConnected(context: Context, url: String, title: String) {
        applicationContext = context.applicationContext
        controller.pushIfConnected(url, title)
    }

    fun disconnect() = controller.disconnect()

    fun acknowledgeStopped(id: Long) = controller.acknowledgeStopped(id)
}
