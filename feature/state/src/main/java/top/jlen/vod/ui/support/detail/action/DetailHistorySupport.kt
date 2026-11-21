package top.jlen.vod.ui

import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterItem

internal fun resolvingHistoryPlayerState(title: String): PlayerUiState =
    PlayerUiState(
        title = title,
        isResolving = true,
        resolveError = null,
        diagnostic = null
    )

internal fun failedHistoryPlayerState(
    title: String,
    message: String
): PlayerUiState = PlayerUiState(
    title = title,
    isResolving = false,
    resolveError = message,
    diagnostic = PlaybackDiagnostic(
        type = "播放记录",
        title = message.ifBlank { "无法恢复播放记录" },
        message = "当前播放记录无法恢复到可播放状态。",
        suggestion = "请返回影片详情页重新选择线路和选集。"
    )
)

fun resolveHistoryVodId(item: UserCenterItem): String =
    item.vodId.ifBlank {
        sequenceOf(item.playUrl, item.actionUrl).firstNotNullOfOrNull { url ->
            val match = historyPlayPath.find(url) ?: historyDetailPath.find(url)
            match?.groupValues?.get(1)
        }.orEmpty()
    }

private val historyPlayPath = Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
private val historyDetailPath = Regex("""/voddetail/([^/.?#]+)(?:\.html)?/?(?:\?.*)?$""")

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
