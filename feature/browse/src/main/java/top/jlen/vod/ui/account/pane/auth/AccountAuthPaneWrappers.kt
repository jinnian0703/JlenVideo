package top.jlen.vod.ui

import androidx.compose.runtime.Composable
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.RegisterEditor

@Composable
internal fun AccountRegisterPane(
    state: AccountUiState,
    onEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) = LegacyAccountRegisterPane(
    state = state,
    onEditorChange = onEditorChange,
    onRefreshCaptcha = onRefreshCaptcha,
    onSendCode = onSendCode,
    onSubmit = onSubmit
)

@Composable
internal fun AccountFindPasswordPane(
    state: AccountUiState,
    onEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSubmit: () -> Unit
) = LegacyAccountFindPasswordPane(
    state = state,
    onEditorChange = onEditorChange,
    onRefreshCaptcha = onRefreshCaptcha,
    onSubmit = onSubmit
)
