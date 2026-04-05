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
    DetailUiState(isLoading = false, error = "閺堫亝澹橀崚鏉垮閻楀洩顕涢幆?")

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
