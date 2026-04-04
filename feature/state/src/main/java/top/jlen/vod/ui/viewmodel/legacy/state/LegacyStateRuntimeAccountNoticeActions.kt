package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.AppRuntimeInfo
import top.jlen.vod.CrashLogger

internal fun LegacyStateRuntimeViewModel.legacyRefreshAccount() {
    val session = legacyRepository().currentSession()
    updateAccountState(refreshedAccountState(currentAccountState(), session))
    if (session.isLoggedIn && hasEnteredAccountScreenFlag()) {
        legacyHydrateAccountSession()
        selectAccountSection(currentAccountState().selectedSection, forceRefresh = true)
    }
}

internal fun LegacyStateRuntimeViewModel.legacyEnsureAccountScreenReady() {
    if (hasEnteredAccountScreenFlag()) return
    markAccountScreenEntered()
    if (!currentAccountState().session.isLoggedIn) return
    legacyHydrateAccountSession()
    selectAccountSection(currentAccountState().selectedSection, forceRefresh = true)
}

internal fun LegacyStateRuntimeViewModel.legacyHydrateAccountSession() {
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadUserProfileForApp() }
        }.onSuccess { page ->
            updateAccountState(
                accountStateWithHydratedSession(
                    currentAccountState(),
                    page.session
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModel.legacyRefreshCrashLog() {
    val latestCrashLog = CrashLogger.readLatest(getApplication())
    updateAccountState(accountStateWithCrashLog(currentAccountState(), latestCrashLog))
}

internal fun LegacyStateRuntimeViewModel.legacyClearCrashLog() {
    CrashLogger.clear(getApplication())
    updateAccountState(accountStateAfterCrashLogCleared(currentAccountState()))
}

internal fun LegacyStateRuntimeViewModel.legacyCheckAppUpdate() {
    if (currentAccountState().isUpdateLoading) return
    viewModelScope.launch {
        updateAccountState(beginUpdateCheck(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadLatestRelease(AppRuntimeInfo.versionName)
            }
        }.onSuccess { updateInfo ->
            updateAccountState(accountStateWithUpdateInfo(currentAccountState(), updateInfo))
        }.onFailure {
            updateAccountState(accountStateWithUpdateError(currentAccountState()))
        }
    }
}

internal fun LegacyStateRuntimeViewModel.legacyRefreshNotices(forceRefresh: Boolean = false) {
    if (currentNoticeState().isLoading && !forceRefresh) return
    val userId = currentAccountState().session.userId
    viewModelScope.launch {
        updateNoticeState(beginNoticeRefresh(currentNoticeState()))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadNotices(
                    appVersion = AppRuntimeInfo.versionName,
                    userId = userId,
                    forceRefresh = forceRefresh
                )
            }
        }.onSuccess { notices ->
            val unreadNoticeIds = legacyRepository().unreadActiveNoticeIds(notices)
            updateNoticeState(
                noticeStateWithLoadedNotices(
                    noticeState = currentNoticeState(),
                    notices = notices,
                    unreadNoticeIds = unreadNoticeIds,
                    pendingNotice = legacyRepository().pickPendingNotice(notices)
                )
            )
        }.onFailure { error ->
            updateNoticeState(
                noticeStateWithRefreshError(
                    noticeState = currentNoticeState(),
                    errorMessage = toUserFacingMessage(error, "公告加载失败", serviceLabel = "公告服务")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModel.legacyDismissNoticeDialog() {
    val dismissedNotice = currentNoticeState().dialogNotice
    dismissedNotice?.let(legacyRepository()::markNoticeDismissed)
    updateNoticeState(
        noticeStateAfterDialogDismiss(
            noticeState = currentNoticeState(),
            unreadNoticeIds = legacyRepository().unreadActiveNoticeIds(currentNoticeState().notices)
        )
    )
}

internal fun LegacyStateRuntimeViewModel.legacyMarkNoticeOpened(noticeId: String) {
    val normalized = noticeId.trim()
    currentNoticeState().notices
        .firstOrNull { it.id == normalized }
        ?.let(legacyRepository()::markNoticeDismissed)
    updateNoticeState(
        noticeStateAfterNoticeOpened(
            noticeState = currentNoticeState(),
            noticeId = normalized,
            unreadNoticeIds = legacyRepository().unreadActiveNoticeIds(currentNoticeState().notices)
        )
    )
}
