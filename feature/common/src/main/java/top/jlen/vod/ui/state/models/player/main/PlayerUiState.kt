package top.jlen.vod.ui

import top.jlen.vod.data.Episode
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.VodItem

data class PlayerUiState(
    val title: String = "",
    val item: VodItem? = null,
    val sources: List<PlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val resolvedUrl: String = "",
    val isResolving: Boolean = false,
    val useWebPlayer: Boolean = false,
    val resolveError: String? = null,
    val playbackSnapshot: PlaybackSnapshot = PlaybackSnapshot()
) {
    val currentSource: PlaySource?
        get() = sources.getOrNull(selectedSourceIndex)

    val sourceName: String
        get() = currentSource?.name.orEmpty()

    val episodes: List<Episode>
        get() = currentSource?.episodes.orEmpty()

    val currentEpisode: Episode?
        get() = episodes.getOrNull(selectedEpisodeIndex)

    val episodeName: String
        get() = currentEpisode?.name.orEmpty()

    val episodePageUrl: String
        get() = currentEpisode?.url.orEmpty()

    val playUrl: String
        get() = resolvedUrl.ifBlank { episodePageUrl }

    val hasNextEpisode: Boolean
        get() = selectedEpisodeIndex < episodes.lastIndex
}
