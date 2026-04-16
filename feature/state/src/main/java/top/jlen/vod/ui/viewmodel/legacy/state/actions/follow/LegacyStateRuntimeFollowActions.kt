package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.VodItem
import top.jlen.vod.data.sanitizeUserFacingComposite

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshFollowContent(forceRefresh: Boolean = false) {
    if (currentFollowState().isLoading || currentFollowState().isRefreshing) return
    if (!currentAccountState().session.isLoggedIn) {
        updateFollowState(FollowUiState(isLoggedIn = false))
        return
    }

    val hasExistingItems = currentFollowState().items.isNotEmpty()
    updateFollowState(
        currentFollowState().copy(
            isLoading = !hasExistingItems,
            isRefreshing = hasExistingItems,
            isLoggedIn = true,
            error = null
        )
    )

    viewModelScope.launch {
        runCatching {
            val favoriteItems = withContext(Dispatchers.IO) {
                if (forceRefresh || currentAccountState().favoriteItems.isEmpty()) {
                    loadAllFavoriteItems()
                } else {
                    currentAccountState().favoriteItems
                }
            }
            val historyItems = withContext(Dispatchers.IO) {
                val rawHistory = if (forceRefresh || currentAccountState().historyItems.isEmpty()) {
                    loadAllHistoryItems()
                } else {
                    currentAccountState().historyItems
                }
                legacyRepository().enrichHistoryItems(rawHistory)
            }

            updateAccountState(
                currentAccountState().copy(
                    favoriteItems = favoriteItems,
                    favoriteNextPageUrl = null,
                    historyItems = historyItems,
                    historyNextPageUrl = null
                )
            )

            withContext(Dispatchers.IO) {
                buildFollowItems(
                    favoriteItems = favoriteItems,
                    historyItems = historyItems,
                    resolveDetails = true
                )
            }
        }.onSuccess { items ->
            updateFollowState(
                FollowUiState(
                    isLoading = false,
                    isRefreshing = false,
                    isLoggedIn = true,
                    items = items
                )
            )
        }.onFailure { error ->
            updateFollowState(
                currentFollowState().copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoggedIn = true,
                    error = toUserFacingMessage(error, "追剧列表加载失败")
                )
            )
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRebuildFollowContent() {
    if (!currentAccountState().session.isLoggedIn) {
        updateFollowState(FollowUiState(isLoggedIn = false))
        return
    }
    val favorites = currentAccountState().favoriteItems
    if (favorites.isEmpty() && currentFollowState().items.isEmpty()) {
        updateFollowState(
            currentFollowState().copy(
                isLoading = false,
                isRefreshing = false,
                isLoggedIn = true,
                error = null,
                items = emptyList()
            )
        )
        return
    }

    viewModelScope.launch {
        val items = withContext(Dispatchers.IO) {
            buildFollowItems(
                favoriteItems = favorites,
                historyItems = currentAccountState().historyItems,
                resolveDetails = false
            )
        }
        updateFollowState(
            currentFollowState().copy(
                isLoading = false,
                isRefreshing = false,
                isLoggedIn = true,
                error = null,
                items = items
            )
        )
    }
}

private suspend fun LegacyStateRuntimeViewModelCore.loadAllFavoriteItems(): List<UserCenterItem> {
    val items = mutableListOf<UserCenterItem>()
    var pageUrl: String? = null
    do {
        val page = legacyRepository().loadFavoritePageForApp(pageUrl)
        items += page.items
        pageUrl = page.nextPageUrl
    } while (!pageUrl.isNullOrBlank())
    return mergeAccountItems(emptyList(), items)
}

private suspend fun LegacyStateRuntimeViewModelCore.loadAllHistoryItems(): List<UserCenterItem> {
    val items = mutableListOf<UserCenterItem>()
    var pageUrl: String? = null
    do {
        val page = legacyRepository().loadHistoryPageForApp(pageUrl)
        items += page.items
        pageUrl = page.nextPageUrl
    } while (!pageUrl.isNullOrBlank())
    return mergeAccountItems(emptyList(), items)
}

private suspend fun LegacyStateRuntimeViewModelCore.buildFollowItems(
    favoriteItems: List<UserCenterItem>,
    historyItems: List<UserCenterItem>,
    resolveDetails: Boolean
): List<FollowUpItem> = coroutineScope {
    val existingByVodId = currentFollowState().items.associateBy { it.vodId }
    val historyByVodId = historyItems
        .mapNotNull { item ->
            resolveFollowVodId(item).takeIf { it.isNotBlank() }?.let { it to item }
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )

    favoriteItems
        .mapNotNull { favorite ->
            resolveFollowVodId(favorite)
                .takeIf { it.isNotBlank() }
                ?.let { vodId -> favorite to vodId }
        }
        .distinctBy { it.second }
        .map { (favorite, vodId) ->
            async {
                buildFollowItem(
                    vodId = vodId,
                    favorite = favorite,
                    historyItems = historyByVodId[vodId].orEmpty(),
                    cached = existingByVodId[vodId],
                    resolveDetails = resolveDetails
                )
            }
        }
        .awaitAll()
        .filterNotNull()
        .sortedWith(
            compareByDescending<FollowUpItem> { it.hasUpdate }
                .thenByDescending { it.lastWatchedAtMillis ?: 0L }
                .thenBy { it.title }
        )
}

private suspend fun LegacyStateRuntimeViewModelCore.buildFollowItem(
    vodId: String,
    favorite: UserCenterItem,
    historyItems: List<UserCenterItem>,
    cached: FollowUpItem?,
    resolveDetails: Boolean
): FollowUpItem? {
    val localResume = legacyRepository().loadPlaybackResumeForApp(vodId)
    val bestHistory = historyItems.maxWithOrNull(
        compareBy<UserCenterItem> { it.episodeIndex }
            .thenBy { if (it.sourceName.isNotBlank()) 1 else 0 }
    )
    val detailItem = if (resolveDetails || cached == null) {
        runCatching { legacyRepository().loadDetail(vodId) }.getOrNull()
    } else {
        null
    }

    val watchedEpisodeIndex = localResume?.episodeIndex
        ?: bestHistory?.episodeIndex
        ?: -1
    val lastWatchedAtMillis = localResume
        ?.updatedAt
        ?.takeIf { it > 0L }

    val latestEpisode = parseLatestEpisodeInfo(
        listOfNotNull(
            detailItem?.resolvedUpdateLabel,
            detailItem?.resolvedSubtitle,
            detailItem?.resolvedBadgeText,
            favorite.subtitle,
            bestHistory?.subtitle,
            cached?.latestEpisodeLabel
        )
    )
    val hasUpdate = latestEpisode?.index != null &&
        watchedEpisodeIndex >= 0 &&
        latestEpisode.index > watchedEpisodeIndex
    val latestEpisodeLabel = latestEpisode?.label.orEmpty()
    val updateLabel = if (hasUpdate && latestEpisode != null) {
        "更新至第${latestEpisode.index + 1}集"
    } else {
        ""
    }

    val title = detailItem?.displayTitle
        ?.takeIf { it.isNotBlank() }
        ?: favorite.title.takeIf { it.isNotBlank() }
        ?: cached?.title
        ?: return null
    val subtitle = listOfNotNull(
        detailItem?.resolvedSubtitle?.takeIf { it.isNotBlank() },
        favorite.subtitle.takeIf { it.isNotBlank() },
        bestHistory?.subtitle?.takeIf { it.isNotBlank() },
        cached?.subtitle?.takeIf { it.isNotBlank() }
    )
        .map(::sanitizeUserFacingComposite)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val sourceName = localResume?.sourceName
        ?.takeIf { it.isNotBlank() }
        ?: bestHistory?.sourceName
        ?.takeIf { it.isNotBlank() }
        ?: cached?.sourceName.orEmpty()

    return FollowUpItem(
        vodId = vodId,
        title = title,
        poster = detailItem?.vodPic ?: cached?.poster,
        subtitle = subtitle,
        latestEpisodeLabel = latestEpisodeLabel,
        latestEpisodeIndex = latestEpisode?.index,
        watchedEpisodeIndex = watchedEpisodeIndex,
        watchedEpisodeLabel = buildFollowWatchedEpisodeLabel(
            watchedEpisodeIndex = watchedEpisodeIndex,
            sourceName = sourceName,
            hasResumeTime = lastWatchedAtMillis != null,
            hasHistory = bestHistory != null
        ),
        lastWatchedAtMillis = lastWatchedAtMillis,
        lastWatchedAtText = buildFollowWatchTimeText(lastWatchedAtMillis, bestHistory != null),
        hasUpdate = hasUpdate,
        updateLabel = updateLabel,
        sourceName = sourceName
    )
}

private fun resolveFollowVodId(item: UserCenterItem): String =
    item.vodId.trim()
        .ifBlank {
            Regex("""/vodplay/([^/-?.]+)""")
                .find(item.playUrl.ifBlank { item.actionUrl })
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }
        .ifBlank {
            Regex("""/voddetail/([^/.?]+)""")
                .find(item.actionUrl)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }

private fun buildFollowWatchedEpisodeLabel(
    watchedEpisodeIndex: Int,
    sourceName: String,
    hasResumeTime: Boolean,
    hasHistory: Boolean
): String {
    val episodeLabel = watchedEpisodeIndex
        .takeIf { it >= 0 }
        ?.let { "已看至第${it + 1}集" }
        .orEmpty()
    val sourceLabel = sourceName.trim()
    return when {
        episodeLabel.isNotBlank() && sourceLabel.isNotBlank() -> "$episodeLabel · $sourceLabel"
        episodeLabel.isNotBlank() -> episodeLabel
        hasResumeTime -> "已记录本地续播"
        hasHistory -> "已加入追剧，尚无本地续播时间"
        else -> "已加入追剧，尚无观看记录"
    }
}

private fun buildFollowWatchTimeText(
    lastWatchedAtMillis: Long?,
    hasHistory: Boolean
): String = when {
    lastWatchedAtMillis != null && lastWatchedAtMillis > 0L ->
        followDisplayTimeFormatter.format(Date(lastWatchedAtMillis))
    hasHistory -> "已加入追剧，尚无本地续播时间"
    else -> "已加入追剧，尚无观看记录"
}

private data class ParsedLatestEpisode(
    val index: Int,
    val label: String
)

private fun parseLatestEpisodeInfo(values: List<String>): ParsedLatestEpisode? {
    values.forEach { raw ->
        val text = sanitizeUserFacingComposite(raw)
        if (text.isBlank()) return@forEach

        parseEpisodeIndex(text, Regex("""更新至第\s*(\d{1,4})\s*集?"""))
            ?.let { return ParsedLatestEpisode(it, "更新至第${it + 1}集") }
        parseEpisodeIndex(text, Regex("""更新至\s*(\d{1,4})\s*集?"""))
            ?.let { return ParsedLatestEpisode(it, "更新至第${it + 1}集") }
        parseEpisodeIndex(text, Regex("""第\s*(\d{1,4})\s*集"""))
            ?.let { return ParsedLatestEpisode(it, "更新至第${it + 1}集") }
        parseEpisodeIndex(text, Regex("""全\s*(\d{1,4})\s*集"""))
            ?.let { return ParsedLatestEpisode(it, "全${it + 1}集") }
        parseEpisodeIndex(text, Regex("""共\s*(\d{1,4})\s*集"""))
            ?.let { return ParsedLatestEpisode(it, "共${it + 1}集") }
        parseEpisodeIndex(text, Regex("""(\d{1,4})\s*集全"""))
            ?.let { return ParsedLatestEpisode(it, "${it + 1}集全") }
    }
    return null
}

private fun parseEpisodeIndex(text: String, regex: Regex): Int? =
    regex.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.minus(1)

private val followDisplayTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
