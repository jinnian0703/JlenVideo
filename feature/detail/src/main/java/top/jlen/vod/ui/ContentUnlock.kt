package top.jlen.vod.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ContentUnlock(
    label: String,
    filmTitle: String = "",
    inline: Boolean = false,
    verify: suspend (String) -> Unit
) {
    var showing by rememberSaveable(label, filmTitle) { mutableStateOf(false) }
    if (inline) {
        if (!showing) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = UiDimens.PagePadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("切换选集", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = UiPalette.Ink)
                FilledTonalButton(onClick = { showing = true },
                    modifier = Modifier.heightIn(min = UiDimens.SecondaryButtonHeight),
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = UiPalette.SurfaceStrong, contentColor = UiPalette.Ink)) {
                    UnlockButtonLabel(label)
                }
            }
        }
    } else {
        Button(onClick = { showing = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = UiDimens.PrimaryButtonHeight),
            shape = RoundedCornerShape(UiDimens.ControlRadius),
            colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Accent, contentColor = UiPalette.OnAccentButton)) {
            UnlockButtonLabel(label)
        }
    }
    if (showing) ContentUnlockPrompt(label, filmTitle, inline, onDismiss = { showing = false }, verify = verify)
}

@Composable
private fun UnlockButtonLabel(label: String) {
    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text("输入${label}密码", fontWeight = FontWeight.Bold)
}

@Composable
internal fun ContentUnlockPrompt(
    label: String,
    filmTitle: String = "",
    inline: Boolean = false,
    onDismiss: () -> Unit,
    onVerified: () -> Unit = onDismiss,
    verify: suspend (String) -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    fun submit() {
        if (busy || answer.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                verify(answer)
                onVerified()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                error = failure.message?.takeIf(String::isNotBlank) ?: "验证失败，请重试"
                focus.requestFocus()
            } finally {
                busy = false
            }
        }
    }

    val form: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (inline) UiDimens.CardRadius else UiDimens.LargeContainerRadius),
            color = UiPalette.Surface,
            border = BorderStroke(1.dp, UiPalette.Border)
        ) {
            // The inline form is already in the player's LazyColumn.
            val scroll = if (inline) Modifier else Modifier.verticalScroll(rememberScrollState())
            Column(scroll.padding(if (inline) UiDimens.CardPadding else 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(UiPalette.AccentGlow, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = UiPalette.Ink, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("解锁$label", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = UiPalette.Ink)
                        if (filmTitle.isNotBlank()) Text(filmTitle, style = MaterialTheme.typography.bodyMedium,
                            color = UiPalette.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it; error = null },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("${label}密码") },
                    singleLine = true,
                    readOnly = busy,
                    isError = error != null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (visible) "隐藏密码" else "显示密码", tint = UiPalette.TextSecondary)
                        }
                    },
                    supportingText = error?.let { message -> ({
                        Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = UiPalette.DangerText)
                    }) },
                    shape = RoundedCornerShape(UiDimens.ControlRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = UiPalette.SurfaceSoft, unfocusedContainerColor = UiPalette.SurfaceSoft,
                        focusedBorderColor = UiPalette.Accent, unfocusedBorderColor = UiPalette.BorderSoft,
                        focusedLabelColor = UiPalette.Ink, unfocusedLabelColor = UiPalette.TextSecondary,
                        errorContainerColor = UiPalette.DangerSurface, errorBorderColor = UiPalette.DangerText,
                        errorLabelColor = UiPalette.DangerText
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = UiDimens.SecondaryButtonHeight),
                        shape = RoundedCornerShape(UiDimens.ControlRadius), border = BorderStroke(1.dp, UiPalette.BorderSoft),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Ink)) {
                        Text("取消")
                    }
                    Button(onClick = ::submit, enabled = answer.isNotBlank() && !busy,
                        modifier = Modifier.weight(1.5f).heightIn(min = UiDimens.SecondaryButtonHeight),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(UiDimens.ControlRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Accent, contentColor = UiPalette.OnAccentButton,
                            disabledContainerColor = UiPalette.SurfaceStrong, disabledContentColor = UiPalette.TextMuted)) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = UiPalette.TextSecondary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (busy) "验证中" else "确认解锁", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (!inline) LaunchedEffect(Unit) { focus.requestFocus() }
    }
    if (inline) Box(Modifier.padding(horizontal = UiDimens.PagePadding)) { form() }
    else Dialog(onDismissRequest = { if (!busy) onDismiss() }) { form() }
}
