package top.jlen.vod.ui

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.VodItem

class StateRegressionTest {
    @Test
    fun loadingFailureAndMissingDetailKeepRouteOwnership() {
        val vodId = "影片/%2F?线路=1"
        val states = listOf(
            beginDetailLoad(DetailUiState(), keepCurrentContent = false, requestedVodId = vodId),
            detailStateWithLoadError("网络错误", vodId),
            missingDetailState(vodId)
        )
        states.forEach { state ->
            assertNull(state.item)
            assertEquals(vodId, state.requestedVodId)
            assertTrue(state.matchesDetailRoute(vodId))
            assertFalse(state.matchesDetailRoute("其他影片"))
            // Navigation 已解码过一次，字面量 %2F 不应再次变成斜杠。
            assertFalse(state.matchesDetailRoute("影片//?线路=1"))
        }
        assertTrue(states.first().isLoading)
        assertFalse(states[1].isLoading)
        assertFalse(states[2].isLoading)
    }

    @Test
    fun retryClearsFailureWithoutLosingRequestedId() {
        val failed = detailStateWithLoadError("暂时不可用", "123")
        val retry = beginDetailLoad(failed, keepCurrentContent = false, requestedVodId = "123")
        assertTrue(retry.isLoading)
        assertNull(retry.error)
        assertTrue(retry.matchesDetailRoute("123"))
    }

    @Test
    fun switchingDetailRequestDoesNotReuseOldItem() {
        val previous = loadedDetailState(VodItem(vodId = "old"), emptyList(), isFavorited = true)
        val loading = beginDetailLoad(previous, keepCurrentContent = false, requestedVodId = "new")
        assertNull(loading.item)
        assertFalse(loading.isFavorited)
        assertFalse(loading.matchesDetailRoute("old"))
        assertTrue(loading.matchesDetailRoute("new"))
    }

    @Test
    fun loadedDetailMatchesRequestedAndCanonicalAliases() {
        val item = VodItem(vodId = "cms-id", siteVodId = "site-id", detailUrl = "https://example.test/voddetail/url-id.html")
        val state = loadedDetailState(item, emptyList(), isFavorited = false, requestedVodId = "route-id")
        listOf("route-id", "cms-id", "site-id", "url-id").forEach { id ->
            assertTrue(state.matchesDetailRoute(id))
        }
        assertFalse(state.matchesDetailRoute(""))
        assertFalse(state.matchesDetailRoute("wrong-id"))
    }

    @Test
    fun cancellationNeverReachesFailureOrFallback() {
        val cancellation = CancellationException("离开页面")
        var wroteFailure = false
        var usedFallback = false
        val thrown = assertThrows(CancellationException::class.java) {
            runStateCatching<Unit> { throw cancellation }
                .onFailure { wroteFailure = true }
                .getOrElse { usedFallback = true }
        }
        assertSame(cancellation, thrown)
        assertFalse(wroteFailure)
        assertFalse(usedFallback)
    }

    @Test
    fun ordinaryFailureStillReachesErrorHandler() {
        val error = IllegalStateException("网络失败")
        val result = runStateCatching<Unit> { throw error }
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun refreshingHomePreservesContentAndStopsLoadingOnFailure() {
        val item = VodItem(vodId = "1")
        val current = HomeUiState(isLoading = false, homeFirstLoaded = true, latest = listOf(item), homeVisibleCount = 1)
        val refreshing = loadingHomeState(null, current)
        assertTrue(refreshing.isLoading)
        assertEquals(current.latest, refreshing.latest)
        assertEquals(current.homeVisibleCount, refreshing.homeVisibleCount)
        val failed = homeStateWithHomeError(refreshing, null, true, "刷新失败")
        assertFalse(failed.isLoading)
        assertEquals(current.latest, failed.latest)
        assertEquals("刷新失败", failed.error)
    }

    @Test
    fun categoryRefreshKeepsContentButNewFilterClearsIt() {
        val category = AppleCmsCategory(typeId = "1", typeName = "电影")
        val filters = mapOf("year" to "2025")
        val current = HomeUiState(
            isLoading = false,
            selectedCategory = category,
            selectedCategoryFilters = filters,
            categoryVideos = listOf(VodItem(vodId = "1")),
            categoryVisibleCount = 1,
            categoryCursor = "next",
            categoryFirstLoaded = true
        )
        val refreshing = beginCategoryLoadState(current, category, filters)
        assertTrue(refreshing.isCategoryLoading)
        assertEquals(current.categoryVideos, refreshing.categoryVideos)
        assertEquals("next", refreshing.categoryCursor)
        val changedFilter = beginCategoryLoadState(current, category, mapOf("year" to "2024"))
        assertTrue(changedFilter.categoryVideos.isEmpty())
        assertEquals(0, changedFilter.categoryVisibleCount)
        val changedCategory = beginCategoryLoadState(current, AppleCmsCategory(typeId = "2"), filters)
        assertTrue(changedCategory.categoryVideos.isEmpty())
    }
}
