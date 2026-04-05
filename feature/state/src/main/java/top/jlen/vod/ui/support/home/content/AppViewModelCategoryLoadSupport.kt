package top.jlen.vod.ui

import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CursorPagedVodItems

internal fun beginCategoryLoadState(
    homeState: HomeUiState,
    category: AppleCmsCategory,
    filters: Map<String, String>
): HomeUiState = homeState.copy(
    selectedCategory = category,
    selectedCategoryFilters = filters,
    categoryVideos = emptyList(),
    categoryVisibleCount = 0,
    categoryCursor = "",
    hasMoreCategoryItems = true,
    categoryFirstLoaded = false,
    isCategoryLoading = true,
    isCategoryAppending = false,
    categoryAppendError = null,
    error = null
)

internal fun homeStateWithCategoryPage(
    homeState: HomeUiState,
    page: CursorPagedVodItems
): HomeUiState = homeState.copy(
    categoryVideos = page.items,
    categoryVisibleCount = page.items.initialGridVisibleCount(),
    categoryCursor = page.nextCursor,
    hasMoreCategoryItems = page.hasMore,
    categoryFirstLoaded = true,
    isCategoryAppending = false,
    isCategoryLoading = false
)

internal fun homeStateWithCategoryError(
    homeState: HomeUiState,
    errorMessage: String
): HomeUiState = homeState.copy(
    isCategoryAppending = false,
    isCategoryLoading = false,
    error = errorMessage
)
