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
import top.jlen.vod.data.ResolvedPlayUrl
import top.jlen.vod.data.UserCenterPage
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfilePage
import top.jlen.vod.data.VodItem


internal fun resolveHeartbeatVodId(playerState: PlayerUiState): String {
    val item = playerState.item
    return item?.siteVodId
        .orEmpty()
        .ifBlank { item?.vodId?.takeIf { it.all(Char::isDigit) }.orEmpty() }
        .ifBlank {
            Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
                .find(playerState.episodePageUrl)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .takeIf { it.all(Char::isDigit) }
                .orEmpty()
        }
}

internal fun buildPlayerState(
    title: String,
    item: VodItem?,
    sources: List<PlaySource>,
    sourceIndex: Int,
    episodeIndex: Int
): PlayerUiState {
    val safeSourceIndex = sourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
    val safeEpisodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
    return PlayerUiState(
        title = title,
        item = item,
        sources = sources,
        selectedSourceIndex = safeSourceIndex,
        selectedEpisodeIndex = episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0)),
        playbackSnapshot = PlaybackSnapshot()
    )
}

internal fun updatePlayerEpisodeSelection(
    playerState: PlayerUiState,
    index: Int
): PlayerUiState? {
    val currentEpisodes = playerState.currentSource?.episodes.orEmpty()
    if (currentEpisodes.isEmpty()) return null
    return playerState.copy(
        selectedEpisodeIndex = index.coerceIn(0, currentEpisodes.lastIndex),
        playbackSnapshot = PlaybackSnapshot()
    )
}

internal fun updatePlayerSourceSelection(
    playerState: PlayerUiState,
    index: Int
): PlayerUiState? {
    if (playerState.sources.isEmpty()) return null
    val safeIndex = index.coerceIn(0, playerState.sources.lastIndex)
    val targetEpisodes = playerState.sources.getOrNull(safeIndex)?.episodes.orEmpty()
    val preservedEpisodeIndex = playerState.selectedEpisodeIndex
        .coerceIn(0, (targetEpisodes.size - 1).coerceAtLeast(0))
    return playerState.copy(
        selectedSourceIndex = safeIndex,
        selectedEpisodeIndex = preservedEpisodeIndex,
        playbackSnapshot = PlaybackSnapshot()
    )
}

internal fun applyDetectedStream(
    playerState: PlayerUiState,
    streamUrl: String
): PlayerUiState? {
    if (streamUrl.isBlank()) return null
    return playerState.copy(
        resolvedUrl = streamUrl,
        isResolving = false,
        useWebPlayer = false,
        resolveError = null
    )
}

internal fun applyTakeoverFailure(
    playerState: PlayerUiState,
    message: String
): PlayerUiState = playerState.copy(
    isResolving = false,
    useWebPlayer = false,
    resolveError = message.ifBlank { "该线路暂不支持，请换个线路试试" }
)

internal fun hasMeaningfulPlaybackChange(
    currentSnapshot: PlaybackSnapshot,
    incomingSnapshot: PlaybackSnapshot
): Boolean =
    abs(incomingSnapshot.positionMs - currentSnapshot.positionMs) >= UiMotion.SnapshotPositionThresholdMillis ||
        abs(incomingSnapshot.speed - currentSnapshot.speed) > 0.01f ||
        incomingSnapshot.playWhenReady != currentSnapshot.playWhenReady

internal data class FullscreenSyncState(
    val playerState: PlayerUiState,
    val shouldResolveCurrentUrl: Boolean
)

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

internal data class RefreshedPlayerState(
    val playerState: PlayerUiState,
    val sourcesChanged: Boolean,
    val episodeChanged: Boolean
)

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

internal fun playerStateWithoutPlayableSource(playerState: PlayerUiState): PlayerUiState =
    playerState.copy(
        isResolving = false,
        resolvedUrl = "",
        useWebPlayer = false
    )

internal fun playerStateAfterSourceRefresh(playerState: PlayerUiState): PlayerUiState =
    playerState.copy(
        isResolving = false,
        useWebPlayer = false,
        resolveError = null
    )

internal fun playerStateWithoutEpisode(title: String): PlayerUiState =
    PlayerUiState(
        title = title,
        isResolving = false,
        resolvedUrl = "",
        useWebPlayer = false,
        resolveError = "暂无可播放地址"
    )

internal fun beginPlayerResolution(playerState: PlayerUiState): PlayerUiState =
    playerState.copy(
        isResolving = true,
        resolvedUrl = "",
        useWebPlayer = false,
        resolveError = null
    )

internal fun playerStateWithResolvedUrl(
    playerState: PlayerUiState,
    resolved: ResolvedPlayUrl
): PlayerUiState = playerState.copy(
    isResolving = false,
    resolvedUrl = resolved.url,
    useWebPlayer = resolved.useWebPlayer,
    resolveError = if (resolved.url.isBlank()) "解析播放地址失败" else null
)

internal fun playerStateWithWebFallback(
    playerState: PlayerUiState,
    episodePageUrl: String
): PlayerUiState = playerState.copy(
    isResolving = false,
    resolvedUrl = episodePageUrl,
    useWebPlayer = true,
    resolveError = "该线路暂不支持，请换个线路试试"
)

internal fun playerStateWithPlaybackSnapshot(
    playerState: PlayerUiState,
    snapshot: PlaybackSnapshot
): PlayerUiState = playerState.copy(playbackSnapshot = snapshot)
