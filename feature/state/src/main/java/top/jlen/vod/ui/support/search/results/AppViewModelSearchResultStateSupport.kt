package top.jlen.vod.ui

import top.jlen.vod.data.CursorPagedVodItems
import top.jlen.vod.data.VodItem

internal fun shouldReuseSearchResults(
    searchState: SearchUiState,
    normalizedQuery: String
): Boolean =
    searchState.submittedQuery == normalizedQuery &&
        (searchState.firstLoaded || searchState.isLoading || !searchState.error.isNullOrBlank())

internal fun beginSearchState(
    searchState: SearchUiState,
    query: String
): SearchUiState = searchState.copy(
    query = query,
    submittedQuery = query,
    isLoading = true,
    isAppending = false,
    cursor = "",
    hasMore = true,
    firstLoaded = false,
    results = emptyList(),
    appendError = null,
    error = null
)

internal fun blankSearchState(searchState: SearchUiState): SearchUiState = searchState.copy(
    submittedQuery = "",
    isLoading = false,
    isAppending = false,
    results = emptyList(),
    cursor = "",
    hasMore = false,
    firstLoaded = false,
    appendError = null,
    error = "请输入影片名称"
)

internal fun searchStateWithFirstPage(
    searchState: SearchUiState,
    history: List<String>,
    page: CursorPagedVodItems
): SearchUiState = searchState.copy(
    isLoading = false,
    history = history,
    results = page.items,
    cursor = page.nextCursor,
    hasMore = page.hasMore,
    firstLoaded = true,
    error = null
)

internal fun searchStateWithEnrichedResults(
    searchState: SearchUiState,
    results: List<VodItem>
): SearchUiState = searchState.copy(results = results)

internal fun searchStateWithError(
    searchState: SearchUiState,
    errorMessage: String
): SearchUiState = searchState.copy(
    isLoading = false,
    firstLoaded = true,
    error = errorMessage
)

internal fun beginSearchAppend(searchState: SearchUiState): SearchUiState =
    searchState.copy(isAppending = true, appendError = null)

internal fun searchStateWithAppendedPage(
    searchState: SearchUiState,
    page: CursorPagedVodItems
): SearchUiState {
    val mergedResults = (searchState.results + page.items).distinctBy { it.vodId }
    return searchState.copy(
        isAppending = false,
        results = mergedResults,
        cursor = page.nextCursor,
        hasMore = page.hasMore
    )
}

internal fun searchStateWithAppendError(
    searchState: SearchUiState,
    errorMessage: String
): SearchUiState = searchState.copy(
    isAppending = false,
    appendError = errorMessage
)
