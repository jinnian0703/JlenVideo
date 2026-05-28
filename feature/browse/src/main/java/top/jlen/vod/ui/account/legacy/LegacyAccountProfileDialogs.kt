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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
                    title = "绑定邮箱",
                    icon = Icons.Rounded.AlternateEmail
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
                            .width(118.dp)
                            .height(56.dp),
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
                    title = "修改密码",
                    icon = Icons.Rounded.Lock
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
                AccountDialogHeader(
                    title = "解绑邮箱",
                    icon = Icons.Rounded.MarkEmailRead,
                    danger = true
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.Ink
                )
                Text(
                    text = "解绑后将不能通过该邮箱找回账号。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
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
    title: String,
    icon: ImageVector,
    danger: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(
                    if (danger) UiPalette.DangerSurface else UiPalette.Accent.copy(alpha = 0.1f),
                    RoundedCornerShape(18.dp)
                )
                .border(
                    1.dp,
                    if (danger) UiPalette.DangerBorder.copy(alpha = 0.6f) else UiPalette.Accent.copy(alpha = 0.18f),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (danger) Icons.Rounded.Warning else icon,
                contentDescription = null,
                tint = if (danger) UiPalette.DangerText else UiPalette.Accent
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = UiPalette.Ink
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
