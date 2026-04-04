package top.jlen.vod.ui

import top.jlen.vod.data.UserCenterItem

internal data class HistoryPageState(
    val accountState: AccountUiState,
    val mergedItems: List<UserCenterItem>
)
