package top.jlen.vod.offline

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.jlen.vod.ui.appTopInsetsPadding

internal val Stage.label get() = when (this) {
    Stage.QUEUED -> "排队中"; Stage.ACTIVE -> "下载中"; Stage.PAUSED -> "已暂停"; Stage.FAILED -> "下载失败"; Stage.DONE -> "已下载"
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lib = OfflineLibrary.get(context)
    val rows by lib.entries.collectAsState()
    val mobile by lib.mobile.collectAsState()
    val allowed by lib.allowed.collectAsState()
    var manage by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf(emptySet<String>()) }
    var completed by rememberSaveable { mutableStateOf(rows.isNotEmpty() && rows.all { it.stage == Stage.DONE }) }
    val done = rows.count { it.stage == Stage.DONE }
    val visible = rows.filter { (it.stage == Stage.DONE) == completed }
    val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
    val chosen = selected.intersect(visibleIds)
    val colors = MaterialTheme.colorScheme
    fun size(n: Long) = Formatter.formatShortFileSize(context, n)
    LaunchedEffect(lib) { lib.schedule() }
    Column(Modifier.fillMaxSize().appTopInsetsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text("下载管理", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(enabled = rows.isNotEmpty() || manage, onClick = { manage = !manage; selected = emptySet() }) {
                Text(if (manage) "完成" else "管理")
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "storage") {
                Surface(shape = RoundedCornerShape(20.dp), color = colors.primary.copy(alpha = .09f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.VideoLibrary, null, Modifier.size(20.dp), tint = colors.primary)
                            Text("离线存储", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(size(rows.sumOf { it.bytes }), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("本机可用 ${size(lib.root.usableSpace)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.primary.copy(alpha = .15f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("允许移动网络下载", style = MaterialTheme.typography.bodyMedium)
                                Text(if (mobile) "可能产生流量费用" else "仅在 Wi-Fi 下下载", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Switch(checked = mobile, onCheckedChange = lib::setMobile)
                        }
                    }
                }
            }
            item(key = "tabs") {
                TabRow(selectedTabIndex = if (completed) 1 else 0, containerColor = colors.background) {
                    listOf("进行中 ${rows.size - done}", "已完成 $done").forEachIndexed { i, label ->
                        Tab(selected = completed == (i == 1), onClick = { completed = i == 1; selected = emptySet() }, text = { Text(label) })
                    }
                }
            }
            if (manage && visible.isNotEmpty()) item(key = "batch") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selected = if (chosen == visibleIds) emptySet() else visibleIds }) {
                        Text(if (chosen == visibleIds) "取消全选" else "全选")
                    }
                    TextButton(enabled = chosen.isNotEmpty(), onClick = { deleting = chosen }) { Text("删除（${chosen.size}）") }
                }
            }
            if (visible.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (completed) Icons.Rounded.VideoLibrary else Icons.Rounded.Download, null, Modifier.size(32.dp), tint = colors.primary)
                    Text(if (completed) "还没有离线剧集" else "暂无下载任务", style = MaterialTheme.typography.titleMedium)
                    Text(if (completed) "下载完成后，可在这里离线播放。" else "在播放页点「下载选集」即可添加。", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            visible.groupBy { it.vod }.forEach { (vod, group) ->
                item(key = "title:$vod") {
                    Text(group.first().title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(group, key = { it.id }) { row ->
                    DownloadRow(row, allowed, mobile, manage, row.id in chosen,
                        toggle = { selected = if (row.id in selected) selected - row.id else selected + row.id },
                        play = { context.startActivity(Intent(context, OfflinePlayerActivity::class.java).putExtra("id", row.id)) },
                        change = { lib.pauseOrResume(row) }, remove = { deleting = setOf(row.id) })
                }
            }
        }
    }
    if (deleting.isNotEmpty()) AlertDialog(onDismissRequest = { deleting = emptySet() },
        icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.error) }, title = { Text("移除 ${deleting.size} 集？") },
        text = { Text("将取消所选任务，并删除这些剧集在本机保存的文件。") },
        confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = colors.error), onClick = {
            lib.remove(deleting); selected -= deleting; deleting = emptySet()
        }) { Text("确认移除") } }, dismissButton = { TextButton(onClick = { deleting = emptySet() }) { Text("保留") } })
}

@Composable
private fun DownloadRow(row: Entry, allowed: Boolean, mobile: Boolean, manage: Boolean, selected: Boolean,
    toggle: () -> Unit, play: () -> Unit, change: () -> Unit, remove: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    fun size(n: Long) = Formatter.formatShortFileSize(context, n)
    val done = row.stage == Stage.DONE
    val running = row.stage == Stage.ACTIVE && allowed
    val status = when {
        row.stage == Stage.FAILED -> row.error.ifBlank { row.stage.label }
        !allowed && row.stage in setOf(Stage.QUEUED, Stage.ACTIVE) -> if (mobile) "等待网络" else "等待 Wi-Fi"
        else -> row.stage.label
    }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().clickable(enabled = manage || done) { if (manage) toggle() else play() }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (manage) Checkbox(checked = selected, onCheckedChange = { toggle() })
                Column(Modifier.weight(1f)) {
                    Text(row.episode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(listOf(row.source, row.quality).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (running) Text("${size(row.bytesPerSecond)}/s", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (row.stage == Stage.FAILED) colors.error else colors.primary)
            if (!done && row.percent >= 0) LinearProgressIndicator(progress = { row.percent / 100f }, modifier = Modifier.fillMaxWidth())
            else if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            val total = if (row.totalBytes >= 0) size(row.totalBytes) else "总大小未知"
            Text(if (done) size(row.bytes) else listOfNotNull("${size(row.bytes)} / $total", "${row.percent}%".takeIf { row.percent >= 0 }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (!manage) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = remove) { Text(if (done) "删除" else "取消下载", color = colors.onSurfaceVariant) }
                FilledTonalButton(onClick = if (done) play else change) { Text(when (row.stage) {
                    Stage.DONE -> "离线播放"; Stage.PAUSED -> "继续"; Stage.FAILED -> "重试"; else -> "暂停"
                }) }
            }
        }
    }
}
