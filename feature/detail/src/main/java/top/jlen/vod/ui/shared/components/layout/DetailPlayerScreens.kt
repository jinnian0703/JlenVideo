package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem

@Composable
private fun DetailPosterImage(
    data: String?,
    title: String,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    RetryablePosterImage(
        data = data,
        title = title,
        width = width,
        height = height,
        modifier = modifier,
        contentScale = contentScale
    )
}


@Composable
internal fun SourceRow(
    sourceNames: List<String>,
    selectedIndex: Int,
    onSelectSource: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = sourceNames,
            key = { index, name -> "$name-$index" },
            contentType = { _, _ -> "source" }
        ) { index, name ->
            val selected = index == selectedIndex
            AssistChip(
                onClick = { onSelectSource(index) },
                label = { Text(name) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) UiPalette.Ink else UiPalette.Surface,
                    labelColor = if (selected) UiPalette.Surface else UiPalette.Ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    borderColor = if (selected) UiPalette.Ink else UiPalette.BorderSoft,
                    enabled = true
                )
            )
        }
    }
}

@Composable
internal fun DetailHero(item: VodItem, onBack: () -> Unit, darkMode: Boolean = isSystemInDarkTheme()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(392.dp)
    ) {
        DetailPosterImage(
            data = item.vodPic,
            title = item.displayTitle,
            width = 1280,
            height = 720,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (darkMode) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.28f),
                            if (darkMode) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.55f),
                            UiPalette.BackgroundTop
                        )
                    )
                )
        )
        DetailTopBar(title = item.displayTitle, onBack = onBack)
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 30.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = if (darkMode) 0.94f else 1f)),
            border = BorderStroke(
                1.dp,
                if (darkMode) UiPalette.Border.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.7f)
            )
        ) {
            DetailPosterImage(
                data = item.vodPic,
                title = item.displayTitle,
                width = 516,
                height = 732,
                modifier = Modifier
                    .width(172.dp)
                    .height(244.dp)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
internal fun DetailInfoCard(item: VodItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            item.metaPairs.forEach { (label, value) ->
                Text(
                    text = "$label：$value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "评分：${item.cleanScore}",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyLarge,
                color = UiPalette.Ink
            )
        }
    }
}

@Composable
internal fun EpisodePanel(
    episodes: List<Episode>,
    selectedIndex: Int,
    pauseMarquee: Boolean,
    onEpisodeClick: (Int) -> Unit
) {
    val columns = 3
    val pageSize = 60
    val pageCount = if (episodes.isEmpty()) 0 else ((episodes.size - 1) / pageSize) + 1
    var currentPage by remember(episodes.size) {
        mutableStateOf(selectedIndex.takeIf { it >= 0 }?.div(pageSize) ?: 0)
    }

    LaunchedEffect(selectedIndex, pageCount) {
        if (pageCount == 0) {
            currentPage = 0
            return@LaunchedEffect
        }
        if (selectedIndex >= 0) {
            currentPage = (selectedIndex / pageSize).coerceIn(0, pageCount - 1)
        } else {
            currentPage = currentPage.coerceIn(0, pageCount - 1)
        }
    }

    val pageStart = currentPage * pageSize
    val visibleEpisodes = episodes.drop(pageStart).take(pageSize)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pageCount > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(List(pageCount) { it }) { _, pageIndex ->
                        val selected = pageIndex == currentPage
                        val start = pageIndex * pageSize + 1
                        val end = minOf((pageIndex + 1) * pageSize, episodes.size)
                        OutlinedButton(
                            onClick = { currentPage = pageIndex },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) UiPalette.Accent else UiPalette.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) UiPalette.AccentGlow else UiPalette.SurfaceSoft,
                                contentColor = if (selected) UiPalette.Accent else UiPalette.Ink
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "$start-$end",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            visibleEpisodes.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEachIndexed { columnIndex, episode ->
                        val absoluteIndex = pageStart + rowIndex * columns + columnIndex
                        val selected = absoluteIndex == selectedIndex
                        OutlinedButton(
                            onClick = { onEpisodeClick(absoluteIndex) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (selected) UiPalette.Accent else UiPalette.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) UiPalette.AccentGlow else UiPalette.SurfaceSoft,
                                contentColor = if (selected) UiPalette.Accent else UiPalette.Ink
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)
                        ) {
                            EpisodeChipLabel(
                                text = episode.name,
                                enableMarquee = !pauseMarquee &&
                                    absoluteIndex == selectedIndex &&
                                    shouldMarqueeEpisodeLabel(episode.name),
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeChipLabel(
    text: String,
    enableMarquee: Boolean,
    fontWeight: FontWeight
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = if (enableMarquee) {
                Modifier
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 800
                    )
                    .wrapContentWidth()
            } else {
                Modifier.wrapContentWidth()
            },
            maxLines = 1,
            overflow = if (enableMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = fontWeight
        )
    }
}

private fun shouldMarqueeEpisodeLabel(text: String): Boolean =
    text.trim().length > 8 && text.any { !it.isDigit() }

@Composable
internal fun ResolveLoadingSurface(
    message: String = "正在解析播放地址...",
    fullscreenMode: Boolean = false,
    subtitle: String = "请稍候"
) {
    if (fullscreenMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(248.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = UiPalette.Accent)
                Spacer(modifier = Modifier.height(14.dp))
                Text(message, color = UiPalette.TextSecondary)
            }
        }
    }
}

@Composable
internal fun ResolveUnavailableSurface(
    fullscreenMode: Boolean = false,
    title: String = "该线路暂不支持",
    message: String = "请换个线路试试"
) {
    if (fullscreenMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(248.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = UiPalette.Ink, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(message, color = UiPalette.TextSecondary)
            }
        }
    }
}

internal fun isDirectVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith(".m3u8") ||
        lower.endsWith(".mp4") ||
        lower.contains(".m3u8?") ||
        lower.contains("/index.m3u8")
}

private fun openExternal(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, darkMode: Boolean = false) {
    val background = if (darkMode) Color(0x66101419) else Color.White.copy(alpha = 0.82f)
    val contentColor = if (darkMode) Color.White else UiPalette.Ink
    val showTitle = title.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = if (darkMode) 4.dp else if (showTitle) 18.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(background)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
        if (showTitle) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
internal fun DetailActionNotice(
    message: String,
    isError: Boolean
) {
    val containerColor = if (isError) UiPalette.DangerSurface.copy(alpha = 0.42f) else UiPalette.AccentSoft.copy(alpha = 0.1f)
    val borderColor = if (isError) UiPalette.DangerBorder.copy(alpha = 0.5f) else UiPalette.BorderSoft.copy(alpha = 0.72f)
    val iconTint = if (isError) UiPalette.DangerText else UiPalette.Accent
    val textColor = if (isError) UiPalette.DangerText else UiPalette.Ink
    val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

