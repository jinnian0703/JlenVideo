package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

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
                    text = "确认解绑当前账号邮箱吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = UiPalette.SurfaceSoft.copy(alpha = 0.76f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "解绑后，$email 将不再用于找回账号和接收验证码。后续可以重新绑定邮箱。",
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
