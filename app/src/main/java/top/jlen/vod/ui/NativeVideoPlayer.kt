package top.jlen.vod.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
    val playbackIdentity = remember(url, episodeName) { "$url|$episodeName" }
    val player = remember(playbackIdentity) {
        runCatching { createNativePlayer(context, url, initialSnapshot) }.getOrNull()
    }
    val latestOrientationCallback = rememberUpdatedState(onVideoOrientationDetected)
    val latestSnapshotCallback = rememberUpdatedState(onPlaybackSnapshotChanged)
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
    var hasCompletedInitialOverlayDelay by remember(playbackIdentity) { mutableStateOf(false) }
    var hasStartedPlaybackOnce by remember(playbackIdentity) { mutableStateOf(false) }
    var showPausedOverlay by remember(playbackIdentity) { mutableStateOf(false) }
    var isUserPaused by remember(playbackIdentity) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    DisposableEffect(player, fullscreenMode) {
        if (player == null || !fullscreenMode || latestOrientationCallback.value == null) {
            onDispose { }
        } else {
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
            latestSnapshotCallback.value?.invoke(
                PlaybackSnapshot(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    speed = player.playbackParameters.speed,
                    playWhenReady = player.isPlaying
                )
            )
            delay(300)
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

    LaunchedEffect(fullscreenMode, controlsVisible, controlsVersion, isPlaying) {
        if (!fullscreenMode || !controlsVisible || !isPlaying) return@LaunchedEffect
        val version = controlsVersion
        delay(2500)
        if (version == controlsVersion) {
            controlsVisible = false
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
                    Text("播放器初始化失败", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("这条线路暂时无法原生播放", color = Color.White.copy(alpha = 0.78f))
                }
            }
        }
        return
    }

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
                .fillMaxWidth()
                .then(
                    if (fullscreenMode) Modifier.fillMaxSize()
                    else Modifier.heightIn(min = 248.dp).height(248.dp)
                )
                .background(Color.Black)
                .clickableWithoutRipple {
                    if (fullscreenMode) {
                        controlsVisible = !controlsVisible
                        controlsVersion++
                    }
                }
        ) {
            key(playbackIdentity) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        playerView.useController = false
                        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        if (playerView.player !== player) {
                            playerView.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (fullscreenMode) 0.dp else 24.dp))
                )
            }

            if (playbackState != Player.STATE_ENDED && showPausedOverlay) {
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
                            controlsVersion++
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

            if (!fullscreenMode || controlsVisible) {
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

            if (!fullscreenMode || controlsVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onClose != null) {
                            IconButton(
                                onClick = {
                                    latestSnapshotCallback.value?.invoke(
                                        PlaybackSnapshot(
                                            positionMs = player.currentPosition.coerceAtLeast(0L),
                                            speed = player.playbackParameters.speed,
                                            playWhenReady = player.isPlaying
                                        )
                                    )
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
                        Column {
                            Text(
                                text = buildString {
                                    append(sourceName.ifBlank { title })
                                    if (episodeName.isNotBlank()) {
                                        append(" · ")
                                        append(episodeName)
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${formatMillis(currentPosition)} / ${formatMillis(duration)}",
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            isDragging = true
                            sliderPosition = it
                            controlsVersion++
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
                            controlsVersion++
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    controlsVersion++
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "${formatMillis(currentPosition)} / ${formatMillis(duration)}",
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = speedLabel(speed),
                                color = Color.White,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    speed = nextSpeed(speed)
                                    player.playbackParameters = PlaybackParameters(speed)
                                    controlsVersion++
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FastForward,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            if (hasNextEpisode && onNextEpisode != null) {
                                Text(
                                    text = "下一集",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickableWithoutRipple {
                                            controlsVersion++
                                            onNextEpisode()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                            if (onToggleFullscreen != null) {
                                IconButton(
                                    onClick = {
                                        val isLandscapeVideo = player.videoSize.isLandscapeVideo() ?: lastReportedLandscape
                                        val shouldAutoPlay = player.isPlaying
                                        shouldResumeOnStart = false
                                        player.pause()
                                        isPlaying = false
                                        showPausedOverlay = false
                                        isUserPaused = false
                                        controlsVersion++
                                        onToggleFullscreen(
                                            PlaybackSnapshot(
                                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                                speed = player.playbackParameters.speed,
                                                playWhenReady = shouldAutoPlay
                                            ),
                                            isLandscapeVideo
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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

private fun speedLabel(speed: Float): String = when (speed) {
    1f -> "1.0x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2.0x"
    else -> "${speed}x"
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
