package top.jlen.vod.ui

import androidx.compose.runtime.Composable
import top.jlen.vod.data.UserCenterItem

@Composable
internal fun AccountRecordPane(
    title: String,
    emptyMessage: String,
    isLoading: Boolean,
    items: List<UserCenterItem>,
    hasMore: Boolean,
    isActionLoading: Boolean,
    onLoadMore: () -> Unit,
    onPrimaryAction: (UserCenterItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) = LegacyAccountRecordPane(
    title = title,
    emptyMessage = emptyMessage,
    isLoading = isLoading,
    items = items,
    hasMore = hasMore,
    isActionLoading = isActionLoading,
    onLoadMore = onLoadMore,
    onPrimaryAction = onPrimaryAction,
    onDeleteItem = onDeleteItem,
    onClearAll = onClearAll
)

@Composable
internal fun AccountRecordCard(
    item: UserCenterItem,
    isActionLoading: Boolean,
    onPrimaryAction: (UserCenterItem) -> Unit,
    onDelete: (String) -> Unit
) = LegacyAccountRecordCard(
    item = item,
    isActionLoading = isActionLoading,
    onPrimaryAction = onPrimaryAction,
    onDelete = onDelete
)
