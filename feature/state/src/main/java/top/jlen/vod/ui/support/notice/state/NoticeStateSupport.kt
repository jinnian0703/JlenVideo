package top.jlen.vod.ui

import top.jlen.vod.data.AppNotice

internal fun beginNoticeRefresh(noticeState: NoticeUiState): NoticeUiState =
    noticeState.copy(isLoading = true, error = null)

internal fun noticeStateWithLoadedNotices(
    noticeState: NoticeUiState,
    notices: List<AppNotice>,
    unreadNoticeIds: Set<String>,
    pendingNotice: AppNotice?
): NoticeUiState {
    val currentDialogId = noticeState.dialogNotice?.id.orEmpty()
    val preservedDialog = notices.firstOrNull { it.id == currentDialogId }
    return noticeState.copy(
        isLoading = false,
        error = null,
        notices = notices,
        unreadNoticeIds = unreadNoticeIds,
        dialogNotice = preservedDialog ?: pendingNotice
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
): NoticeUiState = noticeState.copy(
    dialogNotice = null,
    unreadNoticeIds = unreadNoticeIds
)

internal fun noticeStateAfterNoticeOpened(
    noticeState: NoticeUiState,
    noticeId: String,
    unreadNoticeIds: Set<String>
): NoticeUiState = noticeState.copy(
    dialogNotice = noticeState.dialogNotice?.takeUnless { it.id == noticeId },
    unreadNoticeIds = unreadNoticeIds
)
