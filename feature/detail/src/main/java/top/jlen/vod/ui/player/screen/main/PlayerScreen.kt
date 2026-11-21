package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.jlen.vod.data.Episode
import top.jlen.vod.data.VodItem
import top.jlen.vod.data.isDirectMediaUrl
import top.jlen.vod.data.*

private const val EXPANDED_PLAYBACK_WINDOW_MIN_DP = 600

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    vodId: String,
    api: CmsAppApi,
    features: ViewingFeatures,
    user: String,
    onLogin: () -> Unit,
    state: PlayerUiState,
    onBack: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onPickEpisode: (Int, Int) -> Unit,
    onRefreshSources: () -> Unit,
    onUnlockDownload: suspend (String) -> Unit,
    checkDownloadSources: suspend (String) -> List<top.jlen.vod.data.PlaySource>,
    onPlayNext: () -> Unit,
    onPlaybackSnapshotChange: (PlaybackSnapshot) -> Unit,
    onDetectedStream: (String) -> Unit,
    onResolveFallbackFailed: (String) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val isExpandedPlaybackWindow = minOf(
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) >= EXPANDED_PLAYBACK_WINDOW_MIN_DP
    val downloadVod = state.item?.vodId.orEmpty().ifBlank { state.item?.siteVodId.orEmpty() }.ifBlank { vodId }
    val source = state.sources.getOrNull(state.selectedSourceIndex)
    val downloadLabels = top.jlen.vod.offline.episodeDownloadLabels(downloadVod, state.selectedSourceIndex, source?.name.orEmpty())
    val playUrl = state.playUrl
    val resolveError = state.resolveError
    val directPlayable = resolveError == null && isDirectMediaUrl(playUrl)
    val validVod = downloadVod.toLongOrNull()?.let { it > 0 } == true
    val thread = EpisodeThread(downloadVod, state.selectedSourceIndex, state.selectedEpisodeIndex)
    val hasDanmaku = features.danmaku && validVod && source?.episodes?.getOrNull(state.selectedEpisodeIndex) != null
    val hasChat = features.chat && validVod
    val currentDanmakuAvailable = rememberUpdatedState(hasDanmaku)
    val prefs = remember(context) { context.getSharedPreferences("player_interaction", Context.MODE_PRIVATE) }
    var danmakuOn by remember { mutableStateOf(prefs.getBoolean("danmaku", false)) }
    var reload by remember(thread) { mutableIntStateOf(0) }
    var messages by remember(thread) { mutableStateOf(emptyList<TimedComment>()) }
    var discussing by rememberSaveable(downloadVod) { mutableStateOf(false) }
    var inlineDanmakuFocused by remember(thread) { mutableStateOf(false) }
    val currentUser by rememberUpdatedState(user)
    val login by rememberUpdatedState(onLogin)
    suspend fun publishDanmaku(stamp: Long, text: String) {
        api.sendDanmaku(currentUser, thread, stamp, text)
        reload++
        android.widget.Toast.makeText(context, "弹幕已发送", android.widget.Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(thread, hasDanmaku, danmakuOn, reload) {
        if (hasDanmaku && danmakuOn) viewingRequest { api.danmaku(thread) }.onSuccess { messages = it }.onFailure {
            android.widget.Toast.makeText(context, "弹幕加载失败，可关闭后重试", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var isFullscreen by rememberSaveable {
        mutableStateOf(false)
    }
    var orientationBeforeFullscreen by rememberSaveable {
        mutableIntStateOf(Configuration.ORIENTATION_UNDEFINED)
    }
    var hasExitedFullscreen by rememberSaveable {
        mutableStateOf(false)
    }
    var fullscreenTransitionLabel by remember {
        mutableStateOf("")
    }
    var transitionFeedback by remember(state.title) { mutableStateOf<PlayerTransitionFeedback?>(null) }
    var previousEpisodeName by remember(state.title) { mutableStateOf(state.episodeName) }
    var previousSourceName by remember(state.title) { mutableStateOf(state.sourceName) }
    var hasStartedFeedbackTracking by remember(state.title) { mutableStateOf(false) }
    var latestSnapshot by remember(state.title, state.episodeName, state.sourceName) {
        mutableStateOf(state.playbackSnapshot)
    }

    fun handleSnapshotChange(snapshot: PlaybackSnapshot) {
        latestSnapshot = snapshot
        onPlaybackSnapshotChange(snapshot)
    }

    fun setFullscreen(fullscreen: Boolean, snapshot: PlaybackSnapshot? = null) {
        snapshot?.let(::handleSnapshotChange)
        if (fullscreen && !isFullscreen) {
            orientationBeforeFullscreen = configuration.orientation
            hasExitedFullscreen = false
        } else if (!fullscreen && isFullscreen) {
            hasExitedFullscreen = true
        }
        isFullscreen = fullscreen
    }

    fun closePlayerScreen() {
        handleSnapshotChange(latestSnapshot)
        onBack()
    }

    BackHandler(enabled = isFullscreen) {
        setFullscreen(false, latestSnapshot)
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
            hasStartedFeedbackTracking = true
            return@LaunchedEffect
        }

        val nextFeedback = when {
            state.sourceName.isNotBlank() && state.sourceName != previousSourceName -> PlayerTransitionFeedback(
                badge = "线",
                title = state.sourceName,
                detail = "已切换线路"
            )
            state.episodeName.isNotBlank() && state.episodeName != previousEpisodeName -> PlayerTransitionFeedback(
                badge = "集",
                title = state.episodeName,
                detail = "已切换选集"
            )
            else -> null
        }

        previousEpisodeName = state.episodeName
        previousSourceName = state.sourceName

        if (nextFeedback != null) {
            transitionFeedback = nextFeedback
            delay(650)
            if (transitionFeedback == nextFeedback) {
                transitionFeedback = null
            }
        }
    }

    val orientationAfterFullscreen = when {
        isExpandedPlaybackWindow -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        orientationBeforeFullscreen == Configuration.ORIENTATION_LANDSCAPE ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    DisposableEffect(
        activity,
        isFullscreen,
        isExpandedPlaybackWindow,
        hasExitedFullscreen,
        orientationAfterFullscreen
    ) {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attributes = window.attributes
                    attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attributes
                }
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.requestedOrientation = if (isExpandedPlaybackWindow) {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    val attributes = window.attributes
                    attributes.layoutInDisplayCutoutMode = originalCutoutMode
                    window.attributes = attributes
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
                if (hasExitedFullscreen) {
                    activity.requestedOrientation = orientationAfterFullscreen
                }
            }
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (isFullscreen) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                        val attributes = window.attributes
                        attributes.layoutInDisplayCutoutMode = originalCutoutMode
                        window.attributes = attributes
                    }
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                    activity.requestedOrientation = orientationAfterFullscreen
                }
            }
        }
    }

    var pickerOpen by rememberSaveable(vodId, isFullscreen) { mutableStateOf(false) }
    var pickingDownloads by rememberSaveable(downloadVod, state.selectedSourceIndex) { mutableStateOf(false) }
    val inlineKeyboardOpen = !isFullscreen && WindowInsets.isImeVisible
    val writingDiscussion = inlineKeyboardOpen && discussing && !inlineDanmakuFocused
    val showPortraitDanmaku by rememberUpdatedState(!writingDiscussion)
    BackHandler(enabled = pickingDownloads && !isFullscreen && !discussing) { pickingDownloads = false }
    val hasSources = state.sources.isNotEmpty()
    val playerContent = remember(
        playUrl,
        thread,
        state.title,
        state.sourceName,
        state.episodeName,
        state.hasNextEpisode
    ) {
        movableContentOf<Boolean, (() -> Unit)?> { fullscreen, openEpisodePicker ->
            var quickDraft by rememberSaveable { mutableStateOf("") }
            var sending by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val chatPlayerHeight = if (!fullscreen && discussing) LocalConfiguration.current.screenHeightDp.dp * .35f else null
            NativeVideoPlayer(
                url = playUrl,
                title = state.title,
                sourceName = state.sourceName,
                episodeName = state.episodeName,
                hasNextEpisode = state.hasNextEpisode,
                onNextEpisode = onPlayNext,
                onOpenEpisodePicker = openEpisodePicker,
                timedComments = messages,
                inlineHeightLimit = chatPlayerHeight,
                danmakuEnabled = danmakuOn && currentDanmakuAvailable.value,
                onToggleDanmaku = if (currentDanmakuAvailable.value && (fullscreen || showPortraitDanmaku)) ({
                    danmakuOn = !danmakuOn
                    prefs.edit().putBoolean("danmaku", danmakuOn).apply()
                }) else null,
                quickDanmakuField = { modifier, position, onFocus ->
                    var startedAt by remember(thread) { mutableStateOf<Long?>(null) }
                    QuickDanmakuField(modifier, fullscreen, quickDraft, sending, { quickDraft = it }, { focused ->
                        if (!fullscreen) inlineDanmakuFocused = focused
                        if (focused && quickDraft.isEmpty()) startedAt = position()
                        onFocus(focused)
                    },
                        login = if (currentUser.isBlank()) ({ login() }) else null,
                        onSend = { clearFocus ->
                            val text = quickDraft.trim()
                            if (!sending && text.isNotEmpty()) {
                                val stamp = if (fullscreen) position() else startedAt ?: position()
                                sending = true
                                scope.launch {
                                    try {
                                        viewingRequest { publishDanmaku(stamp, text) }
                                            .onSuccess {
                                                quickDraft = ""
                                                startedAt = null
                                                if (!fullscreen && !danmakuOn) {
                                                    danmakuOn = true
                                                    prefs.edit().putBoolean("danmaku", true).apply()
                                                }
                                                clearFocus()
                                            }
                                            .onFailure { android.widget.Toast.makeText(context, it.message ?: "发送失败，请重试", android.widget.Toast.LENGTH_SHORT).show() }
                                    } finally { sending = false }
                                }
                            }
                        })
                },
                onPlaybackError = { onResolveFallbackFailed("视频播放失败，请刷新或切换线路") },
                onToggleFullscreen = { snapshot, _ ->
                    setFullscreen(!fullscreen, snapshot)
                },
                fullscreenMode = fullscreen,
                initialSnapshot = state.playbackSnapshot,
                onPlaybackSnapshotChanged = ::handleSnapshotChange,
                onPlaybackEnded = {
                    if (state.hasNextEpisode) {
                        onPlayNext()
                    }
                },
                onClose = if (fullscreen) {
                    { setFullscreen(false, latestSnapshot) }
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
                .then(if (isFullscreen) Modifier else Modifier.appTopInsetsPadding().navigationBarsPadding().imePadding())
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UiPalette.BackgroundBottom)
            ) {
                // Keep the video at its viewing height. On short windows the header can scroll
                // to the active editor without measuring the video against the keyboard.
                val headerScroll = rememberScrollState()
                val editorSpace = if (writingDiscussion) 104.dp else 0.dp
                Column(Modifier.heightIn(max = (maxHeight - editorSpace).coerceAtLeast(0.dp)).verticalScroll(headerScroll)) {
                    DetailTopBar(
                        title = state.title.ifBlank { "播放器" },
                        onBack = ::closePlayerScreen,
                        darkMode = isDarkTheme
                    )
                    when {
                        state.isResolving && !isFullscreen -> ResolveLoadingSurface(
                            message = "正在获取播放地址..."
                        )
                        state.useWebPlayer && !isFullscreen -> ResolveLoadingSurface(
                            message = "正在尝试解析线路..."
                        )
                        resolveError != null && !isFullscreen -> ResolveUnavailableSurface(
                            diagnostic = state.diagnostic,
                            title = state.diagnostic?.title ?: "该线路暂不支持",
                            message = state.diagnostic?.message ?: resolveError,
                            onRefresh = onRefreshSources
                        )
                        directPlayable && !isFullscreen -> playerContent(false, null)
                        directPlayable -> Spacer(modifier = Modifier.height(0.dp))
                        else -> EmptyPane(
                            message = "暂无可播放地址",
                            description = resolveError ?: "可以尝试切换线路，或者稍后再回来看看"
                        )
                    }
                }
            }
            if (hasChat && !isFullscreen && !inlineKeyboardOpen) {
                PlaybackTabs(discussing, onSelect = { discussing = it })
            }
            if (hasChat && discussing && !isFullscreen) key(downloadVod) {
                FilmDiscussion(api, downloadVod, user, onLogin, Modifier.weight(1f), compact = inlineKeyboardOpen,
                    showComposer = !inlineKeyboardOpen || !inlineDanmakuFocused)
            } else LazyColumn(
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
                        SectionTitle(title = "切换线路", caption = "共 ${state.episodes.size} 集")
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
                        if (state.item?.downloadLocked == true) {
                            ContentUnlock("下载", filmTitle = state.title, inline = true, verify = onUnlockDownload)
                        } else if (!state.item?.downloadGroups.isNullOrEmpty()) {
                            top.jlen.vod.offline.CmsDownloadHeader(downloadVod, state.title, state.item?.vodPic.orEmpty(),
                                state.item?.downloadGroups.orEmpty(), state.selectedEpisodeIndex, pickingDownloads, { pickingDownloads = it }, checkDownloadSources)
                        } else if (source != null && downloadVod.isNotBlank()) {
                            top.jlen.vod.offline.DownloadEpisodeHeader(downloadVod, state.title, state.item?.vodPic.orEmpty(), state.selectedSourceIndex,
                                source, state.selectedEpisodeIndex, downloadLabels, pickingDownloads, { pickingDownloads = it }, checkDownloadSources)
                        } else SectionTitle(title = "切换选集", action = null, onAction = {})
                    }
                    if (!pickingDownloads) item(key = "episode_panel") {
                        EpisodePanel(
                            episodes = state.episodes,
                            downloadLabels = downloadLabels,
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
                playerContent(true, if (hasSources) ({ pickerOpen = true }) else null)
            }
        } else if (resolveError != null && isFullscreen) {
            ResolveUnavailableSurface(fullscreenMode = true, title = "播放失败", message = resolveError, onRefresh = onRefreshSources)
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
                ResolveLoadingSurface(
                    fullscreenMode = true,
                    message = "正在尝试解析线路...",
                    subtitle = "请稍候"
                )
            }
        }

        if (state.useWebPlayer && playUrl.isNotBlank()) {
            HiddenStreamResolver(
                pageUrl = playUrl,
                onDetected = onDetectedStream,
                onFailed = onResolveFallbackFailed
            )
        }

        if (isFullscreen && pickerOpen && hasSources) {
            PlayerEpisodePicker(
                sources = remember(state.sources) { state.sources.map { PickerSource(it.name, it.episodes.map(Episode::name)) } },
                playingSource = state.selectedSourceIndex,
                playingEpisode = state.selectedEpisodeIndex,
                onDismiss = { pickerOpen = false },
                onPick = onPickEpisode
            )
        }

        transitionFeedback?.let { feedback ->
            PlayerTransitionFeedbackChip(
                feedback = feedback,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isFullscreen) 30.dp else 18.dp)
            )
        }
    }
}

@Composable
private fun PlayerTransitionFeedbackChip(feedback: PlayerTransitionFeedback, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = UiPalette.Surface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(UiDimens.CardRadius),
        border = BorderStroke(1.dp, UiPalette.BorderSoft.copy(alpha = 0.82f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UiPalette.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feedback.badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.Accent,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = feedback.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = UiPalette.Ink,
                    fontWeight = FontWeight.Bold
                )
                feedback.detail?.takeIf(String::isNotBlank)?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiPalette.TextSecondary
                    )
                }
            }
        }
    }
}

private data class PlayerTransitionFeedback(
    val badge: String,
    val title: String,
    val detail: String? = null
)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
