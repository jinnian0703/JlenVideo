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
    val normalizedRawMessage = normalizeKnownUiMessage(rawMessage)

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
            "$host 的安全连接失败，请稍后重试"

        normalizedRawMessage.isNotBlank() &&
            normalizedRawMessage.any { it in '\u4e00'..'\u9fff' } &&
            !looksLikeMojibake(normalizedRawMessage) -> normalizedRawMessage

        fallback.isNotBlank() -> "$fallback，请稍后重试"

        else -> "请求失败，请稍后重试"
    }
}

internal fun normalizeFavoriteActionMessage(rawMessage: String): String {
    val message = normalizeKnownUiMessage(rawMessage.trim())
    if (message.isBlank()) return "已加入收藏"
    return when {
        isDuplicateFavoriteMessage(message) -> "已在收藏中"
        message.contains("成功") || message.contains("获取成功") || message.contains("操作成功") -> "已加入收藏"
        else -> "已加入收藏"
    }
}

internal fun isDuplicateFavoriteMessage(rawMessage: String): Boolean {
    val message = normalizeKnownUiMessage(rawMessage.trim())
    if (message.isBlank()) return false
    return message.contains("已收藏") ||
        message.contains("已经收藏") ||
        message.contains("已存在") ||
        message.contains("重复")
}

internal fun historyRecordKey(item: UserCenterItem): String =
    listOf(item.recordId, item.actionUrl, item.playUrl, item.title)
        .joinToString("|")

private fun normalizeKnownUiMessage(message: String): String =
    message
        .replace("璇峰厛鐧诲綍", "请先登录")
        .replace("鐧诲綍鎴愬姛", "登录成功")
        .replace("宸查€€鍑虹櫥褰", "已退出登录")
        .replace("鐧诲綍宸插け鏁堬紝璇烽噸鏂扮櫥褰", "登录已失效，请重新登录")
        .replace("鍐呭鏈嶅姟", "内容服务")
        .replace("ac不能为空", "当前站点签到接口配置异常，请联系管理员")
        .replace("ac值只能是email,phone", "当前站点签到接口配置异常，请联系管理员")
        .trim()

private fun looksLikeMojibake(message: String): Boolean {
    val suspiciousTokens = listOf(
        "鐧", "璇", "鍔", "浼", "绉", "澶", "鏆", "宸", "鍒", "褰",
        "缁", "鍦", "骞", "鍛", "閫", "鎿", "鏌", "濂", "鍨", "鏁",
        "瑙", "璁", "鏈", "绱", "鏃", "鎴", "鍙"
    )
    return suspiciousTokens.any(message::contains)
}
