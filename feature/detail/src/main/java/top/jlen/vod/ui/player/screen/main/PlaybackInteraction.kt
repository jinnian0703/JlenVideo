package top.jlen.vod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackTabs(discussing: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
        .background(UiPalette.Surface, CircleShape).selectableGroup().padding(horizontal = 4.dp)) {
        listOf("选集", "讨论").forEachIndexed { index, label ->
            val chat = index == 1
            val selected = discussing == chat
            Box(Modifier.weight(1f).clip(CircleShape).selectable(selected, role = Role.Tab, onClick = { onSelect(chat) })
                .heightIn(min = 48.dp).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                val ink = if (selected) UiPalette.Surface else UiPalette.TextSecondary
                Row(Modifier.fillMaxWidth().background(if (selected) UiPalette.Ink else Color.Transparent, CircleShape).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (chat) Icons.Rounded.Forum else Icons.Rounded.VideoLibrary, null, Modifier.size(18.dp), tint = ink)
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink)
                }
            }
        }
    }
}

@Composable
internal fun QuickDanmakuField(modifier: Modifier, fullscreen: Boolean, draft: String, sending: Boolean, onChange: (String) -> Unit,
                             onFocus: (Boolean) -> Unit, login: (() -> Unit)?, onSend: (() -> Unit) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val updateFocus by rememberUpdatedState(onFocus)
    val focusManager = LocalFocusManager.current
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var sawKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(focused, imeOpen) {
        if (focused && sawKeyboard && !imeOpen) focusManager.clearFocus()
        sawKeyboard = focused && imeOpen
    }
    BackHandler(enabled = focused) { focusManager.clearFocus() }
    DisposableEffect(Unit) { onDispose { focused = false; updateFocus(false) } }
    val canSend = !sending && draft.isNotBlank()
    val submit = { if (canSend) onSend { if (focused) focusManager.clearFocus() } }
    val ink = if (fullscreen) Color.White else UiPalette.Ink
    val hint = if (fullscreen) Color.White.copy(alpha = .8f) else UiPalette.TextSecondary
    val outline = if (focused) ink else if (fullscreen) Color.White.copy(alpha = .38f) else UiPalette.BorderSoft
    val height = if (fullscreen) 34.dp else 48.dp
    Row(modifier.clip(CircleShape).background(if (fullscreen) Color.Black.copy(alpha = .55f) else UiPalette.Surface)
        .border(1.dp, outline, CircleShape)
        .then(if (login != null) Modifier.clickable(role = Role.Button, onClick = login) else Modifier)
        .height(height).padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (login != null) Text("登录发弹幕", style = MaterialTheme.typography.labelLarge, color = hint, maxLines = 1)
        else {
            BasicTextField(draft, { onChange(it.replace('\n', ' ').replace('\r', ' ').take(200)) },
                Modifier.weight(1f).onFocusChanged { focused = it.isFocused; updateFocus(it.isFocused) }
                    .semantics { contentDescription = "快速发弹幕" },
                singleLine = true, readOnly = sending, textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
                cursorBrush = SolidColor(ink), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }), decorationBox = { input ->
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) Text("发弹幕…", style = MaterialTheme.typography.bodyMedium, color = hint, maxLines = 1)
                        input()
                    }
                })
            IconButton(onClick = submit, enabled = canSend, modifier = Modifier.size(height)) {
                if (sending) CircularProgressIndicator(Modifier.size(16.dp), color = ink, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Rounded.Send, "发送弹幕", Modifier.size(16.dp), tint = if (canSend) ink else hint.copy(alpha = .55f))
            }
        }
    }
}
