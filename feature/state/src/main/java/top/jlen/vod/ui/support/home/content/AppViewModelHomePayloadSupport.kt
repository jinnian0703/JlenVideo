package top.jlen.vod.ui

import top.jlen.vod.data.ALL_LIBRARY_CATEGORY
import top.jlen.vod.data.HomePayload

internal fun homeStateFromPayload(payload: HomePayload): HomeUiState {
    // 首页最新列表来自不限制分类的游标接口，可作为“全部”的首屏。
    // 不复用旧缓存中的 categoryVideos，否则会把首个分类误当成全部。
    val categoryVideos = payload.latest
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
        selectedCategory = ALL_LIBRARY_CATEGORY,
        categoryVideos = categoryVideos,
        categoryVisibleCount = categoryVideos.initialGridVisibleCount(),
        categoryCursor = payload.latestCursor,
        hasMoreCategoryItems = payload.latestHasMore,
        categoryFirstLoaded = categoryVideos.isNotEmpty(),
        error = null
    )
}

internal fun loadingHomeState(cachedPayload: HomePayload?, currentState: HomeUiState): HomeUiState {
    // 下拉刷新保留已展示的列表和滚动位置，首次加载才使用空状态。
    val content = if (currentState.homeFirstLoaded) currentState else {
        cachedPayload?.let(::homeStateFromPayload) ?: currentState
    }
    return content.copy(isLoading = true, isHomeAppending = false, error = null, homeAppendError = null)
}

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
