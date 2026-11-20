package top.jlen.vod.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun LoginChallengeDialog(
    challenge: LoginChallengeState,
    busy: Boolean,
    dismiss: () -> Unit,
    submit: (String) -> Unit,
    refresh: () -> Unit
) {
    val image = challenge.image
    var answer by remember(image) { mutableStateOf("") }
    val bitmap = remember(image) { image?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val focusRequester = remember { FocusRequester() }
    val canSubmit = !busy && bitmap != null && answer.isNotBlank()
    val onSubmit = { if (canSubmit) submit(answer.trim()) }
    val controlShape = RoundedCornerShape(UiDimens.ControlRadius)

    Dialog(onDismissRequest = dismiss) {
        LaunchedEffect(bitmap) { if (bitmap != null) focusRequester.requestFocus() }
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = UiPalette.Surface
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).background(UiPalette.AccentGlow, controlShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = UiPalette.Accent, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = UiPalette.Ink)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("登录验证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = UiPalette.Ink)
                        Text("请输入图片中的字符", style = MaterialTheme.typography.bodyMedium, color = UiPalette.TextSecondary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clip(controlShape).background(UiPalette.SurfaceSoft)
                        .clickable(enabled = !busy, role = Role.Button, onClickLabel = "更换验证码", onClick = refresh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                        if (bitmap != null) {
                            Image(bitmap, "图形验证码", modifier = Modifier.fillMaxWidth().height(64.dp), contentScale = ContentScale.Fit)
                        } else {
                            Text(if (busy) "正在加载验证码…" else "图片未能显示", style = MaterialTheme.typography.bodyMedium, color = UiPalette.TextSecondary)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = UiPalette.TextSecondary)
                        Text("换一张", style = MaterialTheme.typography.labelMedium, color = UiPalette.TextSecondary)
                    }
                }

                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("验证码") },
                    isError = challenge.invalidAnswer && answer.isBlank(),
                    supportingText = challenge.message?.takeUnless { challenge.invalidAnswer && answer.isNotBlank() }?.let { message ->
                        { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    },
                    singleLine = true,
                    readOnly = busy || bitmap == null,
                    shape = controlShape,
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UiPalette.Accent,
                        unfocusedBorderColor = UiPalette.BorderSoft,
                        focusedLabelColor = UiPalette.Ink,
                        unfocusedLabelColor = UiPalette.TextSecondary,
                        cursorColor = UiPalette.Accent
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("取消", color = UiPalette.TextSecondary)
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier.widthIn(min = 100.dp).heightIn(min = 48.dp),
                        shape = controlShape,
                        colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Ink, contentColor = UiPalette.Surface)
                    ) {
                        Text("登录", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
