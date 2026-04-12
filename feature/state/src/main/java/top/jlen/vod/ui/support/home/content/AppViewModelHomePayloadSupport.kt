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

internal fun homeStateWithEnrichedPayload(
    homeState: HomeUiState,
    payload: HomePayload
): HomeUiState {
    val selectedCategory = homeState.selectedCategory
        ?.let { current -> payload.categories.firstOrNull { it.typeId == current.typeId } }
        ?: payload.selectedCategory
        ?: payload.categories.firstOrNull()
    val categoryVideos = payload.categoryVideos
    return homeState.copy(
        slides = payload.slides,
        hot = payload.hot,
        featured = payload.featured,
        latest = payload.latest,
        sections = payload.sections,
        homeCursor = payload.latestCursor,
        hasMoreHomeItems = payload.latestHasMore,
        categories = payload.categories,
        selectedCategory = selectedCategory,
        categoryVideos = categoryVideos,
        homeVisibleCount = if (homeState.homeVisibleCount > 0) {
            homeState.homeVisibleCount.coerceIn(0, payload.latest.size)
        } else {
            payload.latest.initialGridVisibleCount()
        },
        categoryCursor = payload.categoryCursor,
        hasMoreCategoryItems = payload.categoryHasMore,
        categoryVisibleCount = if (homeState.categoryVisibleCount > 0) {
            homeState.categoryVisibleCount.coerceIn(0, categoryVideos.size)
        } else {
            categoryVideos.initialGridVisibleCount()
        }
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
