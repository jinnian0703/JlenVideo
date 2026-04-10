package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.UserCenterItem

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshRegisterCaptcha() {
    val captchaUrl = currentAccountState().registerCaptchaUrl
    if (captchaUrl.isBlank()) {
        runtimeLoadRegisterPage(forceRefresh = true)
        return
    }

    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadRegisterCaptcha(captchaUrl) }
        }.onSuccess { bytes ->
            updateAccountState(accountStateWithRegisterCaptcha(currentAccountState(), bytes))
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "验证码加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshFindPasswordCaptcha() {
    val captchaUrl = currentAccountState().findPasswordCaptchaUrl
    if (captchaUrl.isBlank()) {
        runtimeLoadFindPasswordPage(forceRefresh = true)
        return
    }

    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadFindPasswordCaptcha(captchaUrl) }
        }.onSuccess { bytes ->
            updateAccountState(accountStateWithFindPasswordCaptcha(currentAccountState(), bytes))
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "验证码加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadRegisterPage(forceRefresh: Boolean = false) {
    if (currentAccountState().isContentLoading && !forceRefresh) return
    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadRegisterPageForApp() }
        }.onSuccess { page ->
            updateAccountState(accountStateWithRegisterPage(currentAccountState(), page))
            if (page.requiresVerify && page.captchaUrl.isNotBlank()) {
                legacyRefreshRegisterCaptcha()
            }
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "注册页面加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadFindPasswordPage(forceRefresh: Boolean = false) {
    if (currentAccountState().isContentLoading && !forceRefresh) return
    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadFindPasswordPageForApp() }
        }.onSuccess { page ->
            updateAccountState(accountStateWithFindPasswordPage(currentAccountState(), page))
            if (page.requiresVerify && page.captchaUrl.isNotBlank()) {
                legacyRefreshFindPasswordCaptcha()
            }
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "找回密码页面加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadAccountProfile() {
    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadUserProfileForApp() }
        }.onSuccess { page ->
            updateAccountState(accountStateWithProfilePage(currentAccountState(), page))
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "加载资料失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadFavoriteRecords(pageUrl: String? = null, append: Boolean = false) {
    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadFavoritePageForApp(pageUrl) }
        }.onSuccess { page ->
            updateAccountState(accountStateWithFavoritePage(currentAccountState(), page, append))
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "加载追剧列表失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadHistoryRecords(pageUrl: String? = null, append: Boolean = false) {
    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadHistoryPageForApp(pageUrl) }
        }.onSuccess { page ->
            val historyPageState = accountStateWithHistoryPage(currentAccountState(), page, append)
            updateAccountState(historyPageState.accountState)
            legacyEnrichHistoryRecords(historyPageState.mergedItems)
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "加载播放记录失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyEnrichHistoryRecords(items: List<UserCenterItem>) {
    val targetItems = items.filter { item ->
        (
            item.sourceName.isBlank() ||
                item.sourceIndex < 0 ||
                item.episodeIndex < 0
            ) &&
            (item.vodId.isNotBlank() || item.playUrl.isNotBlank() || item.actionUrl.isNotBlank())
    }
    if (targetItems.isEmpty()) return

    currentHistoryEnrichJob()?.cancel()
    val requestVersion = nextHistoryEnrichVersion()
    replaceHistoryEnrichJob(viewModelScope.launch {
        val enrichedItems = runCatching {
            withContext(Dispatchers.IO) { legacyRepository().enrichHistoryItems(targetItems) }
        }.getOrNull() ?: return@launch
        if (requestVersion != currentHistoryEnrichVersion()) return@launch

        val enrichedByKey = enrichedItems.associateBy(::historyRecordKey)
        updateAccountState(accountStateWithEnrichedHistoryItems(currentAccountState(), enrichedByKey))
    })
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadMembership() {
    viewModelScope.launch {
        updateAccountState(beginAccountContentLoad(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadMembershipDataForApp() }
        }.onSuccess { page ->
            updateAccountState(
                accountStateWithMembershipPage(
                    accountState = currentAccountState(),
                    page = page,
                    currentSession = legacyRepository().currentSession()
                )
            )
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithContentError(
                    currentAccountState(),
                    toUserFacingMessage(error, "加载会员信息失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshMembershipSignInStatus() {
    if (!currentAccountState().session.isLoggedIn) return
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadMembershipDataForApp() }
        }.onSuccess { page ->
            updateAccountState(
                accountStateWithMembershipPage(
                    accountState = currentAccountState(),
                    page = page,
                    currentSession = legacyRepository().currentSession()
                )
            )
        }.onFailure { error ->
            runtimeHandleAccountSessionExpired(error)
        }
    }
}
