package top.jlen.vod.ui

import top.jlen.vod.data.ResolvedPlayUrl

internal fun playerStateWithoutPlayableSource(playerState: PlayerUiState): PlayerUiState =
    playerState.copy(
        isResolving = false,
        resolvedUrl = "",
        useWebPlayer = false,
        resolveError = "暂无可播放线路",
        diagnostic = playerState.playbackDiagnostic(
            type = "无可用线路",
            title = "暂无可播放线路",
            message = "当前影片没有可用的播放线路。",
            suggestion = "可以稍后刷新详情，或尝试其他影片。"
        )
    )

internal fun playerStateAfterSourceRefresh(playerState: PlayerUiState): PlayerUiState =
    playerState.copy(
        isResolving = false,
        useWebPlayer = false,
        resolveError = null,
        diagnostic = null
    )

internal fun playerStateWithoutEpisode(title: String): PlayerUiState =
    PlayerUiState(
        title = title,
        isResolving = false,
        resolvedUrl = "",
        useWebPlayer = false,
        resolveError = "当前选集暂无播放地址",
        diagnostic = PlaybackDiagnostic(
            type = "无播放地址",
            title = "当前选集暂无播放地址",
            message = "当前选集没有提供可用的播放地址。",
            suggestion = "请切换其他选集，或稍后再试。"
        )
    )

internal fun beginPlayerResolution(playerState: PlayerUiState): PlayerUiState =
    playerState.copy(
        isResolving = true,
        resolvedUrl = "",
        useWebPlayer = false,
        resolveError = null,
        diagnostic = null
    )

internal fun playerStateWithResolvedUrl(
    playerState: PlayerUiState,
    resolved: ResolvedPlayUrl
): PlayerUiState = playerState.copy(
    isResolving = false,
    resolvedUrl = resolved.url,
    useWebPlayer = resolved.useWebPlayer,
    resolveError = if (resolved.url.isBlank()) "解析播放地址失败" else null,
    diagnostic = if (resolved.url.isBlank()) {
        playerState.playbackDiagnostic(
            type = "解析失败",
            title = "解析播放地址失败",
            message = "当前线路没有返回可直接播放的视频地址。",
            suggestion = "请刷新线路，或切换其他线路。"
        )
    } else {
        null
    }
)

internal fun playerStateWithWebFallback(
    playerState: PlayerUiState,
    episodePageUrl: String
): PlayerUiState = playerState.copy(
    isResolving = false,
    resolvedUrl = episodePageUrl,
    useWebPlayer = true,
    resolveError = null,
    diagnostic = playerState.playbackDiagnostic(
        type = "网页线路",
        title = "正在检测网页线路",
        message = "当前线路需要从网页播放器中检测视频地址。",
        suggestion = "如果长时间无响应，请刷新线路或切换其他线路。"
    )
)

internal fun playerStateWithPlaybackSnapshot(
    playerState: PlayerUiState,
    snapshot: PlaybackSnapshot
): PlayerUiState = playerState.copy(playbackSnapshot = snapshot)

internal fun PlayerUiState.playbackDiagnostic(
    type: String,
    title: String,
    message: String,
    suggestion: String
): PlaybackDiagnostic = PlaybackDiagnostic(
    type = type,
    title = title,
    message = message,
    suggestion = suggestion,
    sourceName = sourceName,
    episodeName = episodeName
)
