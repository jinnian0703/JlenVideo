package top.jlen.vod.ui

import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor

internal fun LegacyStateRuntimeViewModel.legacyUpdateLoginUserName(value: String) {
    updateAccountState(accountStateWithUserName(currentAccountState(), value))
}

internal fun LegacyStateRuntimeViewModel.legacyUpdateLoginPassword(value: String) {
    updateAccountState(accountStateWithPassword(currentAccountState(), value))
}

internal fun LegacyStateRuntimeViewModel.legacySetAccountAuthMode(mode: AccountAuthMode) {
    updateAccountState(accountStateWithAuthMode(currentAccountState(), mode))
    when (mode) {
        AccountAuthMode.Login -> Unit
        AccountAuthMode.Register -> runtimeLoadRegisterPage(forceRefresh = true)
        AccountAuthMode.FindPassword -> runtimeLoadFindPasswordPage(forceRefresh = true)
        AccountAuthMode.About -> refreshCrashLog()
    }
}

internal fun LegacyStateRuntimeViewModel.legacyUpdateRegisterEditor(
    transform: (RegisterEditor) -> RegisterEditor
) {
    updateAccountState(
        accountStateWithRegisterEditor(
            currentAccountState(),
            transform(currentAccountState().registerEditor)
        )
    )
}

internal fun LegacyStateRuntimeViewModel.legacyUpdateFindPasswordEditor(
    transform: (FindPasswordEditor) -> FindPasswordEditor
) {
    updateAccountState(
        accountStateWithFindPasswordEditor(
            currentAccountState(),
            transform(currentAccountState().findPasswordEditor)
        )
    )
}

internal fun LegacyStateRuntimeViewModel.legacyUpdateProfileEditor(
    transform: (UserProfileEditor) -> UserProfileEditor
) {
    updateAccountState(
        accountStateWithProfileEditor(
            currentAccountState(),
            transform(currentAccountState().profileEditor)
        )
    )
}

internal fun LegacyStateRuntimeViewModel.legacySetProfileEditTab(editMode: Boolean) {
    updateAccountState(accountStateWithProfileEditTab(currentAccountState(), editMode))
}

internal fun LegacyStateRuntimeViewModel.legacySelectAccountSection(
    section: AccountSection,
    forceRefresh: Boolean = false
) {
    updateAccountState(selectAccountSectionState(currentAccountState(), section))
    if (!currentAccountState().session.isLoggedIn) return
    when (section) {
        AccountSection.Profile -> {
            if (forceRefresh || currentAccountState().profileFields.isEmpty()) {
                runtimeLoadAccountProfile()
            }
        }

        AccountSection.Favorites -> {
            if (forceRefresh || currentAccountState().favoriteItems.isEmpty()) {
                runtimeLoadFavoriteRecords()
            }
        }

        AccountSection.History -> {
            if (forceRefresh || currentAccountState().historyItems.isEmpty()) {
                runtimeLoadHistoryRecords()
            }
        }

        AccountSection.Member -> {
            if (forceRefresh || currentAccountState().membershipPlans.isEmpty()) {
                runtimeLoadMembership()
            }
        }

        AccountSection.About -> refreshCrashLog()
    }
}

internal fun LegacyStateRuntimeViewModel.legacyRefreshSelectedAccountSection() {
    legacySelectAccountSection(currentAccountState().selectedSection, forceRefresh = true)
}

internal fun LegacyStateRuntimeViewModel.legacyLoadMoreFavorites() {
    if (currentAccountState().isContentLoading || currentAccountState().favoriteNextPageUrl.isNullOrBlank()) return
    runtimeLoadFavoriteRecords(pageUrl = currentAccountState().favoriteNextPageUrl, append = true)
}

internal fun LegacyStateRuntimeViewModel.legacyLoadMoreHistory() {
    if (currentAccountState().isContentLoading || currentAccountState().historyNextPageUrl.isNullOrBlank()) return
    runtimeLoadHistoryRecords(pageUrl = currentAccountState().historyNextPageUrl, append = true)
}
