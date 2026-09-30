package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.ALL_LIBRARY_CATEGORY
import top.jlen.vod.data.AppleCmsCategory

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshHome(forceRefresh: Boolean = false) {
    refreshNotices(forceRefresh = forceRefresh)
    val cachedPayload = if (!forceRefresh) {
        legacyRepository().peekHomePayload(allowStale = true)
    } else {
        null
    }
    updateHomeState(loadingHomeState(cachedPayload, currentHomeState()))
    viewModelScope.launch {
        val shouldRefreshFromNetwork = forceRefresh || cachedPayload != null
        runStateCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadHome(forceRefresh = shouldRefreshFromNetwork)
            }
        }.onSuccess { payload ->
            updateHomeState(homeStateKeepingLibrary(homeStateFromPayload(payload), currentHomeState()))
            legacyScheduleHomePreviewEnrich()
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
    if (currentHomeState().isLoading) return
    // 普通刷新只强制重取首页/公告/分类入口；完整清理由设置页负责。
    // 保留旧缓存作为失败兜底，也保留搜索位置和其他页面的有效缓存。
    legacyRefreshHome(forceRefresh = true)
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
    val requestVersion = libraryRequests.begin()
    val requestedFilters = filters.toMap()
    currentCategoryPreviewEnrichJob()?.cancel()
    updateHomeState(beginCategoryLoadState(currentHomeState(), category, requestedFilters))
    viewModelScope.launch {
        runStateCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadCategoryCursorPage(
                    typeId = category.typeId,
                    cursor = "",
                    filters = requestedFilters
                )
            }
        }.onSuccess { payload ->
            if (!libraryRequests.isCurrent(requestVersion)) return@onSuccess
            updateHomeState(homeStateWithCategoryPage(currentHomeState(), payload))
            legacyScheduleCategoryPreviewEnrich()
        }.onFailure { error ->
            if (!libraryRequests.isCurrent(requestVersion)) return@onFailure
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
    if (currentHomeState().isHomeAppending || currentHomeState().isLoading) {
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
        runStateCatching {
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
            legacyScheduleHomePreviewEnrich()
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
    val snapshot = currentHomeState()
    if (snapshot.isCategoryAppending || snapshot.isCategoryLoading || snapshot.isLoading) return
    if (snapshot.categoryVisibleCount < snapshot.categoryVideos.size) {
        updateHomeState(homeStateWithExpandedCategoryVisibleCount(snapshot))
        return
    }
    if (!snapshot.hasMoreCategoryItems) return
    val category = snapshot.selectedCategory ?: return
    val requestVersion = libraryRequests.version
    val previousVisibleCount = snapshot.categoryVisibleCount
    updateHomeState(beginCategoryAppendState(snapshot))
    viewModelScope.launch {
        runStateCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().loadCategoryCursorPage(
                    typeId = category.typeId,
                    cursor = snapshot.categoryCursor,
                    filters = snapshot.selectedCategoryFilters
                )
            }
        }.onSuccess { payload ->
            if (!libraryRequests.isCurrent(requestVersion)) return@onSuccess
            updateHomeState(homeStateWithAppendedCategoryPage(currentHomeState(), previousVisibleCount, payload))
            legacyScheduleCategoryPreviewEnrich()
        }.onFailure { error ->
            if (!libraryRequests.isCurrent(requestVersion)) return@onFailure
            updateHomeState(
                homeStateWithCategoryAppendError(currentHomeState(), toUserFacingMessage(error, "继续加载分类失败"))
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshCategoryTab(forceRefresh: Boolean = false) {
    val selectedCategory = currentHomeState().selectedCategory ?: currentHomeState().categories.firstOrNull()?.let { ALL_LIBRARY_CATEGORY }
    if (selectedCategory == null) {
        // 首次首页加载失败时分类数据同样为空，重试必须重新请求分类入口。
        legacyRefreshHome(forceRefresh = forceRefresh)
        return
    }
    if (forceRefresh || currentHomeState().selectedCategoryFilters.isNotEmpty()) {
        legacyLoadCategoryContent(
            category = selectedCategory,
            filters = currentHomeState().selectedCategoryFilters
        )
    } else {
        legacySelectCategory(selectedCategory, forceRefresh = forceRefresh)
    }
}

private fun LegacyStateRuntimeViewModelCore.legacyScheduleHomePreviewEnrich() {
    currentHomePreviewEnrichJob()?.cancel()
    val featuredSnapshot = currentHomeState().featured
    val latestSnapshot = currentHomeState().latest
    replaceHomePreviewEnrichJob(viewModelScope.launch {
        delay(180)
        val enrichedFeatured = withContext(Dispatchers.IO) {
            legacyRepository().enrichPreviewDisplayMetadata(featuredSnapshot, limit = 4)
        }
        val enrichedLatest = withContext(Dispatchers.IO) {
            legacyRepository().enrichPreviewDisplayMetadata(latestSnapshot, limit = 8)
        }
        val current = currentHomeState()
        if (current.featured != featuredSnapshot && current.latest != latestSnapshot) return@launch
        updateHomeState(
            current.copy(
                featured = if (current.featured == featuredSnapshot) enrichedFeatured else current.featured,
                latest = if (current.latest == latestSnapshot) enrichedLatest else current.latest
            )
        )
    })
}

private fun LegacyStateRuntimeViewModelCore.legacyScheduleCategoryPreviewEnrich() {
    currentCategoryPreviewEnrichJob()?.cancel()
    val selectedTypeId = currentHomeState().selectedCategory?.typeId.orEmpty()
    val categorySnapshot = currentHomeState().categoryVideos
    replaceCategoryPreviewEnrichJob(viewModelScope.launch {
        delay(180)
        val enriched = withContext(Dispatchers.IO) {
            legacyRepository().enrichPreviewDisplayMetadata(categorySnapshot, limit = 8)
        }
        val current = currentHomeState()
        if (current.selectedCategory?.typeId.orEmpty() != selectedTypeId) return@launch
        if (current.categoryVideos != categorySnapshot) return@launch
        updateHomeState(current.copy(categoryVideos = enriched))
    })
}
