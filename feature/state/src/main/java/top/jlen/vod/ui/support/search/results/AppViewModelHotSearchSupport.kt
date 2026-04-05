package top.jlen.vod.ui

internal fun startHotSearchLoading(searchState: SearchUiState): SearchUiState =
    searchState.copy(isHotSearchLoading = true, hotSearchError = null)

internal fun searchStateWithHotSearchGroups(
    searchState: SearchUiState,
    groups: List<top.jlen.vod.data.HotSearchGroup>
): SearchUiState = searchState.copy(
    isHotSearchLoading = false,
    hotSearchGroups = groups,
    hotSearchError = null
)

internal fun searchStateWithHotSearchError(
    searchState: SearchUiState,
    errorMessage: String
): SearchUiState = searchState.copy(
    isHotSearchLoading = false,
    hotSearchError = errorMessage
)
