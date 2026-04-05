package top.jlen.vod.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.PlaySource
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
    episodeIndex: Int
) {
    updatePlayerState(
        buildPlayerState(
            title = title,
            item = item,
            sources = sources,
            sourceIndex = sourceIndex,
            episodeIndex = episodeIndex
        )
    )
    legacyResolveCurrentPlayerUrl()
    legacyRecordCurrentPlayback()
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectPlayerEpisode(index: Int) {
    val updatedState = updatePlayerEpisodeSelection(currentPlayerState(), index) ?: return
    updatePlayerState(updatedState)
    legacyResolveCurrentPlayerUrl()
    legacyRecordCurrentPlayback()
}

internal fun LegacyStateRuntimeViewModelCore.legacySelectPlayerSource(index: Int) {
    val updatedState = updatePlayerSourceSelection(currentPlayerState(), index) ?: return
    updatePlayerState(updatedState)
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
}

internal fun LegacyStateRuntimeViewModelCore.legacySyncFromFullscreen(result: FullscreenPlaybackResult) {
    val syncState = syncPlayerStateFromFullscreen(currentPlayerState(), result)
    updatePlayerState(syncState.playerState)
    if (syncState.shouldResolveCurrentUrl) {
        legacyResolveCurrentPlayerUrl()
    }
}

internal fun LegacyStateRuntimeViewModelCore.legacyRecordCurrentPlayback() {
    val item = currentPlayerState().item ?: return
    val episodePageUrl = currentPlayerState().episodePageUrl
    if (!currentAccountState().session.isLoggedIn || episodePageUrl.isBlank()) return

    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) { legacyRepository().addPlayRecordForApp(item, episodePageUrl) }
        }.onSuccess {
            if (currentAccountState().selectedSection == AccountSection.History) {
                selectAccountSection(AccountSection.History, forceRefresh = true)
            }
        }
    }
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
