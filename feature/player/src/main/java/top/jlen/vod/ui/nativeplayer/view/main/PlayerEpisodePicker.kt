package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f).fillMaxHeight().pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            })
            Surface(
                modifier = Modifier.fillMaxWidth(1f / 3f).fillMaxHeight(),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = Color(0xFF1B2028),
                contentColor = Color.White
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("选集", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onDismiss) {
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
                        LazyVerticalGrid(columns = GridCells.Adaptive(88.dp), state = rememberLazyGridState(startIndex),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(end - first) { offset ->
                                val index = first + offset
                                val playing = playingThisSource && index == playingEpisode
                                EpisodeOption(episodes[index].ifBlank { "第 ${index + 1} 集" }, playing) {
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
}

@Composable
private fun PickerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clip(RoundedCornerShape(10.dp))
        .background(if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent)
        .selectable(selected = selected, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun EpisodeOption(label: String, playing: Boolean, onClick: () -> Unit) {
    val foreground = if (playing) Color(0xFF122941) else Color.White
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (playing) Color(0xFFD5E8FF) else Color.White.copy(alpha = 0.08f))
            .selectable(selected = playing, onClick = onClick)
            .semantics { if (playing) stateDescription = "正在播放" }
            .heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playing) Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = foreground)
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
