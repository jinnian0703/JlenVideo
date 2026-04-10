package top.jlen.vod.ui

data class FollowUpItem(
    val vodId: String = "",
    val title: String = "",
    val poster: String? = null,
    val subtitle: String = "",
    val latestEpisodeLabel: String = "",
    val latestEpisodeIndex: Int? = null,
    val watchedEpisodeIndex: Int = -1,
    val watchedEpisodeLabel: String = "",
    val lastWatchedAtMillis: Long? = null,
    val lastWatchedAtText: String = "",
    val hasUpdate: Boolean = false,
    val updateLabel: String = "",
    val sourceName: String = ""
) {
    val primaryActionLabel: String
        get() = if (watchedEpisodeIndex >= 0) "继续观看" else "查看详情"
}

data class FollowUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val items: List<FollowUpItem> = emptyList()
) {
    val updatingItems: List<FollowUpItem>
        get() = items.filter(FollowUpItem::hasUpdate)

    val continueItems: List<FollowUpItem>
        get() = items.filterNot(FollowUpItem::hasUpdate)
}
