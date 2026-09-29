package top.jlen.vod.data

import java.io.IOException
import kotlinx.coroutines.async
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
    runtimeClearPersistedHotSearchCache()
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
    val latestItems = latestPage.items.ifEmpty { recommendedItems.take(36) }
    val featuredItems = recommendedItems
        .ifEmpty { cachedHome?.featured.orEmpty() }
        .ifEmpty { latestItems.take(16) }
    runtimeRememberPreviewItems(buildList {
        addAll(latestItems)
        addAll(featuredItems)
    })

    return HomePayload(
        slides = emptyList(),
        hot = emptyList(),
        featured = featuredItems,
        latest = latestItems,
        sections = emptyList(),
        categories = categories,
        selectedCategory = ALL_LIBRARY_CATEGORY,
        categoryVideos = latestItems,
        latestCursor = latestPage.nextCursor,
        latestHasMore = latestPage.hasMore,
        categoryCursor = latestPage.nextCursor,
        categoryHasMore = latestPage.hasMore
    ).also { payload ->
        runtimeCacheHomePayload(payload)
        runtimeCleanupCachesIfNeeded()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFreshHome(
    forceRefresh: Boolean
): HomePayload {
    val (latestPage, recommendedItems) = coroutineScope {
        val latestDeferred = async {
            runCatching { runtimeLoadLatestCursorPage(cursor = "") }
                .getOrElse { CursorPagedVodItems() }
        }
        val recommendedDeferred = async {
            runCatching { runtimeLoadRecommendedPreviewItems(limit = 16) }
                .getOrDefault(emptyList())
        }
        latestDeferred.await() to recommendedDeferred.await()
    }
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
    // 默认片库为“全部”；具体分类在用户选择时加载，不阻塞首页。
    runtimeRememberPreviewItems(latest + featured)

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
        selectedCategory = ALL_LIBRARY_CATEGORY,
        categoryVideos = latest,
        latestCursor = latestPage.nextCursor,
        latestHasMore = latestPage.hasMore,
        categoryCursor = latestPage.nextCursor,
        categoryHasMore = latestPage.hasMore
    ).also { payload ->
        runtimeCacheHomePayload(payload)
        runtimeCleanupCachesIfNeeded()
    }
}
