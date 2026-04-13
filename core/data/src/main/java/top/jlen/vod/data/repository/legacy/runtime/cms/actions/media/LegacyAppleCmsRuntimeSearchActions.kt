package top.jlen.vod.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacySearch(
    keyword: String,
    forceRefresh: Boolean = false
): List<VodItem> {
    val normalizedKeyword = keyword.trim()
    if (normalizedKeyword.isBlank()) return emptyList()
    val cacheKey = normalizedKeyword.lowercase()
    if (!forceRefresh) {
        runtimePeekSearchCacheEntry(cacheKey)
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeSearchCacheTtlMs()) }
            ?.value
            ?.let { return it }
    }

    return if (forceRefresh) {
        legacyPerformSearch(normalizedKeyword, cacheKey)
    } else {
        runtimeAwaitSharedRequest("search:$cacheKey") {
            runtimePeekSearchCacheEntry(cacheKey)
                ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeSearchCacheTtlMs()) }
                ?.value
                ?: legacyPerformSearch(normalizedKeyword, cacheKey)
        }
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyPerformSearch(
    keyword: String,
    cacheKey: String
): List<VodItem> {
    return runCatching {
        runtimeRequestSearch(keyword = keyword, page = 1, limit = 60)
            .distinctBy { it.vodId }
            .take(60)
    }.getOrElse {
        runCatching {
            val document = runtimeFetchSearchDocument(keyword)
            runtimeParseSearchResults(document, keyword)
                .distinctBy { it.vodId }
                .take(60)
        }.getOrDefault(emptyList())
    }.also { results ->
        runtimeUpdateSearchCacheEntry(
            cacheKey,
            CachedValue(
                value = results,
                timestampMs = System.currentTimeMillis()
            )
        )
        runtimeRememberPreviewItems(results)
        runtimeCleanupCachesIfNeeded()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacySearchCursor(
    keyword: String,
    cursor: String
): CursorPagedVodItems {
    val query = keyword.trim()
    if (query.isBlank()) return CursorPagedVodItems()
    return runtimeRequestSearchCursor(query, cursor).also { runtimeRememberPreviewItems(it.items) }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyEnrichSearchResults(
    items: List<VodItem>,
    limit: Int = 8
): List<VodItem> {
    if (items.isEmpty()) return items
    val enrichTargets = items.take(limit)
    val enrichedById = coroutineScope {
        enrichTargets.map { item ->
            async {
                val detailItem = runCatching { loadDetail(item.vodId) }.getOrNull()
                val description = detailItem?.description
                    ?.takeIf { it.isNotBlank() && it != "暂无简介" }
                    .orEmpty()
                item.vodId to (
                    detailItem?.let { detail ->
                        item.copy(
                            vodSub = detail.vodSub?.takeIf { it.isNotBlank() } ?: item.vodSub,
                            compatSubtitle = detail.compatSubtitle?.takeIf { it.isNotBlank() } ?: item.compatSubtitle,
                            vodRemarks = detail.vodRemarks?.takeIf { it.isNotBlank() } ?: item.vodRemarks,
                            compatBadgeText = detail.compatBadgeText?.takeIf { it.isNotBlank() } ?: item.compatBadgeText,
                            episodeRemark = detail.episodeRemark?.takeIf { it.isNotBlank() } ?: item.episodeRemark,
                            vodPlayUrl = detail.vodPlayUrl?.takeIf { it.isNotBlank() } ?: item.vodPlayUrl,
                            vodBlurb = description.ifBlank { item.vodBlurb },
                            vodContent = description.ifBlank { item.vodContent }
                        )
                    } ?: item
                )
            }
        }.awaitAll().toMap()
    }
    return items.map { item -> enrichedById[item.vodId] ?: item }
}
