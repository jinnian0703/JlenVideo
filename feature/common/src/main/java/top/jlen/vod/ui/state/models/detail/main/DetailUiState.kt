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
    val pendingResumePlayback: PlaybackResumeRecord? = null,
    // 当前详情状态对应的路由 vodId；加载中/失败/不存在时 item 为空，路由层据此判断状态归属
    val requestedVodId: String? = null
) {
    val selectedSource: PlaySource?
        get() = sources.getOrNull(selectedSourceIndex)
}

fun DetailUiState.matchesDetailRoute(vodId: String): Boolean {
    val normalizedId = vodId.trim()
    return normalizedId.isNotBlank() &&
        (requestedVodId?.trim() == normalizedId || item?.matchesDetailRoute(normalizedId) == true)
}

// 详情与播放恢复共用同一套影片别名匹配，避免两处路由判断不一致。
fun VodItem.matchesDetailRoute(vodId: String): Boolean {
    val normalizedId = vodId.trim()
    if (normalizedId.isBlank()) return false
    return linkedSetOf(
        this.vodId.trim(),
        this.siteVodId.trim(),
        Regex("""/voddetail/([^/.]+)""").find(detailUrl)?.groupValues?.getOrNull(1).orEmpty(),
        Regex("""/vodplay/([^/-?.]+)""").find(detailUrl)?.groupValues?.getOrNull(1).orEmpty()
    ).any { it.isNotBlank() && it == normalizedId }
}
