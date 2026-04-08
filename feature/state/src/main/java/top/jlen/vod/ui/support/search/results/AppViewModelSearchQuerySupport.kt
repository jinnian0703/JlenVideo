package top.jlen.vod.ui

internal fun searchStateWithQuery(searchState: SearchUiState, query: String): SearchUiState =
    searchState.copy(query = query, error = null, suggestError = null)
