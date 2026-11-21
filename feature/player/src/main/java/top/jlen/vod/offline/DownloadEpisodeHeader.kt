package top.jlen.vod.offline

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.jlen.vod.data.PlaySource
import top.jlen.vod.AppConfig
import top.jlen.vod.data.CmsSourceGroup
import top.jlen.vod.data.toSources
import top.jlen.vod.ui.UiPalette

fun resolveDownloadSources(groups: List<CmsSourceGroup>, passwordRequired: Boolean, playback: List<PlaySource>): List<PlaySource> {
    if (groups.isEmpty()) return if (passwordRequired) emptyList() else playback
    return groups.toSources { raw ->
        runCatching { java.net.URI(AppConfig.appleCmsBaseUrl).resolve(raw).toString() }.getOrDefault(raw)
    }.filter { it.episodes.isNotEmpty() }
}

@Composable
fun CmsDownloadHeader(vod: String, title: String, poster: String, groups: List<CmsSourceGroup>, current: Int,
                      picking: Boolean, onPickingChange: (Boolean) -> Unit) {
    val sources = remember(groups) { resolveDownloadSources(groups, false, emptyList()) }
    if (sources.isEmpty()) {
        Text("切换选集", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        return
    }
    var selectedSource by rememberSaveable(vod, sources) { mutableIntStateOf(0) }
    val index = selectedSource.coerceIn(sources.indices)
    val source = sources[index]
    DownloadEpisodeHeader(vod, title, poster, index, source, current.coerceIn(source.episodes.indices),
        episodeDownloadLabels(vod, index, source.name), picking, onPickingChange) {
        if (sources.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sources.size) { i ->
                FilterChip(selected = i == index, onClick = { selectedSource = i }, label = { Text(sources[i].name) })
            }
        }
    }
}

@Composable
fun episodeDownloadLabels(vod: String, si: Int, source: String): Map<Int, String> {
    val lib = OfflineLibrary.get(LocalContext.current)
    return key(lib, vod, si, source) {
        fun labels(rows: List<Entry>) = rows.filter { it.vod == vod && it.si == si && it.source == source }
            .associate { it.ei to it.stage.label }
        val flow = remember { lib.entries.map(::labels).distinctUntilChanged() }
        flow.collectAsState(remember { labels(lib.entries.value) }).value
    }
}

@Composable
fun DownloadEpisodeHeader(vod: String, title: String, poster: String, si: Int, source: PlaySource, current: Int, labels: Map<Int, String>,
                          picking: Boolean, onPickingChange: (Boolean) -> Unit, sourcePicker: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (picking) "下载选集" else "切换选集", Modifier.weight(1f), color = UiPalette.Ink,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { onPickingChange(!picking) },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = UiPalette.Surface, contentColor = UiPalette.Ink)) {
            if (!picking) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp), tint = UiPalette.Accent)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (picking) "取消" else "下载选集")
        }
    }
    if (!picking) return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = UiPalette.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, UiPalette.Border)) {
        DownloadEpisodeSelection(vod, title, poster, si, source, current, labels, Modifier.padding(12.dp),
            inDialog = false, onAdded = { onPickingChange(false) }, sourcePicker = sourcePicker)
    }
}

@Composable
fun DownloadEpisodeDialog(vod: String, title: String, poster: String, sources: List<PlaySource>, initialSource: Int,
                          loading: Boolean, onDismiss: () -> Unit) {
    val indices = remember(sources) { sources.indices.filter { sources[it].episodes.isNotEmpty() } }
    var selected by rememberSaveable(vod) { mutableIntStateOf(initialSource) }
    val index = selected.takeIf { it in indices } ?: indices.firstOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = UiPalette.Surface) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("下载选集", style = MaterialTheme.typography.titleLarge, color = UiPalette.Ink, fontWeight = FontWeight.Bold)
                        Text(title, style = MaterialTheme.typography.bodyMedium, color = UiPalette.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭下载选集", tint = UiPalette.TextSecondary) }
                }
                if (index == null) {
                    Text(if (loading) "正在加载下载线路…" else "暂无可下载的剧集", color = UiPalette.TextSecondary)
                } else {
                    val source = sources[index]
                    DownloadEpisodeSelection(vod, title, poster, index, source, -1, episodeDownloadLabels(vod, index, source.name),
                        Modifier.weight(1f, fill = false), inDialog = true, onAdded = onDismiss) {
                        if (indices.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(indices.size) { position ->
                                val i = indices[position]
                                FilterChip(selected = i == index, onClick = { selected = i }, label = { Text(sources[i].name) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadEpisodeSelection(vod: String, title: String, poster: String, si: Int, source: PlaySource, current: Int,
                                     labels: Map<Int, String>, modifier: Modifier, inDialog: Boolean,
                                     onAdded: () -> Unit, sourcePicker: @Composable () -> Unit) {
    val context = LocalContext.current
    val available = remember(source, labels) { source.episodes.indices.filterNot(labels::containsKey).toSet() }
    var picked by rememberSaveable(vod, si, source.name) {
        val initial = if (inDialog && source.episodes.size == 1) 0 else current
        mutableStateOf(setOf(initial).intersect(available))
    }
    val selection = picked.intersect(available)
    val submit: @Composable () -> Unit = {
        Button(modifier = Modifier.fillMaxWidth(), enabled = selection.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Ink, contentColor = UiPalette.Surface), onClick = {
                OfflineLibrary.get(context).add(vod, title, poster, source.name, si, source.episodes.size == 1, selection.sorted().map { i ->
                    Triple(i, source.episodes[i].name, source.episodes[i].url)
                })
                onAdded()
                Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
            }) { Text(if (available.isEmpty()) "本线路剧集均已加入下载" else "加入下载（${selection.size} 集）") }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val choices = if (inDialog) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier
        Column(choices, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourcePicker()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${source.name} · 共 ${source.episodes.size} 集", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(enabled = available.isNotEmpty(), onClick = { picked = if (selection == available) emptySet() else available }) {
                    Text(if (available.isNotEmpty() && selection == available) "取消全选" else "全选")
                }
            }
            if (!inDialog) submit()
            val pageSize = 30
            val pages = (source.episodes.size + pageSize - 1) / pageSize
            var page by rememberSaveable(vod, si, source.name, pages) { mutableIntStateOf(current.coerceAtLeast(0) / pageSize) }
            if (pages > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pages) { p ->
                    FilterChip(selected = p == page, onClick = { page = p }, label = {
                        Text("${p * pageSize + 1}–${minOf((p + 1) * pageSize, source.episodes.size)}")
                    })
                }
            }
            val indices = page * pageSize until minOf((page + 1) * pageSize, source.episodes.size)
            BoxWithConstraints {
                val columns = (maxWidth / 88.dp).toInt().coerceIn(2, 6)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    indices.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { i ->
                                val chosen = i in selection
                                Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                    color = if (chosen) UiPalette.AccentGlow else UiPalette.SurfaceSoft) {
                                    Column(Modifier.toggleable(chosen, enabled = i in available, role = Role.Checkbox) { checked ->
                                        picked = if (checked) picked + i else picked - i
                                    }.heightIn(min = 48.dp).padding(8.dp), verticalArrangement = Arrangement.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (chosen) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = UiPalette.Accent)
                                            Text(source.episodes[i].name, color = UiPalette.Ink, style = MaterialTheme.typography.labelLarge,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        (labels[i] ?: "当前播放".takeIf { i == current })?.let {
                                            Text(it, style = MaterialTheme.typography.labelSmall, color = UiPalette.TextSecondary)
                                        }
                                    }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (inDialog) submit()
    }
}
