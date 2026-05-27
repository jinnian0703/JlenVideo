package top.jlen.vod.ui

import top.jlen.vod.data.UserCenterItem

internal fun LegacyStateRuntimeViewModelCore.currentHistoryOwnerKey(): String =
    currentAccountState().session.userId
        .trim()
        .ifBlank { currentAccountState().session.userName.trim() }

internal fun LegacyStateRuntimeViewModelCore.legacyShowCachedHistoryRecords(): Boolean {
    if (!currentAccountState().session.isLoggedIn) return false
    if (currentAccountState().historyItems.isNotEmpty()) return false
    val cachedItems = historyCacheStore().load(currentHistoryOwnerKey())
    if (cachedItems.isEmpty()) return false
    updateAccountState(accountStateWithCachedHistory(currentAccountState(), cachedItems))
    return true
}

internal fun LegacyStateRuntimeViewModelCore.legacySaveHistoryCache(items: List<UserCenterItem>) {
    if (!currentAccountState().session.isLoggedIn) return
    historyCacheStore().save(currentHistoryOwnerKey(), items)
}

internal fun LegacyStateRuntimeViewModelCore.legacyClearHistoryCache() {
    if (!currentAccountState().session.isLoggedIn) return
    historyCacheStore().clear(currentHistoryOwnerKey())
}

internal fun LegacyStateRuntimeViewModelCore.legacyUpsertCachedHistoryRecord(item: UserCenterItem) {
    if (!currentAccountState().session.isLoggedIn) return
    val baseItems = currentAccountState().historyItems.ifEmpty {
        historyCacheStore().load(currentHistoryOwnerKey())
    }
    val itemVodId = resolveHistoryItemVodId(item)
    val itemIdentity = historyItemPlaybackIdentity(item)
    val mergedItems = (listOf(item) + baseItems.filterNot { existing ->
        val existingIdentity = historyItemPlaybackIdentity(existing)
        existingIdentity.isNotBlank() && existingIdentity == itemIdentity ||
            (itemVodId.isNotBlank() &&
                resolveHistoryItemVodId(existing) == itemVodId &&
                existing.sourceIndex == item.sourceIndex &&
                existing.episodeIndex == item.episodeIndex)
    }).take(MAX_CACHED_HISTORY_ITEMS)
    updateAccountState(
        currentAccountState().copy(
            isContentLoading = false,
            error = null,
            historyItems = mergedItems
        )
    )
    historyCacheStore().save(currentHistoryOwnerKey(), mergedItems)
    legacyRebuildFollowContent()
}

private fun historyItemPlaybackIdentity(item: UserCenterItem): String =
    listOf(
        resolveHistoryItemVodId(item),
        item.playUrl.trim(),
        item.actionUrl.trim(),
        item.sourceIndex.takeIf { it >= 0 }?.toString().orEmpty(),
        item.episodeIndex.takeIf { it >= 0 }?.toString().orEmpty()
    )
        .filter(String::isNotBlank)
        .joinToString("|")

private const val MAX_CACHED_HISTORY_ITEMS = 120
