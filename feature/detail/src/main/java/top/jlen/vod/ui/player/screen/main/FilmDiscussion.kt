package top.jlen.vod.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.jlen.vod.data.*

internal suspend fun <T> viewingRequest(action: suspend () -> T): Result<T> = try {
    Result.success(action())
} catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilmDiscussion(api: CmsAppApi, vod: String, user: String, onLogin: () -> Unit, modifier: Modifier = Modifier,
                            compact: Boolean = false, showComposer: Boolean = true) {
    var messages by remember(vod) { mutableStateOf(emptyList<ChatLine>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var draft by rememberSaveable(vod, user) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ChatLine?>(null) }
    var unread by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vod, owner, user) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val cursor = messages.lastOrNull()?.id ?: 0
                viewingRequest { api.chat(vod, cursor) }.onSuccess { rows ->
                    readError = null
                    val new = rows.filter { it.id > cursor }.distinctBy { it.id }.sortedBy { it.id }
                    if (new.isNotEmpty()) {
                        val follow = messages.isEmpty() || (!list.canScrollBackward && !list.isScrollInProgress)
                        messages = (messages + new).takeLast(500)
                        if (follow) { list.scrollToItem(0); unread = 0 } else unread += new.size
                    }
                }.onFailure { readError = it.message ?: "讨论加载失败" }
                loading = false
                delay(5000)
            }
        }
    }
    LaunchedEffect(list.canScrollBackward) { if (!list.canScrollBackward) unread = 0 }
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        if (!compact) Text("影片讨论", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = UiPalette.Ink)
        (readError ?: notice)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = if (readError != null) UiPalette.DangerText else UiPalette.TextSecondary) }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = UiPalette.Accent, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(28.dp), tint = UiPalette.TextSecondary)
                    Text(when { loading -> "正在加载讨论…"; readError != null -> "稍后自动重试"; else -> "还没有人讨论这部影片" },
                        style = MaterialTheme.typography.bodyMedium, color = UiPalette.TextSecondary)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(messages.asReversed(), key = { it.id }) { line ->
                    val mine = user.isNotBlank() && line.authorId == user
                    Column(Modifier.fillParentMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Column(Modifier.fillMaxWidth(.88f), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Text(if (mine) "我" else line.author.ifBlank { "观众" }, Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = UiPalette.TextSecondary)
                            val bubble = RoundedCornerShape(topStart = if (mine) 16.dp else 4.dp, topEnd = if (mine) 4.dp else 16.dp,
                                bottomStart = 16.dp, bottomEnd = 16.dp)
                            Text(line.text, Modifier.padding(top = 3.dp).clip(bubble).background(if (mine) UiPalette.AccentGlow else UiPalette.Surface)
                                .combinedClickable(onLongClickLabel = "举报发言", onLongClick = { if (user.isBlank()) onLogin() else report = line }, onClick = {})
                                .padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium, color = UiPalette.Ink)
                        }
                    }
                }
            }
            if (unread > 0 && !compact) FilledTonalButton(modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = { scope.launch { list.scrollToItem(0); unread = 0 } }) { Text("查看 $unread 条新消息") }
        }
        if (!showComposer) return@Column
        val dock = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        if (user.isBlank()) Button(onClick = onLogin, modifier = dock.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Ink, contentColor = UiPalette.Surface)) { Text("登录参与讨论") }
        else Row(dock, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { draft = it.take(500); notice = null }, Modifier.weight(1f), label = { Text("参与讨论") },
                shape = RoundedCornerShape(20.dp), maxLines = 2, readOnly = sending,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = UiPalette.Surface, focusedContainerColor = UiPalette.Surface,
                    unfocusedBorderColor = UiPalette.BorderSoft, focusedBorderColor = UiPalette.Accent))
            FilledIconButton(modifier = Modifier.size(48.dp), shape = CircleShape, enabled = draft.isNotBlank() && !sending, onClick = {
                sending = true
                scope.launch {
                    viewingRequest { api.sendChat(user, vod, draft) }
                        .onSuccess { draft = ""; notice = "已发送" }
                        .onFailure { notice = it.message ?: "发送失败，请重试" }
                    sending = false
                }
            }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = UiPalette.Ink, contentColor = UiPalette.Surface)) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = UiPalette.TextSecondary)
                else Icon(Icons.AutoMirrored.Rounded.Send, "发送发言", Modifier.size(20.dp))
            }
        }
    }
    report?.let { line -> AlertDialog(onDismissRequest = { report = null }, title = { Text("举报这条发言？") },
        text = { Text(line.text, maxLines = 3) }, dismissButton = { TextButton(onClick = { report = null }) { Text("取消") } },
        confirmButton = { TextButton(onClick = {
            report = null
            scope.launch { notice = viewingRequest { api.reportChat(user, line.id) }.fold({ "已提交举报" }, { it.message ?: "举报失败" }) }
        }) { Text("举报") } }) }
}
