package top.jlen.vod.ui

import kotlin.math.abs
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

internal fun beginAccountContentLoad(accountState: AccountUiState): AccountUiState =
    accountState.copy(isContentLoading = true, error = null)

internal fun accountStateWithContentError(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(
    isContentLoading = false,
    error = message
)

internal fun accountStateWithEnrichedHistoryItems(
    accountState: AccountUiState,
    enrichedByKey: Map<String, UserCenterItem>
): AccountUiState = accountState.copy(
    historyItems = accountState.historyItems.map { item ->
        enrichedByKey[historyRecordKey(item)]?.let { enriched ->
            item.copy(
                vodId = enriched.vodId.ifBlank { item.vodId },
                subtitle = enriched.subtitle.ifBlank { item.subtitle },
                sourceName = enriched.sourceName.ifBlank { item.sourceName }
            )
        } ?: item
    }
)

internal fun beginAccountAction(accountState: AccountUiState): AccountUiState =
    accountState.copy(isActionLoading = true, error = null, message = null)

internal fun accountStateWithActionSuccess(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(
    isActionLoading = false,
    message = message.ifBlank { "操作成功" }
)

internal fun accountStateWithActionError(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(
    isActionLoading = false,
    error = message
)


