package top.jlen.vod.ui

data class PlaybackSnapshot(
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val playWhenReady: Boolean = true,
    val durationMs: Long = 0L
)
