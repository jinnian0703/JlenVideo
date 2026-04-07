package top.jlen.vod.ui

import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.PlaybackResumeRecord
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
    DetailUiState(isLoading = false, error = "详情不存在或已失效")

internal fun loadedDetailState(
    item: VodItem,
    sources: List<PlaySource>,
    isFavorited: Boolean,
    selectedSourceIndex: Int = 0,
    pendingResumePlayback: PlaybackResumeRecord? = null
): DetailUiState = DetailUiState(
    isLoading = false,
    item = item,
    sources = sources,
    selectedSourceIndex = selectedSourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0)),
    actionMessage = null,
    isActionLoading = false,
    isFavorited = isFavorited,
    pendingResumePlayback = pendingResumePlayback
)

internal fun detailStateWithLoadError(message: String): DetailUiState =
    DetailUiState(
        isLoading = false,
        error = message
    )
