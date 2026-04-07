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
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem


@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlaybackSnapshotChange: (PlaybackSnapshot) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
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
    var transitionFeedback by remember(state.title) { mutableStateOf<String?>(null) }
    var previousEpisodeName by remember(state.title) { mutableStateOf(state.episodeName) }
    var previousSourceName by remember(state.title) { mutableStateOf(state.sourceName) }
    var previousSpeed by remember(state.title) { mutableStateOf(state.playbackSnapshot.speed) }
    var hasStartedFeedbackTracking by remember(state.title) { mutableStateOf(false) }

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

    LaunchedEffect(state.episodeName, state.sourceName, state.playbackSnapshot.speed) {
        if (!hasStartedFeedbackTracking) {
            previousEpisodeName = state.episodeName
            previousSourceName = state.sourceName
            previousSpeed = state.playbackSnapshot.speed
            hasStartedFeedbackTracking = true
            return@LaunchedEffect
        }

        val nextFeedback = when {
            state.sourceName.isNotBlank() && state.sourceName != previousSourceName -> "线路：${state.sourceName}"
            state.episodeName.isNotBlank() && state.episodeName != previousEpisodeName -> "已切换到 ${state.episodeName}"
            kotlin.math.abs(state.playbackSnapshot.speed - previousSpeed) > 0.01f ->
                String.format(java.util.Locale.US, "%.1fx", state.playbackSnapshot.speed)
            else -> null
        }

        previousEpisodeName = state.episodeName
        previousSourceName = state.sourceName
        previousSpeed = state.playbackSnapshot.speed

        if (nextFeedback != null) {
            transitionFeedback = nextFeedback
            delay(750)
            if (transitionFeedback == nextFeedback) {
                transitionFeedback = null
            }
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
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                    .background(UiPalette.BackgroundBottom)
            ) {
                Column {
                    DetailTopBar(
                        title = state.title.ifBlank { "播放器" },
                        onBack = onBack,
                        darkMode = isDarkTheme
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

        transitionFeedback?.let { feedback ->
            PlayerTransitionFeedbackChip(
                text = feedback,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isFullscreen) 28.dp else 18.dp)
            )
        }
    }
}

@Composable
private fun PlayerTransitionFeedbackChip(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, UiPalette.Border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            color = UiPalette.Ink,
            fontWeight = FontWeight.Bold
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
