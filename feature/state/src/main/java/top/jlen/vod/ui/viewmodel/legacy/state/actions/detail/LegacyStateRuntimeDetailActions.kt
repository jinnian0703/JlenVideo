package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        runStateCatching {
            withContext(Dispatchers.IO) { legacyRepository().addFavoriteForApp(item) }
        }.onSuccess { message ->
            val normalizedMessage = normalizeFavoriteActionMessage(message)
            // 用户可能已切到其他详情页，只在仍是同一影片时更新详情状态
            if (currentDetailState().item?.vodId == item.vodId) {
                updateDetailState(detailStateWithFavoriteSuccess(currentDetailState(), normalizedMessage))
            }
            legacyUpsertFollowContentFromDetail(item)
            legacyRefreshFollowContent(forceRefresh = true)
            if (currentAccountState().selectedSection == AccountSection.Favorites) {
                selectAccountSection(AccountSection.Favorites, forceRefresh = true)
            }
        }.onFailure { error ->
            if (currentDetailState().item?.vodId != item.vodId) return@onFailure
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
            if (currentDetailState().item?.vodId == item.vodId) {
                updateDetailState(detailStateWithFavoriteRemoved(currentDetailState(), "已取消追剧"))
            }
            legacyRemoveFollowContentByVodId(favoriteVodId)
            legacyRefreshFollowContent(forceRefresh = true)
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
        updatePlayerState(failedHistoryPlayerState(item.title, "无法确认影片编号，请从片库重新打开"))
        return
    }

    updatePlayerState(resolvingHistoryPlayerState(item.title))
    viewModelScope.launch {
        runStateCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadMediaDetailForApp(resolvedVodId) }
        }.onSuccess { detailItem ->
            if (detailItem == null) {
                updatePlayerState(
                    PlayerUiState(
                        title = item.title,
                        isResolving = false,
                        resolveError = "未找到影片详情",
                        diagnostic = PlaybackDiagnostic(
                            type = "详情缺失",
                            title = "未找到影片详情",
                            message = "当前播放记录关联的影片详情已失效或无法读取。",
                            suggestion = "请返回详情页重新选择影片。"
                        )
                    )
                )
                return@onSuccess
            }

            val sources = legacyRepository().parseSources(detailItem)
            openHistoryWithProgress(item, detailItem, sources)
        }.onFailure { error ->
            updatePlayerState(failedHistoryPlayerState(item.title, error.message ?: "继续观看失败"))
        }
    }
}

private suspend fun LegacyStateRuntimeViewModelCore.openHistoryWithProgress(item: UserCenterItem, detail: VodItem, sources: List<top.jlen.vod.data.PlaySource>) {
    if (detail.playLocked) {
        updatePlayerState(failedHistoryPlayerState(item.title, "请先在影片详情页输入播放密码"))
        return
    }
    val repo = legacyRepository()
    val user = currentAccountState().session
    val local = repo.loadPlaybackResumeForApp(detail.vodId)
    val remote = if (user.isLoggedIn) runStateCatching { withContext(Dispatchers.IO) { repo.appApi.resume(detail.vodId, sources) } }.getOrNull() else null
    if (user != currentAccountState().session) return
    val latest = listOfNotNull(local, remote).filter { it.episodeIndex in sources.getOrNull(it.sourceIndex)?.episodes.orEmpty().indices }.maxByOrNull { it.updatedAt }
    val selected = resolveHistoryResumeSelection(item, sources)
    legacyOpenPlayer(detail.displayTitle, detail, sources, latest?.sourceIndex ?: selected.sourceIndex,
        latest?.episodeIndex ?: selected.episodeIndex, PlaybackSnapshot(positionMs = latest?.positionMs ?: 0L, speed = latest?.speed ?: 1f))
}

internal fun LegacyStateRuntimeViewModelCore.legacyLoadDetail(vodId: String, afterUnlock: Boolean = false): Job {
    currentDetailLoadJob()?.cancel()
    val state = currentDetailState()
    updateDetailState(beginDetailLoad(state, afterUnlock && state.matchesDetailRoute(vodId), vodId))
    return viewModelScope.launch {
        runStateCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadMediaDetailForApp(vodId) }
        }.onSuccess { item ->
            // 期间已切换到其他详情页时丢弃旧结果，避免覆盖新页面
            if (currentDetailState().requestedVodId != vodId) return@onSuccess
            if (item == null) {
                updateDetailState(missingDetailState(vodId))
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
                        pendingResumePlayback = resumeRecord,
                        requestedVodId = vodId
                    )
                )
                syncDetailAccount(vodId, item, sources)
            }
        }.onFailure { error ->
            if (currentDetailState().requestedVodId != vodId) return@onFailure
            if (error is top.jlen.vod.data.CmsApiError && error.code == 1012) {
                updateDetailState(DetailUiState(requestedVodId = vodId, detailLocked = true))
                return@onFailure
            }
            updateDetailState(detailStateWithLoadError(toUserFacingMessage(error, "详情加载失败"), vodId))
        }
    }.also(::replaceDetailLoadJob)
}

private suspend fun LegacyStateRuntimeViewModelCore.syncDetailAccount(vodId: String, item: VodItem, sources: List<top.jlen.vod.data.PlaySource>) {
    val session = currentAccountState().session
    if (!session.isLoggedIn || item.vodId.toLongOrNull() == null) return
    val repo = legacyRepository()
    val baseline = currentDetailState()
    val favorite = runStateCatching { withContext(Dispatchers.IO) { repo.appApi.favorite(item.vodId) } }.getOrNull()
    val remote = runStateCatching { withContext(Dispatchers.IO) { repo.appApi.resume(item.vodId, sources) } }.getOrNull()
    val state = currentDetailState()
    if (state.requestedVodId != vodId || currentAccountState().session != session) return
    val local = state.pendingResumePlayback
    val newer = remote?.takeIf { local == null || it.updatedAt / 1000 > local.updatedAt / 1000 }
    if (newer != null) repo.savePlaybackResumeForApp(newer)
    updateDetailState(state.copy(isFavorited = if (!state.isActionLoading && state.isFavorited == baseline.isFavorited) favorite ?: state.isFavorited else state.isFavorited,
        pendingResumePlayback = newer ?: local, playbackResumeBucket = if (newer != null) repo.loadPlaybackResumeBucketForApp(item.vodId) else state.playbackResumeBucket,
        selectedSourceIndex = if (state.selectedSourceIndex == baseline.selectedSourceIndex) newer?.sourceIndex ?: state.selectedSourceIndex else state.selectedSourceIndex))
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
