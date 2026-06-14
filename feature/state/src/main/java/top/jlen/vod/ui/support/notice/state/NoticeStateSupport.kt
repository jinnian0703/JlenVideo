package top.jlen.vod.ui

import top.jlen.vod.data.AppNotice

internal fun beginNoticeRefresh(noticeState: NoticeUiState): NoticeUiState =
    noticeState.copy(isLoading = true, error = null)

internal fun noticeStateWithLoadedNotices(
    noticeState: NoticeUiState,
    notices: List<AppNotice>,
    unreadNoticeIds: Set<String>,
    pendingDialogNotices: List<AppNotice>
): NoticeUiState {
    val currentDialogId = noticeState.dialogNotice?.id.orEmpty()
    val normalizedPendingDialogs = pendingDialogNotices
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
    val preservedDialog = normalizedPendingDialogs.firstOrNull { it.id == currentDialogId }
    val activeDialog = preservedDialog ?: normalizedPendingDialogs.firstOrNull()
    return noticeState.copy(
        isLoading = false,
        error = null,
        notices = notices,
        unreadNoticeIds = unreadNoticeIds,
        dialogNotice = activeDialog,
        pendingDialogNotices = normalizedPendingDialogs.filterNot { it.id == activeDialog?.id }
    )
}

internal fun noticeStateWithRefreshError(
    noticeState: NoticeUiState,
    errorMessage: String
): NoticeUiState = noticeState.copy(
    isLoading = false,
    error = errorMessage
)

internal fun noticeStateAfterDialogDismiss(
    noticeState: NoticeUiState,
    unreadNoticeIds: Set<String>
): NoticeUiState {
    val nextDialog = noticeState.pendingDialogNotices.firstOrNull()
    return noticeState.copy(
        dialogNotice = nextDialog,
        pendingDialogNotices = noticeState.pendingDialogNotices.drop(1),
        unreadNoticeIds = unreadNoticeIds
    )
}

internal fun noticeStateAfterNoticeOpened(
    noticeState: NoticeUiState,
    noticeId: String,
    unreadNoticeIds: Set<String>
): NoticeUiState {
    val normalized = noticeId.trim()
    val remainingDialogs = noticeState.pendingDialogNotices.filterNot { it.id == normalized }
    if (noticeState.dialogNotice?.id != normalized) {
        return noticeState.copy(
            pendingDialogNotices = remainingDialogs,
            unreadNoticeIds = unreadNoticeIds
        )
    }
    val nextDialog = remainingDialogs.firstOrNull()
    return noticeState.copy(
        dialogNotice = nextDialog,
        pendingDialogNotices = remainingDialogs.drop(1),
        unreadNoticeIds = unreadNoticeIds
    )
}

internal fun pendingNoticeDialogs(
    notices: List<AppNotice>,
    unreadNoticeIds: Set<String>
): List<AppNotice> = notices.filter { notice ->
    notice.id.isNotBlank() && notice.id in unreadNoticeIds
}
