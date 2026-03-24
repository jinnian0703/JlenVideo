package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem

@Composable
fun DetailScreen(
    state: DetailUiState,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onFavorite: () -> Unit,
    onPlay: (String, Int, Int) -> Unit
) {
    when {
        state.isLoading -> LoadingPane("正在加载详情...")
        !state.error.isNullOrBlank() -> ErrorBanner(message = state.error, onRetry = onBack)
        state.item == null -> EmptyPane("没有找到影片详情")
        else -> {
            val item = state.item
            val source = state.selectedSource
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { DetailHero(item = item, onBack = onBack) }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = UiPalette.Ink
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = item.subtitle.ifBlank { "站内资源" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = UiPalette.TextSecondary
                        )
                        if (item.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(item.tags) { _, tag ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(tag) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = UiPalette.SurfaceStrong,
                                            labelColor = UiPalette.Ink
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(
                                            borderColor = UiPalette.Border,
                                            enabled = true
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (source != null) {
                                        onPlay(item.displayTitle, state.selectedSourceIndex, 0)
                                    }
                                },
                                enabled = source != null,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = UiPalette.Accent,
                                    contentColor = UiPalette.AccentText
                                )
                            ) {
                                Text("立即播放", fontWeight = FontWeight.ExtraBold)
                            }
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Ink)
                            ) {
                                Text("返回")
                            }
                            OutlinedButton(
                                onClick = onFavorite,
                                enabled = isLoggedIn && !state.isActionLoading,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Accent)
                            ) {
                                Text(
                                    if (!isLoggedIn) "登录后收藏"
                                    else if (state.isActionLoading) "收藏中..."
                                    else "收藏"
                                )
                            }
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        state.actionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = UiPalette.Accent
                            )
                        }
                    }
                }
                item { DetailInfoCard(item = item) }
                if (state.sources.isNotEmpty()) {
                    item { SectionTitle(title = "播放线路", action = null, onAction = {}) }
                    item {
                        SourceRow(
                            sourceNames = state.sources.map { it.name },
                            selectedIndex = state.selectedSourceIndex,
                            onSelectSource = onSelectSource
                        )
                    }
                    source?.let { selected ->
                        item { SectionTitle(title = "选集播放", action = selected.name, onAction = {}) }
                        item {
                            EpisodePanel(
                                episodes = selected.episodes,
                                selectedIndex = -1,
                                onEpisodeClick = { episodeIndex ->
                                    onPlay(item.displayTitle, state.selectedSourceIndex, episodeIndex)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlaybackSnapshotChange: (PlaybackSnapshot) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playUrl = state.playUrl
    val directPlayable = playUrl.isNotBlank() && isDirectVideoUrl(playUrl)
    var detectedLandscapeVideo by remember(playUrl, state.episodeName, state.sourceName) {
        mutableStateOf<Boolean?>(null)
    }
    var isFullscreen by remember(playUrl, state.episodeName, state.sourceName) {
        mutableStateOf(false)
    }

    fun setFullscreen(fullscreen: Boolean, snapshot: PlaybackSnapshot? = null, isLandscapeVideo: Boolean? = null) {
        snapshot?.let(onPlaybackSnapshotChange)
        detectedLandscapeVideo = isLandscapeVideo ?: detectedLandscapeVideo
        isFullscreen = fullscreen
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    DisposableEffect(activity, isFullscreen, detectedLandscapeVideo) {
        if (activity == null) {
            onDispose { }
        } else {
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.requestedOrientation = if (detectedLandscapeVideo == false) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            onDispose {
                if (isFullscreen) {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.isAppearanceLightStatusBars = true
                    controller.isAppearanceLightNavigationBars = true
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    val playerContent = remember(
        playUrl,
        state.title,
        state.sourceName,
        state.episodeName,
        state.hasNextEpisode
    ) {
        movableContentOf<Boolean> { fullscreen ->
            NativeVideoPlayer(
                url = playUrl,
                title = state.title,
                sourceName = state.sourceName,
                episodeName = state.episodeName,
                hasNextEpisode = state.hasNextEpisode,
                onNextEpisode = onPlayNext,
                onToggleFullscreen = { snapshot, isLandscapeVideo ->
                    setFullscreen(!fullscreen, snapshot, isLandscapeVideo)
                },
                fullscreenMode = fullscreen,
                onVideoOrientationDetected = { detectedLandscapeVideo = it },
                initialSnapshot = state.playbackSnapshot,
                onPlaybackSnapshotChanged = onPlaybackSnapshotChange,
                onPlaybackEnded = {
                    if (state.hasNextEpisode) {
                        onPlayNext()
                    }
                },
                onClose = if (fullscreen) {
                    { isFullscreen = false }
                } else {
                    null
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UiPalette.PlayerBackground)
            ) {
                Column {
                    DetailTopBar(
                        title = state.title.ifBlank { "播放器" },
                        onBack = onBack,
                        darkMode = true
                    )
                    when {
                        state.isResolving -> ResolveLoadingSurface()
                        state.useWebPlayer -> ResolveUnavailableSurface()
                        directPlayable && !isFullscreen -> playerContent(false)
                        directPlayable -> Spacer(modifier = Modifier.height(0.dp))
                        else -> EmptyPane("暂无可播放地址")
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = UiPalette.Ink
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("正在播放")
                        if (state.sourceName.isNotBlank()) {
                            append(" · ")
                            append(state.sourceName)
                        }
                        if (state.episodeName.isNotBlank()) {
                            append(" · ")
                            append(state.episodeName)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = UiPalette.TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        state.isResolving -> "正在解析真实播放地址..."
                        state.useWebPlayer -> "当前线路正在后台接管，拿到视频流后会自动切到原生播放器。"
                        else -> "当前线路已直连视频源，加载和全屏会更稳定。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiPalette.TextMuted
                )
                if (!state.resolveError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.resolveError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UiPalette.DangerText
                    )
                }
                if (directPlayable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                setFullscreen(
                                    fullscreen = true,
                                    isLandscapeVideo = detectedLandscapeVideo
                                )
                            },
                            enabled = !state.isResolving,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, UiPalette.BorderSoft),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Ink)
                        ) {
                            Text("全屏播放")
                        }
                        OutlinedButton(
                            onClick = { openExternal(context, playUrl) },
                            enabled = !state.isResolving,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, UiPalette.BorderSoft),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Ink)
                        ) {
                            Text("外部打开")
                        }
                    }
                }
            }
        }

        if (state.sources.isNotEmpty()) {
            item { SectionTitle(title = "切换线路", action = state.sourceName, onAction = {}) }
            item {
                SourceRow(
                    sourceNames = state.sources.map { it.name },
                    selectedIndex = state.selectedSourceIndex,
                    onSelectSource = onSelectSource
                )
            }
        }

        if (state.episodes.isNotEmpty()) {
            item { SectionTitle(title = "切换选集", action = null, onAction = {}) }
            item {
                EpisodePanel(
                    episodes = state.episodes,
                    selectedIndex = state.selectedEpisodeIndex,
                    onEpisodeClick = onSelectEpisode
                )
            }
        }

        }

        if (directPlayable && isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                playerContent(true)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SourceRow(
    sourceNames: List<String>,
    selectedIndex: Int,
    onSelectSource: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(sourceNames) { index, name ->
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
private fun DetailHero(item: VodItem, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(392.dp)
    ) {
        AsyncImage(
            model = item.vodPic,
            contentDescription = item.displayTitle,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.55f),
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
            colors = CardDefaults.cardColors(containerColor = UiPalette.Surface),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
        ) {
            AsyncImage(
                model = item.vodPic,
                contentDescription = item.displayTitle,
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
private fun DetailInfoCard(item: VodItem) {
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
private fun EpisodePanel(
    episodes: List<Episode>,
    selectedIndex: Int,
    onEpisodeClick: (Int) -> Unit
) {
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
            episodes.chunked(4).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEachIndexed { columnIndex, episode ->
                        val absoluteIndex = rowIndex * 4 + columnIndex
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
                            Text(
                                text = episode.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolveLoadingSurface(message: String = "正在解析播放地址...") {
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
private fun ResolveUnavailableSurface() {
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
                Text("这条线路暂时没有接管成功", color = UiPalette.Ink, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Text("请先切换其他线路，我继续把这类线路补齐。", color = UiPalette.TextSecondary)
            }
        }
    }
}

private fun isDirectVideoUrl(url: String): Boolean {
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
    val background = if (darkMode) Color(0x66000000) else Color.White.copy(alpha = 0.82f)
    val contentColor = if (darkMode) Color.White else UiPalette.Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
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

@Composable
fun LoadingPane(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = UiPalette.Accent)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = UiPalette.TextSecondary)
        }
    }
}

@Composable
fun EmptyPane(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = UiPalette.TextMuted)
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.DangerSurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.DangerText
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, UiPalette.DangerBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.Ink)
            ) {
                Text("返回")
            }
        }
    }
}
