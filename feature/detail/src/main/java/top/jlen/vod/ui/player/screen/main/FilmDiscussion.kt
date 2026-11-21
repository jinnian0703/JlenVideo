package top.jlen.vod.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
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

@Composable
internal fun MessageComposer(title: String, maxLength: Int, onDismiss: () -> Unit,
                             onSend: suspend (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!sending) onDismiss() }, title = { Text(title) }, text = {
        Column {
            OutlinedTextField(text, { text = it.take(maxLength); error = null }, enabled = !sending,
                label = { Text("内容") }, maxLines = 4, supportingText = { Text(error ?: "${text.length}/$maxLength") }, isError = error != null)
        }
    }, confirmButton = {
        TextButton(enabled = text.isNotBlank() && !sending, onClick = {
            sending = true
            scope.launch {
                viewingRequest { onSend(text.trim()) }.onSuccess { onDismiss() }.onFailure { error = it.message ?: "发送失败，请重试" }
                sending = false
            }
        }) { Text(if (sending) "发送中…" else "发送") }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("取消") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilmDiscussion(api: CmsAppApi, vod: String, user: String, onLogin: () -> Unit) {
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
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Text("整部剧讨论", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        (readError ?: notice)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (messages.isEmpty()) {
            Text(if (loading) "正在加载讨论…" else if (readError == null) "还没有发言" else "稍后自动重试")
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), state = list, reverseLayout = true) {
                items(messages.asReversed(), key = { it.id }) { line ->
                    Column(Modifier.fillMaxWidth().combinedClickable(onLongClickLabel = "举报发言",
                        onLongClick = { if (user.isBlank()) onLogin() else report = line }, onClick = {}).padding(vertical = 8.dp)) {
                        Text(line.author, style = MaterialTheme.typography.labelMedium, color = UiPalette.TextSecondary)
                        Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (unread > 0) TextButton(onClick = { scope.launch { list.scrollToItem(0); unread = 0 } }) { Text("$unread 条新消息 ↓") }
        }
        if (user.isBlank()) OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("登录发言") }
        else Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it.take(500); notice = null }, Modifier.weight(1f),
                label = { Text("参与讨论") }, maxLines = 2, enabled = !sending)
            TextButton(enabled = draft.isNotBlank() && !sending, onClick = {
                sending = true
                scope.launch {
                    viewingRequest { api.sendChat(user, vod, draft) }
                        .onSuccess { draft = ""; notice = "已发送" }
                        .onFailure { notice = it.message ?: "发送失败，请重试" }
                    sending = false
                }
            }) { Text(if (sending) "发送中" else "发送") }
        }
    }
    report?.let { line -> AlertDialog(onDismissRequest = { report = null }, title = { Text("举报这条发言？") },
        text = { Text(line.text, maxLines = 3) }, dismissButton = { TextButton(onClick = { report = null }) { Text("取消") } },
        confirmButton = { TextButton(onClick = {
            report = null
            scope.launch { notice = viewingRequest { api.reportChat(user, line.id) }.fold({ "已提交举报" }, { it.message ?: "举报失败" }) }
        }) { Text("举报") } }) }
}
