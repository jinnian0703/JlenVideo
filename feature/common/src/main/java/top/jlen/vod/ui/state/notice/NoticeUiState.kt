package top.jlen.vod.ui

import top.jlen.vod.data.AppNotice

data class NoticeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val notices: List<AppNotice> = emptyList(),
    val unreadNoticeIds: Set<String> = emptySet(),
    val dialogNotice: AppNotice? = null
) {
    val activeNotices: List<AppNotice>
        get() = notices.filter { it.isActive }

    val hasUnreadActiveNotices: Boolean
        get() = unreadNoticeIds.isNotEmpty()
}
