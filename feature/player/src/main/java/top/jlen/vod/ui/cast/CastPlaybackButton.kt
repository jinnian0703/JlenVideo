package top.jlen.vod.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@Composable
internal fun CastPlaybackButton(
    url: String,
    title: String,
    subtitle: String,
    positionMs: Long,
    playWhenReady: Boolean,
    onConnectionChanged: (Boolean) -> Unit,
    onCastPlaybackStarted: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val castContext = remember(context) {
        val playServicesAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context.applicationContext) == ConnectionResult.SUCCESS
        if (playServicesAvailable) {
            runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
        } else {
            null
        }
    }
    var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }
    var castSession by remember(castContext) {
        mutableStateOf(
            runCatching { castContext?.sessionManager?.currentCastSession }.getOrNull()
        )
    }
    val latestPosition by rememberUpdatedState(positionMs.coerceAtLeast(0L))
    val latestPlayWhenReady by rememberUpdatedState(playWhenReady)
    val latestConnectionCallback by rememberUpdatedState(onConnectionChanged)
    val latestPlaybackStartedCallback by rememberUpdatedState(onCastPlaybackStarted)

    DisposableEffect(castContext) {
        val sessionManager = runCatching { castContext?.sessionManager }.getOrNull()
        if (sessionManager == null) {
            castSession = null
            latestConnectionCallback(false)
            onDispose { }
        } else {
            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarting(session: CastSession) = Unit

                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    castSession = session
                    latestConnectionCallback(true)
                }

                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    castSession = null
                    latestConnectionCallback(false)
                }

                override fun onSessionEnding(session: CastSession) = Unit

                override fun onSessionEnded(session: CastSession, error: Int) {
                    castSession = null
                    latestConnectionCallback(false)
                }

                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    castSession = session
                    latestConnectionCallback(true)
                }

                override fun onSessionResumeFailed(session: CastSession, error: Int) {
                    castSession = null
                    latestConnectionCallback(false)
                }

                override fun onSessionSuspended(session: CastSession, reason: Int) {
                    castSession = session
                    latestConnectionCallback(true)
                }
            }
            val listenerRegistered = runCatching {
                sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            }.isSuccess
            if (listenerRegistered) {
                val currentSession = runCatching { sessionManager.currentCastSession }.getOrNull()
                castSession = currentSession
                latestConnectionCallback(currentSession?.isConnected == true)
            } else {
                castSession = null
                latestConnectionCallback(false)
            }
            onDispose {
                if (listenerRegistered) {
                    runCatching {
                        sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
                    }
                }
            }
        }
    }

    LaunchedEffect(castSession, url, title, subtitle) {
        val session = castSession?.takeIf { it.isConnected } ?: return@LaunchedEffect
        if (url.isBlank()) return@LaunchedEffect
        loadCastMedia(
            session = session,
            url = url,
            title = title,
            subtitle = subtitle,
            positionMs = latestPosition,
            playWhenReady = latestPlayWhenReady,
            onSuccess = latestPlaybackStartedCallback
        )
    }

    Box(modifier = modifier) {
        if (castContext != null) {
            AndroidView(
                factory = { viewContext ->
                    MediaRouteButton(viewContext).also { button ->
                        routeButton = if (
                            runCatching {
                                CastButtonFactory.setUpMediaRouteButton(viewContext, button)
                            }.isSuccess
                        ) {
                            button
                        } else {
                            null
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f),
                update = { button ->
                    if (routeButton != null) {
                        routeButton = button
                    }
                }
            )
        }
        IconButton(
            onClick = {
                onInteraction()
                val button = routeButton
                if (castContext == null || button == null) {
                    Toast.makeText(context, "当前设备不支持投屏", Toast.LENGTH_SHORT).show()
                } else {
                    val opened = runCatching { button.performClick() }.getOrDefault(false)
                    if (!opened) {
                        Toast.makeText(context, "无法打开投屏设备列表", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (castSession?.isConnected == true) {
                    Icons.Rounded.CastConnected
                } else {
                    Icons.Rounded.Cast
                },
                contentDescription = "投屏",
                tint = Color.White
            )
        }
    }
}

private fun loadCastMedia(
    session: CastSession,
    url: String,
    title: String,
    subtitle: String,
    positionMs: Long,
    playWhenReady: Boolean,
    onSuccess: () -> Unit
) {
    runCatching {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title.ifBlank { "JlenVideo" })
            subtitle.takeIf(String::isNotBlank)?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(castContentType(url))
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(playWhenReady)
            .setCurrentTime(positionMs.coerceAtLeast(0L))
            .build()

        session.remoteMediaClient
            ?.load(request)
            ?.setResultCallback { result ->
                if (result.status.isSuccess) {
                    onSuccess()
                }
            }
    }
}

private fun castContentType(url: String): String {
    val normalized = url.substringBefore('#').lowercase()
    return when {
        ".m3u8" in normalized -> "application/x-mpegURL"
        ".mpd" in normalized -> "application/dash+xml"
        else -> "video/mp4"
    }
}
