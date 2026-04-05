package top.jlen.vod.ui

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import top.jlen.vod.data.UserCenterItem

internal fun toUserFacingMessage(
    error: Throwable,
    fallback: String,
    serviceLabel: String? = null
): String {
    val host = serviceLabel?.trim()?.takeIf(String::isNotBlank) ?: "内容服务"
    val rawMessage = error.message.orEmpty().trim()

    return when {
        error is UnknownHostException || rawMessage.contains("Unable to resolve host", ignoreCase = true) ->
            "无法连接到 $host，请检查网络或站点状态"

        error is SocketTimeoutException ||
            error is InterruptedIOException ||
            rawMessage.contains("timeout", ignoreCase = true) ||
            rawMessage.contains("timed out", ignoreCase = true) ->
            "连接 $host 超时，请稍后重试"

        error is ConnectException ||
            rawMessage.contains("failed to connect", ignoreCase = true) ||
            rawMessage.contains("connection refused", ignoreCase = true) ->
            "无法连接到 $host，请稍后重试"

        error is SSLException || rawMessage.contains("ssl", ignoreCase = true) ->
            "与 $host 的安全连接失败，请稍后重试"

        rawMessage.any { it in '\u4e00'..'\u9fff' } -> rawMessage

        fallback.isNotBlank() -> "$fallback，请稍后重试"

        else -> "请求失败，请稍后重试"
    }
}

internal fun normalizeFavoriteActionMessage(rawMessage: String): String {
    val message = rawMessage.trim()
    if (message.isBlank()) return "已加入收藏"
    return when {
        isDuplicateFavoriteMessage(message) -> "已在收藏中"
        message.contains("成功") || message.contains("获取成功") || message.contains("操作成功") -> "已加入收藏"
        else -> "已加入收藏"
    }
}

internal fun isDuplicateFavoriteMessage(rawMessage: String): Boolean {
    val message = rawMessage.trim()
    if (message.isBlank()) return false
    return message.contains("已收藏") ||
        message.contains("已经收藏") ||
        message.contains("已存在") ||
        message.contains("重复")
}

internal fun historyRecordKey(item: UserCenterItem): String =
    listOf(item.recordId, item.actionUrl, item.playUrl, item.title)
        .joinToString("|")
