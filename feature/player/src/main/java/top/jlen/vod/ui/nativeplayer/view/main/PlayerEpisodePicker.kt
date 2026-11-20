package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private const val EPISODES_PER_PAGE = 30

data class PickerSource(val name: String, val episodes: List<String>)

@Composable
fun PlayerEpisodePicker(
    sources: List<PickerSource>,
    playingSource: Int,
    playingEpisode: Int,
    onDismiss: () -> Unit,
    onPick: (Int, Int) -> Unit
) {
    if (sources.isEmpty()) return
    var selectedSource by rememberSaveable { mutableIntStateOf(playingSource) }
    val sourceIndex = selectedSource.coerceIn(sources.indices)
    val episodes = sources[sourceIndex].episodes
    val playingThisSource = sourceIndex == playingSource
    val initialEpisode = if (playingThisSource && playingEpisode in episodes.indices) playingEpisode else 0
    val lastPage = episodes.lastIndex.coerceAtLeast(0) / EPISODES_PER_PAGE
    var selectedPage by rememberSaveable(sourceIndex) { mutableIntStateOf(initialEpisode / EPISODES_PER_PAGE) }
    val page = selectedPage.coerceIn(0, lastPage)
    val first = page * EPISODES_PER_PAGE
    val end = minOf(first + EPISODES_PER_PAGE, episodes.size)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1B2028), contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选集", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭选集")
                    }
                }
                LazyRow(state = rememberLazyListState(sourceIndex), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sources.size) { index ->
                        PickerOption(sources[index].name.ifBlank { "线路 ${index + 1}" }, index == sourceIndex) {
                            selectedSource = index
                        }
                    }
                }
                if (lastPage > 0) key(sourceIndex) {
                    LazyRow(state = rememberLazyListState(page), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(lastPage + 1) { index ->
                            val start = index * EPISODES_PER_PAGE
                            PickerOption("${start + 1}–${minOf(start + EPISODES_PER_PAGE, episodes.size)}", index == page) {
                                selectedPage = index
                            }
                        }
                    }
                }
                if (episodes.isEmpty()) {
                    Text("该线路暂无剧集", modifier = Modifier.padding(vertical = 12.dp))
                } else key(sourceIndex, page) {
                    val startIndex = if (playingThisSource && playingEpisode in first until end) playingEpisode - first else 0
                    LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), state = rememberLazyGridState(startIndex),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(end - first) { offset ->
                            val index = first + offset
                            val playing = playingThisSource && index == playingEpisode
                            PickerOption(episodes[index].ifBlank { "第 ${index + 1} 集" }, playing) {
                                onDismiss()
                                if (!playing) onPick(sourceIndex, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clip(RoundedCornerShape(10.dp))
        .background(if (selected) Color(0xFFD5E8FF) else Color.White.copy(alpha = 0.08f))
        .selectable(selected = selected, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) Color(0xFF122941) else Color.White,
        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
