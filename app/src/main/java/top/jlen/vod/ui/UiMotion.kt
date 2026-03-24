package top.jlen.vod.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

object UiMotion {
    const val ScreenDurationMillis = 260
    const val CarouselAutoScrollMillis = 3500L
    const val CarouselSlideMillis = 420
    const val SnapshotDispatchIntervalMillis = 900L
    const val SnapshotPositionThresholdMillis = 1200L
    const val PlayerUiRefreshMillis = 500L
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(UiMotion.ScreenDurationMillis)
    ) + fadeIn(animationSpec = tween(UiMotion.ScreenDurationMillis))

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(UiMotion.ScreenDurationMillis)
    ) + fadeOut(animationSpec = tween(UiMotion.ScreenDurationMillis))

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(UiMotion.ScreenDurationMillis)
    ) + fadeIn(animationSpec = tween(UiMotion.ScreenDurationMillis))

fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(UiMotion.ScreenDurationMillis)
    ) + fadeOut(animationSpec = tween(UiMotion.ScreenDurationMillis))
