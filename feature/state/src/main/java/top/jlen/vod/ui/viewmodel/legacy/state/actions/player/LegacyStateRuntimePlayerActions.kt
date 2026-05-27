package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.normalizePlaybackSourceKey
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.VodItem

internal fun LegacyStateRuntimeViewModelCore.legacyRefreshPlayerSources() {
    val currentItem = currentPlayerState().item ?: return
    val vodId = currentItem.vodId
    if (vodId.isBlank()) return

    val currentSourceName = currentPlayerState().currentSource?.name.orEmpty()
    val currentEpisodeUrl = currentPlayerState().currentEpisode?.url.orEmpty()
    val currentEpisodeName = currentPlayerState().currentEpisode?.name.orEmpty()

    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().loadDetail(vodId) }
        }.onSuccess { detailItem ->
            if (detailItem == null || currentPlayerState().item?.vodId != vodId) {
                return@onSuccess
            }

            val refreshedSources = legacyRepository().parseSources(detailItem)
            val refreshedState = playerStateWithRefreshedSources(
                playerState = currentPlayerState(),
                detailItem = detailItem,
                refreshedSources = refreshedSources,
                currentSourceName = currentSourceName,
                currentEpisodeUrl = currentEpisodeUrl,
                currentEpisodeName = currentEpisodeName
            )
            updatePlayerState(refreshedState.playerState)

            if (refreshedSources.isEmpty()) {
                updatePlayerState(playerStateWithoutPlayableSource(currentPlayerState()))
                return@onSuccess
            }

            if (refreshedState.episodeChanged || currentPlayerState().resolvedUrl.isBlank()) {
                legacyResolveCurrentPlayerUrl()
            } else if (refreshedState.sourcesChanged) {
                updatePlayerState(playerStateAfterSourceRefresh(currentPlayerState()))
            }
        }
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyOpenPlayer(
    title: String,
    item: VodItem?,
    sources: List<PlaySource>,
    sourceIndex: Int,
    episodeIndex: Int,
    snapshot: PlaybackSnapshot = PlaybackSnapshot()
) {
    updatePlayerState(
        buildPlayerState(
            title = title,
            item = item,
            sources = sources,
            sourceIndex = sourceIndex,
            episodeIndex = episodeIndex,
            playbackSnapshot = snapshot
        )
    )
    persistCurrentPlaybackResume()
    legacyResolveCurrentPlayerUrl()
    legacyRecordCurrentPlayback()
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectPlayerEpisode(index: Int) {
    val updatedState = updatePlayerEpisodeSelection(currentPlayerState(), index) ?: return
    updatePlayerState(updatedState)
    persistCurrentPlaybackResume()
    legacyResolveCurrentPlayerUrl()
    legacyRecordCurrentPlayback()
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectPlayerSource(index: Int) {
    val playerState = currentPlayerState()
    val item = playerState.item
    val resolvedVodId = item?.let { resolvePlaybackResumeVodId(it, playerState.episodePageUrl) }.orEmpty()
    val safeIndex = index.coerceIn(0, (playerState.sources.lastIndex).coerceAtLeast(0))
    val targetSource = playerState.sources.getOrNull(safeIndex)
    val resumeRecord = if (resolvedVodId.isNotBlank() && targetSource != null) {
        legacyRepository().loadPlaybackResumeForSourceForApp(
            vodId = resolvedVodId,
            sourceName = targetSource.name,
            sourceIndex = safeIndex
        )
    } else {
        null
    }
    val updatedState = updatePlayerSourceSelection(playerState, index, resumeRecord) ?: return
    updatePlayerState(updatedState)
    persistCurrentPlaybackResume()
    legacyResolveCurrentPlayerUrl()
    legacyRecordCurrentPlayback()
}

internal fun LegacyStateRuntimeViewModelCore.legacyPlayNextEpisode() {
    val nextIndex = currentPlayerState().selectedEpisodeIndex + 1
    if (nextIndex <= currentPlayerState().episodes.lastIndex) {
        legacySelectPlayerEpisode(nextIndex)
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyAdoptDetectedStream(streamUrl: String) {
    updatePlayerState(applyDetectedStream(currentPlayerState(), streamUrl) ?: return)
}

internal fun LegacyStateRuntimeViewModelCore.legacyReportTakeoverFailure(message: String) {
    updatePlayerState(applyTakeoverFailure(currentPlayerState(), message))
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpdatePlaybackSnapshot(snapshot: PlaybackSnapshot) {
    if (!hasMeaningfulPlaybackChange(currentPlayerState().playbackSnapshot, snapshot)) return
    updatePlayerState(playerStateWithPlaybackSnapshot(currentPlayerState(), snapshot))
    persistCurrentPlaybackResume()
}

internal fun LegacyStateRuntimeViewModelCore.legacySyncFromFullscreen(result: FullscreenPlaybackResult) {
    val syncState = syncPlayerStateFromFullscreen(currentPlayerState(), result)
    updatePlayerState(syncState.playerState)
    persistCurrentPlaybackResume()
    if (syncState.shouldResolveCurrentUrl) {
        legacyResolveCurrentPlayerUrl()
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRecordCurrentPlayback() {
    val item = currentPlayerState().item ?: return
    val episodePageUrl = currentPlayerState().episodePageUrl
    persistCurrentPlaybackResume()
    if (!currentAccountState().session.isLoggedIn || episodePageUrl.isBlank()) return

    buildOptimisticHistoryRecord(item, episodePageUrl)?.let { record ->
        legacyUpsertCachedHistoryRecord(record)
    }

    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().addPlayRecordForApp(item, episodePageUrl) }
        }.onSuccess {
            if (currentAccountState().historyItems.any { it.recordId.startsWith("local:") }) {
                runtimeLoadHistoryRecords()
            }
        }
    }
}

private fun LegacyStateRuntimeViewModelCore.buildOptimisticHistoryRecord(
    item: VodItem,
    episodePageUrl: String
): UserCenterItem? {
    val playerState = currentPlayerState()
    val resolvedVodId = resolvePlaybackResumeVodId(item, episodePageUrl)
    val title = item.displayTitle.ifBlank { playerState.title }
    if (title.isBlank() || (resolvedVodId.isBlank() && episodePageUrl.isBlank())) return null

    val sourceName = playerState.currentSource?.name.orEmpty()
    val episodeName = playerState.currentEpisode?.name.orEmpty()
    val subtitle = listOf(sourceName, episodeName, item.resolvedSubtitle)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString(" | ")
    return UserCenterItem(
        recordId = localHistoryRecordId(
            vodId = resolvedVodId,
            playUrl = episodePageUrl,
            sourceIndex = playerState.selectedSourceIndex,
            episodeIndex = playerState.selectedEpisodeIndex
        ),
        vodId = resolvedVodId,
        title = title,
        subtitle = subtitle,
        actionLabel = "继续观看",
        actionUrl = buildOptimisticDetailUrl(item, resolvedVodId),
        playUrl = episodePageUrl,
        sourceName = sourceName,
        sourceIndex = playerState.selectedSourceIndex,
        episodeIndex = playerState.selectedEpisodeIndex
    )
}

private fun localHistoryRecordId(
    vodId: String,
    playUrl: String,
    sourceIndex: Int,
    episodeIndex: Int
): String =
    "local:" + listOf(vodId, playUrl, sourceIndex.toString(), episodeIndex.toString())
        .joinToString(":")
        .hashCode()
        .let(java.lang.Integer::toHexString)

private fun buildOptimisticDetailUrl(item: VodItem, vodId: String): String =
    item.detailUrl.trim()
        .ifBlank {
            vodId.takeIf(String::isNotBlank)
                ?.let { "/index.php/voddetail/$it.html" }
                .orEmpty()
        }

private fun LegacyStateRuntimeViewModelCore.persistCurrentPlaybackResume() {
    val playerState = currentPlayerState()
    val item = playerState.item ?: return
    val resolvedVodId = resolvePlaybackResumeVodId(item, playerState.episodePageUrl)
    if (resolvedVodId.isBlank()) return

    val normalizedSnapshot = normalizePlaybackResumeSnapshot(playerState.playbackSnapshot)
    val record = PlaybackResumeRecord(
        vodId = resolvedVodId,
        sourceKey = normalizePlaybackSourceKey(
            sourceName = playerState.currentSource?.name.orEmpty(),
            sourceIndex = playerState.selectedSourceIndex
        ),
        sourceName = playerState.currentSource?.name.orEmpty(),
        sourceIndex = playerState.selectedSourceIndex,
        episodeIndex = playerState.selectedEpisodeIndex,
        positionMs = normalizedSnapshot.positionMs,
        speed = normalizedSnapshot.speed,
        updatedAt = System.currentTimeMillis()
    )

    viewModelScope.launch(Dispatchers.IO) {
        legacyRepository().savePlaybackResumeForApp(record)
    }
    updateAccountState(
        accountStateWithUpdatedHistoryResume(
            accountState = currentAccountState(),
            vodId = resolvedVodId,
            sourceIndex = playerState.selectedSourceIndex,
            episodeIndex = playerState.selectedEpisodeIndex,
            sourceName = playerState.currentSource?.name.orEmpty()
        )
    )
    legacyRebuildFollowContent()
}

private fun resolvePlaybackResumeVodId(item: VodItem, episodePageUrl: String): String =
    item.vodId.trim()
        .ifBlank { item.siteVodId.trim() }
        .ifBlank {
            Regex("""/vodplay/([^/-]+)""")
                .find(episodePageUrl)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }
        .ifBlank {
            Regex("""/voddetail/([^/.]+)""")
                .find(item.detailUrl)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }

private fun normalizePlaybackResumeSnapshot(snapshot: PlaybackSnapshot): PlaybackSnapshot {
    val durationMs = snapshot.durationMs.coerceAtLeast(0L)
    val normalizedPosition = snapshot.positionMs
        .coerceAtLeast(0L)
        .let { position ->
            if (durationMs > 60_000L && position >= durationMs - 10_000L) 0L else position
        }
    return snapshot.copy(
        positionMs = normalizedPosition,
        speed = snapshot.speed.coerceIn(0.5f, 3f)
    )
}

internal fun LegacyStateRuntimeViewModelCore.legacyResolveCurrentPlayerUrl() {
    val currentEpisode = currentPlayerState().currentEpisode ?: run {
        updatePlayerState(playerStateWithoutEpisode(currentPlayerState().title))
        return
    }
    val episodePageUrl = currentEpisode.url
    updatePlayerState(beginPlayerResolution(currentPlayerState()))
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().resolvePlayUrl(episodePageUrl) }
        }.onSuccess { resolved ->
            if (currentPlayerState().currentEpisode?.url != episodePageUrl) return@onSuccess
            updatePlayerState(playerStateWithResolvedUrl(currentPlayerState(), resolved))
        }.onFailure {
            if (currentPlayerState().currentEpisode?.url != episodePageUrl) return@onFailure
            updatePlayerState(playerStateWithWebFallback(currentPlayerState(), episodePageUrl))
        }
    }
}
