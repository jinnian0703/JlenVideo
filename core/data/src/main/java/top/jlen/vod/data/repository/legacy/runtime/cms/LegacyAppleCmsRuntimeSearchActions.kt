package top.jlen.vod.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal suspend fun LegacyAppleCmsRuntimeRepository.legacySearch(
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

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyPerformSearch(
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
            LegacyAppleCmsRuntimeRepository.CachedValue(
                value = results,
                timestampMs = System.currentTimeMillis()
            )
        )
        runtimeRememberPreviewItems(results)
        runtimeCleanupCachesIfNeeded()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacySearchCursor(
    keyword: String,
    cursor: String
): CursorPagedVodItems {
    val query = keyword.trim()
    if (query.isBlank()) return CursorPagedVodItems()
    return runtimeRequestSearchCursor(query, cursor).also { runtimeRememberPreviewItems(it.items) }
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyEnrichSearchResults(
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
                    ?.takeIf { it.isNotBlank() && it != "鏆傛棤绠€浠?" }
                    .orEmpty()
                item.vodId to if (description.isNotBlank()) {
                    item.copy(
                        vodBlurb = description,
                        vodContent = description
                    )
                } else {
                    item
                }
            }
        }.awaitAll().toMap()
    }
    return items.map { item -> enrichedById[item.vodId] ?: item }
}
