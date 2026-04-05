package top.jlen.vod.ui

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
