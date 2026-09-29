package top.jlen.vod.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object UiMotion {
    const val ScreenDurationMillis = 280
    const val ScreenPopDurationMillis = 220
    const val ScreenFadeDurationMillis = 180
    const val CarouselAutoScrollMillis = 3500L
    const val CarouselSlideMillis = 420
    const val SnapshotDispatchIntervalMillis = 900L
    const val SnapshotPositionThresholdMillis = 1200L
    const val PlayerUiRefreshMillis = 500L
}

enum class ScreenTransitionStyle { None, Slide, Fade }

// Standard Compose transitions follow the system animator duration scale, including zero.
fun AnimatedContentTransitionScope<*>.screenEnterTransition(
    style: ScreenTransitionStyle,
    direction: Int,
    maxSlideDistancePx: Int
): EnterTransition = screenEnter(style, isPop = false, direction, maxSlideDistancePx)

fun AnimatedContentTransitionScope<*>.screenExitTransition(
    style: ScreenTransitionStyle,
    direction: Int,
    maxSlideDistancePx: Int
): ExitTransition = screenExit(style, isPop = false, direction, maxSlideDistancePx)

fun AnimatedContentTransitionScope<*>.screenPopEnterTransition(
    style: ScreenTransitionStyle,
    direction: Int,
    maxSlideDistancePx: Int
): EnterTransition = screenEnter(style, isPop = true, direction, maxSlideDistancePx)

fun AnimatedContentTransitionScope<*>.screenPopExitTransition(
    style: ScreenTransitionStyle,
    direction: Int,
    maxSlideDistancePx: Int
): ExitTransition = screenExit(style, isPop = true, direction, maxSlideDistancePx)

internal fun screenSlideOffset(width: Int, maxDistancePx: Int, direction: Int): Int =
    (width / 12).coerceIn(0, maxDistancePx.coerceAtLeast(0)) * direction

private fun screenEnter(
    style: ScreenTransitionStyle,
    isPop: Boolean,
    direction: Int,
    maxSlideDistancePx: Int
): EnterTransition {
    if (style == ScreenTransitionStyle.None) return EnterTransition.None
    val duration = screenDuration(style, isPop)
    val fade = fadeIn(tween(duration, easing = FastOutSlowInEasing))
    if (style == ScreenTransitionStyle.Fade) return fade
    return fade + slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { width ->
        val offset = screenSlideOffset(width, maxSlideDistancePx, direction)
        if (isPop) -offset / 2 else offset
    }
}

private fun screenExit(
    style: ScreenTransitionStyle,
    isPop: Boolean,
    direction: Int,
    maxSlideDistancePx: Int
): ExitTransition {
    if (style == ScreenTransitionStyle.None) return ExitTransition.None
    val duration = screenDuration(style, isPop)
    val fade = fadeOut(tween(duration, easing = FastOutSlowInEasing))
    if (style == ScreenTransitionStyle.Fade) return fade
    return fade + slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { width ->
        val offset = screenSlideOffset(width, maxSlideDistancePx, direction)
        if (isPop) offset else -offset / 2
    }
}

internal fun screenDuration(style: ScreenTransitionStyle, isPop: Boolean): Int = when {
    style == ScreenTransitionStyle.None -> 0
    style == ScreenTransitionStyle.Fade -> UiMotion.ScreenFadeDurationMillis
    isPop -> UiMotion.ScreenPopDurationMillis
    else -> UiMotion.ScreenDurationMillis
}
