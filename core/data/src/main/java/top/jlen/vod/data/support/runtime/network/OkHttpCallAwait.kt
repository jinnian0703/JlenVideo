package top.jlen.vod.data

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resumeWithException

/**
 * 以挂起方式执行 OkHttp 请求：协程取消时同步取消底层 Call，
 * 替代在 withContext(IO) 中阻塞调用 execute() 导致的无法响应取消。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // 恢复前若已取消则关闭响应，避免连接泄漏
            continuation.resume(response) { response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    })
}
