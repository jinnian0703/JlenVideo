package top.jlen.vod.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Keep backgrounds edge-to-edge while protecting headers from system UI.
 * Apply after background(), before the page's normal content spacing.
 * windowInsetsPadding consumes the applied space, so nested headers do not add it twice.
 */
@Composable
fun Modifier.appTopInsetsPadding(): Modifier = windowInsetsPadding(
    appTopWindowInsets(WindowInsets.statusBars, WindowInsets.captionBar, WindowInsets.displayCutout)
)

internal fun appTopWindowInsets(
    statusBars: WindowInsets,
    captionBar: WindowInsets,
    displayCutout: WindowInsets
): WindowInsets = statusBars
    .union(captionBar)
    .union(displayCutout)
    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
