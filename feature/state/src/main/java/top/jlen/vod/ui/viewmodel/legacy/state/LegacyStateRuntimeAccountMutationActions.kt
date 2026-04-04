package top.jlen.vod.ui

import android.net.Uri
import top.jlen.vod.data.MembershipPlan

internal fun LegacyStateRuntimeViewModel.legacyDeleteFavorite(recordId: String) {
    if (recordId.isBlank()) return
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 2, clearAll = false) },
        onSuccess = {
            val removedItem = currentAccountState().favoriteItems.firstOrNull { item -> item.recordId == recordId }
            updateAccountState(accountStateRemovingFavorite(currentAccountState(), recordId))
            if (removedItem?.vodId == currentDetailState().item?.vodId) {
                updateDetailState(detailStateWithoutFavorite(currentDetailState()))
            }
            selectAccountSection(AccountSection.Favorites, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyClearFavorites() {
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = emptyList(), type = 2, clearAll = true) },
        onSuccess = {
            updateAccountState(accountStateClearingFavorites(currentAccountState()))
            if (currentDetailState().item != null) {
                updateDetailState(detailStateWithoutFavorite(currentDetailState()))
            }
            selectAccountSection(AccountSection.Favorites, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyDeleteHistory(recordId: String) {
    if (recordId.isBlank()) return
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 4, clearAll = false) },
        onSuccess = {
            updateAccountState(accountStateRemovingHistory(currentAccountState(), recordId))
            selectAccountSection(AccountSection.History, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyClearHistory() {
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = emptyList(), type = 4, clearAll = true) },
        onSuccess = {
            updateAccountState(accountStateClearingHistory(currentAccountState()))
            selectAccountSection(AccountSection.History, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyUpgradeMembership(plan: MembershipPlan) {
    if (plan.groupId.isBlank() || plan.duration.isBlank()) return
    runtimeRunAccountAction(
        block = { upgradeMembership(plan) },
        onSuccess = { selectAccountSection(AccountSection.Member, forceRefresh = true) }
    )
}

internal fun LegacyStateRuntimeViewModel.legacySaveProfile() {
    runtimeRunAccountAction(
        block = { saveUserProfile(currentAccountState().profileEditor) },
        onSuccess = {
            updateAccountState(accountStateAfterProfileSaved(currentAccountState()))
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
            refreshAccount()
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyUploadPortrait(uri: Uri) {
    if (!currentAccountState().session.isLoggedIn) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇峰厛鐧诲綍"))
        return
    }
    runtimeRunAccountAction(
        block = { uploadPortraitOptimized(uri) },
        onSuccess = {
            refreshNotices(forceRefresh = true)
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacySendEmailBindCode() {
    val email = currentAccountState().profileEditor.pendingEmail.trim()
    if (email.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱地址"))
        return
    }
    runtimeRunAccountAction(
        block = { sendEmailBindCode(email) },
        onSuccess = { }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyBindEmail() {
    val email = currentAccountState().profileEditor.pendingEmail.trim()
    val code = currentAccountState().profileEditor.emailCode.trim()
    if (email.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱地址"))
        return
    }
    if (code.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱验证码"))
        return
    }
    runtimeRunAccountAction(
        block = { bindEmail(email, code) },
        onSuccess = {
            updateAccountState(accountStateAfterEmailBound(currentAccountState(), email))
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyUnbindEmail() {
    runtimeRunAccountAction(
        block = { unbindEmail() },
        onSuccess = {
            updateAccountState(accountStateAfterEmailUnbound(currentAccountState()))
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
        }
    )
}
