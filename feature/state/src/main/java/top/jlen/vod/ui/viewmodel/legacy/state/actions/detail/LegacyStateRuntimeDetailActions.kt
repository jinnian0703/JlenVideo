package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.PlaybackResumeBucket
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.VodItem

internal fun LegacyStateRuntimeViewModelCore.legacyAddCurrentDetailFavorite() {
    val item = currentDetailState().item ?: return
    if (!currentAccountState().session.isLoggedIn) {
        updateDetailState(detailStateWithActionMessage(currentDetailState(), "请先登录后再加入追剧", true))
        return
    }
    if (currentDetailState().isFavorited) {
        updateDetailState(detailStateWithActionMessage(currentDetailState(), "已在追剧中", false))
        return
    }
    if (currentDetailState().isActionLoading) return
    viewModelScope.launch {
        updateDetailState(beginDetailFavoriteAction(currentDetailState()))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().addFavoriteForApp(item) }
        }.onSuccess { message ->
            val normalizedMessage = normalizeFavoriteActionMessage(message)
            updateDetailState(detailStateWithFavoriteSuccess(currentDetailState(), normalizedMessage))
            legacyRefreshFollowContent(forceRefresh = true)
            if (currentAccountState().selectedSection == AccountSection.Favorites) {
                selectAccountSection(AccountSection.Favorites, forceRefresh = true)
            }
        }.onFailure { error ->
            val isDuplicate = isDuplicateFavoriteMessage(error.message.orEmpty())
            updateDetailState(
                detailStateWithFavoriteFailure(
                    detailState = currentDetailState(),
                    message = if (isDuplicate) "已在追剧中" else toUserFacingMessage(error, "加入追剧失败"),
                    isDuplicate = isDuplicate
                )
            )
        }
    }
}


internal fun LegacyStateRuntimeViewModelCore.legacyCancelCurrentDetailFavorite() {
    val item = currentDetailState().item ?: return
    if (!currentAccountState().session.isLoggedIn) {
        updateDetailState(detailStateWithActionMessage(currentDetailState(), "请先登录后再管理追剧", true))
        return
    }
    val favoriteVodId = resolveCancelableFavoriteVodId(item).takeIf { it.isNotBlank() }
        ?: currentAccountState()
            .favoriteItems
            .firstOrNull { favorite -> favorite.vodId == item.vodId }
            ?.vodId
            ?.takeIf { it.isNotBlank() }
    if (favoriteVodId == null) {
        updateDetailState(detailStateWithFavoriteRemoveFailure(currentDetailState(), "未找到可取消的追剧记录"))
        return
    }
    if (currentDetailState().isActionLoading) return
    updateDetailState(beginDetailFavoriteAction(currentDetailState()))
    runtimeRunAccountAction(
        block = { deleteUserRecordForApp(recordIds = listOf(favoriteVodId), type = 2, clearAll = false) },
        onSuccess = {
            updateAccountState(accountStateRemovingFavoriteByVodId(currentAccountState(), favoriteVodId))
            updateDetailState(detailStateWithFavoriteRemoved(currentDetailState(), "已取消追剧"))
            legacyRebuildFollowContent()
        }
    )
}

private fun resolveCancelableFavoriteVodId(item: VodItem): String {
    val directVodId = item.vodId.trim()
    if (directVodId.isNotBlank()) return directVodId

    val siteVodId = item.siteVodId.trim()
    if (siteVodId.isNotBlank()) return siteVodId

    val detailUrl = item.detailUrl.trim()
    if (detailUrl.isBlank()) return ""

    return Regex("""/voddetail/([^/]+)/?""")
        .find(detailUrl)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
        .ifBlank {
            Regex("""/vodplay/([^/-]+)""")
                .find(detailUrl)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }
}

internal fun LegacyStateRuntimeViewModelCore.legacyDismissDetailActionMessage() {
    if (currentDetailState().actionMessage.isNullOrBlank()) return
    updateDetailState(detailStateWithoutActionMessage(currentDetailState()))
}

internal fun LegacyStateRuntimeViewModelCore.legacyOpenHistoryRecord(item: UserCenterItem) {
    val resolvedVodId = resolveHistoryVodId(item)
    if (resolvedVodId.isBlank()) {
        legacyOpenHistoryRecordDirectly(item)
        return
    }

    updatePlayerState(resolvingHistoryPlayerState(item.title))
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadDetail(item.vodId) }
        }.onSuccess { detailItem ->
            if (detailItem == null) {
                updatePlayerState(
                    PlayerUiState(
                        title = item.title,
                        isResolving = false,
                        resolveError = "未找到影片详情"
                    )
                )
                return@onSuccess
            }

            val sources = legacyRepository().parseSources(detailItem)
            val selection = resolveHistoryResumeSelection(item, sources)
            legacyOpenPlayer(
                title = detailItem.displayTitle,
                item = detailItem,
                sources = sources,
                sourceIndex = selection.sourceIndex,
                episodeIndex = selection.episodeIndex
            )
        }.onFailure { error ->
            updatePlayerState(failedHistoryPlayerState(item.title, error.message ?: "继续观看失败"))
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyResumeHistoryRecord(item: UserCenterItem) {
    val resolvedVodId = resolveHistoryVodId(item)
    if (resolvedVodId.isBlank()) {
        legacyOpenHistoryRecordDirectly(item)
        return
    }

    updatePlayerState(resolvingHistoryPlayerState(item.title))
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadDetail(resolvedVodId) }
        }.onSuccess { detailItem ->
            if (detailItem == null) {
                legacyOpenHistoryRecordDirectly(item)
                return@onSuccess
            }

            val sources = legacyRepository().parseSources(detailItem)
            val selection = resolveHistoryResumeSelection(item, sources)
            legacyOpenPlayer(
                title = detailItem.displayTitle,
                item = detailItem,
                sources = sources,
                sourceIndex = selection.sourceIndex,
                episodeIndex = selection.episodeIndex
            )
        }.onFailure {
            legacyOpenHistoryRecordDirectly(item)
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyOpenHistoryRecordDirectly(item: UserCenterItem) {
    val resumeUrl = item.playUrl.ifBlank { item.actionUrl }
    if (resumeUrl.isBlank()) {
        updatePlayerState(failedHistoryPlayerState(item.title, "无法恢复该条播放记录"))
        return
    }

    legacyOpenPlayer(
        title = item.title,
        item = null,
        sources = buildHistoryFallbackSources(item, resumeUrl),
        sourceIndex = 0,
        episodeIndex = 0
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadDetail(vodId: String) {
    viewModelScope.launch {
        val keepCurrentContent = currentDetailState().item?.vodId == vodId
        updateDetailState(beginDetailLoad(currentDetailState(), keepCurrentContent))
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadDetail(vodId) }
        }.onSuccess { item ->
            if (item == null) {
                updateDetailState(missingDetailState())
            } else {
                val sources = legacyRepository().parseSources(item)
                val resumeBucket = resolvePlaybackResumeBucket(item, vodId)
                val resumeRecord = resolvePlaybackResumeRecordForSources(resumeBucket, sources)
                val selectedSourceIndex = when {
                    sources.isEmpty() -> 0
                    resumeRecord != null -> resumeRecord.sourceIndex.coerceIn(0, sources.lastIndex)
                    else -> 0
                }
                updateDetailState(
                    loadedDetailState(
                        item = item,
                        sources = sources,
                        isFavorited = currentAccountState().favoriteItems.any { favorite -> favorite.vodId == item.vodId },
                        selectedSourceIndex = selectedSourceIndex,
                        playbackResumeBucket = resumeBucket,
                        pendingResumePlayback = resumeRecord
                    )
                )
            }
        }.onFailure { error ->
            updateDetailState(detailStateWithLoadError(toUserFacingMessage(error, "详情加载失败")))
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectSource(index: Int) {
    updateDetailState(detailStateWithSelectedSource(currentDetailState(), index))
}

private fun LegacyStateRuntimeViewModelCore.resolvePlaybackResumeBucket(
    item: VodItem,
    requestedVodId: String
): PlaybackResumeBucket? {
    val candidateIds = linkedSetOf(
        requestedVodId.trim(),
        item.vodId.trim(),
        item.siteVodId.trim(),
        Regex("""/voddetail/([^/.]+)""").find(item.detailUrl)?.groupValues?.getOrNull(1).orEmpty(),
        Regex("""/vodplay/([^/-?.]+)""").find(item.detailUrl)?.groupValues?.getOrNull(1).orEmpty()
    ).filter(String::isNotBlank)

    return candidateIds.firstNotNullOfOrNull { candidate ->
        legacyRepository().loadPlaybackResumeBucketForApp(candidate)
    }
}

private fun resolvePlaybackResumeRecordForSources(
    bucket: PlaybackResumeBucket?,
    sources: List<top.jlen.vod.data.PlaySource>
): PlaybackResumeRecord? {
    if (bucket == null) return null
    val latestRecord = bucket.latestOrLastSourceRecord() ?: return null
    if (sources.isEmpty()) return latestRecord

    val resolvedIndex = when {
        latestRecord.sourceName.isNotBlank() -> sources.indexOfFirst {
            it.name.equals(latestRecord.sourceName, ignoreCase = true)
        }
        else -> -1
    }.takeIf { it >= 0 } ?: latestRecord.sourceIndex.coerceIn(0, sources.lastIndex)

    val selectedSource = sources.getOrNull(resolvedIndex)
    return bucket.recordForSource(
        sourceName = selectedSource?.name.orEmpty(),
        sourceIndex = resolvedIndex
    ) ?: latestRecord.copy(sourceIndex = resolvedIndex)
}
