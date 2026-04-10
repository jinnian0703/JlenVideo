package top.jlen.vod.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

object UiMotion {
    const val ScreenDurationMillis = 130
    const val CarouselAutoScrollMillis = 3500L
    const val CarouselSlideMillis = 420
    const val SnapshotDispatchIntervalMillis = 900L
    const val SnapshotPositionThresholdMillis = 1200L
    const val PlayerUiRefreshMillis = 500L
}

fun AnimatedContentTransitionScope<*>.screenEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(UiMotion.ScreenDurationMillis))

fun AnimatedContentTransitionScope<*>.screenExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(UiMotion.ScreenDurationMillis))

fun AnimatedContentTransitionScope<*>.screenPopEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(UiMotion.ScreenDurationMillis))

fun AnimatedContentTransitionScope<*>.screenPopExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(UiMotion.ScreenDurationMillis))
