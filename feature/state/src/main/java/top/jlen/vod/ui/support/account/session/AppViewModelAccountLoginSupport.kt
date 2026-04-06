package top.jlen.vod.ui

import top.jlen.vod.data.AuthSession

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
