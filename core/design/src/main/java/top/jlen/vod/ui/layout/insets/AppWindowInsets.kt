package top.jlen.vod.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Keep backgrounds edge-to-edge while protecting headers from system UI.
 * Apply after background(), before the page's normal content spacing.
 * windowInsetsPadding consumes the applied space, so nested headers do not add it twice.
 */
@Composable
fun Modifier.appTopInsetsPadding(): Modifier = windowInsetsPadding(
    appTopWindowInsets()
)

/** Horizontal cutouts must not shrink the vertical scrolling viewport. */
@Composable
fun Modifier.appHorizontalInsetsPadding(): Modifier = windowInsetsPadding(
    appTopWindowInsets().only(WindowInsetsSides.Horizontal)
)

/** Use with appTopContentPadding: the list scrolls behind the status bar. */
@Composable
fun Modifier.appScrollingInsets(): Modifier = appHorizontalInsetsPadding()
    .consumeWindowInsets(appTopWindowInsets().only(WindowInsetsSides.Top))

@Composable
fun appTopContentPadding(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues =
    TopInsetContentPadding(PaddingValues(top = top, bottom = bottom), appTopWindowInsets(), LocalDensity.current)

// Read insets when LazyColumn measures padding, not a frame earlier in composition.
internal class TopInsetContentPadding(
    private val content: PaddingValues,
    private val insets: WindowInsets,
    private val density: Density
) : PaddingValues {
    override fun calculateTopPadding(): Dp = content.calculateTopPadding() +
        with(density) { insets.getTop(this).toDp() }
    override fun calculateBottomPadding(): Dp = content.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = content.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = content.calculateRightPadding(layoutDirection)
}

@Composable
private fun appTopWindowInsets(): WindowInsets =
    appTopWindowInsets(WindowInsets.statusBars, WindowInsets.captionBar, WindowInsets.displayCutout)

internal fun appTopWindowInsets(
    statusBars: WindowInsets,
    captionBar: WindowInsets,
    displayCutout: WindowInsets
): WindowInsets = statusBars
    .union(captionBar)
    .union(displayCutout)
    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
