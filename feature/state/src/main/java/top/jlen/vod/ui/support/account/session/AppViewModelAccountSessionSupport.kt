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
