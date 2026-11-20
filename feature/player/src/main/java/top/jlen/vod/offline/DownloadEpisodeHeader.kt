package top.jlen.vod.offline

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun episodeDownloadLabels(vod: String, si: Int, source: String): Map<Int, String> {
    val lib = OfflineLibrary.get(LocalContext.current)
    val flow = remember(lib, vod, si, source) {
        lib.entries.map { rows -> rows.filter { it.vod == vod && it.si == si && it.source == source }
            .associate { it.ei to it.stage.label } }.distinctUntilChanged()
    }
    return flow.collectAsState(emptyMap()).value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadEpisodeHeader(vod: String, title: String, si: Int, source: PlaySource, current: Int, labels: Map<Int, String>) {
    val context = LocalContext.current
    var picking by remember(vod, si, source) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("切换选集", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { picking = true }) {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("下载选集")
        }
    }
    if (!picking) return
    val available = remember(source, labels) { source.episodes.indices.filterNot(labels::containsKey).toSet() }
    var picked by remember { mutableStateOf(setOf(current).intersect(available)) }
    val selection = picked.intersect(available)
    ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            Text("下载选集", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("$title · ${source.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${source.episodes.size} 集", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(enabled = available.isNotEmpty(), onClick = { picked = if (selection == available) emptySet() else available }) {
                    Text(if (available.isNotEmpty() && selection == available) "取消全选" else "全选可下载集数")
                }
            }
            LazyVerticalGrid(GridCells.Adaptive(90.dp), Modifier.weight(1f, fill = false).heightIn(max = 360.dp),
                state = rememberLazyGridState(initialFirstVisibleItemIndex = current.coerceIn(source.episodes.indices)),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(source.episodes.size) { i ->
                    val chosen = i in selection
                    val enabled = i in available
                    val colors = MaterialTheme.colorScheme
                    Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) colors.primary else colors.surfaceVariant,
                        contentColor = if (chosen) colors.onPrimary else if (enabled) colors.onSurface else colors.onSurfaceVariant) {
                        Column(Modifier.toggleable(chosen, enabled = enabled, role = Role.Checkbox) { checked ->
                            picked = if (checked) picked + i else picked - i
                        }.heightIn(min = 54.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                            Text(source.episodes[i].name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            (labels[i] ?: "当前播放".takeIf { i == current })?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(modifier = Modifier.fillMaxWidth(), enabled = selection.isNotEmpty(), onClick = {
                OfflineLibrary.get(context).add(vod, title, source.name, si, selection.sorted().map { i ->
                    Triple(i, source.episodes[i].name, source.episodes[i].url)
                })
                picking = false
                Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
            }) { Text(if (available.isEmpty()) "本线路剧集均已加入下载" else "加入下载（${selection.size} 集）") }
        }
    }
}
