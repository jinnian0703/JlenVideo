package top.jlen.vod.ui

import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.PlaybackResumeBucket
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.VodItem

data class DetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val item: VodItem? = null,
    val sources: List<PlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val isActionLoading: Boolean = false,
    val actionMessage: String? = null,
    val isActionError: Boolean = false,
    val isFavorited: Boolean = false,
    val playbackResumeBucket: PlaybackResumeBucket? = null,
    val pendingResumePlayback: PlaybackResumeRecord? = null
) {
    val selectedSource: PlaySource?
        get() = sources.getOrNull(selectedSourceIndex)
}
