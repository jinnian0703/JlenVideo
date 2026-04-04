package top.jlen.vod.ui

import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.VodItem

internal fun playerStateWithRefreshedSources(
    playerState: PlayerUiState,
    detailItem: VodItem,
    refreshedSources: List<PlaySource>,
    currentSourceName: String,
    currentEpisodeUrl: String,
    currentEpisodeName: String
): RefreshedPlayerState {
    val previousSources = playerState.sources
    val resolvedSourceIndex = when {
        refreshedSources.isEmpty() -> 0
        else -> {
            val matchedByName = refreshedSources.indexOfFirst { it.name == currentSourceName }
            when {
                matchedByName >= 0 -> matchedByName
                else -> playerState.selectedSourceIndex.coerceIn(0, refreshedSources.lastIndex)
            }
        }
    }
    val resolvedEpisodes = refreshedSources.getOrNull(resolvedSourceIndex)?.episodes.orEmpty()
    val resolvedEpisodeIndex = when {
        resolvedEpisodes.isEmpty() -> 0
        else -> {
            val matchedByUrl = resolvedEpisodes.indexOfFirst { it.url == currentEpisodeUrl }
            val matchedByName = resolvedEpisodes.indexOfFirst { it.name == currentEpisodeName }
            when {
                matchedByUrl >= 0 -> matchedByUrl
                matchedByName >= 0 -> matchedByName
                else -> playerState.selectedEpisodeIndex.coerceIn(0, resolvedEpisodes.lastIndex)
            }
        }
    }
    val refreshedEpisodeUrl = resolvedEpisodes.getOrNull(resolvedEpisodeIndex)?.url.orEmpty()
    val sourcesChanged = previousSources != refreshedSources
    val episodeChanged = refreshedEpisodeUrl != currentEpisodeUrl
    return RefreshedPlayerState(
        playerState = playerState.copy(
            title = detailItem.displayTitle,
            item = detailItem,
            sources = refreshedSources,
            selectedSourceIndex = resolvedSourceIndex,
            selectedEpisodeIndex = resolvedEpisodeIndex,
            playbackSnapshot = if (episodeChanged) PlaybackSnapshot() else playerState.playbackSnapshot,
            resolveError = if (refreshedSources.isEmpty()) "暂无可播放线路" else null
        ),
        sourcesChanged = sourcesChanged,
        episodeChanged = episodeChanged
    )
}
