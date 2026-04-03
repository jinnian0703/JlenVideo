package top.jlen.vod.ui

data class FullscreenPlaybackResult(
    val episodeIndex: Int,
    val resolvedUrl: String,
    val snapshot: PlaybackSnapshot
)
