package top.jlen.vod.ui

import kotlin.math.abs
import top.jlen.vod.data.PlaySource
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
