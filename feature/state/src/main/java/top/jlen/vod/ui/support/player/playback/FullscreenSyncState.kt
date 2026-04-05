package top.jlen.vod.ui

internal data class FullscreenSyncState(
    val playerState: PlayerUiState,
    val shouldResolveCurrentUrl: Boolean
)
