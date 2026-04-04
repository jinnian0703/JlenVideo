package top.jlen.vod.ui

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.UserCenterItem

internal fun beginLogin(accountState: AccountUiState): AccountUiState =
    accountState.copy(isLoading = true, error = null, message = null)

internal fun loggedInAccountState(
    accountState: AccountUiState,
    session: AuthSession
): AccountUiState = accountState.copy(
    isLoading = false,
    session = session,
    password = "",
    error = null,
    message = "登录成功"
)

internal fun accountStateWithLoginError(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(
    isLoading = false,
    message = null,
    error = message
)

internal fun beginLogout(accountState: AccountUiState): AccountUiState =
    accountState.copy(isLoading = true, error = null)

internal fun accountStateWithLogoutError(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(
    isLoading = false,
    error = message
)

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

internal fun loggedOutAccountState(accountState: AccountUiState): AccountUiState = AccountUiState(
    userName = accountState.userName,
    session = AuthSession(),
    message = "已退出登录",
    updateInfo = accountState.updateInfo,
    hasCrashLog = accountState.hasCrashLog,
    latestCrashLog = accountState.latestCrashLog
)

internal fun expiredAccountState(accountState: AccountUiState): AccountUiState = AccountUiState(
    userName = accountState.userName,
    authMode = AccountAuthMode.Login,
    message = "登录已失效，请重新登录",
    updateInfo = accountState.updateInfo,
    hasCrashLog = accountState.hasCrashLog,
    latestCrashLog = accountState.latestCrashLog
)

internal fun historyRecordKey(item: UserCenterItem): String =
    listOf(item.recordId, item.actionUrl, item.playUrl, item.title)
        .joinToString("|")
