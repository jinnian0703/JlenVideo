package top.jlen.vod.data

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyClearMemoryCaches() {
    runtimeClearHomeCacheEntry()
    runtimeClearHotSearchCacheEntry()
    runtimeClearNoticeCacheEntry()
    runtimeClearBrowsableCategoriesCacheEntry()
    runtimeClearCategoryPageCache()
    runtimeClearDetailCache()
    runtimeClearSearchCache()
    runtimeClearHistorySourceCache()
    runtimeClearPreviewItemCache()
    runtimeClearInFlightRequests()
    runtimeResetRequestPreference()
    runtimeResetCleanupTimestamps()
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyClearAllAppCaches() {
    legacyClearMemoryCaches()
    runtimeClearPersistedPageCache()
    runtimeClearPersistedHomeCache()
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyClearProcessMemoryCaches() {
    legacyClearMemoryCaches()
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyClearRuntimeCaches() {
    legacyClearAllAppCaches()
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyPeekHomePayload(
    allowStale: Boolean = false
): HomePayload? {
    val ttlMs = runtimeHomeCacheTtlMs(allowStale)
    runtimePeekHomeCacheEntry()
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, ttlMs) }
        ?.value
        ?.let { return it }
    return runtimeReadPersistedHomeCache()
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, ttlMs) }
        ?.also { cached -> runtimeUpdateHomeCacheEntry(cached) }
        ?.value
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadHome(
    forceRefresh: Boolean = false
): HomePayload {
    if (!forceRefresh) {
        legacyPeekHomePayload()?.let { return it }
    }

    return runCatching {
        if (forceRefresh) {
            legacyLoadFreshHome(forceRefresh = true)
        } else {
            runtimeAwaitSharedRequest("home") {
                legacyPeekHomePayload() ?: legacyLoadFreshHome(forceRefresh = false)
            }
        }
    }.getOrElse {
        legacyLoadEmergencyHome()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadEmergencyHome(): HomePayload {
    val cachedHome = runtimePeekHomeCacheEntry()?.value
    val latestPage = runCatching { runtimeLoadLatestCursorPage(cursor = "") }
        .getOrNull()
        ?: CursorPagedVodItems(
            items = cachedHome?.latest.orEmpty(),
            limit = cachedHome?.latest?.size ?: 0,
            nextCursor = cachedHome?.latestCursor.orEmpty(),
            hasMore = cachedHome?.latestHasMore ?: false
        )
    val recommendedItems = runCatching {
        runtimeLoadRecommendedPreviewItems(limit = 16)
    }.getOrElse {
        cachedHome?.featured.orEmpty()
    }
    val categories = runCatching { runtimeLoadBrowsableCategories(forceRefresh = false) }
        .getOrElse { runtimeGetCachedBrowsableCategories() }
        .ifEmpty { runtimeDefaultCategories().map { runtimeNormalizeCategory(it) } }
    val selectedCategory = categories.firstOrNull()
    val categoryPage = selectedCategory?.let { category ->
        runCatching {
            runtimeLoadCategoryCursorPage(typeId = category.typeId, cursor = "")
        }.getOrNull()
    } ?: CursorPagedVodItems()
    val latestItems = latestPage.items.ifEmpty { recommendedItems.take(36) }
    val featuredItems = recommendedItems
        .ifEmpty { cachedHome?.featured.orEmpty() }
        .ifEmpty { latestItems.take(16) }
    runtimeRememberPreviewItems(buildList {
        addAll(latestItems)
        addAll(featuredItems)
        addAll(categoryPage.items)
    })

    return HomePayload(
        slides = emptyList(),
        hot = emptyList(),
        featured = featuredItems,
        latest = latestItems,
        sections = emptyList(),
        categories = categories,
        selectedCategory = selectedCategory,
        categoryVideos = categoryPage.items,
        latestCursor = latestPage.nextCursor,
        latestHasMore = latestPage.hasMore,
        categoryCursor = categoryPage.nextCursor,
        categoryHasMore = categoryPage.hasMore
    ).also { payload ->
        runtimeCacheHomePayload(payload)
        runtimeCleanupCachesIfNeeded()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFreshHome(
    forceRefresh: Boolean
): HomePayload {
    val latestPage = runCatching {
        runtimeLoadLatestCursorPage(cursor = "")
    }.getOrElse {
        CursorPagedVodItems()
    }
    val recommendedItems = runCatching {
        runtimeLoadRecommendedPreviewItems(limit = 16)
    }.getOrDefault(emptyList())
    val homeDocument: Document? = if (recommendedItems.isEmpty()) {
        runCatching { runtimeFetchHomeDocument() }.getOrNull()
    } else {
        null
    }
    val latest = latestPage.items
    val featured = recommendedItems.ifEmpty {
        homeDocument?.let { runtimeParseLevelOneItemsFromHomePage(it, limit = 16) }.orEmpty()
    }
    val categories = runtimeLoadBrowsableCategories(homeDocument = homeDocument, forceRefresh = forceRefresh)
    val selectedCategory = categories.firstOrNull()
    val selectedCategoryPage = selectedCategory?.let { category ->
        runCatching {
            runtimeLoadCategoryCursorPage(typeId = category.typeId, cursor = "")
        }.getOrNull()
    }
    runtimeRememberPreviewItems(latest + featured + selectedCategoryPage?.items.orEmpty())

    if (latest.isEmpty() && featured.isEmpty() && categories.isEmpty()) {
        throw IOException("首页内容解析失败")
    }
    return HomePayload(
        slides = emptyList(),
        hot = emptyList(),
        featured = featured,
        latest = latest,
        sections = emptyList(),
        categories = categories,
        selectedCategory = selectedCategory,
        categoryVideos = selectedCategoryPage?.items.orEmpty(),
        latestCursor = latestPage.nextCursor,
        latestHasMore = latestPage.hasMore,
        categoryCursor = selectedCategoryPage?.nextCursor.orEmpty(),
        categoryHasMore = selectedCategoryPage?.hasMore ?: false
    ).also { payload ->
        runtimeCacheHomePayload(payload)
        runtimeCleanupCachesIfNeeded()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyEnrichHomeDisplayItems(
    payload: HomePayload
): HomePayload {
    if (
        payload.slides.isEmpty() &&
        payload.hot.isEmpty() &&
        payload.featured.isEmpty() &&
        payload.latest.isEmpty() &&
        payload.categoryVideos.isEmpty()
    ) {
        return payload
    }

    val enrichedSlides = enrichHomePreviewItems(payload.slides, limit = 4)
    val enrichedHot = enrichHomePreviewItems(payload.hot, limit = 4)
    val enrichedFeatured = enrichHomePreviewItems(payload.featured, limit = 6)
    val enrichedLatest = enrichHomePreviewItems(payload.latest, limit = 8)
    val enrichedCategoryVideos = payload.categoryVideos

    val enrichedPayload = payload.copy(
        slides = enrichedSlides,
        hot = enrichedHot,
        featured = enrichedFeatured,
        latest = enrichedLatest,
        categoryVideos = enrichedCategoryVideos
    )
    if (enrichedPayload != payload) {
        runtimeRememberPreviewItems(
            enrichedSlides + enrichedHot + enrichedFeatured + enrichedLatest + enrichedCategoryVideos
        )
        runtimeCacheHomePayload(enrichedPayload)
    }
    return enrichedPayload
}

private suspend fun LegacyAppleCmsRuntimeRepositoryCore.enrichHomePreviewItems(
    items: List<VodItem>,
    limit: Int
): List<VodItem> {
    if (items.isEmpty() || limit <= 0) return items
    val enrichTargets = items
        .take(limit)
        .filter { item ->
            item.vodId.isNotBlank() &&
                item.resolvedLatestEpisodeNumber == null &&
                item.vodPlayUrl.isNullOrBlank()
        }
    if (enrichTargets.isEmpty()) return items
    val enrichedById = coroutineScope {
        enrichTargets.map { item ->
            async {
                val vodId = item.vodId.trim()
                if (vodId.isBlank()) return@async vodId to item
                val detailItem = runCatching { loadDetail(vodId) }.getOrNull()
                val mergedItem = when {
                    detailItem == null -> item
                    detailMatchesPreview(detailItem, item) -> mergePreviewIntoDetail(item, detailItem)
                    else -> item
                }
                vodId to mergedItem
            }
        }.awaitAll().toMap()
    }
    return items.map { item ->
        enrichedById[item.vodId.trim()] ?: item
    }
}
