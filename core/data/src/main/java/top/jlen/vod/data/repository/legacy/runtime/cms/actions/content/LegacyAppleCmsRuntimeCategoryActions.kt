package top.jlen.vod.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadByCategory(
    typeId: String
): List<VodItem> = runtimeLoadCategoryPage(typeId = typeId, page = 1).items

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadAllCategoryPage(
    page: Int,
    forceRefresh: Boolean = false
): PagedVodItems {
    val safePage = page.coerceAtLeast(1)
    val cacheKey = "all:$safePage"
    if (!forceRefresh) {
        runtimePeekCategoryPageCacheEntry(cacheKey)
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs()) }
            ?.value
            ?.let { return it }
        runtimeReadPersistedCategoryPageCache(cacheKey)
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs(allowStale = true)) }
            ?.also { cached ->
                runtimeUpdateCategoryPageCacheEntry(cacheKey, cached)
                return cached.value
            }
    }
    return if (forceRefresh) {
        legacyLoadFreshAllCategoryPage(page = safePage, forceRefresh = true)
    } else {
        runtimeAwaitSharedRequest("all_page:$safePage") {
            runtimePeekCategoryPageCacheEntry(cacheKey)
                ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs()) }
                ?.value
                ?: runtimeReadPersistedCategoryPageCache(cacheKey)
                    ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs(allowStale = true)) }
                    ?.also { cached -> runtimeUpdateCategoryPageCacheEntry(cacheKey, cached) }
                    ?.value
                ?: legacyLoadFreshAllCategoryPage(page = safePage, forceRefresh = false)
        }
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFreshAllCategoryPage(
    page: Int,
    forceRefresh: Boolean
): PagedVodItems {
    val pages = coroutineScope {
        runtimeGetBrowsableCategories(forceRefresh = forceRefresh)
            .map { category ->
                async { runtimeLoadCategoryPage(category.typeId, page, forceRefresh = forceRefresh) }
            }
            .awaitAll()
    }
    return runtimeBuildMergedCategoryPage(pages, page).also { payload ->
        runtimeCacheCategoryPagePayload("all:$page", payload)
    }
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyPeekAllCategoryPage(page: Int): PagedVodItems? {
    val safePage = page.coerceAtLeast(1)
    val allCacheKey = "all:$safePage"
    runtimePeekCategoryPageCacheEntry(allCacheKey)
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs()) }
        ?.value
        ?.let { return it }

    runtimeReadPersistedCategoryPageCache(allCacheKey)
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs(allowStale = true)) }
        ?.also { cached ->
            runtimeUpdateCategoryPageCacheEntry(allCacheKey, cached)
            return cached.value
        }

    val cachedPages = runtimeGetCachedBrowsableCategories().mapNotNull { category ->
        val cacheKey = "${category.typeId}:$safePage"
        runtimePeekCategoryPageCacheEntry(cacheKey)
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs()) }
            ?.value
            ?: runtimeReadPersistedCategoryPageCache(cacheKey)
                ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimePageCacheTtlMs(allowStale = true)) }
                ?.also { cached -> runtimeUpdateCategoryPageCacheEntry(cacheKey, cached) }
                ?.value
    }
    if (cachedPages.isEmpty()) return null
    return runtimeBuildMergedCategoryPage(cachedPages, safePage).also { payload ->
        runtimeCacheCategoryPagePayload(allCacheKey, payload)
    }
}

internal fun LegacyAppleCmsRuntimeRepositoryCore.legacyPeekCategoryPage(
    typeId: String,
    page: Int,
    allowStale: Boolean = false
): PagedVodItems? {
    val safePage = page.coerceAtLeast(1)
    val cacheKey = "$typeId:$safePage"
    val ttlMs = runtimePageCacheTtlMs(allowStale)
    runtimePeekCategoryPageCacheEntry(cacheKey)
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, ttlMs) }
        ?.value
        ?.let { return it }
    runtimeReadPersistedCategoryPageCache(cacheKey)
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, ttlMs) }
        ?.also { cached -> runtimeUpdateCategoryPageCacheEntry(cacheKey, cached) }
        ?.value
        ?.let { return it }
    return null
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyPrewarmCategoryFirstPages(
    forceRefresh: Boolean = false
) {
    val categories = runtimeGetBrowsableCategories(forceRefresh = forceRefresh)
    coroutineScope {
        categories.map { category ->
            async {
                runCatching {
                    runtimeLoadCategoryPage(
                        typeId = category.typeId,
                        page = 1,
                        forceRefresh = forceRefresh
                    )
                }
            }
        }.awaitAll()
    }
}
