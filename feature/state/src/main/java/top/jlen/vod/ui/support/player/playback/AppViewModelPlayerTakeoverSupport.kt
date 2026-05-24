package top.jlen.vod.ui

import kotlin.math.abs

internal fun applyDetectedStream(
    playerState: PlayerUiState,
    streamUrl: String
): PlayerUiState? {
    if (streamUrl.isBlank()) return null
    return playerState.copy(
        resolvedUrl = streamUrl,
        isResolving = false,
        useWebPlayer = false,
        resolveError = null,
        diagnostic = null
    )
}

internal fun applyTakeoverFailure(
    playerState: PlayerUiState,
    message: String
): PlayerUiState = playerState.copy(
    isResolving = false,
    useWebPlayer = false,
    resolveError = message.ifBlank { "未检测到可播放视频" },
    diagnostic = playerState.playbackDiagnostic(
        type = "网页检测失败",
        title = "未检测到可播放视频",
        message = message.ifBlank { "当前网页线路没有检测到可接管播放的视频内容。" },
        suggestion = "请刷新线路，或切换其他线路。"
    )
)

internal fun hasMeaningfulPlaybackChange(
    currentSnapshot: PlaybackSnapshot,
    incomingSnapshot: PlaybackSnapshot
): Boolean =
    abs(incomingSnapshot.positionMs - currentSnapshot.positionMs) >= UiMotion.SnapshotPositionThresholdMillis ||
        abs(incomingSnapshot.speed - currentSnapshot.speed) > 0.01f ||
        incomingSnapshot.playWhenReady != currentSnapshot.playWhenReady
