package top.jlen.vod.ui

import top.jlen.vod.data.Episode
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterItem
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
    DetailUiState(isLoading = false, error = "未找到影片详情")

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

internal fun resolvingHistoryPlayerState(title: String): PlayerUiState =
    PlayerUiState(
        title = title,
        isResolving = true,
        resolveError = null
    )

internal fun failedHistoryPlayerState(
    title: String,
    message: String
): PlayerUiState = PlayerUiState(
    title = title,
    isResolving = false,
    resolveError = message
)

internal data class HistoryResumeSelection(
    val sourceIndex: Int,
    val episodeIndex: Int
)

internal fun resolveHistoryVodId(item: UserCenterItem): String =
    item.vodId.ifBlank {
        Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
            .find(item.playUrl.ifBlank { item.actionUrl })
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

internal fun resolveHistoryResumeSelection(
    item: UserCenterItem,
    sources: List<PlaySource>
): HistoryResumeSelection {
    val safeSourceIndex = item.sourceIndex.coerceIn(0, (sources.lastIndex).coerceAtLeast(0))
    val episodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
    val matchedEpisodeIndex = episodes.indexOfFirst { episode ->
        item.playUrl.isNotBlank() && episode.url == item.playUrl
    }
    val safeEpisodeIndex = when {
        matchedEpisodeIndex >= 0 -> matchedEpisodeIndex
        item.episodeIndex >= 0 -> item.episodeIndex.coerceIn(0, (episodes.lastIndex).coerceAtLeast(0))
        else -> 0
    }
    return HistoryResumeSelection(
        sourceIndex = safeSourceIndex,
        episodeIndex = safeEpisodeIndex
    )
}

internal fun buildHistoryFallbackSources(item: UserCenterItem, resumeUrl: String): List<PlaySource> =
    listOf(
        PlaySource(
            name = item.sourceName.ifBlank { "继续观看" },
            episodes = listOf(
                Episode(
                    name = item.subtitle.substringBefore("|").trim().ifBlank { "继续观看" },
                    url = resumeUrl
                )
            )
        )
    )
