package top.jlen.vod.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMotionTest {
    @Test
    fun phoneOffsetIsOnlyASmallFractionOfWidth() {
        assertEquals(30, screenSlideOffset(360, 32, 1))
    }

    @Test
    fun tabletOffsetIsCappedInsteadOfSlidingAcrossTheScreen() {
        assertEquals(32, screenSlideOffset(1600, 32, 1))
        assertEquals(96, screenSlideOffset(2400, 96, 1))
    }

    @Test
    fun rtlReversesMotion() {
        assertEquals(-30, screenSlideOffset(360, 32, -1))
    }

    @Test
    fun zeroWidthOrOffsetDoesNotMove() {
        assertEquals(0, screenSlideOffset(0, 32, 1))
        assertEquals(0, screenSlideOffset(360, 0, 1))
    }

    @Test
    fun returnIsFasterThanForwardNavigation() {
        assertTrue(screenDuration(ScreenTransitionStyle.Slide, true) < screenDuration(ScreenTransitionStyle.Slide, false))
        assertTrue(screenDuration(ScreenTransitionStyle.Slide, false) in 150..300)
        assertTrue(screenDuration(ScreenTransitionStyle.Slide, true) in 150..300)
    }

    @Test
    fun playerFadeIsShortAndSymmetric() {
        assertEquals(180, screenDuration(ScreenTransitionStyle.Fade, false))
        assertEquals(180, screenDuration(ScreenTransitionStyle.Fade, true))
    }

    @Test
    fun disabledTransitionHasNoDuration() {
        assertEquals(0, screenDuration(ScreenTransitionStyle.None, false))
        assertEquals(0, screenDuration(ScreenTransitionStyle.None, true))
    }
}
