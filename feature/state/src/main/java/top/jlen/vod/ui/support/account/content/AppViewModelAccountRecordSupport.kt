package top.jlen.vod.ui

import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserCenterPage

internal fun mergeAccountItems(
    current: List<UserCenterItem>,
    incoming: List<UserCenterItem>
): List<UserCenterItem> = (current + incoming)
    .distinctBy { item -> "${item.recordId}:${item.actionUrl}:${item.vodId}" }

internal fun accountStateWithFavoritePage(
    accountState: AccountUiState,
    page: UserCenterPage,
    append: Boolean
): AccountUiState = accountState.copy(
    isContentLoading = false,
    favoriteItems = mergeAccountItems(
        current = if (append) accountState.favoriteItems else emptyList(),
        incoming = page.items
    ),
    favoriteNextPageUrl = page.nextPageUrl
)

internal fun accountStateWithHistoryPage(
    accountState: AccountUiState,
    page: UserCenterPage,
    append: Boolean
): HistoryPageState {
    val mergedItems = mergeAccountItems(
        current = if (append) accountState.historyItems else emptyList(),
        incoming = page.items
    )
    return HistoryPageState(
        accountState = accountState.copy(
            isContentLoading = false,
            historyItems = mergedItems,
            historyNextPageUrl = page.nextPageUrl
        ),
        mergedItems = mergedItems
    )
}

internal fun accountStateRemovingFavorite(
    accountState: AccountUiState,
    recordId: String
): AccountUiState = accountState.copy(
    favoriteItems = accountState.favoriteItems.filterNot { item -> item.recordId == recordId }
)

internal fun accountStateRemovingFavoriteByVodId(
    accountState: AccountUiState,
    vodId: String
): AccountUiState = accountState.copy(
    favoriteItems = accountState.favoriteItems.filterNot { item -> item.vodId == vodId }
)

internal fun accountStateClearingFavorites(accountState: AccountUiState): AccountUiState = accountState.copy(
    favoriteItems = emptyList(),
    favoriteNextPageUrl = null
)

internal fun accountStateRemovingHistory(
    accountState: AccountUiState,
    recordId: String
): AccountUiState = accountState.copy(
    historyItems = accountState.historyItems.filterNot { item -> item.recordId == recordId }
)

internal fun accountStateClearingHistory(accountState: AccountUiState): AccountUiState = accountState.copy(
    historyItems = emptyList(),
    historyNextPageUrl = null
)

internal fun accountStateWithUpdatedHistoryResume(
    accountState: AccountUiState,
    vodId: String,
    sourceIndex: Int,
    episodeIndex: Int,
    sourceName: String
): AccountUiState {
    val normalizedVodId = vodId.trim()
    if (normalizedVodId.isBlank()) return accountState

    return accountState.copy(
        historyItems = accountState.historyItems.map { item ->
            val itemVodId = resolveHistoryItemVodId(item)
            if (itemVodId == normalizedVodId) {
                item.copy(
                    sourceIndex = sourceIndex,
                    episodeIndex = episodeIndex,
                    sourceName = sourceName.ifBlank { item.sourceName }
                )
            } else {
                item
            }
        }
    )
}

private fun resolveHistoryItemVodId(item: UserCenterItem): String =
    item.vodId.trim()
        .ifBlank {
            Regex("""/vodplay/([^/-?.]+)""")
                .find(item.playUrl.ifBlank { item.actionUrl })
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }
        .ifBlank {
            Regex("""/voddetail/([^/.?]+)""")
                .find(item.actionUrl)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }
