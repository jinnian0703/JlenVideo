package top.jlen.vod.ui

import android.content.pm.ActivityInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.AppleCmsRepository

class FullscreenPlayerActivity : ComponentActivity() {
    private val repository by lazy { AppleCmsRepository(applicationContext) }
    private var latestSnapshot: PlaybackSnapshot = PlaybackSnapshot()
    private var currentEpisodeIndex by mutableIntStateOf(0)
    private var currentResolvedUrl by mutableStateOf("")
    private var currentEpisodeName by mutableStateOf("")
    private var episodeNames: List<String> = emptyList()
    private var episodePageUrls: List<String> = emptyList()
    private var lastAppliedOrientation: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME).orEmpty()
        episodeNames = intent.getStringArrayListExtra(EXTRA_EPISODE_NAMES).orEmpty()
        episodePageUrls = intent.getStringArrayListExtra(EXTRA_EPISODE_PAGE_URLS).orEmpty()
        currentEpisodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
            .coerceIn(0, (episodePageUrls.size - 1).coerceAtLeast(0))
        currentResolvedUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        currentEpisodeName = episodeNames.getOrNull(currentEpisodeIndex).orEmpty()
        latestSnapshot = PlaybackSnapshot(
            positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L),
            speed = intent.getFloatExtra(EXTRA_SPEED, 1f),
            playWhenReady = intent.getBooleanExtra(EXTRA_PLAY_WHEN_READY, true)
        )
        if (intent.getBooleanExtra(EXTRA_HAS_INITIAL_ORIENTATION, false)) {
            applyVideoOrientation(intent.getBooleanExtra(EXTRA_IS_LANDSCAPE_VIDEO, true))
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                val resolvedUrl = currentResolvedUrl
                val selectedIndex = currentEpisodeIndex
                val selectedEpisodeName = currentEpisodeName
                val hasNextEpisode = selectedIndex < episodePageUrls.lastIndex

                FullscreenPlayerContent(
                    url = resolvedUrl,
                    title = title,
                    sourceName = sourceName,
                    episodeName = selectedEpisodeName,
                    hasNextEpisode = hasNextEpisode,
                    initialSnapshot = latestSnapshot,
                    onVideoOrientationDetected = ::applyVideoOrientation,
                    onPlaybackSnapshotChanged = { latestSnapshot = it },
                    onNextEpisode = { openEpisode(selectedIndex + 1, autoPlay = true) },
                    onClose = ::finish
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun openEpisode(index: Int, autoPlay: Boolean) {
        val safeIndex = index.coerceIn(0, (episodePageUrls.size - 1).coerceAtLeast(0))
        val pageUrl = episodePageUrls.getOrNull(safeIndex).orEmpty()
        if (pageUrl.isBlank()) return

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.resolvePlayUrl(pageUrl) }
            }
                .onSuccess { resolved ->
                    currentEpisodeIndex = safeIndex
                    currentEpisodeName = episodeNames.getOrNull(safeIndex).orEmpty()
                    currentResolvedUrl = resolved.url
                    latestSnapshot = PlaybackSnapshot(
                        positionMs = 0L,
                        speed = latestSnapshot.speed,
                        playWhenReady = autoPlay
                    )
                }
                .onFailure {
                    currentEpisodeIndex = safeIndex
                    currentEpisodeName = episodeNames.getOrNull(safeIndex).orEmpty()
                    currentResolvedUrl = pageUrl
                    latestSnapshot = PlaybackSnapshot(
                        positionMs = 0L,
                        speed = latestSnapshot.speed,
                        playWhenReady = autoPlay
                    )
                }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun applyVideoOrientation(isLandscapeVideo: Boolean) {
        val targetOrientation = if (isLandscapeVideo) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        if (lastAppliedOrientation == targetOrientation && requestedOrientation == targetOrientation) {
            return
        }
        lastAppliedOrientation = targetOrientation
        requestedOrientation = targetOrientation
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_POSITION_MS, latestSnapshot.positionMs)
                putExtra(EXTRA_SPEED, latestSnapshot.speed)
                putExtra(EXTRA_PLAY_WHEN_READY, latestSnapshot.playWhenReady)
                putExtra(EXTRA_EPISODE_INDEX, currentEpisodeIndex)
                putExtra(EXTRA_URL, currentResolvedUrl)
            }
        )
        super.finish()
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SOURCE_NAME = "extra_source_name"
        private const val EXTRA_EPISODE_NAME = "extra_episode_name"
        private const val EXTRA_EPISODE_NAMES = "extra_episode_names"
        private const val EXTRA_EPISODE_PAGE_URLS = "extra_episode_page_urls"
        private const val EXTRA_EPISODE_INDEX = "extra_episode_index"
        private const val EXTRA_POSITION_MS = "extra_position_ms"
        private const val EXTRA_SPEED = "extra_speed"
        private const val EXTRA_PLAY_WHEN_READY = "extra_play_when_ready"
        private const val EXTRA_HAS_INITIAL_ORIENTATION = "extra_has_initial_orientation"
        private const val EXTRA_IS_LANDSCAPE_VIDEO = "extra_is_landscape_video"

        fun createIntent(
            context: Context,
            url: String,
            title: String,
            sourceName: String,
            episodeName: String,
            episodeNames: ArrayList<String>,
            episodePageUrls: ArrayList<String>,
            selectedEpisodeIndex: Int,
            snapshot: PlaybackSnapshot,
            isLandscapeVideo: Boolean?
        ): Intent = Intent(context, FullscreenPlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SOURCE_NAME, sourceName)
            putExtra(EXTRA_EPISODE_NAME, episodeName)
            putStringArrayListExtra(EXTRA_EPISODE_NAMES, episodeNames)
            putStringArrayListExtra(EXTRA_EPISODE_PAGE_URLS, episodePageUrls)
            putExtra(EXTRA_EPISODE_INDEX, selectedEpisodeIndex)
            putExtra(EXTRA_POSITION_MS, snapshot.positionMs)
            putExtra(EXTRA_SPEED, snapshot.speed)
            putExtra(EXTRA_PLAY_WHEN_READY, snapshot.playWhenReady)
            putExtra(EXTRA_HAS_INITIAL_ORIENTATION, isLandscapeVideo != null)
            putExtra(EXTRA_IS_LANDSCAPE_VIDEO, isLandscapeVideo ?: true)
        }

        fun extractResult(intent: Intent?): FullscreenPlaybackResult? {
            if (intent == null) return null
            return FullscreenPlaybackResult(
                episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0),
                resolvedUrl = intent.getStringExtra(EXTRA_URL).orEmpty(),
                snapshot = PlaybackSnapshot(
                    positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L),
                    speed = intent.getFloatExtra(EXTRA_SPEED, 1f),
                    playWhenReady = intent.getBooleanExtra(EXTRA_PLAY_WHEN_READY, true)
                )
            )
        }
    }
}

@Composable
private fun FullscreenPlayerContent(
    url: String,
    title: String,
    sourceName: String,
    episodeName: String,
    hasNextEpisode: Boolean,
    initialSnapshot: PlaybackSnapshot,
    onVideoOrientationDetected: (Boolean) -> Unit,
    onPlaybackSnapshotChanged: (PlaybackSnapshot) -> Unit,
    onNextEpisode: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (url.isNotBlank()) {
            NativeVideoPlayer(
                url = url,
                title = title,
                sourceName = sourceName,
                episodeName = episodeName,
                hasNextEpisode = hasNextEpisode,
                onNextEpisode = onNextEpisode,
                onToggleFullscreen = null,
                fullscreenMode = true,
                onVideoOrientationDetected = onVideoOrientationDetected,
                initialSnapshot = initialSnapshot,
                onPlaybackSnapshotChanged = onPlaybackSnapshotChanged,
                onPlaybackEnded = {
                    if (hasNextEpisode) {
                        onNextEpisode()
                    }
                },
                onClose = onClose
            )
        }
    }
}
