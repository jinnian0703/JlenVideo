package top.jlen.vod.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.NavBackStackEntry

private val topLevelRoutes = setOf("home", "categories", "follow", "search", "account")

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationTransitionStyle(): ScreenTransitionStyle =
    navigationTransitionStyle(
        initialRoute = initialState.destination.route,
        targetRoute = targetState.destination.route,
        isSameEntry = initialState.id == targetState.id
    )

internal fun navigationTransitionStyle(
    initialRoute: String?,
    targetRoute: String?,
    isSameEntry: Boolean = false
): ScreenTransitionStyle = when {
    initialRoute.isNullOrBlank() || targetRoute.isNullOrBlank() || isSameEntry -> ScreenTransitionStyle.None
    initialRoute in topLevelRoutes && targetRoute in topLevelRoutes -> ScreenTransitionStyle.None
    // Do not translate the player's SurfaceView, or the first-run onboarding screens.
    initialRoute.isPlayerRoute() || targetRoute.isPlayerRoute() ||
        initialRoute.startsWith("onboarding/") || targetRoute.startsWith("onboarding/") -> ScreenTransitionStyle.Fade
    else -> ScreenTransitionStyle.Slide
}

private fun String.isPlayerRoute(): Boolean = this == "player" || startsWith("player?")
