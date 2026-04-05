package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LegacyStateRuntimeViewModel.legacyUpdateQuery(query: String) {
    updateSearchState(searchStateWithQuery(currentSearchState(), query))
}

internal fun LegacyStateRuntimeViewModel.legacySearchHistory(keyword: String) {
    val normalized = keyword.trim()
    if (normalized.isBlank()) return
    updateSearchState(searchStateWithQuery(currentSearchState(), normalized))
}

internal fun LegacyStateRuntimeViewModel.legacyClearSearchHistory() {
    searchHistoryStore().clear()
    updateSearchState(searchStateWithHistory(currentSearchState(), emptyList()))
}

internal fun LegacyStateRuntimeViewModel.legacySearch(keyword: String) {
    val normalized = keyword.trim()
    updateSearchState(searchStateWithQuery(currentSearchState(), normalized))
    legacyPerformSearch(normalized)
}

internal fun LegacyStateRuntimeViewModel.legacyRefreshHotSearches(forceRefresh: Boolean = false) {
    if (!forceRefresh && (currentSearchState().hotSearchGroups.isNotEmpty() || currentSearchState().isHotSearchLoading)) {
        return
    }
    viewModelScope.launch {
        updateSearchState(startHotSearchLoading(currentSearchState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadHotSearchGroups(forceRefresh = forceRefresh) }
        }.onSuccess { groups ->
            updateSearchState(searchStateWithHotSearchGroups(currentSearchState(), groups))
        }.onFailure { error ->
            updateSearchState(
                searchStateWithHotSearchError(
                    currentSearchState(),
                    toUserFacingMessage(error, "热搜加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModel.legacySearch() {
    legacyPerformSearch(currentSearchState().query)
}

internal fun LegacyStateRuntimeViewModel.legacyGetSearchResultScroll(query: String): SearchResultScrollPosition =
    getSearchResultScrollPosition(query.trim()) ?: SearchResultScrollPosition()

internal fun LegacyStateRuntimeViewModel.legacyUpdateSearchResultScroll(query: String, index: Int, offset: Int) {
    val normalized = query.trim()
    if (normalized.isBlank()) return
    val current = getSearchResultScrollPosition(normalized)
    if (current?.index == index && current.offset == offset) return
    putSearchResultScrollPosition(normalized, SearchResultScrollPosition(index = index, offset = offset))
}

internal fun LegacyStateRuntimeViewModel.legacyEnsureSearchResults(query: String) {
    val normalized = query.trim()
    if (normalized.isBlank()) return
    val current = currentSearchState()
    val alreadyShowingSameQuery = shouldReuseSearchResults(current, normalized)
    if (alreadyShowingSameQuery) {
        if (current.query != normalized) {
            updateSearchState(searchStateWithQuery(current, normalized))
        }
        return
    }
    legacyPerformSearch(normalized)
}

internal fun LegacyStateRuntimeViewModel.legacyPerformSearch(keyword: String) {
    val query = keyword.trim()
    if (query.isBlank()) {
        currentSearchJob()?.cancel()
        currentSearchEnrichJob()?.cancel()
        updateSearchState(blankSearchState(currentSearchState()))
        return
    }
    currentSearchJob()?.cancel()
    currentSearchEnrichJob()?.cancel()
    val requestVersion = nextSearchRequestVersion()
    updateSearchState(beginSearchState(currentSearchState(), query))
    replaceSearchJob(viewModelScope.launch searchLaunch@{
        try {
            val firstPage = withContext(Dispatchers.IO) {
                legacyRepository().searchCursor(keyword = query, cursor = "")
            }
            if (requestVersion != currentSearchRequestVersion()) return@searchLaunch

            searchHistoryStore().save(query)
            updateSearchState(
                searchStateWithFirstPage(
                    searchState = currentSearchState(),
                    history = searchHistoryStore().load(),
                    page = firstPage
                )
            )

            if (firstPage.items.isNotEmpty()) {
                replaceSearchEnrichJob(viewModelScope.launch searchEnrichLaunch@{
                    val enrichedResults = withContext(Dispatchers.IO) {
                        legacyRepository().enrichSearchResults(firstPage.items, limit = 8)
                    }
                    if (requestVersion != currentSearchRequestVersion()) return@searchEnrichLaunch
                    updateSearchState(searchStateWithEnrichedResults(currentSearchState(), enrichedResults))
                })
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (requestVersion != currentSearchRequestVersion()) return@searchLaunch
            updateSearchState(
                searchStateWithError(
                    currentSearchState(),
                    toUserFacingMessage(error, "搜索失败")
                )
            )
        }
    })
}

internal fun LegacyStateRuntimeViewModel.legacyLoadMoreSearchResults() {
    val query = currentSearchState().submittedQuery.trim()
    if (
        query.isBlank() ||
        currentSearchState().isLoading ||
        currentSearchState().isAppending ||
        !currentSearchState().hasMore
    ) {
        return
    }

    viewModelScope.launch {
        updateSearchState(beginSearchAppend(currentSearchState()))
        runCatching {
            withContext(Dispatchers.IO) {
                legacyRepository().searchCursor(keyword = query, cursor = currentSearchState().cursor)
            }
        }.onSuccess { page ->
            updateSearchState(searchStateWithAppendedPage(currentSearchState(), page))
        }.onFailure { error ->
            updateSearchState(
                searchStateWithAppendError(
                    currentSearchState(),
                    toUserFacingMessage(error, "继续加载搜索结果失败")
                )
            )
        }
    }
}
