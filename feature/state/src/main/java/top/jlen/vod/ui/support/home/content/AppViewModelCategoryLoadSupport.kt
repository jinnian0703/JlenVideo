package top.jlen.vod.ui

import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.CursorPagedVodItems

internal fun beginCategoryLoadState(
    homeState: HomeUiState,
    category: AppleCmsCategory,
    filters: Map<String, String>
): HomeUiState {
    val keepContent = homeState.selectedCategory?.typeId == category.typeId &&
        homeState.selectedCategoryFilters == filters
    return homeState.copy(
        selectedCategory = category,
        selectedCategoryFilters = filters,
        categoryVideos = if (keepContent) homeState.categoryVideos else emptyList(),
        categoryVisibleCount = if (keepContent) homeState.categoryVisibleCount else 0,
        categoryCursor = if (keepContent) homeState.categoryCursor else "",
        hasMoreCategoryItems = if (keepContent) homeState.hasMoreCategoryItems else true,
        categoryFirstLoaded = keepContent && homeState.categoryFirstLoaded,
        isCategoryLoading = true,
        isCategoryAppending = false,
        categoryAppendError = null,
        error = null
    )
}

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
