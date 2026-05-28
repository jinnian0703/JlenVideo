package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EMAIL_CODE_INTERVAL_SECONDS = 60

internal fun LegacyStateRuntimeViewModelCore.legacySendRegisterCode() {
    val state = currentAccountState()
    val editor = state.registerEditor
    val contact = editor.contact.trim()
    if (contact.isBlank()) {
        updateAccountState(
            accountStateWithValidationError(
                state,
                "请输入${state.registerContactLabel}"
            )
        )
        return
    }

    if (state.registerChannel == "email" && !Patterns.EMAIL_ADDRESS.matcher(contact).matches()) {
        updateAccountState(accountStateWithValidationError(state, "请输入正确的邮箱地址"))
        return
    }

    if (state.registerCodeCountdown > 0) return

    runtimeRunAccountAction(
        block = { sendRegisterCodeForApp(state.registerChannel, contact) },
        successMessage = "验证码已发送，请注意查收",
        onSuccess = {
            startRegisterCodeCountdown(state.registerChannel, contact)
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacySendFindPasswordCode() {
    val editor = currentAccountState().findPasswordEditor
    val email = editor.email.trim()
    if (email.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱地址"))
        return
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入正确的邮箱地址"))
        return
    }
    if (currentAccountState().findPasswordCodeCountdown > 0) return

    runtimeRunAccountAction(
        block = { sendFindPasswordCodeForApp(email) },
        successMessage = "验证码已发送，请注意查收",
        onSuccess = {
            startFindPasswordCodeCountdown()
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyRegister() {
    val state = currentAccountState()
    val editor = state.registerEditor
    if (editor.userName.isBlank()) {
        updateAccountState(accountStateWithValidationError(state, "请输入用户名"))
        return
    }
    if (editor.password.isBlank()) {
        updateAccountState(accountStateWithValidationError(state, "请输入密码"))
        return
    }
    if (editor.confirmPassword.isBlank()) {
        updateAccountState(accountStateWithValidationError(state, "请确认密码"))
        return
    }
    if (editor.password != editor.confirmPassword) {
        updateAccountState(accountStateWithValidationError(state, "两次输入的密码不一致"))
        return
    }
    if (editor.contact.isBlank()) {
        updateAccountState(
            accountStateWithValidationError(
                state,
                "请输入${state.registerContactLabel}"
            )
        )
        return
    }
    if (state.registerChannel == "email" && !Patterns.EMAIL_ADDRESS.matcher(editor.contact.trim()).matches()) {
        updateAccountState(accountStateWithValidationError(state, "请输入正确的邮箱地址"))
        return
    }
    if (state.registerRequiresCode && editor.code.isBlank()) {
        updateAccountState(
            accountStateWithValidationError(
                state,
                "请输入${state.registerCodeLabel}"
            )
        )
        return
    }
    if (state.registerRequiresVerify && editor.verify.isBlank()) {
        updateAccountState(accountStateWithValidationError(state, "请输入图片验证码"))
        return
    }

    runtimeRunAccountAction(
        block = { registerForApp(editor.copy(channel = state.registerChannel)) },
        onSuccess = {
            updateAccountState(accountStateAfterRegisterSuccess(currentAccountState(), editor.userName))
        }
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyFindPassword() {
    val editor = currentAccountState().findPasswordEditor
    val email = editor.email.trim()
    if (email.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱地址"))
        return
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入正确的邮箱地址"))
        return
    }
    if (editor.code.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入邮箱验证码"))
        return
    }
    if (editor.password.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入新密码"))
        return
    }
    if (editor.password.length < 6) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "新密码至少 6 位"))
        return
    }
    if (editor.confirmPassword.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请确认新密码"))
        return
    }
    if (editor.password != editor.confirmPassword) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "两次输入的新密码不一致"))
        return
    }

    runtimeRunAccountAction(
        block = { findPasswordForApp(editor) },
        successMessage = "密码重置成功",
        onSuccess = {
            updateAccountState(accountStateAfterFindPasswordSuccess(currentAccountState(), email))
        }
    )
}

private fun LegacyStateRuntimeViewModelCore.startRegisterCodeCountdown(
    channel: String,
    contact: String
) {
    viewModelScope.launch {
        for (seconds in EMAIL_CODE_INTERVAL_SECONDS downTo 0) {
            val state = currentAccountState()
            if (state.authMode != AccountAuthMode.Register ||
                state.registerChannel != channel ||
                state.registerEditor.contact.trim() != contact
            ) {
                updateAccountState(accountStateWithRegisterCodeCountdown(state, 0))
                break
            }
            updateAccountState(accountStateWithRegisterCodeCountdown(state, seconds))
            if (seconds > 0) delay(1000L)
        }
    }
}

private fun LegacyStateRuntimeViewModelCore.startFindPasswordCodeCountdown() {
    viewModelScope.launch {
        val email = currentAccountState().findPasswordEditor.email.trim()
        for (seconds in EMAIL_CODE_INTERVAL_SECONDS downTo 0) {
            val state = currentAccountState()
            if (state.authMode != AccountAuthMode.FindPassword ||
                state.findPasswordEditor.email.trim() != email
            ) {
                updateAccountState(accountStateWithFindPasswordCodeCountdown(state, 0))
                break
            }
            updateAccountState(accountStateWithFindPasswordCodeCountdown(state, seconds))
            if (seconds > 0) delay(1000L)
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLogin() {
    val userName = currentAccountState().userName.trim()
    val password = currentAccountState().password
    if (userName.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入用户名"))
        return
    }
    if (password.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入密码"))
        return
    }

    viewModelScope.launch {
        updateAccountState(beginLogin(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loginForApp(userName = userName, password = password)
            }
        }.onSuccess { session ->
            updateAccountState(loggedInAccountState(currentAccountState(), session))
            updateFollowState(FollowUiState(isLoggedIn = true))
            legacyShowCachedFollowContent()
            selectAccountSection(AccountSection.Overview, forceRefresh = true)
        }.onFailure { error ->
            updateAccountState(
                accountStateWithLoginError(
                    currentAccountState(),
                    toUserFacingMessage(error, "登录失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLogout() {
    if (currentAccountState().isLoading) return
    viewModelScope.launch {
        updateAccountState(beginLogout(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().logoutForApp() }
        }.onSuccess {
            updateAccountState(loggedOutAccountState(currentAccountState()))
            updateFollowState(FollowUiState(isLoggedIn = false))
            refreshNotices(forceRefresh = true)
        }.onFailure { error ->
            if (runtimeHandleAccountSessionExpired(error)) return@onFailure
            updateAccountState(
                accountStateWithLogoutError(
                    currentAccountState(),
                    toUserFacingMessage(error, "退出登录失败")
                )
            )
        }
    }
}
