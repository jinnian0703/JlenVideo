package top.jlen.vod.ui

import androidx.compose.runtime.Composable
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.RegisterEditor

@Composable
internal fun AccountRegisterPane(
    state: AccountUiState,
    error: String? = null,
    onEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) = LegacyAccountRegisterPane(
    state = state,
    error = error,
    onEditorChange = onEditorChange,
    onRefreshCaptcha = onRefreshCaptcha,
    onSendCode = onSendCode,
    onSubmit = onSubmit
)

@Composable
internal fun AccountFindPasswordPane(
    state: AccountUiState,
    error: String? = null,
    onEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) = LegacyAccountFindPasswordPane(
    state = state,
    error = error,
    onEditorChange = onEditorChange,
    onSendCode = onSendCode,
    onSubmit = onSubmit
)
