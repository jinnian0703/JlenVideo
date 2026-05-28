package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.RegisterEditor

@Composable
fun UserAgreementOnboardingScreen(
    onAccept: () -> Unit,
    onExit: () -> Unit
) {
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    if (showExitConfirm) {
        Dialog(
            onDismissRequest = { showExitConfirm = false },
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
                colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(UiPalette.DangerSurface, RoundedCornerShape(UiDimens.ControlRadius))
                                .border(1.dp, UiPalette.DangerBorder, RoundedCornerShape(UiDimens.ControlRadius)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.DangerText
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "确定不同意并退出？",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = UiPalette.Ink
                            )
                            Text(
                                text = "协议是首次使用前的必要确认",
                                style = MaterialTheme.typography.bodySmall,
                                color = UiPalette.TextSecondary
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft),
                        shape = RoundedCornerShape(UiDimens.ControlRadius)
                    ) {
                        Text(
                            text = "不同意用户协议与隐私说明将无法继续使用 JlenVideo。你可以返回继续阅读，或确定退出应用。",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showExitConfirm = false },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = UiDimens.SecondaryButtonHeight),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.TextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("返回阅读", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onExit,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = UiDimens.SecondaryButtonHeight),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.DangerText,
                                contentColor = UiPalette.Surface
                            )
                        ) {
                            Text("确定退出", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiDimens.PagePadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "用户协议与隐私说明",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = "请先阅读并同意以下内容，再继续使用 JlenVideo。",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                JlenUserAgreementSections.forEach { section ->
                    AgreementSection(
                        title = section.title,
                        body = section.body
                    )
                }
            }
        }

        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text("同意并继续", fontWeight = FontWeight.ExtraBold)
        }
        TextButton(
            onClick = { showExitConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "不同意",
                fontWeight = FontWeight.Bold,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun AgreementSection(
    title: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = UiPalette.TextPrimary
        )
    }
}

@Composable
fun FirstLoginOnboardingScreen(
    state: AccountUiState,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    onAuthModeChange: (AccountAuthMode) -> Unit,
    onRegisterEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshRegisterCaptcha: () -> Unit,
    onSendRegisterCode: () -> Unit,
    onRegister: () -> Unit,
    onFindPasswordEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onSendFindPasswordCode: () -> Unit,
    onFindPassword: () -> Unit
) {
    val title = when (state.authMode) {
        AccountAuthMode.Register -> "注册账号"
        AccountAuthMode.FindPassword -> "找回密码"
        else -> "登录账号"
    }
    val subtitle = when (state.authMode) {
        AccountAuthMode.Register -> "创建账号后，可继续在这里登录并完成首次引导。"
        AccountAuthMode.FindPassword -> "重置密码后，可返回登录并继续进入应用。"
        else -> "登录后可同步追剧、播放记录和会员积分，也可以先跳过进入首页。"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UiPalette.Ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            shape = RoundedCornerShape(UiDimens.LargeContainerRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.error?.takeIf { it.isNotBlank() }?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UiPalette.DangerSurface),
                        shape = RoundedCornerShape(UiDimens.ControlRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.DangerBorder)
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.DangerText
                        )
                    }
                }

                when (state.authMode) {
                    AccountAuthMode.Register -> FirstLoginRegisterPane(
                        state = state,
                        onEditorChange = onRegisterEditorChange,
                        onRefreshCaptcha = onRefreshRegisterCaptcha,
                        onSendCode = onSendRegisterCode,
                        onSubmit = onRegister
                    )

                    AccountAuthMode.FindPassword -> FirstLoginFindPasswordPane(
                        state = state,
                        onEditorChange = onFindPasswordEditorChange,
                        onSendCode = onSendFindPasswordCode,
                        onSubmit = onFindPassword
                    )

                    else -> FirstLoginLoginPane(
                        state = state,
                        onUserNameChange = onUserNameChange,
                        onPasswordChange = onPasswordChange,
                        onLogin = onLogin
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.authMode == AccountAuthMode.Login) {
                        OutlinedButton(
                            onClick = { onAuthModeChange(AccountAuthMode.Register) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("注册账号", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onAuthModeChange(AccountAuthMode.FindPassword) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("找回密码", maxLines = 1)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAuthModeChange(AccountAuthMode.Login) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(UiDimens.ControlRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
                        ) {
                            Text("返回登录", maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "跳过，先逛逛",
                fontWeight = FontWeight.Bold,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun FirstLoginLoginPane(
    state: AccountUiState,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingTextField(
            value = state.userName,
            onValueChange = onUserNameChange,
            label = "用户名",
            placeholder = "请输入站内用户名"
        )
        OnboardingTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "密码",
            placeholder = "请输入密码",
            isPassword = true
        )

        Button(
            onClick = onLogin,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isLoading) "正在登录..." else "立即登录", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun FirstLoginRegisterPane(
    state: AccountUiState,
    onEditorChange: ((RegisterEditor) -> RegisterEditor) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingTextField(
            value = state.registerEditor.userName,
            onValueChange = { value -> onEditorChange { it.copy(userName = value) } },
            label = "用户名",
            placeholder = "请输入注册用户名"
        )
        OnboardingTextField(
            value = state.registerEditor.password,
            onValueChange = { value -> onEditorChange { it.copy(password = value) } },
            label = "密码",
            placeholder = "请输入注册密码",
            isPassword = true
        )
        OnboardingTextField(
            value = state.registerEditor.confirmPassword,
            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
            label = "确认密码",
            placeholder = "请再次输入密码",
            isPassword = true
        )
        OnboardingTextField(
            value = state.registerEditor.contact,
            onValueChange = { value -> onEditorChange { it.copy(contact = value) } },
            label = state.registerContactLabel,
            placeholder = "请输入${state.registerContactLabel}",
            keyboardType = if (state.registerChannel == "phone") KeyboardType.Phone else KeyboardType.Email
        )
        if (state.registerRequiresCode) {
            OnboardingTextField(
                value = state.registerEditor.code,
                onValueChange = { value -> onEditorChange { it.copy(code = value) } },
                label = state.registerCodeLabel,
                placeholder = "请输入${state.registerCodeLabel}"
            )
            OutlinedButton(
                onClick = onSendCode,
                enabled = !state.isActionLoading && state.registerCodeCountdown <= 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UiDimens.SecondaryButtonHeight),
                shape = RoundedCornerShape(UiDimens.ControlRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft)
            ) {
                Text(
                    if (state.registerCodeCountdown > 0) {
                        "${state.registerCodeCountdown}s"
                    } else if (state.isActionLoading) {
                        "发送中..."
                    } else {
                        "发送${state.registerCodeLabel}"
                    }
                )
            }
        }
        if (state.registerRequiresVerify) {
            OnboardingTextField(
                value = state.registerEditor.verify,
                onValueChange = { value -> onEditorChange { it.copy(verify = value) } },
                label = "图片验证码",
                placeholder = "请输入图片验证码"
            )
            CaptchaImageBox(
                bytes = state.registerCaptcha,
                isLoading = state.isContentLoading,
                onRefresh = onRefreshCaptcha
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isActionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isActionLoading) "注册中..." else "立即注册", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun FirstLoginFindPasswordPane(
    state: AccountUiState,
    onEditorChange: ((FindPasswordEditor) -> FindPasswordEditor) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingTextField(
            value = state.findPasswordEditor.email,
            onValueChange = { value -> onEditorChange { it.copy(email = value) } },
            label = "邮箱",
            placeholder = "请输入绑定邮箱",
            keyboardType = KeyboardType.Email
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnboardingTextField(
                value = state.findPasswordEditor.code,
                onValueChange = { value -> onEditorChange { it.copy(code = value) } },
                label = "邮箱验证码",
                placeholder = "请输入验证码",
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )
            OutlinedButton(
                onClick = onSendCode,
                enabled = !state.isActionLoading && state.findPasswordCodeCountdown <= 0,
                modifier = Modifier
                    .width(118.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(UiDimens.ControlRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.72f),
                    contentColor = UiPalette.Accent,
                    disabledContainerColor = UiPalette.SurfaceSoft.copy(alpha = 0.56f),
                    disabledContentColor = UiPalette.TextMuted
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = if (state.findPasswordCodeCountdown > 0) {
                        "${state.findPasswordCodeCountdown}s"
                    } else if (state.isActionLoading) {
                        "发送中"
                    } else {
                        "获取验证码"
                    },
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        OnboardingTextField(
            value = state.findPasswordEditor.password,
            onValueChange = { value -> onEditorChange { it.copy(password = value) } },
            label = "新密码",
            placeholder = "请输入新的登录密码",
            isPassword = true
        )
        OnboardingTextField(
            value = state.findPasswordEditor.confirmPassword,
            onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
            label = "确认新密码",
            placeholder = "请再次输入新密码",
            isPassword = true
        )

        Button(
            onClick = onSubmit,
            enabled = !state.isActionLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(if (state.isActionLoading) "重置中..." else "重置密码", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiDimens.ControlRadius),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = UiPalette.Accent,
            unfocusedBorderColor = UiPalette.BorderSoft,
            focusedTextColor = UiPalette.Ink,
            unfocusedTextColor = UiPalette.Ink,
            cursorColor = UiPalette.Accent,
            focusedContainerColor = UiPalette.SurfaceSoft,
            unfocusedContainerColor = UiPalette.SurfaceSoft
        )
    )
}
