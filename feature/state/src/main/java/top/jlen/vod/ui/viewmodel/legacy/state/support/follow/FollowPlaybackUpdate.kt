package top.jlen.vod.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.jlen.vod.data.PlaybackResumeRecord

internal val followPlaybackComparator = compareByDescending<FollowUpItem> { it.hasUpdate }
    .thenByDescending { it.lastWatchedAtMillis ?: 0L }
    .thenBy { it.title }

// 网络刷新/后台重建结束时，不覆盖期间产生的更新续播信息。
internal fun mergeNewerFollowPlayback(loaded: List<FollowUpItem>, current: List<FollowUpItem>): List<FollowUpItem> {
    val currentById = current.associateBy { it.vodId }
    return loaded.map { item ->
        val newer = currentById[item.vodId]
        if (newer == null || (newer.lastWatchedAtMillis ?: 0L) <= (item.lastWatchedAtMillis ?: 0L)) item
        else {
            val hasUpdate = item.latestEpisodeIndex?.let { it > newer.watchedEpisodeIndex && newer.watchedEpisodeIndex >= 0 } ?: false
            item.copy(
                watchedEpisodeIndex = newer.watchedEpisodeIndex,
                watchedEpisodeLabel = newer.watchedEpisodeLabel,
                lastWatchedAtMillis = newer.lastWatchedAtMillis,
                lastWatchedAtText = newer.lastWatchedAtText,
                sourceName = newer.sourceName,
                hasUpdate = hasUpdate,
                updateLabel = if (hasUpdate) "更新至第${item.latestEpisodeIndex!! + 1}集" else ""
            )
        }
    }.sortedWith(followPlaybackComparator)
}

/** Progress is persisted separately; follow cards only show episode/source and minute precision. */
internal fun followItemsWithPlayback(items: List<FollowUpItem>, record: PlaybackResumeRecord): List<FollowUpItem> {
    val index = items.indexOfFirst { it.vodId == record.vodId }
    if (index < 0) return items
    val previous = items[index]
    // Ignore stale callbacks and avoid relabelling a card for every playback second.
    if (record.updatedAt < (previous.lastWatchedAtMillis ?: 0L)) return items
    val source = record.sourceName.ifBlank { previous.sourceName }
    if (previous.watchedEpisodeIndex == record.episodeIndex && previous.sourceName == source &&
        previous.lastWatchedAtMillis?.div(60_000L) == record.updatedAt / 60_000L
    ) return items
    val hasUpdate = previous.latestEpisodeIndex?.let { it > record.episodeIndex && record.episodeIndex >= 0 } ?: false
    val episodeLabel = if (record.episodeIndex >= 0) "已看至第${record.episodeIndex + 1}集" else "已记录本地续播"
    val updated = previous.copy(
        watchedEpisodeIndex = record.episodeIndex,
        watchedEpisodeLabel = if (source.isNotBlank()) "$episodeLabel · $source" else episodeLabel,
        lastWatchedAtMillis = record.updatedAt,
        lastWatchedAtText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.updatedAt)),
        hasUpdate = hasUpdate,
        updateLabel = if (hasUpdate) "更新至第${previous.latestEpisodeIndex!! + 1}集" else "",
        sourceName = source
    )
    return items.toMutableList().apply { set(index, updated) }.sortedWith(followPlaybackComparator)
}
