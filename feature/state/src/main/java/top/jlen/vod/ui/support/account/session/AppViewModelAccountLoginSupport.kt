package top.jlen.vod.ui

import top.jlen.vod.data.AuthSession

internal fun beginLogin(accountState: AccountUiState): AccountUiState =
    accountState.copy(isLoading = true, error = null, message = null)

internal fun loggedInAccountState(
    accountState: AccountUiState,
    session: AuthSession
): AccountUiState =
    accountStateWithToast(
        accountState.copy(
            isLoading = false,
            session = session,
            password = "",
            error = null,
            message = "登录成功"
        ),
        "登录成功"
    )

internal fun accountStateWithLoginError(
    accountState: AccountUiState,
    message: String
): AccountUiState =
    accountStateWithToast(
        accountState.copy(
            isLoading = false,
            message = null,
            error = message
        ),
        message
    )

internal fun beginLogout(accountState: AccountUiState): AccountUiState =
    accountState.copy(isLoading = true, error = null)

internal fun accountStateWithLogoutError(
    accountState: AccountUiState,
    message: String
): AccountUiState =
    accountStateWithToast(
        accountState.copy(
            isLoading = false,
            error = message
        ),
        message
    )

internal fun loggedOutAccountState(accountState: AccountUiState): AccountUiState =
    accountStateWithToast(
        AccountUiState(
            userName = accountState.userName,
            session = AuthSession(),
            message = "已退出登录",
            updateInfo = accountState.updateInfo,
            hasCrashLog = accountState.hasCrashLog,
            latestCrashLog = accountState.latestCrashLog,
            issueLogEntries = accountState.issueLogEntries,
            cacheRetention = accountState.cacheRetention,
            cacheSizeSummary = accountState.cacheSizeSummary,
            isCacheSizeLoading = accountState.isCacheSizeLoading,
            isCacheClearing = accountState.isCacheClearing,
            toastSerial = accountState.toastSerial
        ),
        "已退出登录"
    )

internal fun expiredAccountState(accountState: AccountUiState): AccountUiState =
    accountStateWithToast(
        AccountUiState(
            userName = accountState.userName,
            authMode = AccountAuthMode.Login,
            message = "登录已失效，请重新登录",
            updateInfo = accountState.updateInfo,
            hasCrashLog = accountState.hasCrashLog,
            latestCrashLog = accountState.latestCrashLog,
            issueLogEntries = accountState.issueLogEntries,
            cacheRetention = accountState.cacheRetention,
            cacheSizeSummary = accountState.cacheSizeSummary,
            isCacheSizeLoading = accountState.isCacheSizeLoading,
            isCacheClearing = accountState.isCacheClearing,
            toastSerial = accountState.toastSerial
        ),
        "登录已失效，请重新登录"
    )
