package top.jlen.vod.ui

import top.jlen.vod.data.Episode
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterItem

internal fun resolvingHistoryPlayerState(title: String): PlayerUiState =
    PlayerUiState(
        title = title,
        isResolving = true,
        resolveError = null
    )

internal fun failedHistoryPlayerState(
    title: String,
    message: String
): PlayerUiState = PlayerUiState(
    title = title,
    isResolving = false,
    resolveError = message
)

internal fun resolveHistoryVodId(item: UserCenterItem): String =
    item.vodId.ifBlank {
        Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
            .find(item.playUrl.ifBlank { item.actionUrl })
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

internal fun resolveHistoryResumeSelection(
    item: UserCenterItem,
    sources: List<PlaySource>
): HistoryResumeSelection {
    val safeSourceIndex = item.sourceIndex.coerceIn(0, (sources.lastIndex).coerceAtLeast(0))
    val episodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
    val matchedEpisodeIndex = episodes.indexOfFirst { episode ->
        item.playUrl.isNotBlank() && episode.url == item.playUrl
    }
    val safeEpisodeIndex = when {
        matchedEpisodeIndex >= 0 -> matchedEpisodeIndex
        item.episodeIndex >= 0 -> item.episodeIndex.coerceIn(0, (episodes.lastIndex).coerceAtLeast(0))
        else -> 0
    }
    return HistoryResumeSelection(
        sourceIndex = safeSourceIndex,
        episodeIndex = safeEpisodeIndex
    )
}

internal fun buildHistoryFallbackSources(item: UserCenterItem, resumeUrl: String): List<PlaySource> =
    listOf(
        PlaySource(
            name = item.sourceName.ifBlank { "继续观看" },
            episodes = listOf(
                Episode(
                    name = item.subtitle.substringBefore("|").trim().ifBlank { "继续观看" },
                    url = resumeUrl
                )
            )
        )
    )
