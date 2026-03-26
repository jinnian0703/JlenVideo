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
import androidx.compose.foundation.BorderStroke
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
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem

@Composable
fun DetailScreen(
    state: DetailUiState,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onFavorite: () -> Unit,
    onDismissActionMessage: () -> Unit,
    onPlay: (String, Int, Int) -> Unit
) {
    when {
        state.isLoading -> LoadingPane("正在加载详情...")
        !state.error.isNullOrBlank() -> ErrorBanner(message = state.error, onRetry = onBack, actionLabel = "返回")
        state.item == null -> EmptyPane("没有找到影片详情")
        else -> {
            val item = state.item
            val source = state.selectedSource
            val detailListState = rememberLazyListState()
            LaunchedEffect(state.actionMessage, state.isActionError) {
                if (!state.actionMessage.isNullOrBlank() && !state.isActionError) {
                    delay(2200)
                    onDismissActionMessage()
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = detailListState,
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
                                enabled = !state.isActionLoading,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isFavorited) UiPalette.Accent.copy(alpha = 0.28f) else UiPalette.BorderSoft
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (state.isFavorited) UiPalette.AccentSoft.copy(alpha = 0.16f) else Color.Transparent,
                                    contentColor = UiPalette.Accent
                                )
                            ) {
                                Text(
                                    if (!isLoggedIn) "登录后收藏"
                                    else if (state.isActionLoading) "收藏中..."
                                    else if (state.isFavorited) "已收藏"
                                    else "收藏"
                                )
                            }
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        state.actionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            DetailActionNotice(
                                message = message,
                                isError = state.isActionError
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
                                pauseMarquee = detailListState.isScrollInProgress,
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
    onOpenDetail: (() -> Unit)?,
    onSelectEpisode: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlaybackSnapshotChange: (PlaybackSnapshot) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playUrl = state.playUrl
    val directPlayable = playUrl.isNotBlank() && isDirectVideoUrl(playUrl)
    var detectedLandscapeVideo by remember {
        mutableStateOf<Boolean?>(null)
    }
    var isFullscreen by remember {
        mutableStateOf(false)
    }
    var fullscreenTransitionLabel by remember {
        mutableStateOf("")
    }

    fun setFullscreen(fullscreen: Boolean, snapshot: PlaybackSnapshot? = null, isLandscapeVideo: Boolean? = null) {
        snapshot?.let(onPlaybackSnapshotChange)
        detectedLandscapeVideo = isLandscapeVideo ?: detectedLandscapeVideo
        isFullscreen = fullscreen
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    LaunchedEffect(isFullscreen, state.selectedEpisodeIndex, state.episodeName, state.sourceName) {
        if (isFullscreen) {
            fullscreenTransitionLabel = when {
                state.episodeName.isNotBlank() && state.sourceName.isNotBlank() -> "${state.sourceName} · ${state.episodeName}"
                state.episodeName.isNotBlank() -> state.episodeName
                state.sourceName.isNotBlank() -> state.sourceName
                else -> state.title
            }
        } else {
            fullscreenTransitionLabel = ""
        }
    }

    DisposableEffect(activity, isFullscreen) {
        if (activity == null) {
            onDispose { }
        } else {
            val window = activity.window
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode
            } else {
                null
            }
            if (isFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attributes = window.attributes
                    attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attributes
                }
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    val attributes = window.attributes
                    attributes.layoutInDisplayCutoutMode = originalCutoutMode
                    window.attributes = attributes
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            onDispose {
                if (isFullscreen) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                        val attributes = window.attributes
                        attributes.layoutInDisplayCutoutMode = originalCutoutMode
                        window.attributes = attributes
                    }
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
        val listState = rememberLazyListState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
        ) {
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
                        state.isResolving && !isFullscreen -> ResolveLoadingSurface(
                            message = "正在获取播放地址..."
                        )
                        state.useWebPlayer && !isFullscreen -> ResolveUnavailableSurface()
                        directPlayable && !isFullscreen -> playerContent(false)
                        directPlayable -> Spacer(modifier = Modifier.height(0.dp))
                        else -> EmptyPane("暂无可播放地址")
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                state = listState,
                contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "player_meta") {
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
                        if (onOpenDetail != null && state.item?.vodId?.isNotBlank() == true) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = onOpenDetail,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, UiPalette.BorderSoft),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = UiPalette.Ink
                                )
                            ) {
                                Text("查看详情", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (state.sources.isNotEmpty()) {
                    item(key = "source_title") {
                        SectionTitle(title = "切换线路", action = state.sourceName, onAction = {})
                    }
                    item(key = "source_row") {
                        SourceRow(
                            sourceNames = state.sources.map { it.name },
                            selectedIndex = state.selectedSourceIndex,
                            onSelectSource = onSelectSource
                        )
                    }
                }

                if (state.episodes.isNotEmpty()) {
                    item(key = "episode_title") {
                        SectionTitle(title = "切换选集", action = null, onAction = {})
                    }
                    item(key = "episode_panel") {
                        EpisodePanel(
                            episodes = state.episodes,
                            selectedIndex = state.selectedEpisodeIndex,
                            pauseMarquee = false,
                            onEpisodeClick = onSelectEpisode
                        )
                    }
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
        } else if (state.isResolving && isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                ResolveLoadingSurface(
                    message = "正在切换到 $fullscreenTransitionLabel",
                    fullscreenMode = true,
                    subtitle = "请稍候"
                )
            }
        } else if (state.useWebPlayer && isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                ResolveUnavailableSurface(
                    fullscreenMode = true,
                    title = "该线路暂不支持",
                    message = "请换个线路试试"
                )
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
private fun DetailHero(item: VodItem, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(392.dp)
    ) {
        AsyncImage(
            model = rememberPosterRequest(
                data = item.vodPic,
                width = 1280,
                height = 720
            ),
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
                model = rememberPosterRequest(
                    data = item.vodPic,
                    width = 516,
                    height = 732
                ),
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
private fun ResolveLoadingSurface(
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
private fun ResolveUnavailableSurface(
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
    val showTitle = title.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (darkMode) 4.dp else if (showTitle) 18.dp else 10.dp),
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
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    actionLabel: String = "重试"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = UiPalette.DangerSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, UiPalette.DangerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = UiPalette.DangerText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.DangerText
            )
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, UiPalette.DangerBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = UiPalette.DangerText)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailActionNotice(
    message: String,
    isError: Boolean
) {
    val containerColor = if (isError) UiPalette.DangerSurface else UiPalette.AccentSoft.copy(alpha = 0.18f)
    val borderColor = if (isError) UiPalette.DangerBorder else UiPalette.BorderSoft
    val iconTint = if (isError) UiPalette.DangerText else UiPalette.Accent
    val textColor = if (isError) UiPalette.DangerText else UiPalette.Ink
    val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isError) 0.42f else 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}
