package top.jlen.vod.ui

import top.jlen.vod.data.UserCenterItem

internal fun beginAccountContentLoad(accountState: AccountUiState): AccountUiState =
    accountState.copy(isContentLoading = true, error = null)

internal fun accountStateWithContentError(
    accountState: AccountUiState,
    message: String
): AccountUiState = accountState.copy(
    isContentLoading = false,
    error = message
)

internal fun accountStateWithEnrichedHistoryItems(
    accountState: AccountUiState,
    enrichedByKey: Map<String, UserCenterItem>
): AccountUiState = accountState.copy(
    historyItems = accountState.historyItems.map { item ->
        enrichedByKey[historyRecordKey(item)]?.let { enriched ->
            item.copy(
                vodId = enriched.vodId.ifBlank { item.vodId },
                subtitle = enriched.subtitle.ifBlank { item.subtitle },
                sourceName = enriched.sourceName.ifBlank { item.sourceName }
            )
        } ?: item
    }
)
