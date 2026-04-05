package top.jlen.vod.ui

internal fun searchStateWithHistory(
    searchState: SearchUiState,
    history: List<String>
): SearchUiState = searchState.copy(history = history)
