package top.jlen.vod.ui

import top.jlen.vod.data.HomePayload

internal fun homeStateFromPayload(payload: HomePayload): HomeUiState {
    val defaultSelected = payload.categories.firstOrNull()
    val categoryVideos = payload.categoryVideos
    return HomeUiState(
        isLoading = false,
        slides = payload.slides,
        hot = payload.hot,
        featured = payload.featured,
        latest = payload.latest,
        sections = payload.sections,
        homeVisibleCount = payload.latest.initialGridVisibleCount(),
        homeCursor = payload.latestCursor,
        hasMoreHomeItems = payload.latestHasMore,
        homeFirstLoaded = true,
        categories = payload.categories,
        selectedCategory = defaultSelected,
        categoryVideos = categoryVideos,
        categoryVisibleCount = categoryVideos.initialGridVisibleCount(),
        categoryCursor = payload.categoryCursor,
        hasMoreCategoryItems = payload.categoryHasMore,
        categoryFirstLoaded = true,
        error = null
    )
}

internal fun loadingHomeState(cachedPayload: HomePayload?): HomeUiState =
    cachedPayload?.let(::homeStateFromPayload) ?: HomeUiState(isLoading = true)

internal fun homeStateWithHomeError(
    homeState: HomeUiState,
    cachedPayload: HomePayload?,
    forceRefresh: Boolean,
    errorMessage: String
): HomeUiState =
    if (cachedPayload != null && !forceRefresh) {
        homeState.copy(isLoading = false)
    } else {
        homeState.copy(
            isLoading = false,
            error = errorMessage
        )
    }
