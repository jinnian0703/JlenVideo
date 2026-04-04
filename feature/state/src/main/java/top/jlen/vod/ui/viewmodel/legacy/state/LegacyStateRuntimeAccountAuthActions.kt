package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LegacyStateRuntimeViewModel.legacySendRegisterCode() {
    val editor = currentAccountState().registerEditor
    val contact = editor.contact.trim()
    if (contact.isBlank()) {
        updateAccountState(
            accountStateWithValidationError(
                currentAccountState(),
                "请输入${currentAccountState().registerContactLabel}"
            )
        )
        return
    }

    if (editor.channel == "email" && !contact.contains("@")) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入正确的邮箱地址"))
        return
    }

    runtimeRunAccountAction(
        block = { sendRegisterCodeForApp(editor.channel, contact) },
        onSuccess = { }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyRegister() {
    val editor = currentAccountState().registerEditor
    if (editor.userName.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入用户名"))
        return
    }
    if (editor.password.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入密码"))
        return
    }
    if (editor.confirmPassword.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请确认密码"))
        return
    }
    if (editor.password != editor.confirmPassword) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "两次输入的密码不一致"))
        return
    }
    if (editor.contact.isBlank()) {
        updateAccountState(
            accountStateWithValidationError(
                currentAccountState(),
                "请输入${currentAccountState().registerContactLabel}"
            )
        )
        return
    }
    if (currentAccountState().registerRequiresCode && editor.code.isBlank()) {
        updateAccountState(
            accountStateWithValidationError(
                currentAccountState(),
                "请输入${currentAccountState().registerCodeLabel}"
            )
        )
        return
    }
    if (currentAccountState().registerRequiresVerify && editor.verify.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "请输入图片验证码"))
        return
    }

    runtimeRunAccountAction(
        block = { registerForApp(editor.copy(channel = currentAccountState().registerChannel)) },
        onSuccess = {
            updateAccountState(accountStateAfterRegisterSuccess(currentAccountState(), editor.userName))
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyFindPassword() {
    val editor = currentAccountState().findPasswordEditor
    if (editor.userName.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇疯緭鍏ョ敤鎴峰悕"))
        return
    }
    if (editor.question.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇疯緭鍏ュ瘑淇濋棶棰?"))
        return
    }
    if (editor.answer.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇疯緭鍏ュ瘑淇濈瓟妗?"))
        return
    }
    if (editor.password.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇疯緭鍏ユ柊瀵嗙爜"))
        return
    }
    if (editor.confirmPassword.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇风‘璁ゆ柊瀵嗙爜"))
        return
    }
    if (editor.password != editor.confirmPassword) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?"))
        return
    }
    if (currentAccountState().findPasswordRequiresVerify && editor.verify.isBlank()) {
        updateAccountState(accountStateWithValidationError(currentAccountState(), "璇疯緭鍏ュ浘鐗囬獙璇佺爜"))
        return
    }

    runtimeRunAccountAction(
        block = { findPasswordForApp(editor) },
        onSuccess = {
            updateAccountState(accountStateAfterFindPasswordSuccess(currentAccountState(), editor.userName))
        }
    )
}

internal fun LegacyStateRuntimeViewModel.legacyLogin() {
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
            selectAccountSection(AccountSection.Profile, forceRefresh = true)
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

internal fun LegacyStateRuntimeViewModel.legacyLogout() {
    if (currentAccountState().isLoading) return
    viewModelScope.launch {
        updateAccountState(beginLogout(currentAccountState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().logoutForApp() }
        }.onSuccess {
            updateAccountState(loggedOutAccountState(currentAccountState()))
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
