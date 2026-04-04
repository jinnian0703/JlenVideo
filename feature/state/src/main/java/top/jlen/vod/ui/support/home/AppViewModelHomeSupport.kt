package top.jlen.vod.ui

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs
import javax.net.ssl.SSLException
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.CursorPagedVodItems
import top.jlen.vod.data.HomePayload
import top.jlen.vod.data.MembershipPage
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterPage
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfilePage
import top.jlen.vod.data.VodItem


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
