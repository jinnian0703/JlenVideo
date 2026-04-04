package top.jlen.vod.ui

internal fun selectAccountSectionState(
    accountState: AccountUiState,
    section: AccountSection
): AccountUiState = accountState.copy(selectedSection = section, error = null, message = null)
