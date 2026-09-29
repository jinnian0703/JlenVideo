package top.jlen.vod.ui

import org.junit.Assert.*
import org.junit.Test
import top.jlen.vod.data.*

class LibraryFilterStateTest {
    private val movie = AppleCmsCategory(typeId = "1", typeName = "电影", typeExtend = """{"area":"中国,日本","year":"2026","class":"喜剧"}""")
    private val series = AppleCmsCategory(typeId = "2", typeName = "电视剧", typeExtend = """{"area":"中国,美国","year":"2025","class":"剧情"}""")

    @Test fun allIsFirstAndDoesNotDuplicateServerCategories() {
        val options = libraryCategoryOptions(listOf(movie, series, movie, ALL_LIBRARY_CATEGORY))
        assertEquals(listOf("", "1", "2"), options.map { it.typeId })
        assertEquals("全部", options.first().typeName)
    }
    @Test fun allMergesAvailableFiltersWithoutDuplicates() {
        val groups = libraryFilterGroups(ALL_LIBRARY_CATEGORY, listOf(movie, series))
        assertEquals(listOf("中国", "日本", "美国"), groups.first { it.key == "area" }.options)
        assertEquals(listOf("2026", "2025"), groups.first { it.key == "year" }.options)
    }
    @Test fun specificCategoryDoesNotInheritOtherCategoryOptions() {
        assertEquals(movie.filterGroups, libraryFilterGroups(movie, listOf(movie, series)))
    }
    @Test fun summaryUsesSelectedFiltersOnlyInDisplayOrder() {
        val groups = libraryFilterGroups(ALL_LIBRARY_CATEGORY, listOf(movie, series))
        assertEquals("全部 · 中国 · 2026", libraryFilterSummary(ALL_LIBRARY_CATEGORY, groups, mapOf("area" to "中国", "year" to "2026", "class" to "", "unknown" to "ignored")))
    }
    @Test fun homeRefreshPreservesAllSelectionFiltersAndCursor() {
        val current = HomeUiState(
            selectedCategory = ALL_LIBRARY_CATEGORY,
            selectedCategoryFilters = mapOf("year" to "2026"),
            categoryVideos = listOf(VodItem(vodId = "88", vodName = "测试")),
            categoryVisibleCount = 1,
            categoryCursor = "next",
            hasMoreCategoryItems = true,
            categoryFirstLoaded = true
        )
        val fresh = HomeUiState(isLoading = false, categories = listOf(movie, series), selectedCategory = movie)
        val result = homeStateKeepingLibrary(fresh, current)
        assertEquals(ALL_LIBRARY_CATEGORY, result.selectedCategory)
        assertEquals(current.selectedCategoryFilters, result.selectedCategoryFilters)
        assertEquals(current.categoryVideos, result.categoryVideos)
        assertEquals("next", result.categoryCursor)
        assertEquals(fresh.categories, result.categories)
    }
    @Test fun newSelectionInvalidatesOldFirstPageAndPagination() {
        val tracker = LibraryRequestTracker()
        val old = tracker.begin()
        val current = tracker.begin()
        assertFalse(tracker.isCurrent(old))
        assertTrue(tracker.isCurrent(current))
    }
    private fun initialPayload() = HomePayload(
        slides = emptyList(),
        hot = emptyList(),
        featured = emptyList(),
        latest = listOf(VodItem(vodId = "movie-1"), VodItem(vodId = "series-1")),
        sections = emptyList(),
        categories = listOf(movie, series),
        selectedCategory = movie,
        categoryVideos = listOf(VodItem(vodId = "only-movie")),
        latestCursor = "all-next",
        latestHasMore = true,
        categoryCursor = "movie-next",
        categoryHasMore = false
    )
    @Test fun initialHomeLoadDefaultsToAllWithMatchingItemsAndCursor() {
        val payload = initialPayload()
        val fresh = homeStateFromPayload(payload)
        assertEquals(ALL_LIBRARY_CATEGORY, fresh.selectedCategory)
        assertEquals(payload.latest, fresh.categoryVideos)
        assertEquals(2, fresh.categoryVisibleCount)
        assertEquals("all-next", fresh.categoryCursor)
        assertTrue(fresh.hasMoreCategoryItems)
        assertTrue(fresh.categoryFirstLoaded)
        assertTrue(fresh.selectedCategoryFilters.isEmpty())
        assertEquals(payload.categories, fresh.categories)
        assertEquals(fresh, homeStateKeepingLibrary(fresh, HomeUiState()))
    }
    @Test fun emptyLatestDoesNotMasqueradeOldCategoryCacheAsAll() {
        val fresh = homeStateFromPayload(initialPayload().copy(
            latest = emptyList(), latestCursor = "", latestHasMore = false
        ))
        assertEquals(ALL_LIBRARY_CATEGORY, fresh.selectedCategory)
        assertTrue(fresh.categoryVideos.isEmpty())
        assertFalse(fresh.categoryFirstLoaded)
        assertEquals("", fresh.categoryCursor)
        assertFalse(fresh.hasMoreCategoryItems)
    }
    @Test fun manuallySelectedCategorySurvivesHomeRefresh() {
        val fresh = homeStateFromPayload(initialPayload())
        val current = fresh.copy(
            selectedCategory = series,
            selectedCategoryFilters = mapOf("year" to "2025"),
            categoryVideos = listOf(VodItem(vodId = "selected-series")),
            categoryVisibleCount = 1,
            categoryCursor = "series-next"
        )
        val result = homeStateKeepingLibrary(fresh, current)
        assertEquals(series, result.selectedCategory)
        assertEquals(current.selectedCategoryFilters, result.selectedCategoryFilters)
        assertEquals(current.categoryVideos, result.categoryVideos)
        assertEquals(current.categoryCursor, result.categoryCursor)
    }
}
