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
import top.jlen.vod.ui.SingleChoiceChip
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
                SingleChoiceChip(sources[i].name, i == index) { selectedSource = i }
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
    val context = LocalContext.current
    val available = source.episodes.indices.filterNot(labels::containsKey).toSet()
    var picked by rememberSaveable(vod, si, source.name) { mutableStateOf(setOf(current).intersect(available)) }
    val selection = picked.intersect(available)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = UiPalette.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, UiPalette.Border)) {
        DownloadEpisodeSelection(si, source, current, labels, selection, { picked = it }, Modifier.padding(12.dp),
            inDialog = false, sourcePicker = sourcePicker) {
            AddEpisodesButton(selection.size, available.isEmpty()) {
                OfflineLibrary.get(context).addEpisodes(vod, title, poster, si, source, selection)
                onPickingChange(false)
                Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
            }
        }
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
                    val context = LocalContext.current
                    val lib = OfflineLibrary.get(context)
                    fun labelsForFilm(rows: List<Entry>) = rows.filter { it.vod == vod && sources.getOrNull(it.si)?.name == it.source }
                        .groupBy { it.si }.mapValues { (_, entries) -> entries.associate { it.ei to it.stage.label } }
                    val labelFlow = remember(vod, sources) { lib.entries.map(::labelsForFilm).distinctUntilChanged() }
                    val labels by labelFlow.collectAsState(remember(vod, sources) { labelsForFilm(lib.entries.value) })
                    var picks by rememberSaveable(vod, sources) {
                        val initial = if (source.episodes.size == 1) mapOf(index to setOf(0)) else emptyMap()
                        mutableStateOf(initial)
                    }
                    val selectedEpisodes = picks.mapValues { (line, episodes) ->
                        episodes.filterTo(mutableSetOf()) { it in sources.getOrNull(line)?.episodes.orEmpty().indices && it !in labels[line].orEmpty() }
                    }
                    DownloadEpisodeSelection(index, source, -1, labels[index].orEmpty(), selectedEpisodes[index].orEmpty(),
                        { picks = picks + (index to it) }, Modifier.weight(1f, fill = false), inDialog = true, sourcePicker = {
                        if (indices.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(indices.size) { position ->
                                val i = indices[position]
                                SingleChoiceChip(sources[i].name, i == index) { selected = i }
                            }
                        }
                    }) {
                        AddEpisodesButton(selectedEpisodes.values.sumOf { it.size }, labels[index].orEmpty().size == source.episodes.size) {
                            selectedEpisodes.filterValues { it.isNotEmpty() }.forEach { (line, episodes) ->
                                lib.addEpisodes(vod, title, poster, line, sources[line], episodes)
                            }
                            onDismiss()
                            Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

private fun OfflineLibrary.addEpisodes(vod: String, title: String, poster: String, index: Int, source: PlaySource, selected: Set<Int>) {
    val episodes = selected.sorted().map { i -> Triple(i, source.episodes[i].name, source.episodes[i].url) }
    add(vod, title, poster, source.name, index, source.episodes.size == 1, episodes)
}

@Composable
private fun AddEpisodesButton(count: Int, lineAlreadyAdded: Boolean, onAdd: () -> Unit) {
    Button(modifier = Modifier.fillMaxWidth(), enabled = count > 0, onClick = onAdd,
        colors = ButtonDefaults.buttonColors(containerColor = UiPalette.Ink, contentColor = UiPalette.Surface)) {
        Text(if (count == 0 && lineAlreadyAdded) "本线路剧集均已加入下载" else "加入下载（$count 集）")
    }
}

@Composable
private fun DownloadEpisodeSelection(si: Int, source: PlaySource, current: Int, labels: Map<Int, String>,
                                     selection: Set<Int>, onSelection: (Set<Int>) -> Unit, modifier: Modifier, inDialog: Boolean,
                                     sourcePicker: @Composable () -> Unit, submit: @Composable () -> Unit) {
    val available = remember(source, labels) { source.episodes.indices.filterNot(labels::containsKey).toSet() }
    val pageSize = 30
    val pages = (source.episodes.size + pageSize - 1) / pageSize
    var page by rememberSaveable(si, source.name, pages) { mutableIntStateOf(current.coerceAtLeast(0) / pageSize) }
    val indices = page * pageSize until minOf((page + 1) * pageSize, source.episodes.size)
    val availableOnPage = indices.filterTo(mutableSetOf(), available::contains)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val choices = if (inDialog) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier
        Column(choices, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourcePicker()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${source.name} · 共 ${source.episodes.size} 集", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pages > 1) TextButton(enabled = availableOnPage.isNotEmpty(), onClick = {
                    onSelection(toggleDownloadScope(selection, availableOnPage))
                }) { Text(if (availableOnPage.isNotEmpty() && selection.containsAll(availableOnPage)) "取消本页" else "选本页") }
                TextButton(enabled = available.isNotEmpty(), onClick = { onSelection(toggleDownloadScope(selection, available)) }) {
                    Text(if (available.isNotEmpty() && selection == available) "取消整条线路" else "选整条线路")
                }
            }
            if (!inDialog) submit()
            if (pages > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pages) { p ->
                    SingleChoiceChip("${p * pageSize + 1}–${minOf((p + 1) * pageSize, source.episodes.size)}", p == page) { page = p }
                }
            }
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
                                        onSelection(if (checked) selection + i else selection - i)
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

internal fun toggleDownloadScope(selection: Set<Int>, scope: Set<Int>): Set<Int> =
    if (selection.containsAll(scope)) selection - scope else selection + scope
