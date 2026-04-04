package top.jlen.vod.ui

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
