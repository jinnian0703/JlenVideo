package top.jlen.vod.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppWindowInsetsTest {
    private val none = WindowInsets(0, 0, 0, 0)
    private val density = Density(1f)

    @Test
    fun scrollingContentStartsBelowStatusBarWithoutShrinkingViewport() {
        val padding = TopInsetContentPadding(
            PaddingValues(top = 16.dp, bottom = 24.dp),
            WindowInsets(0, 72, 0, 0), Density(3f)
        )
        assertEquals(40.dp, padding.calculateTopPadding())
        assertEquals(24.dp, padding.calculateBottomPadding())
        assertEquals(0.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateRightPadding(LayoutDirection.Rtl))
    }

    @Test
    fun phoneUsesActualStatusBarHeight() {
        val insets = appTopWindowInsets(WindowInsets(0, 72, 0, 0), none, none)
        assertEquals(72, insets.getTop(density))
    }

    @Test
    fun notchAndStatusBarOverlapInsteadOfAdding() {
        val insets = appTopWindowInsets(
            WindowInsets(0, 72, 0, 0), none, WindowInsets(0, 96, 0, 0)
        )
        assertEquals(96, insets.getTop(density))
    }

    @Test
    fun tabletCaptionBarIsIncluded() {
        val insets = appTopWindowInsets(
            WindowInsets(0, 24, 0, 0), WindowInsets(0, 48, 0, 0), none
        )
        assertEquals(48, insets.getTop(density))
    }

    @Test
    fun landscapeCutoutProtectsBothSidesInEitherLayoutDirection() {
        val insets = appTopWindowInsets(none, none, WindowInsets(80, 0, 40, 0))
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            assertEquals(80, insets.getLeft(density, direction))
            assertEquals(40, insets.getRight(density, direction))
        }
    }

    @Test
    fun topSafetyDoesNotAddBottomNavigationPadding() {
        val insets = appTopWindowInsets(
            WindowInsets(0, 24, 0, 36), none, WindowInsets(0, 0, 0, 48)
        )
        assertEquals(0, insets.getBottom(density))
    }

    @Test
    fun hiddenSystemUiHasNoHardcodedTopGap() {
        assertEquals(0, appTopWindowInsets(none, none, none).getTop(density))
    }

    @Test
    fun consumedParentSpaceLeavesOnlyRemainingSafetyInset() {
        val insets = appTopWindowInsets(WindowInsets(0, 72, 0, 0), none, none)
        assertEquals(0, insets.exclude(insets).getTop(density))
        assertEquals(24, insets.exclude(WindowInsets(0, 48, 0, 0)).getTop(density))
    }
}
