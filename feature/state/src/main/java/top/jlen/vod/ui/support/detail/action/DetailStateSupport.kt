package top.jlen.vod.ui

import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.VodItem

internal fun beginDetailLoad(
    detailState: DetailUiState,
    keepCurrentContent: Boolean
): DetailUiState = if (keepCurrentContent) {
    detailState.copy(isLoading = true, error = null)
} else {
    DetailUiState(isLoading = true, error = null)
}

internal fun missingDetailState(): DetailUiState =
    DetailUiState(isLoading = false, error = "鏈壘鍒板奖鐗囪鎯?")

internal fun loadedDetailState(
    item: VodItem,
    sources: List<PlaySource>,
    isFavorited: Boolean
): DetailUiState = DetailUiState(
    isLoading = false,
    item = item,
    sources = sources,
    selectedSourceIndex = 0,
    actionMessage = null,
    isActionLoading = false,
    isFavorited = isFavorited
)

internal fun detailStateWithLoadError(message: String): DetailUiState =
    DetailUiState(
        isLoading = false,
        error = message
    )

internal fun beginDetailFavoriteAction(detailState: DetailUiState): DetailUiState =
    detailState.copy(
        isActionLoading = true,
        actionMessage = null,
        isActionError = false
    )

internal fun detailStateWithFavoriteSuccess(
    detailState: DetailUiState,
    message: String
): DetailUiState = detailState.copy(
    isActionLoading = false,
    actionMessage = message,
    isActionError = false,
    isFavorited = true
)

internal fun detailStateWithFavoriteFailure(
    detailState: DetailUiState,
    message: String,
    isDuplicate: Boolean
): DetailUiState = detailState.copy(
    isActionLoading = false,
    actionMessage = message,
    isActionError = !isDuplicate,
    isFavorited = isDuplicate || detailState.isFavorited
)

internal fun detailStateWithoutActionMessage(detailState: DetailUiState): DetailUiState =
    detailState.copy(actionMessage = null, isActionError = false)

internal fun detailStateWithActionMessage(
    detailState: DetailUiState,
    message: String,
    isError: Boolean
): DetailUiState = detailState.copy(
    actionMessage = message,
    isActionError = isError
)

internal fun detailStateWithoutFavorite(detailState: DetailUiState): DetailUiState =
    detailState.copy(isFavorited = false)

internal fun detailStateWithSelectedSource(
    detailState: DetailUiState,
    index: Int
): DetailUiState = detailState.copy(selectedSourceIndex = index)
