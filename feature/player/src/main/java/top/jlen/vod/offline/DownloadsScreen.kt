package top.jlen.vod.offline

import top.jlen.vod.ui.FullscreenPlayerActivity
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import top.jlen.vod.ui.UiPalette
import top.jlen.vod.ui.appTopInsetsPadding

internal val Stage.label get() = when (this) {
    Stage.QUEUED -> "排队中"; Stage.ACTIVE -> "下载中"; Stage.PAUSED -> "已暂停"; Stage.FAILED -> "下载失败"; Stage.DONE -> "已下载"
}

private fun waitingForNetwork(mobileAllowed: Boolean) = if (mobileAllowed) "等待网络" else "等待 Wi-Fi"

internal fun albumTransferStatus(pending: List<Entry>, allowed: Boolean, mobile: Boolean, formatSize: (Long) -> String): String? {
    val active = pending.filter { it.stage == Stage.ACTIVE }
    val queued = pending.count { it.stage == Stage.QUEUED }
    val failed = pending.count { it.stage == Stage.FAILED }
    val status = when {
        !allowed && (active.isNotEmpty() || queued > 0) -> waitingForNetwork(mobile)
        active.isNotEmpty() -> "下载中 · ${formatSize(active.sumOf { it.bytesPerSecond })}/s"
        queued > 0 -> "排队中"
        pending.any { it.stage == Stage.PAUSED } -> "已暂停"
        else -> null
    }
    if (failed == 0) return status
    return listOfNotNull(status, "$failed 项失败").joinToString(" · ")
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
    val disk = rememberDiskUsage(lib)
    val groups = rows.groupBy { it.vod }
    var toggled by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val done = rows.count { it.stage == Stage.DONE }
    val visible = rows.filter { (it.stage == Stage.DONE) == completed }
    val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
    val chosen = selected.intersect(visibleIds)
    val colors = MaterialTheme.colorScheme
    fun size(n: Long) = Formatter.formatShortFileSize(context, n)
    LaunchedEffect(lib) { lib.schedule() }
    Column(Modifier.fillMaxSize().background(UiPalette.BackgroundTop).appTopInsetsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text("下载管理", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(enabled = rows.isNotEmpty() || manage, onClick = { manage = !manage; selected = emptySet() }) {
                Text(if (manage) "完成" else "管理")
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "storage") {
                Surface(shape = RoundedCornerShape(22.dp), color = UiPalette.SurfaceStrong) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Rounded.VideoLibrary, null, Modifier.size(30.dp), tint = UiPalette.Accent)
                        Column(Modifier.weight(1f)) {
                            Text("离线文件占用", style = MaterialTheme.typography.labelLarge, color = UiPalette.TextSecondary)
                            Text(disk?.let { size(it.downloads) } ?: "统计中…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Text("${groups.size} 部影片", style = MaterialTheme.typography.labelLarge, color = UiPalette.TextSecondary)
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
            if (visible.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (completed) Icons.Rounded.VideoLibrary else Icons.Rounded.Download, null, Modifier.size(32.dp), tint = colors.primary)
                    Text(if (completed) "还没有离线剧集" else "暂无下载任务", style = MaterialTheme.typography.titleMedium)
                    Text(if (completed) "下载完成后，可在这里离线播放。" else "在影片详情页点「下载」，或在播放页点「下载选集」。", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            visible.groupBy { it.vod }.forEach { (vod, group) ->
                val first = group.first()
                val all = groups.getValue(vod)
                val key = "$completed:$vod"
                val expanded = manage || (if (completed) key in toggled else key !in toggled)
                item(key = "album:$vod") {
                    val folders = all.map { it.folder.ifEmpty { it.id } }.distinct()
                    val bytes = disk?.let { scan -> folders.sumOf { scan.folders[it] ?: 0L } }
                    val cover = all.firstOrNull { it.folder in disk?.covers.orEmpty() }?.let(lib::cover)
                    val finished = all.count { it.stage == Stage.DONE }
                    val unit = if (all.singleOrNull()?.single == true) "部" else "集"
                    val count = if (completed) "已下载 $finished $unit" else "已完成 $finished/${all.size} $unit"
                    val summary = "$count · ${bytes?.let(::size) ?: "统计中…"}"
                    val status = if (completed) null else albumTransferStatus(group, allowed, mobile, ::size)
                    DownloadAlbumCard(first.title, cover, summary, status, expanded, manage) {
                        toggled = ArrayList(if (key in toggled) toggled - key else toggled + key)
                    }
                }
                if (expanded) items(group, key = { it.id }) { row ->
                    DownloadRow(row, allowed, mobile, manage, row.id in chosen,
                        toggle = { selected = if (row.id in selected) selected - row.id else selected + row.id },
                        play = { context.startActivity(FullscreenPlayerActivity.forDownload(context, row.id)) },
                        change = { lib.pauseOrResume(row) }, remove = { deleting = setOf(row.id) })
                }
            }
        }
        if (manage) Surface(color = UiPalette.Surface, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = visibleIds.isNotEmpty(), onClick = {
                    selected = if (chosen == visibleIds) emptySet() else visibleIds
                }) { Text(if (chosen.isNotEmpty() && chosen == visibleIds) "取消全选" else "全选") }
                Text("已选 ${chosen.size} 项", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary)
                Button(enabled = chosen.isNotEmpty(), onClick = { deleting = chosen }, colors = ButtonDefaults.buttonColors(
                    containerColor = UiPalette.DangerSurface, contentColor = UiPalette.DangerText)) {
                    Text(if (completed) "删除文件" else "取消下载")
                }
            }
        }
    }
    val targets = visible.filter { it.id in deleting }
    LaunchedEffect(deleting, targets.isEmpty()) { if (targets.isEmpty() && deleting.isNotEmpty()) deleting = emptySet() }
    if (targets.isNotEmpty()) RemoveDownloadsDialog(targets, onDismiss = { deleting = emptySet() }, onConfirm = {
        lib.remove(deleting, completed); selected -= deleting; deleting = emptySet()
    })
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
        !allowed && row.stage in setOf(Stage.QUEUED, Stage.ACTIVE) -> waitingForNetwork(mobile)
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
