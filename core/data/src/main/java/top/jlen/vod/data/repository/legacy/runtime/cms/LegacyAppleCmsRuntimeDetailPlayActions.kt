package top.jlen.vod.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyLoadDetail(
    vodId: String,
    forceRefresh: Boolean = false
): VodItem? {
    val normalizedId = vodId.trim()
    if (normalizedId.isBlank()) return null
    if (!forceRefresh) {
        runtimePeekDetailCacheEntry(normalizedId)
            ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeDetailCacheTtlMs()) }
            ?.value
            ?.let { return it }
    }

    return if (forceRefresh) {
        legacyLoadFreshDetail(normalizedId)
    } else {
        runtimeAwaitSharedRequest("detail:$normalizedId") {
            runtimePeekDetailCacheEntry(normalizedId)
                ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeDetailCacheTtlMs()) }
                ?.value
                ?: legacyLoadFreshDetail(normalizedId)
        }
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyLoadFreshDetail(
    normalizedId: String
): VodItem? {
    if (normalizedId.all(Char::isDigit)) {
        val previewItem = runtimeFindPreviewItem(normalizedId)
        val apiItem = legacyLoadDetailFromApi(normalizedId)
        val resolvedItem = when {
            apiItem == null -> previewItem?.let { preview ->
                legacyResolveDetailMismatch(preview, excludedVodId = normalizedId)
                    ?.let { mergePreviewIntoDetail(preview, it) }
                    ?: preview
            }
            previewItem != null && !detailMatchesPreview(apiItem, previewItem) ->
                legacyResolveDetailMismatch(previewItem, excludedVodId = normalizedId)
                    ?.let { mergePreviewIntoDetail(previewItem, it) }
                    ?: previewItem
            previewItem != null -> mergePreviewIntoDetail(previewItem, apiItem)
            else -> apiItem
        }
        runtimeUpdateDetailCacheEntry(
            normalizedId,
            CachedValue(
                value = resolvedItem,
                timestampMs = System.currentTimeMillis()
            )
        )
        resolvedItem?.let { runtimeRememberPreviewItems(listOf(it)) }
        runtimeCleanupCachesIfNeeded()
        return resolvedItem
    }

    return runtimeParseDetail(
        runtimeFetchDocument("${runtimeBaseUrl()}/voddetail/$normalizedId/")
    ).also { item ->
        runtimeUpdateDetailCacheEntry(
            normalizedId,
            CachedValue(
                value = item,
                timestampMs = System.currentTimeMillis()
            )
        )
        runtimeCleanupCachesIfNeeded()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyLoadDetailFromApi(
    vodId: String
): VodItem? = runtimeRequestDetailApi(vodId)

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyResolveDetailMismatch(
    previewItem: VodItem,
    excludedVodId: String
): VodItem? {
    val targetTitle = canonicalTitle(previewItem.vodName)
    if (targetTitle.isBlank()) return null

    val candidates = runCatching {
        runtimeRequestSearch(keyword = previewItem.vodName, page = 1, limit = 10)
    }.getOrDefault(emptyList())
        .filter { candidate ->
            candidate.vodId != excludedVodId &&
                canonicalTitle(candidate.vodName) == targetTitle
        }
        .sortedByDescending { candidate ->
            searchCandidateScore(candidate, previewItem)
        }

    for (candidate in candidates.take(5)) {
        val detail = runCatching { legacyLoadDetailFromApi(candidate.vodId) }.getOrNull() ?: continue
        if (detailMatchesPreview(detail, previewItem)) {
            return detail
        }
    }
    return null
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyFilterPlayablePreviewItems(
    items: List<VodItem>
): List<VodItem> {
    if (items.isEmpty()) return emptyList()
    return coroutineScope {
        items.map { previewItem ->
            async {
                val resolved = runCatching {
                    legacyResolvePlayableDetailForPreview(previewItem)
                }.getOrNull()
                if (resolved != null && legacyParseSources(resolved).isNotEmpty()) {
                    runtimeUpdateDetailCacheEntry(
                        previewItem.vodId,
                        CachedValue(
                            value = resolved,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                    runtimeRememberPreviewItems(listOf(resolved))
                    previewItem
                } else {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyResolvePlayableDetailForPreview(
    previewItem: VodItem
): VodItem? {
    runtimePeekDetailCacheEntry(previewItem.vodId)
        ?.takeIf { runtimeIsCacheValid(it.timestampMs, runtimeDetailCacheTtlMs()) }
        ?.value
        ?.takeIf { legacyParseSources(it).isNotEmpty() }
        ?.let { return it }

    val apiItem = legacyLoadDetailFromApi(previewItem.vodId)
    return when {
        apiItem == null -> legacyResolveDetailMismatch(previewItem, excludedVodId = previewItem.vodId)
            ?.let { mergePreviewIntoDetail(previewItem, it) }
        !detailMatchesPreview(apiItem, previewItem) ->
            legacyResolveDetailMismatch(previewItem, excludedVodId = previewItem.vodId)
                ?.let { mergePreviewIntoDetail(previewItem, it) }
        else -> mergePreviewIntoDetail(previewItem, apiItem)
    }
}

internal suspend fun LegacyAppleCmsRuntimeRepository.legacyResolvePlayUrl(
    playPageUrl: String
): ResolvedPlayUrl {
    val normalizedPageUrl = runtimeResolveUrl(playPageUrl)
    if (isDirectMediaUrl(normalizedPageUrl)) {
        return ResolvedPlayUrl(url = normalizedPageUrl, useWebPlayer = false)
    }

    val html = runtimeFetchHtml(normalizedPageUrl, referer = "${runtimeBaseUrl()}/")
    val playerConfig = runtimeExtractPlayerConfig(html)
    val rawUrl = playerConfig?.first.orEmpty()
    val encrypt = playerConfig?.second ?: 0
    val decodedUrl = runtimeDecodePlayerUrl(rawUrl, encrypt)
    val firstResolvedUrl = runtimeNormalizeAgainst(
        decodedUrl.ifBlank { normalizedPageUrl },
        normalizedPageUrl
    )
    val resolvedUrl = runtimeResolveNestedMediaUrl(
        candidateUrl = firstResolvedUrl,
        referer = normalizedPageUrl,
        depth = 0
    )
    val useWebPlayer = !isDirectMediaUrl(resolvedUrl)

    return ResolvedPlayUrl(
        url = if (useWebPlayer) normalizedPageUrl else resolvedUrl,
        useWebPlayer = useWebPlayer
    )
}

internal fun LegacyAppleCmsRuntimeRepository.legacyParseSources(
    item: VodItem
): List<PlaySource> {
    val sourceNames = item.vodPlayFrom
        .orEmpty()
        .split("$$$")
        .map(::sanitizeUserFacingToken)
        .filter { it.isNotBlank() }

    val groups = item.vodPlayUrl
        .orEmpty()
        .split("$$$")
        .map { rawGroup ->
            rawGroup.split("#")
                .mapNotNull { rawEpisode ->
                    val pair = rawEpisode.split("$", limit = 2)
                    val url = pair.getOrNull(1).orEmpty().trim()
                    if (url.isBlank()) {
                        null
                    } else {
                        Episode(
                            name = pair.firstOrNull().orEmpty().ifBlank { "播放" },
                            url = runtimeNormalizeUrl(url)
                        )
                    }
                }
        }
        .filter { it.isNotEmpty() }
        .map { episodes ->
            episodes.map { episode ->
                episode.copy(name = sanitizeUserFacingToken(episode.name).ifBlank { "播放" })
            }
        }

    return groups.mapIndexed { index, episodes ->
        PlaySource(
            name = sourceNames.getOrNull(index).orEmpty().ifBlank { "线路 ${index + 1}" },
            episodes = episodes
        )
    }
}
