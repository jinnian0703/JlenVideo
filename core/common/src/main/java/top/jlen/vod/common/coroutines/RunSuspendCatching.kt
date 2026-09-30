package top.jlen.vod.common.coroutines

import kotlin.coroutines.cancellation.CancellationException

/**
 * 与 runCatching 相同，但会重新抛出 CancellationException，避免吞掉协程取消。
 * block 为 inline lambda，可直接在其中调用挂起函数。
 */
suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
