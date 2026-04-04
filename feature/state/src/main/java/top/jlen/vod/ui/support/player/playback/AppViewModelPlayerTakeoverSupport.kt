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
        resolveError = null
    )
}

internal fun applyTakeoverFailure(
    playerState: PlayerUiState,
    message: String
): PlayerUiState = playerState.copy(
    isResolving = false,
    useWebPlayer = false,
    resolveError = message.ifBlank { "璇ョ嚎璺殏涓嶆敮鎸侊紝璇锋崲涓嚎璺瘯璇?" }
)

internal fun hasMeaningfulPlaybackChange(
    currentSnapshot: PlaybackSnapshot,
    incomingSnapshot: PlaybackSnapshot
): Boolean =
    abs(incomingSnapshot.positionMs - currentSnapshot.positionMs) >= UiMotion.SnapshotPositionThresholdMillis ||
        abs(incomingSnapshot.speed - currentSnapshot.speed) > 0.01f ||
        incomingSnapshot.playWhenReady != currentSnapshot.playWhenReady
