package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.os.SystemClock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NativeVideoPlayer(
    url: String,
    title: String,
    sourceName: String,
    episodeName: String,
    hasNextEpisode: Boolean,
    onNextEpisode: (() -> Unit)?,
    onToggleFullscreen: ((PlaybackSnapshot, Boolean?) -> Unit)?,
    fullscreenMode: Boolean = false,
    onVideoOrientationDetected: ((Boolean) -> Unit)? = null,
    initialSnapshot: PlaybackSnapshot = PlaybackSnapshot(),
    onPlaybackSnapshotChanged: ((PlaybackSnapshot) -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val hostActivity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val playbackIdentity = remember(url, episodeName) { "$url|$episodeName" }
    val player = remember(playbackIdentity) {
        runCatching { createNativePlayer(context, url, initialSnapshot) }.getOrNull()
    }
    val latestOrientationCallback = rememberUpdatedState(onVideoOrientationDetected)
    val latestSnapshotCallback = rememberUpdatedState(onPlaybackSnapshotChanged)
    val latestFullscreenToggleCallback = rememberUpdatedState(onToggleFullscreen)
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var currentPosition by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var sliderPosition by remember(playbackIdentity) { mutableFloatStateOf(0f) }
    var isDragging by remember(playbackIdentity) { mutableStateOf(false) }
    var speed by remember(playbackIdentity) { mutableFloatStateOf(initialSnapshot.speed) }
    var shouldResumeOnStart by remember(playbackIdentity) { mutableStateOf(false) }
    var controlsVisible by remember(fullscreenMode, playbackIdentity) { mutableStateOf(true) }
    var controlsVersion by remember(fullscreenMode, playbackIdentity) { mutableLongStateOf(0L) }
    var hasHandledEnded by remember(playbackIdentity) { mutableStateOf(false) }
    var playbackState by remember(player) { mutableStateOf(player?.playbackState ?: Player.STATE_IDLE) }
    var lastReportedLandscape by remember(playbackIdentity) { mutableStateOf<Boolean?>(null) }
    var fullscreenResizeMode by remember(playbackIdentity) {
        mutableIntStateOf(
            if (fullscreenMode) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        )
    }
    var hasCompletedInitialOverlayDelay by remember(playbackIdentity) { mutableStateOf(false) }
    var hasStartedPlaybackOnce by remember(playbackIdentity) { mutableStateOf(false) }
    var showPausedOverlay by remember(playbackIdentity) { mutableStateOf(false) }
    var isUserPaused by remember(playbackIdentity) { mutableStateOf(false) }
    var lastReportedSnapshot by remember(playbackIdentity) { mutableStateOf<PlaybackSnapshot?>(null) }
    var lastSnapshotDispatchAt by remember(playbackIdentity) { mutableLongStateOf(0L) }
    var contentSize by remember(playbackIdentity, fullscreenMode) { mutableStateOf(IntSize.Zero) }
    var playerLocked by remember(playbackIdentity, fullscreenMode) { mutableStateOf(false) }
    var unlockHintVisible by remember(playbackIdentity, fullscreenMode) { mutableStateOf(false) }
    var unlockHintVersion by remember(playbackIdentity, fullscreenMode) { mutableLongStateOf(0L) }
    var gestureFeedback by remember(playbackIdentity, fullscreenMode) { mutableStateOf<PlayerGestureFeedback?>(null) }
    var gestureMode by remember(playbackIdentity, fullscreenMode) { mutableStateOf(PlayerGestureMode.None) }
    var gestureStartX by remember(playbackIdentity, fullscreenMode) { mutableFloatStateOf(0f) }
    var gestureStartY by remember(playbackIdentity, fullscreenMode) { mutableFloatStateOf(0f) }
    var gestureSeekPreviewMs by remember(playbackIdentity, fullscreenMode) { mutableLongStateOf(0L) }
    var gestureStartPositionMs by remember(playbackIdentity, fullscreenMode) { mutableLongStateOf(0L) }
    var gestureWasPlaying by remember(playbackIdentity, fullscreenMode) { mutableStateOf(false) }
    var gestureBrightnessStart by remember(playbackIdentity, fullscreenMode) { mutableFloatStateOf(0.5f) }
    var gestureVolumeStart by remember(playbackIdentity, fullscreenMode) { mutableFloatStateOf(0.5f) }
    var gestureMoved by remember(playbackIdentity, fullscreenMode) { mutableStateOf(false) }
    var longPressBoostActive by remember(playbackIdentity, fullscreenMode) { mutableStateOf(false) }
    var longPressJob by remember(playbackIdentity, fullscreenMode) { mutableStateOf<Job?>(null) }
    val longPressTimeoutMs = remember { ViewConfiguration.getLongPressTimeout().toLong() }
    val touchSlopPx = remember { 24f }

    fun currentSnapshot(): PlaybackSnapshot = PlaybackSnapshot(
        positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
        speed = player?.playbackParameters?.speed ?: speed,
        playWhenReady = player?.playWhenReady == true
    )

    fun dispatchSnapshot(force: Boolean = false): PlaybackSnapshot {
        val snapshot = currentSnapshot()
        val previous = lastReportedSnapshot
        val now = SystemClock.elapsedRealtime()
        val shouldDispatch = force || previous == null || (
            now - lastSnapshotDispatchAt >= UiMotion.SnapshotDispatchIntervalMillis &&
                (
                    kotlin.math.abs(snapshot.positionMs - previous.positionMs) >= UiMotion.SnapshotPositionThresholdMillis ||
                        kotlin.math.abs(snapshot.speed - previous.speed) > 0.01f ||
                        snapshot.playWhenReady != previous.playWhenReady
                    )
            )
        if (shouldDispatch) {
            lastReportedSnapshot = snapshot
            lastSnapshotDispatchAt = now
            latestSnapshotCallback.value?.invoke(snapshot)
        }
        return snapshot
    }

    fun markInteraction(forceControlsVisible: Boolean = fullscreenMode && !playerLocked) {
        if (forceControlsVisible) {
            controlsVisible = true
        }
        controlsVersion++
    }

    fun updateBrightness(value: Float) {
        val safeValue = value.coerceIn(0.05f, 1f)
        hostActivity?.setScreenBrightness(safeValue)
        gestureFeedback = PlayerGestureFeedback(
            title = "亮度",
            detail = "${(safeValue * 100).toInt()}%"
        )
    }

    fun updateVolume(value: Float) {
        val safeManager = audioManager ?: return
        val maxVolume = safeManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (value.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)
        safeManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        gestureFeedback = PlayerGestureFeedback(
            title = "音量",
            detail = "${((target.toFloat() / maxVolume) * 100).toInt()}%"
        )
    }

    fun showUnlockHint() {
        unlockHintVisible = true
        unlockHintVersion++
    }

    fun setPlayerLocked(locked: Boolean) {
        playerLocked = locked
        if (locked) {
            controlsVisible = false
            unlockHintVisible = false
            gestureFeedback = null
            gestureMode = PlayerGestureMode.None
            longPressJob?.cancel()
            longPressJob = null
            if (longPressBoostActive) {
                player?.playbackParameters = PlaybackParameters(speed)
                longPressBoostActive = false
            }
        } else {
            controlsVisible = true
        }
        controlsVersion++
    }

    fun finishGesture(cancelled: Boolean) {
        longPressJob?.cancel()
        longPressJob = null

        if (longPressBoostActive) {
            player?.playbackParameters = PlaybackParameters(speed)
            longPressBoostActive = false
        } else if (!cancelled && gestureMode == PlayerGestureMode.Seek) {
            val target = gestureSeekPreviewMs.coerceIn(0L, duration.coerceAtLeast(0L))
            player?.seekTo(target)
            currentPosition = target
            if (gestureWasPlaying && playbackState != Player.STATE_ENDED) {
                player?.play()
            }
            dispatchSnapshot(force = true)
        }

        if (playerLocked) {
            showUnlockHint()
        } else if (!gestureMoved && gestureMode == PlayerGestureMode.None) {
            controlsVisible = !controlsVisible
            controlsVersion++
        } else {
            controlsVersion++
        }

        gestureMode = PlayerGestureMode.None
        gestureMoved = false
        gestureFeedback = null
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    DisposableEffect(player, fullscreenMode, latestOrientationCallback.value, latestFullscreenToggleCallback.value) {
        if (
            player == null ||
            (!fullscreenMode && latestOrientationCallback.value == null && latestFullscreenToggleCallback.value == null)
        ) {
            onDispose { }
        } else {
            player.videoSize.isLandscapeVideo()?.let { orientation ->
                lastReportedLandscape = orientation
                latestOrientationCallback.value?.invoke(orientation)
            }
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val orientation = videoSize.isLandscapeVideo() ?: return
                    if (lastReportedLandscape != orientation) {
                        lastReportedLandscape = orientation
                        latestOrientationCallback.value?.invoke(orientation)
                    }
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    DisposableEffect(player, onPlaybackEnded) {
        if (player == null || onPlaybackEnded == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(newPlaybackState: Int) {
                    playbackState = newPlaybackState
                    if (newPlaybackState == Player.STATE_ENDED && !hasHandledEnded) {
                        hasHandledEnded = true
                        onPlaybackEnded()
                    } else if (newPlaybackState != Player.STATE_ENDED) {
                        hasHandledEnded = false
                    }
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        if (player == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        shouldResumeOnStart = player.isPlaying
                        player.pause()
                        isPlaying = false
                        showPausedOverlay = false
                        isUserPaused = false
                    }

                    Lifecycle.Event.ON_START -> {
                        if (shouldResumeOnStart) {
                            player.play()
                            isPlaying = true
                            showPausedOverlay = false
                            isUserPaused = false
                        }
                    }

                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(player, initialSnapshot.positionMs, initialSnapshot.speed, initialSnapshot.playWhenReady) {
        if (player == null) return@LaunchedEffect
        val reportedSnapshot = lastReportedSnapshot
        if (reportedSnapshot != null && initialSnapshot.matchesReportedSnapshot(reportedSnapshot)) {
            return@LaunchedEffect
        }
        player.playbackParameters = PlaybackParameters(initialSnapshot.speed)
        speed = initialSnapshot.speed
        if (kotlin.math.abs(player.currentPosition - initialSnapshot.positionMs) > 900L) {
            player.seekTo(initialSnapshot.positionMs)
            currentPosition = initialSnapshot.positionMs
        }
        player.playWhenReady = initialSnapshot.playWhenReady
        isPlaying = initialSnapshot.playWhenReady
        showPausedOverlay = false
        isUserPaused = false
        if (initialSnapshot.playWhenReady) {
            player.play()
        }
    }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            isPlaying = player.isPlaying
            playbackState = player.playbackState
            if (player.isPlaying || player.currentPosition > 1_000L) {
                hasStartedPlaybackOnce = true
            }
            if (player.isPlaying) {
                isUserPaused = false
                showPausedOverlay = false
            } else if (!isUserPaused && playbackState == Player.STATE_BUFFERING) {
                showPausedOverlay = false
            } else if (!isUserPaused && playbackState == Player.STATE_READY && !initialSnapshot.playWhenReady) {
                showPausedOverlay = false
            }
            duration = player.duration.coerceAtLeast(0L)
            if (!isDragging) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                sliderPosition = if (duration > 0) {
                    currentPosition.toFloat() / duration.toFloat()
                } else {
                    0f
                }
            }
            dispatchSnapshot(force = false)
            delay(UiMotion.PlayerUiRefreshMillis)
        }
    }

    LaunchedEffect(playbackIdentity, player, initialSnapshot.playWhenReady, fullscreenMode) {
        hasCompletedInitialOverlayDelay = false
        delay(if (fullscreenMode) 450L else 1_500L)
        if (
            player != null &&
            initialSnapshot.playWhenReady &&
            !hasStartedPlaybackOnce &&
            playbackState != Player.STATE_ENDED
        ) {
            repeat(if (fullscreenMode) 4 else 3) {
                player.play()
                delay(if (fullscreenMode) 350L else 500L)
                if (hasStartedPlaybackOnce || player.isPlaying) {
                    return@LaunchedEffect
                }
            }
        }
        hasCompletedInitialOverlayDelay = true
    }

    LaunchedEffect(fullscreenMode, controlsVisible, controlsVersion, isPlaying, playerLocked) {
        if (!fullscreenMode || playerLocked || !controlsVisible || !isPlaying) return@LaunchedEffect
        val version = controlsVersion
        delay(5_000L)
        if (version == controlsVersion) {
            controlsVisible = false
        }
    }

    LaunchedEffect(fullscreenMode, playerLocked, unlockHintVisible, unlockHintVersion) {
        if (!fullscreenMode || !playerLocked || !unlockHintVisible) return@LaunchedEffect
        val version = unlockHintVersion
        delay(2_500L)
        if (version == unlockHintVersion) {
            unlockHintVisible = false
        }
    }

    if (player == null) {
        Card(
            modifier = Modifier
                .then(
                    if (fullscreenMode) Modifier.fillMaxSize()
                    else Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ),
            shape = RoundedCornerShape(if (fullscreenMode) 0.dp else 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("閹绢厽鏂侀崳銊ュ灥婵瀵叉径杈Е", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("鏉╂瑦娼痪鑳熅閺嗗倹妞傞弮鐘崇《閸樼喓鏁撻幘顓熸杹", color = Color.White.copy(alpha = 0.78f))
                }
            }
        }
        return
    }

    val showLoadingOverlay =
        playbackState != Player.STATE_ENDED &&
            !showPausedOverlay &&
            !isUserPaused &&
            (
                playbackState == Player.STATE_BUFFERING ||
                    (player.playWhenReady && !hasStartedPlaybackOnce)
                )
    val embeddedPlayerHeight = when {
        lastReportedLandscape == false -> 420.dp
        else -> 228.dp
    }
    val embeddedResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    val controlPillTextStyle = if (fullscreenMode) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium
    val controlChipHeight = if (fullscreenMode) 36.dp else 32.dp
    val controlChipWidth = if (fullscreenMode) 76.dp else 64.dp
    val controlsShown = controlsVisible && !playerLocked

    Card(
        modifier = Modifier
            .then(
                if (fullscreenMode) Modifier.fillMaxSize()
                else Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp)
            ),
        shape = RoundedCornerShape(if (fullscreenMode) 0.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (fullscreenMode) Color.Black else UiPalette.BackgroundBottom
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fullscreenMode) Modifier.fillMaxSize()
                    else Modifier.heightIn(min = embeddedPlayerHeight).height(embeddedPlayerHeight)
                )
                .background(if (fullscreenMode) Color.Black else UiPalette.BackgroundBottom)
                .onSizeChanged { contentSize = it }
        ) {
            key(playbackIdentity) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            applyPlayerViewLayout(
                                resizeMode = if (fullscreenMode) {
                                    fullscreenResizeMode
                                } else {
                                    embeddedResizeMode
                                }
                            )
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        playerView.applyPlayerViewLayout(
                            resizeMode = if (fullscreenMode) {
                                fullscreenResizeMode
                            } else {
                                embeddedResizeMode
                            }
                        )
                        if (playerView.player !== player) {
                            playerView.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (fullscreenMode) 0.dp else 24.dp))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                gestureStartX = event.x
                                gestureStartY = event.y
                                gestureStartPositionMs = player.currentPosition.coerceAtLeast(0L)
                                gestureSeekPreviewMs = gestureStartPositionMs
                                gestureWasPlaying = player.isPlaying
                                gestureBrightnessStart = hostActivity?.currentScreenBrightness() ?: 0.5f
                                gestureVolumeStart = audioManager.currentVolumeFraction()
                                gestureMoved = false
                                gestureMode = PlayerGestureMode.None
                                gestureFeedback = null
                                longPressBoostActive = false
                                longPressJob?.cancel()
                                longPressJob = null

                                if (playerLocked) {
                                    showUnlockHint()
                                    return@pointerInteropFilter true
                                }

                                if (
                                    fullscreenMode &&
                                    contentSize.width > 0 &&
                                    event.x >= contentSize.width / 2f &&
                                    playbackState != Player.STATE_ENDED
                                ) {
                                    longPressJob = scope.launch {
                                        delay(longPressTimeoutMs)
                                        if (!gestureMoved && gestureMode == PlayerGestureMode.None && !playerLocked) {
                                            longPressBoostActive = true
                                            controlsVisible = false
                                            player.playbackParameters = PlaybackParameters(2f)
                                            gestureFeedback = PlayerGestureFeedback(
                                                title = "2.0x",
                                                detail = "长按加速"
                                            )
                                        }
                                    }
                                }
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (playerLocked) {
                                    return@pointerInteropFilter true
                                }

                                val deltaX = event.x - gestureStartX
                                val deltaY = event.y - gestureStartY
                                val absDeltaX = kotlin.math.abs(deltaX)
                                val absDeltaY = kotlin.math.abs(deltaY)
                                val travel = maxOf(absDeltaX, absDeltaY)

                                if (!gestureMoved && travel > touchSlopPx) {
                                    gestureMoved = true
                                    longPressJob?.cancel()
                                    longPressJob = null
                                }

                                if (longPressBoostActive) {
                                    return@pointerInteropFilter true
                                }

                                if (!gestureMoved || contentSize.width == 0 || contentSize.height == 0) {
                                    return@pointerInteropFilter true
                                }

                                if (gestureMode == PlayerGestureMode.None) {
                                    gestureMode = when {
                                        absDeltaX >= absDeltaY && duration > 0L -> PlayerGestureMode.Seek
                                        gestureStartX < contentSize.width / 2f -> PlayerGestureMode.Brightness
                                        else -> PlayerGestureMode.Volume
                                    }
                                    if (fullscreenMode) {
                                        controlsVisible = false
                                    }
                                }

                                when (gestureMode) {
                                    PlayerGestureMode.Seek -> {
                                        val width = contentSize.width.coerceAtLeast(1)
                                        val preview = (
                                            gestureStartPositionMs +
                                                (duration.toFloat() * (deltaX / width.toFloat()) * 0.9f).toLong()
                                            ).coerceIn(0L, duration.coerceAtLeast(0L))
                                        gestureSeekPreviewMs = preview
                                        gestureFeedback = PlayerGestureFeedback(
                                            title = formatMillis(preview),
                                            detail = formatSignedDuration(preview - gestureStartPositionMs)
                                        )
                                    }

                                    PlayerGestureMode.Brightness -> {
                                        val height = contentSize.height.coerceAtLeast(1)
                                        updateBrightness(gestureBrightnessStart - (deltaY / height.toFloat()))
                                    }

                                    PlayerGestureMode.Volume -> {
                                        val height = contentSize.height.coerceAtLeast(1)
                                        updateVolume(gestureVolumeStart - (deltaY / height.toFloat()))
                                    }

                                    PlayerGestureMode.None -> Unit
                                }
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                finishGesture(cancelled = false)
                                true
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                finishGesture(cancelled = true)
                                true
                            }

                            else -> false
                        }
                    }
            )

            if (showLoadingOverlay) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (fullscreenMode) 86.dp else 78.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.56f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (fullscreenMode) 34.dp else 30.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            if (playbackState != Player.STATE_ENDED && showPausedOverlay && !playerLocked) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (fullscreenMode) 92.dp else 84.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickableWithoutRipple {
                            player.play()
                            isPlaying = true
                            showPausedOverlay = false
                            isUserPaused = false
                            markInteraction()
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.62f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (fullscreenMode) 42.dp else 38.dp)
                        )
                    }
                }
            }

            if (controlsShown) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            }

            if (controlsShown) {
                Text(
                    text = "视频来源于第三方，切勿相信任何广告信息",
                    color = Color.White.copy(alpha = 0.88f),
                    style = if (fullscreenMode) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (fullscreenMode) 60.dp else 24.dp)
                        .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = if (fullscreenMode) 12.dp else 10.dp, vertical = if (fullscreenMode) 6.dp else 4.dp)
                )

                if (fullscreenMode) {
                    Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onClose != null) {
                            IconButton(
                                onClick = {
                                    dispatchSnapshot(force = true)
                                    onClose()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (fullscreenMode) {
                                Text(
                                text = title.ifBlank { "姝ｅ湪鎾斁" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                                )
                            }
                            Text(
                                text = buildString {
                                    if (sourceName.isNotBlank()) {
                                        append(sourceName)
                                    }
                                    if (episodeName.isNotBlank()) {
                                        if (isNotEmpty()) append(" 路 ")
                                        append(episodeName)
                                    }
                                    if (isNotEmpty()) append("\n")
                                    append("${formatMillis(currentPosition)} / ${formatMillis(duration)}")
                                },
                                color = Color.Transparent,
                                modifier = Modifier.height(0.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sourceName.isNotBlank()) {
                                    Text(
                                        text = sourceName,
                                        color = Color.White.copy(alpha = 0.92f),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                if (episodeName.isNotBlank()) {
                                    Text(
                                        text = episodeName,
                                        color = Color.White.copy(alpha = 0.92f),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            if (false) {
                                Text(
                                    text = "全屏",
                                    color = Color.White,
                                    style = controlPillTextStyle,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .clickableWithoutRipple {
                                            val snapshot = dispatchSnapshot(force = true)
                                            latestFullscreenToggleCallback.value?.invoke(snapshot, lastReportedLandscape)
                                            controlsVersion++
                                        }
                                        .width(controlChipWidth)
                                        .height(controlChipHeight)
                                )
                            }
                            if (false && !fullscreenMode && onToggleFullscreen != null) {
                                Text(
                                    text = "全屏",
                                    color = Color.White,
                                    style = controlPillTextStyle,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .clickableWithoutRipple {
                                            val snapshot = dispatchSnapshot(force = true)
                                            latestFullscreenToggleCallback.value?.invoke(snapshot, lastReportedLandscape)
                                            controlsVersion++
                                        }
                                        .width(controlChipWidth)
                                        .height(controlChipHeight)
                                )
                            }
                        }
                    }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            isDragging = true
                            sliderPosition = it
                            markInteraction()
                        },
                        onValueChangeFinished = {
                            val target = (duration * sliderPosition).toLong()
                            player.seekTo(target)
                            if (playbackState != Player.STATE_ENDED) {
                                player.play()
                                isPlaying = true
                            }
                            showPausedOverlay = false
                            isUserPaused = false
                            currentPosition = target
                            isDragging = false
                            dispatchSnapshot(force = true)
                            markInteraction()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) {
                                        player.pause()
                                        isUserPaused = true
                                        showPausedOverlay = true
                                    } else {
                                        player.play()
                                        isUserPaused = false
                                        showPausedOverlay = false
                                    }
                                    isPlaying = player.isPlaying
                                    markInteraction()
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "${formatMillis(currentPosition)}/${formatMillis(duration)}",
                                color = Color.White,
                                style = if (fullscreenMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (fullscreenMode) {
                                ControlChip(
                                    text = resizeModeLabel(fullscreenResizeMode),
                                    width = controlChipWidth,
                                    height = controlChipHeight,
                                    textStyle = controlPillTextStyle,
                                    onClick = {
                                        fullscreenResizeMode = nextResizeMode(fullscreenResizeMode)
                                        markInteraction()
                                    }
                                )
                            }
                            ControlChip(
                                text = speedLabel(speed),
                                width = controlChipWidth,
                                height = controlChipHeight,
                                textStyle = controlPillTextStyle,
                                onClick = {
                                    speed = nextSpeed(speed)
                                    player.playbackParameters = PlaybackParameters(speed)
                                    markInteraction()
                                }
                            )
                            if (hasNextEpisode && onNextEpisode != null) {
                                IconControlChip(
                                    icon = Icons.Rounded.SkipNext,
                                    contentDescription = "下一集",
                                    width = controlChipWidth,
                                    height = controlChipHeight,
                                    onClick = {
                                        markInteraction()
                                        onNextEpisode()
                                    }
                                )
                            }
                            if (false && hasNextEpisode && onNextEpisode != null) {
                                Text(
                                    text = "≫",
                                    color = Color.White,
                                    style = controlPillTextStyle,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .width(controlChipWidth)
                                        .height(controlChipHeight)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                        .clickableWithoutRipple {
                                            controlsVersion++
                                            onNextEpisode()
                                        }
                                )
                            }
                            if (!fullscreenMode && onToggleFullscreen != null) {
                                IconControlChip(
                                    icon = Icons.Rounded.Fullscreen,
                                    contentDescription = "全屏播放",
                                    width = controlChipWidth,
                                    height = controlChipHeight,
                                    onClick = {
                                        val snapshot = dispatchSnapshot(force = true)
                                        latestFullscreenToggleCallback.value?.invoke(snapshot, lastReportedLandscape)
                                        controlsVersion++
                                    }
                                )
                            }
                            if (false && !fullscreenMode && onToggleFullscreen != null) {
                                Text(
                                    text = "⛶",
                                    color = Color.White,
                                    style = controlPillTextStyle,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .width(controlChipWidth)
                                        .height(controlChipHeight)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                        .clickableWithoutRipple {
                                            val snapshot = dispatchSnapshot(force = true)
                                            latestFullscreenToggleCallback.value?.invoke(snapshot, lastReportedLandscape)
                                            controlsVersion++
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconControlChip(
    icon: ImageVector,
    contentDescription: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp)
            )
            .width(width)
            .height(height)
            .clickableWithoutRipple(onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ControlChip(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    textStyle: TextStyle,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp)
            )
            .width(width)
            .height(height)
            .clickableWithoutRipple(onClick)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = textStyle,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier =
    composed {
        val interactionSource = remember { MutableInteractionSource() }
        clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

private fun PlayerView.applyPlayerViewLayout(resizeMode: Int) {
    useController = false
    this.resizeMode = resizeMode
    setPadding(0, 0, 0, 0)
    clipToPadding = false
    clipChildren = false

    val contentFrame = findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
    val contentParams = (contentFrame.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    contentFrame.layoutParams = contentParams.apply {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.MATCH_PARENT
        gravity = Gravity.CENTER
    }

    val videoSurface = contentFrame.getChildAt(0)
    val surfaceParams = (videoSurface?.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    videoSurface?.layoutParams = surfaceParams.apply {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.MATCH_PARENT
        gravity = Gravity.CENTER
    }
}

private fun formatMillis(value: Long): String {
    if (value <= 0L) return "00:00"
    val totalSeconds = value / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun nextSpeed(current: Float): Float = when (current) {
    1f -> 1.25f
    1.25f -> 1.5f
    1.5f -> 2f
    else -> 1f
}

private fun nextResizeMode(current: Int): Int = when (current) {
    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private fun resizeModeLabel(mode: Int): String = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "适应"
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "裁剪"
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "拉伸"
    else -> "适应"
}

private fun speedLabel(speed: Float): String = when (speed) {
    1f -> "1.0x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2.0x"
    else -> "${speed}x"
}

private data class PlayerGestureFeedback(
    val title: String,
    val detail: String? = null
)

private enum class PlayerGestureMode {
    None,
    Seek,
    Brightness,
    Volume
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity.currentScreenBrightness(): Float {
    val lp = window.attributes
    val current = if (lp.screenBrightness in 0f..1f) {
        lp.screenBrightness
    } else {
        runCatching {
            val system = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            system / 255f
        }.getOrDefault(0.5f)
    }
    return current.coerceIn(0.05f, 1f)
}

private fun Activity.setScreenBrightness(value: Float) {
    val lp = window.attributes
    lp.screenBrightness = value.coerceIn(0.05f, 1f)
    window.attributes = lp
}

private fun AudioManager?.currentVolumeFraction(): Float {
    val manager = this ?: return 0.5f
    val maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return (current.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
}

private fun formatSignedDuration(deltaMs: Long): String {
    if (deltaMs == 0L) return "+0s"
    val sign = if (deltaMs > 0) "+" else "-"
    val value = kotlin.math.abs(deltaMs)
    val totalSeconds = value / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val base = if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
    return sign + base
}

private fun VideoSize.isLandscapeVideo(): Boolean? {
    if (width <= 0 || height <= 0) return null
    val rotated = unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270
    val displayWidth = if (rotated) height else width
    val displayHeight = if (rotated) width else height
    val ratio = displayWidth.toFloat() / displayHeight.toFloat()
    return when {
        ratio > 1.02f -> true
        ratio < 0.98f -> false
        else -> displayWidth >= displayHeight
    }
}

private fun PlaybackSnapshot.matchesReportedSnapshot(reported: PlaybackSnapshot): Boolean =
    kotlin.math.abs(positionMs - reported.positionMs) <= 900L &&
        kotlin.math.abs(speed - reported.speed) <= 0.01f &&
        playWhenReady == reported.playWhenReady
