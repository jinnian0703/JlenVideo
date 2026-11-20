package top.jlen.vod.ui

import android.content.pm.ActivityInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.jlen.vod.offline.OfflinePlaybackScreen

class FullscreenPlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = resolveFullscreenOrientation(resources.configuration)
        super.onCreate(savedInstanceState)
        configureImmersiveWindow()

        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
        if (downloadId.isNullOrBlank()) {
            finish()
            return
        }
        setContent { OfflinePlaybackScreen(downloadId, onClose = ::finish) }
        consumeRootWindowInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestedOrientation = resolveFullscreenOrientation(newConfig)
    }

    private fun configureImmersiveWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        allowDisplayCutout()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun consumeRootWindowInsets() {
        val contentRoot = findViewById<ViewGroup>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, _ ->
            view.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
        contentRoot.setPadding(0, 0, 0, 0)
        ViewCompat.requestApplyInsets(contentRoot)
    }

    private fun allowDisplayCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val attributes = window.attributes
        attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = attributes
    }

    private fun resolveFullscreenOrientation(configuration: Configuration): Int =
        if (minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= EXPANDED_WINDOW_MIN_DP) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

    companion object {
        private const val EXTRA_DOWNLOAD_ID = "offline_download_id"

        internal fun forDownload(context: Context, id: String) =
            Intent(context, FullscreenPlayerActivity::class.java).putExtra(EXTRA_DOWNLOAD_ID, id)
        private const val EXPANDED_WINDOW_MIN_DP = 600
    }
}

@Composable
internal fun FullscreenPlayerContent(
    url: String,
    title: String,
    sourceName: String,
    episodeName: String,
    hasNextEpisode: Boolean,
    initialSnapshot: PlaybackSnapshot,
    onPlaybackSnapshotChanged: (PlaybackSnapshot) -> Unit,
    onNextEpisode: () -> Unit,
    onClose: () -> Unit,
    localFilesOnly: Boolean = false,
    onOpenEpisodePicker: (() -> Unit)? = null,
    onPlaybackError: (() -> Unit)? = null
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
                initialSnapshot = initialSnapshot,
                onPlaybackSnapshotChanged = onPlaybackSnapshotChanged,
                onPlaybackEnded = {
                    if (hasNextEpisode) {
                        onNextEpisode()
                    }
                },
                onClose = onClose,
                onOpenEpisodePicker = onOpenEpisodePicker,
                localFilesOnly = localFilesOnly,
                onPlaybackError = onPlaybackError
            )
        }
    }
}
