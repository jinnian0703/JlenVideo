package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.AppleCmsCategory

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshHome(forceRefresh: Boolean = false) {
    refreshNotices(forceRefresh = forceRefresh)
    val cachedPayload = if (!forceRefresh) {
        legacyRepository().peekHomePayload(allowStale = true)
    } else {
        null
    }
    updateHomeState(loadingHomeState(cachedPayload))
    viewModelScope.launch {
        val shouldRefreshFromNetwork = forceRefresh || cachedPayload != null
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadHome(forceRefresh = shouldRefreshFromNetwork)
            }
        }.onSuccess { payload ->
            updateHomeState(homeStateFromPayload(payload))
        }.onFailure { error ->
            updateHomeState(
                homeStateWithHomeError(
                    homeState = currentHomeState(),
                    cachedPayload = cachedPayload,
                    forceRefresh = forceRefresh,
                    errorMessage = toUserFacingMessage(error, "首页加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshHomeAndClearCaches() {
    viewModelScope.launch {
        withContext(Dispatchers.IO) { legacyRepository().clearRuntimeCaches() }
        clearSearchResultScrollPositions()
        legacyRefreshHome(forceRefresh = true)
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectCategory(
    category: AppleCmsCategory,
    forceRefresh: Boolean = false
) {
    val sameCategory = category.typeId == currentHomeState().selectedCategory?.typeId
    if (!forceRefresh && sameCategory && currentHomeState().categoryFirstLoaded) {
        return
    }
    legacyLoadCategoryContent(
        category = category,
        filters = emptyMap()
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpdateCategoryFilter(key: String, value: String) {
    val category = currentHomeState().selectedCategory ?: return
    val normalizedKey = key.trim()
    if (normalizedKey.isBlank()) return
    val normalizedValue = value.trim()
    val updatedFilters = currentHomeState().selectedCategoryFilters.toMutableMap().apply {
        if (normalizedValue.isBlank()) {
            remove(normalizedKey)
        } else {
            put(normalizedKey, normalizedValue)
        }
    }
    if (updatedFilters == currentHomeState().selectedCategoryFilters && currentHomeState().categoryFirstLoaded) {
        return
    }
    legacyLoadCategoryContent(
        category = category,
        filters = updatedFilters
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadCategoryContent(
    category: AppleCmsCategory,
    filters: Map<String, String>
) {
    viewModelScope.launch {
        updateHomeState(beginCategoryLoadState(currentHomeState(), category, filters))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadCategoryCursorPage(
                    typeId = category.typeId,
                    cursor = "",
                    filters = filters
                )
            }
        }.onSuccess { payload ->
            updateHomeState(homeStateWithCategoryPage(currentHomeState(), payload))
        }.onFailure { error ->
            updateHomeState(
                homeStateWithCategoryError(
                    currentHomeState(),
                    toUserFacingMessage(error, "分类加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadMoreHome() {
    if (currentHomeState().isHomeAppending) {
        return
    }
    if (currentHomeState().homeVisibleCount < currentHomeState().latest.size) {
        updateHomeState(homeStateWithExpandedHomeVisibleCount(currentHomeState()))
        return
    }
    if (!currentHomeState().hasMoreHomeItems) return
    viewModelScope.launch {
        val previousVisibleCount = currentHomeState().homeVisibleCount
        updateHomeState(beginHomeAppendState(currentHomeState()))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadLatestCursorPage(cursor = currentHomeState().homeCursor)
            }
        }.onSuccess { payload ->
            updateHomeState(
                homeStateWithAppendedHomePage(
                    currentHomeState(),
                    previousVisibleCount,
                    payload
                )
            )
        }.onFailure { error ->
            updateHomeState(
                homeStateWithHomeAppendError(
                    currentHomeState(),
                    toUserFacingMessage(error, "继续加载首页失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadMoreCategory() {
    if (currentHomeState().isCategoryAppending) {
        return
    }
    if (currentHomeState().categoryVisibleCount < currentHomeState().categoryVideos.size) {
        updateHomeState(homeStateWithExpandedCategoryVisibleCount(currentHomeState()))
        return
    }
    if (!currentHomeState().hasMoreCategoryItems) return
    val category = currentHomeState().selectedCategory ?: return
    viewModelScope.launch {
        val previousVisibleCount = currentHomeState().categoryVisibleCount
        updateHomeState(beginCategoryAppendState(currentHomeState()))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadCategoryCursorPage(
                    typeId = category.typeId,
                    cursor = currentHomeState().categoryCursor,
                    filters = currentHomeState().selectedCategoryFilters
                )
            }
        }.onSuccess { payload ->
            updateHomeState(
                homeStateWithAppendedCategoryPage(
                    currentHomeState(),
                    previousVisibleCount,
                    payload
                )
            )
        }.onFailure { error ->
            updateHomeState(
                homeStateWithCategoryAppendError(
                    currentHomeState(),
                    toUserFacingMessage(error, "继续加载分类失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshCategoryTab(forceRefresh: Boolean = false) {
    val selectedCategory = currentHomeState().selectedCategory ?: currentHomeState().categories.firstOrNull() ?: return
    if (forceRefresh || currentHomeState().selectedCategoryFilters.isNotEmpty()) {
        legacyLoadCategoryContent(
            category = selectedCategory,
            filters = currentHomeState().selectedCategoryFilters
        )
    } else {
        legacySelectCategory(selectedCategory, forceRefresh = forceRefresh)
    }
}
