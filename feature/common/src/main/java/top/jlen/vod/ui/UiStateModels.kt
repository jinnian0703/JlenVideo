package top.jlen.vod.ui

import top.jlen.vod.data.AppNotice
import top.jlen.vod.data.AppUpdateInfo
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.Episode
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.HomeSection
import top.jlen.vod.data.MembershipInfo
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem

data class HomeUiState(
    val isLoading: Boolean = true,
    val isHomeAppending: Boolean = false,
    val isCategoryLoading: Boolean = false,
    val isCategoryAppending: Boolean = false,
    val error: String? = null,
    val homeAppendError: String? = null,
    val categoryAppendError: String? = null,
    val slides: List<VodItem> = emptyList(),
    val hot: List<VodItem> = emptyList(),
    val featured: List<VodItem> = emptyList(),
    val latest: List<VodItem> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val homeVisibleCount: Int = 0,
    val homeCursor: String = "",
    val hasMoreHomeItems: Boolean = false,
    val homeFirstLoaded: Boolean = false,
    val categories: List<AppleCmsCategory> = emptyList(),
    val selectedCategory: AppleCmsCategory? = null,
    val selectedCategoryFilters: Map<String, String> = emptyMap(),
    val categoryVideos: List<VodItem> = emptyList(),
    val categoryVisibleCount: Int = 0,
    val categoryCursor: String = "",
    val hasMoreCategoryItems: Boolean = false,
    val categoryFirstLoaded: Boolean = false
) {
    val visibleLatest: List<VodItem>
        get() = latest.take(homeVisibleCount.coerceIn(0, latest.size))

    val hasMoreLatest: Boolean
        get() = homeVisibleCount < latest.size || hasMoreHomeItems

    val visibleCategoryVideos: List<VodItem>
        get() = categoryVideos.take(categoryVisibleCount.coerceIn(0, categoryVideos.size))

    val hasMoreCategoryVideos: Boolean
        get() = categoryVisibleCount < categoryVideos.size || hasMoreCategoryItems

    val categoryFilterGroups: List<CategoryFilterGroup>
        get() = selectedCategory?.filterGroups.orEmpty()
}

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

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isHotSearchLoading: Boolean = false,
    val error: String? = null,
    val appendError: String? = null,
    val hotSearchError: String? = null,
    val history: List<String> = emptyList(),
    val hotSearchGroups: List<HotSearchGroup> = emptyList(),
    val results: List<VodItem> = emptyList(),
    val cursor: String = "",
    val hasMore: Boolean = false,
    val firstLoaded: Boolean = false
)

enum class AccountSection {
    Profile,
    Favorites,
    History,
    Member,
    About
}

enum class AccountAuthMode {
    Login,
    Register,
    FindPassword,
    About
}

data class AccountUiState(
    val isLoading: Boolean = false,
    val isContentLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val isUpdateLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val userName: String = "",
    val password: String = "",
    val authMode: AccountAuthMode = AccountAuthMode.Login,
    val registerChannel: String = "email",
    val registerContactLabel: String = "” œ‰",
    val registerCodeLabel: String = "” œ‰—È÷§¬Î",
    val registerRequiresCode: Boolean = true,
    val registerRequiresVerify: Boolean = true,
    val registerCaptchaUrl: String = "",
    val registerCaptcha: ByteArray? = null,
    val registerEditor: RegisterEditor = RegisterEditor(),
    val findPasswordRequiresVerify: Boolean = true,
    val findPasswordCaptchaUrl: String = "",
    val findPasswordCaptcha: ByteArray? = null,
    val findPasswordEditor: FindPasswordEditor = FindPasswordEditor(),
    val session: AuthSession = AuthSession(),
    val selectedSection: AccountSection = AccountSection.Profile,
    val isProfileEditTab: Boolean = false,
    val profileFields: List<Pair<String, String>> = emptyList(),
    val profileEditor: UserProfileEditor = UserProfileEditor(),
    val favoriteItems: List<UserCenterItem> = emptyList(),
    val favoriteNextPageUrl: String? = null,
    val historyItems: List<UserCenterItem> = emptyList(),
    val historyNextPageUrl: String? = null,
    val membershipInfo: MembershipInfo = MembershipInfo(),
    val membershipPlans: List<MembershipPlan> = emptyList(),
    val updateInfo: AppUpdateInfo? = null,
    val hasCrashLog: Boolean = false,
    val latestCrashLog: String = ""
)

data class DetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val item: VodItem? = null,
    val sources: List<PlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val isActionLoading: Boolean = false,
    val actionMessage: String? = null,
    val isActionError: Boolean = false,
    val isFavorited: Boolean = false
) {
    val selectedSource: PlaySource?
        get() = sources.getOrNull(selectedSourceIndex)
}

data class PlayerUiState(
    val title: String = "",
    val item: VodItem? = null,
    val sources: List<PlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val resolvedUrl: String = "",
    val isResolving: Boolean = false,
    val useWebPlayer: Boolean = false,
    val resolveError: String? = null,
    val playbackSnapshot: PlaybackSnapshot = PlaybackSnapshot()
) {
    val currentSource: PlaySource?
        get() = sources.getOrNull(selectedSourceIndex)

    val sourceName: String
        get() = currentSource?.name.orEmpty()

    val episodes: List<Episode>
        get() = currentSource?.episodes.orEmpty()

    val currentEpisode: Episode?
        get() = episodes.getOrNull(selectedEpisodeIndex)

    val episodeName: String
        get() = currentEpisode?.name.orEmpty()

    val episodePageUrl: String
        get() = currentEpisode?.url.orEmpty()

    val playUrl: String
        get() = resolvedUrl.ifBlank { episodePageUrl }

    val hasNextEpisode: Boolean
        get() = selectedEpisodeIndex < episodes.lastIndex
}

