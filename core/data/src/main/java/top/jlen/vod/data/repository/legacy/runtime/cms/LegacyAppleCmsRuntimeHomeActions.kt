package top.jlen.vod.data

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document

internal fun LegacyAppleCmsRuntimeRepository.legacyClearMemoryCaches() {
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

internal fun LegacyAppleCmsRuntimeRepository.legacyClearAllAppCaches() {
    legacyClearMemoryCaches()
    runtimeClearPersistedPageCache()
    runtimeClearPersistedHomeCache()
}

internal fun LegacyAppleCmsRuntimeRepository.legacyClearProcessMemoryCaches() {
    legacyClearMemoryCaches()
}

internal fun LegacyAppleCmsRuntimeRepository.legacyClearRuntimeCaches() {
    legacyClearAllAppCaches()
}

internal fun LegacyAppleCmsRuntimeRepository.legacyPeekHomePayload(
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

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyLoadHome(
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

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyLoadEmergencyHome(): HomePayload {
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

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyLoadFreshHome(
    forceRefresh: Boolean
): HomePayload {
    val (latestPage, recommendedItems) = coroutineScope {
        val latestDeferred = async {
            runCatching {
                runtimeLoadLatestCursorPage(cursor = "")
            }.getOrElse {
                CursorPagedVodItems()
            }
        }
        val recommendationsDeferred = async {
            runCatching {
                runtimeLoadRecommendedPreviewItems(limit = 16)
            }.getOrDefault(emptyList())
        }
        latestDeferred.await() to recommendationsDeferred.await()
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
    val selectedCategory = categories.firstOrNull()
    val selectedCategoryPage = selectedCategory?.let { category ->
        runCatching {
            runtimeLoadCategoryCursorPage(typeId = category.typeId, cursor = "")
        }.getOrNull()
    }
    runtimeRememberPreviewItems(latest + featured + selectedCategoryPage?.items.orEmpty())

    if (latest.isEmpty() && featured.isEmpty() && categories.isEmpty()) {
        throw IOException("棣栭〉鍐呭瑙ｆ瀽澶辫触")
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
