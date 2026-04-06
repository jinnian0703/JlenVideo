package top.jlen.vod.ui

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.MembershipPlan

private fun String.isValidEmailAddress(): Boolean =
    Patterns.EMAIL_ADDRESS.matcher(this).matches()

internal fun LegacyStateRuntimeViewModelCore.legacyDeleteFavorite(recordId: String) {
    if (recordId.isBlank()) return
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 2, clearAll = false) },
        successMessage = "已移出收藏",
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

internal fun LegacyStateRuntimeViewModelCore.legacyClearFavorites() {
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = emptyList(), type = 2, clearAll = true) },
        successMessage = "已清空收藏",
        onSuccess = {
            updateAccountState(accountStateClearingFavorites(currentAccountState()))
            if (currentDetailState().item != null) {
                updateDetailState(detailStateWithoutFavorite(currentDetailState()))
            }
            selectAccountSection(AccountSection.Favorites, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyDeleteHistory(recordId: String) {
    if (recordId.isBlank()) return
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 4, clearAll = false) },
        successMessage = "已删除播放记录",
        onSuccess = {
            updateAccountState(accountStateRemovingHistory(currentAccountState(), recordId))
            selectAccountSection(AccountSection.History, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyClearHistory() {
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = emptyList(), type = 4, clearAll = true) },
        successMessage = "已清空播放记录",
        onSuccess = {
            updateAccountState(accountStateClearingHistory(currentAccountState()))
            selectAccountSection(AccountSection.History, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpgradeMembership(plan: MembershipPlan) {
    if (plan.groupId.isBlank() || plan.duration.isBlank()) return
    val currentPoints = currentAccountState().membershipInfo.points.trim().toIntOrNull()
    val requiredPoints = plan.points.trim().toIntOrNull()
    if (currentPoints != null && requiredPoints != null && currentPoints < requiredPoints) {
        updateAccountState(
            accountStateWithValidationError(
                currentAccountState(),
                "当前积分不足，无法升级该套餐"
            )
        )
        return
    }
    runtimeRunAccountAction(
        block = { upgradeMembership(plan) },
        successMessage = "会员信息已更新",
        onSuccess = { selectAccountSection(AccountSection.Member, forceRefresh = true) }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacySignInMembership() {
    val cachedSignInInfo = currentAccountState().membershipSignInInfo
    val shouldRefreshMembershipFirst =
        currentAccountState().selectedSection != AccountSection.Member ||
            (!cachedSignInInfo.enabled &&
                !cachedSignInInfo.signedToday &&
                cachedSignInInfo.rewardPoints.isBlank() &&
                cachedSignInInfo.rewardMinPoints.isBlank() &&
                cachedSignInInfo.rewardMaxPoints.isBlank())

    if (shouldRefreshMembershipFirst) {
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
            }

            val refreshedSignInInfo = currentAccountState().membershipSignInInfo
            when {
                !refreshedSignInInfo.enabled -> {
                    updateAccountState(
                        accountStateWithValidationError(
                            currentAccountState(),
                            "当前站点未开启签到功能"
                        )
                    )
                }
                refreshedSignInInfo.signedToday -> {
                    updateAccountState(
                        accountStateWithValidationError(
                            currentAccountState(),
                            "今日已签到，请明天再来"
                        )
                    )
                }
                else -> {
                    runtimeRunAccountAction(
                        block = { signInMembership() },
                        onSuccess = { selectAccountSection(AccountSection.Member, forceRefresh = true) }
                    )
                }
            }
        }
        return
    }

    if (!cachedSignInInfo.enabled) {
        updateAccountState(
            accountStateWithValidationError(
                currentAccountState(),
                "当前站点未开启签到功能"
            )
        )
        return
    }
    if (cachedSignInInfo.signedToday) {
        updateAccountState(
            accountStateWithValidationError(
                currentAccountState(),
                "今日已签到，请明天再来"
            )
        )
        return
    }
    runtimeRunAccountAction(
        block = { signInMembership() },
        onSuccess = { selectAccountSection(AccountSection.Member, forceRefresh = true) }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacySaveProfile() {
    runtimeRunAccountAction(
        block = { saveUserProfile(currentAccountState().profileEditor) },
        successMessage = "资料已保存",
        onSuccess = {
            updateAccountState(accountStateAfterProfileSaved(currentAccountState()))
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
            refreshAccount()
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyUploadPortrait(uri: Uri) {
    if (!currentAccountState().session.isLoggedIn) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请先登录"))
        return
    }
    runtimeRunAccountAction(
        block = { uploadPortraitOptimized(uri) },
        successMessage = "头像已更新",
        onSuccess = {
            refreshNotices(forceRefresh = true)
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacySendEmailBindCode() {
    val email = currentAccountState().profileEditor.pendingEmail.trim()
    if (email.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱地址"))
        return
    }
    if (!email.isValidEmailAddress()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入正确的邮箱地址"))
        return
    }
    runtimeRunAccountAction(
        block = { sendEmailBindCode(email) },
        successMessage = "验证码已发送，请注意查收",
        onSuccess = { }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyBindEmail() {
    val email = currentAccountState().profileEditor.pendingEmail.trim()
    val code = currentAccountState().profileEditor.emailCode.trim()
    if (email.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱地址"))
        return
    }
    if (!email.isValidEmailAddress()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入正确的邮箱地址"))
        return
    }
    if (code.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱验证码"))
        return
    }
    runtimeRunAccountAction(
        block = { bindEmail(email, code) },
        successMessage = "邮箱已绑定",
        onSuccess = {
            updateAccountState(accountStateAfterEmailBound(currentAccountState(), email))
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyUnbindEmail() {
    runtimeRunAccountAction(
        block = { unbindEmail() },
        successMessage = "邮箱已解绑",
        onSuccess = {
            updateAccountState(accountStateAfterEmailUnbound(currentAccountState()))
        }
    )
}
