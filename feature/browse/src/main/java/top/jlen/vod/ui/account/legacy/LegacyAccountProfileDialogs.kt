package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.jlen.vod.data.UserProfileEditor

@Composable
internal fun BindEmailDialog(
    editor: UserProfileEditor,
    isSaving: Boolean,
    emailBindCodeCountdown: Int,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSendEmailCode: () -> Unit,
    onBindEmail: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AccountDialogHeader(
                    label = "邮箱绑定",
                    title = "绑定邮箱",
                    description = "输入邮箱和验证码。"
                )
                AccountDialogInfoCard(
                    text = "用于找回账号和接收验证码。"
                )
                ProfileEditorField(
                    label = "邮箱",
                    value = editor.pendingEmail,
                    onValueChange = { value -> onEditorChange { it.copy(pendingEmail = value) } },
                    keyboardType = KeyboardType.Email,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileEditorField(
                        label = "邮箱验证码",
                        value = editor.emailCode,
                        onValueChange = { value -> onEditorChange { it.copy(emailCode = value) } },
                        keyboardType = KeyboardType.Ascii,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onSendEmailCode,
                        enabled = !isSaving && emailBindCodeCountdown <= 0,
                        modifier = Modifier
                            .width(112.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.BorderSoft),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.72f),
                            contentColor = UiPalette.Accent,
                            disabledContainerColor = UiPalette.SurfaceSoft.copy(alpha = 0.56f),
                            disabledContentColor = UiPalette.TextMuted
                        )
                    ) {
                        Text(
                            text = when {
                                emailBindCodeCountdown > 0 -> "${emailBindCodeCountdown}s"
                                isSaving -> "发送中"
                                else -> "获取验证码"
                            },
                            maxLines = 1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                AccountDialogActions(
                    dismissText = "取消",
                    confirmText = if (isSaving) "绑定中..." else "确认绑定",
                    confirmEnabled = !isSaving,
                    onDismiss = onDismiss,
                    onConfirm = onBindEmail
                )
            }
        }
    }
}

@Composable
private fun AccountDialogInfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.76f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextPrimary
        )
    }
}

@Composable
internal fun ChangePasswordDialog(
    editor: UserProfileEditor,
    isSaving: Boolean,
    onEditorChange: ((UserProfileEditor) -> UserProfileEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AccountDialogHeader(
                    label = "密码设置",
                    title = "修改密码",
                    description = "填写原密码和新密码后保存。"
                )
                ProfileEditorField(
                    label = "原密码",
                    value = editor.currentPassword,
                    onValueChange = { value -> onEditorChange { it.copy(currentPassword = value) } },
                    password = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )
                ProfileEditorField(
                    label = "新密码",
                    value = editor.newPassword,
                    onValueChange = { value -> onEditorChange { it.copy(newPassword = value) } },
                    password = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )
                ProfileEditorField(
                    label = "确认新密码",
                    value = editor.confirmPassword,
                    onValueChange = { value -> onEditorChange { it.copy(confirmPassword = value) } },
                    password = true
                )
                AccountDialogActions(
                    dismissText = "取消",
                    confirmText = if (isSaving) "保存中..." else "保存密码",
                    confirmEnabled = !isSaving,
                    onDismiss = onDismiss,
                    onConfirm = onSave
                )
            }
        }
    }
}

@Composable
internal fun UnbindEmailConfirmDialog(
    email: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(UiPalette.DangerSurface.copy(alpha = 0.68f), RoundedCornerShape(999.dp))
                        .border(1.dp, UiPalette.DangerBorder.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "邮箱绑定",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = UiPalette.DangerText
                    )
                }
                Text(
                    text = "解绑邮箱",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "确认解绑邮箱？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.76f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "$email 将不再用于找回账号和接收验证码。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(110.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.36f),
                            contentColor = UiPalette.TextPrimary
                        )
                    ) {
                        Text("取消", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.width(122.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.DangerText,
                            contentColor = UiPalette.Surface
                        )
                    ) {
                        Text("确认解绑", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDialogHeader(
    label: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .background(UiPalette.Accent.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
                .border(1.dp, UiPalette.Accent.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = UiPalette.Accent
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = UiPalette.TextSecondary
        )
    }
}

@Composable
private fun AccountDialogActions(
    dismissText: String,
    confirmText: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            enabled = confirmEnabled,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.36f),
                contentColor = UiPalette.TextPrimary
            )
        ) {
            Text(dismissText, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            enabled = confirmEnabled,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UiPalette.Accent,
                contentColor = UiPalette.AccentText
            )
        ) {
            Text(confirmText, fontWeight = FontWeight.Bold)
        }
    }
}
