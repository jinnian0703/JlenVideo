package top.jlen.vod.ui

import top.jlen.vod.data.SearchSuggestionItem

internal fun clearSearchSuggestions(searchState: SearchUiState): SearchUiState = searchState.copy(
    isSuggestLoading = false,
    suggestSubmittedQuery = "",
    suggestions = emptyList(),
    suggestError = null
)

internal fun beginSearchSuggestionLoading(
    searchState: SearchUiState,
    query: String
): SearchUiState = searchState.copy(
    isSuggestLoading = true,
    suggestSubmittedQuery = query,
    suggestError = null
)

internal fun searchStateWithSuggestions(
    searchState: SearchUiState,
    query: String,
    suggestions: List<SearchSuggestionItem>
): SearchUiState = searchState.copy(
    isSuggestLoading = false,
    suggestSubmittedQuery = query,
    suggestions = suggestions,
    suggestError = null
)

internal fun searchStateWithSuggestionError(
    searchState: SearchUiState,
    query: String,
    errorMessage: String
): SearchUiState = searchState.copy(
    isSuggestLoading = false,
    suggestSubmittedQuery = query,
    suggestions = emptyList(),
    suggestError = errorMessage
)
