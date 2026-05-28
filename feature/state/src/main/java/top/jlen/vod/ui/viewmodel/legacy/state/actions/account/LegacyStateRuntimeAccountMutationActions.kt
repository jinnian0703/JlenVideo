package top.jlen.vod.ui

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.MembershipPlan

private fun String.isValidEmailAddress(): Boolean =
    Patterns.EMAIL_ADDRESS.matcher(this).matches()

internal fun LegacyStateRuntimeViewModelCore.legacyDeleteFavorite(recordId: String) {
    if (recordId.isBlank()) return
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 2, clearAll = false) },
        successMessage = "已移出追剧",
        onSuccess = {
            val removedItem = currentAccountState().favoriteItems.firstOrNull { item -> item.recordId == recordId }
            updateAccountState(accountStateRemovingFavorite(currentAccountState(), recordId))
            legacyRebuildFollowContent(forceUpdate = true)
            legacyRefreshFollowContent(forceRefresh = true)
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
        successMessage = "已清空追剧",
        onSuccess = {
            updateAccountState(accountStateClearingFavorites(currentAccountState()))
            legacyRebuildFollowContent(forceUpdate = true)
            legacyRefreshFollowContent(forceRefresh = true)
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
            val nextState = accountStateRemovingHistory(currentAccountState(), recordId)
            updateAccountState(nextState)
            legacySaveHistoryCache(nextState.historyItems)
            legacyRebuildFollowContent()
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
            legacyClearHistoryCache()
            legacyRebuildFollowContent()
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

internal fun LegacyStateRuntimeViewModelCore.legacyRedeemMembershipCard(
    cardNo: String,
    cardPassword: String
) {
    val normalizedCardNo = cardNo.trim()
    val normalizedPassword = cardPassword.trim()
    if (normalizedCardNo.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入充值卡卡号"))
        return
    }
    if (normalizedPassword.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入充值卡密码"))
        return
    }
    runtimeRunAccountAction(
        block = { redeemMembershipCard(normalizedCardNo, normalizedPassword) },
        onSuccess = { runtimeLoadMembership() }
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
                        onSuccess = { refreshAfterMembershipSignIn() }
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
        onSuccess = { refreshAfterMembershipSignIn() }
    )
}

private fun LegacyStateRuntimeViewModelCore.refreshAfterMembershipSignIn() {
    val targetSection = if (currentAccountState().selectedSection == AccountSection.Overview) {
        AccountSection.Overview
    } else {
        AccountSection.Member
    }
    selectAccountSection(targetSection, forceRefresh = true)
}

internal fun LegacyStateRuntimeViewModelCore.legacySaveProfile() {
    val editor = currentAccountState().profileEditor
    val hasPasswordInput = editor.currentPassword.isNotBlank() ||
        editor.newPassword.isNotBlank() ||
        editor.confirmPassword.isNotBlank()
    if (hasPasswordInput) {
        val validationError = when {
            editor.currentPassword.isBlank() -> "请输入原密码"
            editor.newPassword.isBlank() -> "请输入新密码"
            editor.confirmPassword.isBlank() -> "请确认新密码"
            editor.newPassword != editor.confirmPassword -> "两次输入的新密码不一致"
            else -> null
        }
        if (validationError != null) {
            updateAccountState(accountStateWithValidationError(currentAccountState(), validationError))
            return
        }
    }
    runtimeRunAccountAction(
        block = { saveUserProfile(currentAccountState().profileEditor) },
        successMessage = if (hasPasswordInput) "密码已修改" else "资料已保存",
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
    if (currentAccountState().emailBindCodeCountdown > 0) return
    runtimeRunAccountAction(
        block = { sendEmailBindCode(email) },
        successMessage = "验证码已发送，请注意查收",
        onSuccess = {
            startEmailBindCodeCountdown(email)
        }
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

private fun LegacyStateRuntimeViewModelCore.startEmailBindCodeCountdown(email: String) {
    viewModelScope.launch {
        for (seconds in 60 downTo 0) {
            val state = currentAccountState()
            if (state.selectedSection != AccountSection.Profile ||
                state.profileEditor.pendingEmail.trim() != email
            ) {
                updateAccountState(accountStateWithEmailBindCodeCountdown(state, 0))
                break
            }
            updateAccountState(accountStateWithEmailBindCodeCountdown(state, seconds))
            if (seconds > 0) delay(1000L)
        }
    }
}
