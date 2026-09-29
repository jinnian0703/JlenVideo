package top.jlen.vod.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationMotionTest {
    @Test
    fun primaryTabsSwitchWithoutAnimation() {
        val tabs = listOf("home", "categories", "follow", "search", "account")
        tabs.forEach { from ->
            tabs.forEach { to ->
                assertEquals("$from -> $to", ScreenTransitionStyle.None, navigationTransitionStyle(from, to))
            }
        }
    }

    @Test
    fun openingAndReturningFromSecondaryScreensUsesSlide() {
        listOf(
            "home" to "detail/{vodId}",
            "categories" to "detail/{vodId}",
            "follow" to "detail/{vodId}",
            "search" to "search/results/{query}",
            "account" to "account/points",
            "account" to "account/settings/about",
            "home" to "announcements"
        ).forEach { (parent, child) ->
            assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle(parent, child))
            assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle(child, parent))
        }
    }

    @Test
    fun openingAndReturningFromTertiaryScreensUsesSlide() {
        listOf(
            "search/results/{query}" to "detail/{vodId}",
            "account/settings/about" to "account/settings/update",
            "account/settings/about" to "account/settings/agreement",
            "account/settings/about" to "account/settings/logs",
            "account/settings/logs" to "account/settings/logs/detail/{logId}",
            "announcements" to "announcement/{noticeId}"
        ).forEach { (parent, child) ->
            assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle(parent, child))
            assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle(child, parent))
        }
    }

    @Test
    fun differentEntriesWithSameRoutePatternStillAnimate() {
        listOf("detail/{vodId}", "search/results/{query}").forEach { route ->
            assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle(route, route))
        }
    }

    @Test
    fun sameEntryDoesNotAnimate() {
        assertEquals(
            ScreenTransitionStyle.None,
            navigationTransitionStyle("detail/{vodId}", "detail/{vodId}", isSameEntry = true)
        )
    }

    @Test
    fun startupWithoutAnEntryDoesNotAnimate() {
        assertEquals(ScreenTransitionStyle.None, navigationTransitionStyle(null, "home"))
        assertEquals(ScreenTransitionStyle.None, navigationTransitionStyle("home", null))
        assertEquals(ScreenTransitionStyle.None, navigationTransitionStyle("", "home"))
    }

    @Test
    fun playerOnlyFadesInBothDirectionsIncludingParameterizedRoute() {
        listOf("player", "player?vodId={vodId}&sourceIndex={sourceIndex}").forEach { player ->
            listOf("detail/{vodId}", "account", "home").forEach { parent ->
                assertEquals(ScreenTransitionStyle.Fade, navigationTransitionStyle(parent, player))
                assertEquals(ScreenTransitionStyle.Fade, navigationTransitionStyle(player, parent))
            }
        }
    }

    @Test
    fun onboardingOnlyFades() {
        assertEquals(
            ScreenTransitionStyle.Fade,
            navigationTransitionStyle("onboarding/agreement", "onboarding/login")
        )
        assertEquals(ScreenTransitionStyle.Fade, navigationTransitionStyle("onboarding/login", "home"))
    }

    @Test
    fun nestedRoutesMustNotBeMistakenForTabs() {
        assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle("account/points", "search"))
        assertEquals(ScreenTransitionStyle.Slide, navigationTransitionStyle("search/results/{query}", "account"))
    }
}
