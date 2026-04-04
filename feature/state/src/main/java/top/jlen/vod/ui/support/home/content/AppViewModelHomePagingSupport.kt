package top.jlen.vod.ui

import top.jlen.vod.data.CursorPagedVodItems

internal fun homeStateWithExpandedHomeVisibleCount(homeState: HomeUiState): HomeUiState {
    val nextVisibleCount = homeState.homeVisibleCount + GRID_BATCH_ITEM_COUNT
    return homeState.copy(
        homeVisibleCount = nextVisibleCount.coerceAtMost(homeState.latest.size)
    )
}

internal fun beginHomeAppendState(homeState: HomeUiState): HomeUiState =
    homeState.copy(isHomeAppending = true, homeAppendError = null)

internal fun homeStateWithAppendedHomePage(
    homeState: HomeUiState,
    previousVisibleCount: Int,
    page: CursorPagedVodItems
): HomeUiState {
    val mergedLatest = (homeState.latest + page.items).distinctBy { it.vodId }
    return homeState.copy(
        latest = mergedLatest,
        homeVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
            .coerceAtLeast(mergedLatest.initialGridVisibleCount())
            .coerceAtMost(mergedLatest.size),
        homeCursor = page.nextCursor,
        hasMoreHomeItems = page.hasMore,
        isHomeAppending = false
    )
}

internal fun homeStateWithHomeAppendError(
    homeState: HomeUiState,
    errorMessage: String
): HomeUiState = homeState.copy(
    isHomeAppending = false,
    homeAppendError = errorMessage
)

internal fun homeStateWithExpandedCategoryVisibleCount(homeState: HomeUiState): HomeUiState {
    val nextVisibleCount = homeState.categoryVisibleCount + GRID_BATCH_ITEM_COUNT
    return homeState.copy(
        categoryVisibleCount = nextVisibleCount.coerceAtMost(homeState.categoryVideos.size)
    )
}

internal fun beginCategoryAppendState(homeState: HomeUiState): HomeUiState =
    homeState.copy(isCategoryAppending = true, categoryAppendError = null)

internal fun homeStateWithAppendedCategoryPage(
    homeState: HomeUiState,
    previousVisibleCount: Int,
    page: CursorPagedVodItems
): HomeUiState {
    val mergedVideos = (homeState.categoryVideos + page.items).distinctBy { it.vodId }
    return homeState.copy(
        categoryVideos = mergedVideos,
        categoryVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
            .coerceAtLeast(mergedVideos.initialGridVisibleCount())
            .coerceAtMost(mergedVideos.size),
        categoryCursor = page.nextCursor,
        hasMoreCategoryItems = page.hasMore,
        isCategoryAppending = false
    )
}

internal fun homeStateWithCategoryAppendError(
    homeState: HomeUiState,
    errorMessage: String
): HomeUiState = homeState.copy(
    isCategoryAppending = false,
    categoryAppendError = errorMessage
)
