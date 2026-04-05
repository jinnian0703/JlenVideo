package top.jlen.vod.ui

internal data class RefreshedPlayerState(
    val playerState: PlayerUiState,
    val sourcesChanged: Boolean,
    val episodeChanged: Boolean
)
