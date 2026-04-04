package top.jlen.vod.ui

import top.jlen.vod.data.ResolvedPlayUrl

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
