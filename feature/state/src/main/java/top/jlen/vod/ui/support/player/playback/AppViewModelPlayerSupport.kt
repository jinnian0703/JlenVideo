package top.jlen.vod.ui

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs
import javax.net.ssl.SSLException
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.CursorPagedVodItems
import top.jlen.vod.data.HomePayload
import top.jlen.vod.data.MembershipPage
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterPage
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfilePage
import top.jlen.vod.data.VodItem


internal fun syncPlayerStateFromFullscreen(
    playerState: PlayerUiState,
    result: FullscreenPlaybackResult
): FullscreenSyncState {
    val safeEpisodes = playerState.episodes
    val previousEpisodeIndex = playerState.selectedEpisodeIndex
    val safeEpisodeIndex = result.episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0))
    val nextState = playerState.copy(
        selectedEpisodeIndex = safeEpisodeIndex,
        resolvedUrl = result.resolvedUrl.ifBlank {
            if (safeEpisodeIndex == previousEpisodeIndex) playerState.resolvedUrl else ""
        },
        useWebPlayer = false,
        isResolving = false,
        resolveError = null,
        playbackSnapshot = result.snapshot
    )
    return FullscreenSyncState(
        playerState = nextState,
        shouldResolveCurrentUrl = result.resolvedUrl.isBlank() && safeEpisodeIndex != previousEpisodeIndex
    )
}

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

