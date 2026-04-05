package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.UserCenterItem

internal fun LegacyStateRuntimeViewModelCore.legacyAddCurrentDetailFavorite() {
    val item = currentDetailState().item ?: return
    if (!currentAccountState().session.isLoggedIn) {
        updateDetailState(detailStateWithActionMessage(currentDetailState(), "请先登录后再收藏", true))
        return
    }
    if (currentDetailState().isFavorited) {
        updateDetailState(detailStateWithActionMessage(currentDetailState(), "已在收藏中", false))
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
            if (currentAccountState().selectedSection == AccountSection.Favorites) {
                selectAccountSection(AccountSection.Favorites, forceRefresh = true)
            }
        }.onFailure { error ->
            val isDuplicate = isDuplicateFavoriteMessage(error.message.orEmpty())
            updateDetailState(
                detailStateWithFavoriteFailure(
                    detailState = currentDetailState(),
                    message = if (isDuplicate) "已在收藏中" else toUserFacingMessage(error, "收藏失败"),
                    isDuplicate = isDuplicate
                )
            )
        }
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
                updateDetailState(
                    loadedDetailState(
                        item = item,
                        sources = legacyRepository().parseSources(item),
                        isFavorited = currentAccountState().favoriteItems.any { favorite -> favorite.vodId == item.vodId }
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
