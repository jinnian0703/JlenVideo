package top.jlen.vod.ui

import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserProfileEditor

internal fun LegacyStateRuntimeViewModelCore.legacyUpdateLoginUserName(value: String) {
    updateAccountState(accountStateWithUserName(currentAccountState(), value))
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpdateLoginPassword(value: String) {
    updateAccountState(accountStateWithPassword(currentAccountState(), value))
}

internal fun LegacyStateRuntimeViewModelCore.legacySetAccountAuthMode(mode: AccountAuthMode) {
    updateAccountState(accountStateWithAuthMode(currentAccountState(), mode))
    when (mode) {
        AccountAuthMode.Login -> Unit
        AccountAuthMode.Register -> runtimeLoadRegisterPage(forceRefresh = true)
        AccountAuthMode.FindPassword -> runtimeLoadFindPasswordPage(forceRefresh = true)
        AccountAuthMode.About -> refreshCrashLog()
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpdateRegisterEditor(
    transform: (RegisterEditor) -> RegisterEditor
) {
    updateAccountState(
        accountStateWithRegisterEditor(
            currentAccountState(),
            transform(currentAccountState().registerEditor)
        )
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpdateFindPasswordEditor(
    transform: (FindPasswordEditor) -> FindPasswordEditor
) {
    updateAccountState(
        accountStateWithFindPasswordEditor(
            currentAccountState(),
            transform(currentAccountState().findPasswordEditor)
        )
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpdateProfileEditor(
    transform: (UserProfileEditor) -> UserProfileEditor
) {
    updateAccountState(
        accountStateWithProfileEditor(
            currentAccountState(),
            transform(currentAccountState().profileEditor)
        )
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacySetProfileEditTab(editMode: Boolean) {
    updateAccountState(accountStateWithProfileEditTab(currentAccountState(), editMode))
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectAccountSection(
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

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshSelectedAccountSection() {
    legacySelectAccountSection(currentAccountState().selectedSection, forceRefresh = true)
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadMoreFavorites() {
    if (currentAccountState().isContentLoading || currentAccountState().favoriteNextPageUrl.isNullOrBlank()) return
    runtimeLoadFavoriteRecords(pageUrl = currentAccountState().favoriteNextPageUrl, append = true)
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadMoreHistory() {
    if (currentAccountState().isContentLoading || currentAccountState().historyNextPageUrl.isNullOrBlank()) return
    runtimeLoadHistoryRecords(pageUrl = currentAccountState().historyNextPageUrl, append = true)
}
