package top.jlen.vod.ui

import top.jlen.vod.data.AuthSession

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
        latestCrashLog = accountState.latestCrashLog,
        cacheRetention = accountState.cacheRetention,
        cacheSizeSummary = accountState.cacheSizeSummary,
        isCacheSizeLoading = accountState.isCacheSizeLoading,
        isCacheClearing = accountState.isCacheClearing
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
    latestCrashLog: String,
    entries: List<AccountIssueLogEntry> = accountState.issueLogEntries
): AccountUiState = accountState.copy(
    hasCrashLog = latestCrashLog.isNotBlank() || entries.isNotEmpty(),
    latestCrashLog = latestCrashLog,
    issueLogEntries = entries
)

internal fun accountStateAfterCrashLogCleared(accountState: AccountUiState): AccountUiState =
    accountStateWithToast(
        accountState.copy(
            hasCrashLog = false,
            latestCrashLog = "",
            issueLogEntries = emptyList(),
            message = "已清空问题日志",
            error = null
        ),
        "已清空问题日志"
    )
