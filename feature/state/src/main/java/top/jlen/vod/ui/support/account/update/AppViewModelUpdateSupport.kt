package top.jlen.vod.ui

import top.jlen.vod.data.AppUpdateInfo

internal fun beginUpdateCheck(accountState: AccountUiState): AccountUiState =
    accountState.copy(isUpdateLoading = true)

internal fun accountStateWithUpdateInfo(
    accountState: AccountUiState,
    updateInfo: AppUpdateInfo
): AccountUiState = accountState.copy(
    isUpdateLoading = false,
    updateInfo = updateInfo,
    error = null
)

internal fun accountStateWithUpdateError(accountState: AccountUiState): AccountUiState =
    accountState.copy(
        isUpdateLoading = false,
        message = "检查更新失败，请稍后重试"
    )
