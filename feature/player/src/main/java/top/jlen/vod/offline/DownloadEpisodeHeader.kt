package top.jlen.vod.offline

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.jlen.vod.data.PlaySource
import top.jlen.vod.AppConfig
import top.jlen.vod.data.CmsSourceGroup
import top.jlen.vod.data.toSources
import top.jlen.vod.ui.UiPalette

@Composable
fun CmsDownloadHeader(vod: String, title: String, poster: String, groups: List<CmsSourceGroup>, current: Int,
                      picking: Boolean, onPickingChange: (Boolean) -> Unit) {
    val sources = remember(groups) { groups.toSources { raw ->
        runCatching { java.net.URI(AppConfig.appleCmsBaseUrl).resolve(raw).toString() }.getOrDefault(raw)
    }.filter { it.episodes.isNotEmpty() } }
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
    val flow = remember(lib, vod, si, source) {
        lib.entries.map { rows -> rows.filter { it.vod == vod && it.si == si && it.source == source }
            .associate { it.ei to it.stage.label } }.distinctUntilChanged()
    }
    return flow.collectAsState(emptyMap()).value
}

@Composable
fun DownloadEpisodeHeader(vod: String, title: String, poster: String, si: Int, source: PlaySource, current: Int, labels: Map<Int, String>,
                          picking: Boolean, onPickingChange: (Boolean) -> Unit, sourcePicker: @Composable () -> Unit = {}) {
    val context = LocalContext.current
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
    val available = remember(source, labels) { source.episodes.indices.filterNot(labels::containsKey).toSet() }
    var picked by rememberSaveable(vod, si, source.name) { mutableStateOf(setOf(current).intersect(available)) }
    val selection = picked.intersect(available)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = UiPalette.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, UiPalette.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourcePicker()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${source.name} · 共 ${source.episodes.size} 集", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(enabled = available.isNotEmpty(), onClick = { picked = if (selection == available) emptySet() else available }) {
                    Text(if (available.isNotEmpty() && selection == available) "取消全选" else "全选")
                }
            }
            Button(modifier = Modifier.fillMaxWidth(), enabled = selection.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Ink, contentColor = UiPalette.Surface), onClick = {
                OfflineLibrary.get(context).add(vod, title, poster, source.name, si, source.episodes.size == 1, selection.sorted().map { i ->
                    Triple(i, source.episodes[i].name, source.episodes[i].url)
                })
                onPickingChange(false)
                Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
            }) { Text(if (available.isEmpty()) "本线路剧集均已加入下载" else "加入下载（${selection.size} 集）") }
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
    }
}
