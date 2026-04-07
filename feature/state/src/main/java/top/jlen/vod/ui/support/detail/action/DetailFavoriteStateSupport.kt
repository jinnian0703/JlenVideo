package top.jlen.vod.ui

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

internal fun detailStateWithFavoriteRemoved(
    detailState: DetailUiState,
    message: String
): DetailUiState = detailState.copy(
    isActionLoading = false,
    actionMessage = message,
    isActionError = false,
    isFavorited = false
)

internal fun detailStateWithFavoriteRemoveFailure(
    detailState: DetailUiState,
    message: String
): DetailUiState = detailState.copy(
    isActionLoading = false,
    actionMessage = message,
    isActionError = true
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

internal fun detailStateWithoutPendingResume(detailState: DetailUiState): DetailUiState =
    detailState.copy(pendingResumePlayback = null)
