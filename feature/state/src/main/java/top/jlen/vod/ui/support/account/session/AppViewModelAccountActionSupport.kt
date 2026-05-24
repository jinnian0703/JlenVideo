package top.jlen.vod.ui

internal fun beginAccountAction(accountState: AccountUiState): AccountUiState =
    accountState.copy(isActionLoading = true, error = null, message = null)

internal fun accountStateWithToast(
    accountState: AccountUiState,
    message: String
): AccountUiState {
    val normalized = message.trim()
    return accountState.copy(
        toastMessage = normalized,
        toastSerial = accountState.toastSerial + 1
    )
}

internal fun accountStateWithoutToast(accountState: AccountUiState): AccountUiState =
    accountState.copy(toastMessage = null)

internal fun accountStateWithActionSuccess(
    accountState: AccountUiState,
    message: String
): AccountUiState {
    val normalized = message.ifBlank { "操作成功" }
    return accountStateWithToast(
        accountState.copy(
            isActionLoading = false,
            message = normalized
        ),
        normalized
    )
}

internal fun accountStateWithActionError(
    accountState: AccountUiState,
    message: String
): AccountUiState =
    accountStateWithToast(
        accountState.copy(
            isActionLoading = false,
            error = message
        ),
        message
    )
