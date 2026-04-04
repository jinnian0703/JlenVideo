package top.jlen.vod.ui

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs
import javax.net.ssl.SSLException
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.CursorPagedVodItems
import top.jlen.vod.data.HomePayload
import top.jlen.vod.data.MembershipPage
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterPage
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfilePage
import top.jlen.vod.data.VodItem


internal fun mergeAccountSession(
    session: AuthSession,
    currentSession: AuthSession
): AuthSession = session.copy(
    userName = session.userName.ifBlank { currentSession.userName },
    groupName = session.groupName.ifBlank { currentSession.groupName },
    portraitUrl = session.portraitUrl.ifBlank { currentSession.portraitUrl }
)

internal fun refreshedAccountState(
    accountState: AccountUiState,
    session: AuthSession
): AccountUiState = if (session.isLoggedIn) {
    accountState.copy(
        session = mergeAccountSession(session, accountState.session),
        error = null
    )
} else {
    AccountUiState(
        userName = accountState.userName,
        session = session,
        updateInfo = accountState.updateInfo,
        hasCrashLog = accountState.hasCrashLog,
        latestCrashLog = accountState.latestCrashLog
    )
}

internal fun accountStateWithHydratedSession(
    accountState: AccountUiState,
    session: AuthSession
): AccountUiState = accountState.copy(
    session = mergeAccountSession(session, accountState.session)
)

internal fun accountStateWithCrashLog(
    accountState: AccountUiState,
    latestCrashLog: String
): AccountUiState = accountState.copy(
    hasCrashLog = latestCrashLog.isNotBlank(),
    latestCrashLog = latestCrashLog
)

internal fun accountStateAfterCrashLogCleared(accountState: AccountUiState): AccountUiState =
    accountState.copy(
        hasCrashLog = false,
        latestCrashLog = "",
        message = "已清空崩溃日志",
        error = null
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

internal fun mergeAccountItems(
    current: List<UserCenterItem>,
    incoming: List<UserCenterItem>
): List<UserCenterItem> = (current + incoming)
    .distinctBy { item -> "${item.recordId}:${item.actionUrl}:${item.vodId}" }

internal fun selectAccountSectionState(
    accountState: AccountUiState,
    section: AccountSection
): AccountUiState = accountState.copy(selectedSection = section, error = null, message = null)

internal fun accountStateWithProfilePage(
    accountState: AccountUiState,
    page: UserProfilePage
): AccountUiState = accountState.copy(
    isContentLoading = false,
    profileFields = page.fields,
    profileEditor = page.editor,
    session = mergeAccountSession(page.session, accountState.session)
)

internal fun accountStateWithFavoritePage(
    accountState: AccountUiState,
    page: UserCenterPage,
    append: Boolean
): AccountUiState = accountState.copy(
    isContentLoading = false,
    favoriteItems = mergeAccountItems(
        current = if (append) accountState.favoriteItems else emptyList(),
        incoming = page.items
    ),
    favoriteNextPageUrl = page.nextPageUrl
)

internal data class HistoryPageState(
    val accountState: AccountUiState,
    val mergedItems: List<UserCenterItem>
)

internal fun accountStateWithHistoryPage(
    accountState: AccountUiState,
    page: UserCenterPage,
    append: Boolean
): HistoryPageState {
    val mergedItems = mergeAccountItems(
        current = if (append) accountState.historyItems else emptyList(),
        incoming = page.items
    )
    return HistoryPageState(
        accountState = accountState.copy(
            isContentLoading = false,
            historyItems = mergedItems,
            historyNextPageUrl = page.nextPageUrl
        ),
        mergedItems = mergedItems
    )
}

internal fun accountStateWithMembershipPage(
    accountState: AccountUiState,
    page: MembershipPage,
    currentSession: AuthSession
): AccountUiState {
    val refreshedSession = mergeAccountSession(currentSession, accountState.session)
        .let { session -> session.copy(groupName = page.info.groupName.ifBlank { session.groupName }) }
    return accountState.copy(
        isContentLoading = false,
        session = refreshedSession,
        membershipInfo = page.info.copy(
            groupName = page.info.groupName.ifBlank { refreshedSession.groupName }
        ),
        membershipPlans = page.plans
    )
}

internal fun accountStateRemovingFavorite(
    accountState: AccountUiState,
    recordId: String
): AccountUiState = accountState.copy(
    favoriteItems = accountState.favoriteItems.filterNot { item -> item.recordId == recordId }
)

internal fun accountStateClearingFavorites(accountState: AccountUiState): AccountUiState = accountState.copy(
    favoriteItems = emptyList(),
    favoriteNextPageUrl = null
)

internal fun accountStateRemovingHistory(
    accountState: AccountUiState,
    recordId: String
): AccountUiState = accountState.copy(
    historyItems = accountState.historyItems.filterNot { item -> item.recordId == recordId }
)

internal fun accountStateClearingHistory(accountState: AccountUiState): AccountUiState = accountState.copy(
    historyItems = emptyList(),
    historyNextPageUrl = null
)

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
