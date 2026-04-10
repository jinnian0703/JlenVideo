package top.jlen.vod.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import top.jlen.vod.AppRuntimeInfo
import top.jlen.vod.data.AppNotice
import top.jlen.vod.CrashLogger
import top.jlen.vod.data.AppUpdateInfo
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AppleCmsRepository
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.CategoryFilterGroup
import top.jlen.vod.data.Episode
import top.jlen.vod.data.FindPasswordEditor
import top.jlen.vod.data.HotSearchGroup
import top.jlen.vod.data.HomeSection
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.SearchHistoryStore
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem

open class LegacyStateRuntimeViewModelCore(application: Application) : AndroidViewModel(application) {
    private val repository = AppleCmsRepository(application)
    private val searchHistoryStore = SearchHistoryStore(application)
    private val searchResultScrollPositions = mutableMapOf<String, SearchResultScrollPosition>()
    private var hasEnteredAccountScreen = false
    private var searchJob: Job? = null
    private var searchSuggestJob: Job? = null
    private var searchEnrichJob: Job? = null
    private var historyEnrichJob: Job? = null
    private var searchRequestVersion: Long = 0L
    private var searchSuggestRequestVersion: Long = 0L
    private var historyEnrichVersion: Long = 0L

    var homeState by mutableStateOf(HomeUiState())
        private set

    var noticeState by mutableStateOf(NoticeUiState())
        private set

    var searchState by mutableStateOf(SearchUiState())
        private set

    var detailState by mutableStateOf(DetailUiState())
        private set

    var playerState by mutableStateOf(PlayerUiState())
        private set

    var accountState by mutableStateOf(AccountUiState())
        private set

    var followState by mutableStateOf(FollowUiState())
        private set

    internal fun legacyRepository(): AppleCmsRepository = repository

    internal fun currentAccountState(): AccountUiState = accountState

    internal fun updateAccountState(value: AccountUiState) {
        accountState = value
    }

    internal fun currentNoticeState(): NoticeUiState = noticeState

    internal fun updateNoticeState(value: NoticeUiState) {
        noticeState = value
    }

    internal fun currentHomeState(): HomeUiState = homeState

    internal fun updateHomeState(value: HomeUiState) {
        homeState = value
    }

    internal fun currentSearchState(): SearchUiState = searchState

    internal fun updateSearchState(value: SearchUiState) {
        searchState = value
    }

    internal fun currentDetailState(): DetailUiState = detailState

    internal fun updateDetailState(value: DetailUiState) {
        detailState = value
    }

    internal fun currentPlayerState(): PlayerUiState = playerState

    internal fun updatePlayerState(value: PlayerUiState) {
        playerState = value
    }

    internal fun currentFollowState(): FollowUiState = followState

    internal fun updateFollowState(value: FollowUiState) {
        followState = value
    }

    internal fun hasEnteredAccountScreenFlag(): Boolean = hasEnteredAccountScreen

    internal fun markAccountScreenEntered() {
        hasEnteredAccountScreen = true
    }

    internal fun clearSearchResultScrollPositions() {
        searchResultScrollPositions.clear()
    }

    internal fun runtimeLoadRegisterPage(forceRefresh: Boolean = false) {
        loadRegisterPage(forceRefresh)
    }

    internal fun runtimeLoadFindPasswordPage(forceRefresh: Boolean = false) {
        loadFindPasswordPage(forceRefresh)
    }

    internal fun runtimeLoadAccountProfile() {
        loadAccountProfile()
    }

    internal fun runtimeLoadFavoriteRecords(pageUrl: String? = null, append: Boolean = false) {
        loadFavoriteRecords(pageUrl, append)
    }

    internal fun runtimeLoadHistoryRecords(pageUrl: String? = null, append: Boolean = false) {
        loadHistoryRecords(pageUrl, append)
    }

    internal fun runtimeLoadMembership() {
        loadMembership()
    }

    internal fun runtimeRunAccountAction(
        block: suspend AppleCmsRepository.() -> String,
        successMessage: String? = null,
        onSuccess: () -> Unit
    ) {
        legacyRunAccountAction(
            block = block,
            successMessage = successMessage,
            onSuccess = onSuccess
        )
    }

    internal fun runtimeHandleAccountSessionExpired(error: Throwable): Boolean =
        handleAccountSessionExpired(error)

    internal fun searchHistoryStore(): SearchHistoryStore = searchHistoryStore

    internal fun getSearchResultScrollPosition(query: String): SearchResultScrollPosition? =
        searchResultScrollPositions[query]

    internal fun putSearchResultScrollPosition(query: String, position: SearchResultScrollPosition) {
        searchResultScrollPositions[query] = position
    }

    internal fun currentSearchJob(): Job? = searchJob

    internal fun replaceSearchJob(value: Job?) {
        searchJob = value
    }

    internal fun currentSearchEnrichJob(): Job? = searchEnrichJob

    internal fun replaceSearchEnrichJob(value: Job?) {
        searchEnrichJob = value
    }

    internal fun currentSearchSuggestJob(): Job? = searchSuggestJob

    internal fun replaceSearchSuggestJob(value: Job?) {
        searchSuggestJob = value
    }

    internal fun nextSearchRequestVersion(): Long {
        searchRequestVersion += 1L
        return searchRequestVersion
    }

    internal fun currentSearchRequestVersion(): Long = searchRequestVersion

    internal fun nextSearchSuggestRequestVersion(): Long {
        searchSuggestRequestVersion += 1L
        return searchSuggestRequestVersion
    }

    internal fun currentSearchSuggestRequestVersion(): Long = searchSuggestRequestVersion

    internal fun currentHistoryEnrichJob(): Job? = historyEnrichJob

    internal fun replaceHistoryEnrichJob(value: Job?) {
        historyEnrichJob = value
    }

    internal fun nextHistoryEnrichVersion(): Long {
        historyEnrichVersion += 1L
        return historyEnrichVersion
    }

    internal fun currentHistoryEnrichVersion(): Long = historyEnrichVersion

    init {
        searchState = searchStateWithHistory(searchState, searchHistoryStore.load())
        refreshCrashLog()
        refreshAccount()
        refreshHome()
        checkAppUpdate()
    }

    fun refreshAccount() = legacyRefreshAccount()

    fun ensureAccountScreenReady() = legacyEnsureAccountScreenReady()

    private fun hydrateAccountSession() = legacyHydrateAccountSession()

    fun refreshCrashLog() = legacyRefreshCrashLog()

    fun clearCrashLog() = legacyClearCrashLog()

    fun checkAppUpdate() = legacyCheckAppUpdate()

    fun refreshNotices(forceRefresh: Boolean = false) = legacyRefreshNotices(forceRefresh)

    fun dismissNoticeDialog() = legacyDismissNoticeDialog()

    fun markNoticeOpened(noticeId: String) = legacyMarkNoticeOpened(noticeId)

    fun findNotice(noticeId: String): AppNotice? =
        noticeState.notices.firstOrNull { it.id == noticeId }

    fun reportHeartbeat(route: String) = legacyReportHeartbeat(route)

    fun refreshHome(forceRefresh: Boolean = false) = legacyRefreshHome(forceRefresh)

    fun refreshHomeAndClearCaches() = legacyRefreshHomeAndClearCaches()

    fun selectCategory(category: AppleCmsCategory, forceRefresh: Boolean = false) =
        legacySelectCategory(category, forceRefresh)

    fun updateCategoryFilter(key: String, value: String) = legacyUpdateCategoryFilter(key, value)

    private fun loadCategoryContent(
        category: AppleCmsCategory,
        filters: Map<String, String>
    ) = legacyLoadCategoryContent(category, filters)

    fun loadMoreHome() = legacyLoadMoreHome()

    fun loadMoreCategory() = legacyLoadMoreCategory()

    fun updateQuery(query: String) = legacyUpdateQuery(query)

    fun searchHistory(keyword: String) = legacySearchHistory(keyword)

    fun clearSearchHistory() = legacyClearSearchHistory()

    fun search(keyword: String) = legacySearch(keyword)

    fun refreshHotSearches(forceRefresh: Boolean = false) = legacyRefreshHotSearches(forceRefresh)

    fun search() = legacySearch()

    fun getSearchResultScroll(query: String): SearchResultScrollPosition = legacyGetSearchResultScroll(query)

    fun updateSearchResultScroll(query: String, index: Int, offset: Int) =
        legacyUpdateSearchResultScroll(query, index, offset)

    fun ensureSearchResults(query: String) = legacyEnsureSearchResults(query)

    private fun performSearch(keyword: String) = legacyPerformSearch(keyword)

    fun loadMoreSearchResults() = legacyLoadMoreSearchResults()

    fun updateLoginUserName(value: String) = legacyUpdateLoginUserName(value)

    fun updateLoginPassword(value: String) = legacyUpdateLoginPassword(value)

    fun setAccountAuthMode(mode: AccountAuthMode) = legacySetAccountAuthMode(mode)

    fun refreshCategoryTab(forceRefresh: Boolean = false) = legacyRefreshCategoryTab(forceRefresh)

    fun updateRegisterEditor(transform: (RegisterEditor) -> RegisterEditor) =
        legacyUpdateRegisterEditor(transform)

    fun refreshRegisterCaptcha() = legacyRefreshRegisterCaptcha()

    fun updateFindPasswordEditor(transform: (FindPasswordEditor) -> FindPasswordEditor) =
        legacyUpdateFindPasswordEditor(transform)

    fun refreshFindPasswordCaptcha() = legacyRefreshFindPasswordCaptcha()

    fun updateProfileEditor(transform: (UserProfileEditor) -> UserProfileEditor) =
        legacyUpdateProfileEditor(transform)

    fun setProfileEditTab(editMode: Boolean) = legacySetProfileEditTab(editMode)

    fun selectAccountSection(section: AccountSection, forceRefresh: Boolean = false) =
        legacySelectAccountSection(section, forceRefresh)

    fun refreshSelectedAccountSection() = legacyRefreshSelectedAccountSection()

    fun loadMoreFavorites() = legacyLoadMoreFavorites()

    fun loadMoreHistory() = legacyLoadMoreHistory()

    fun deleteFavorite(recordId: String) = legacyDeleteFavorite(recordId)

    fun clearFavorites() = legacyClearFavorites()

    fun deleteHistory(recordId: String) = legacyDeleteHistory(recordId)

    fun clearHistory() = legacyClearHistory()

    fun upgradeMembership(plan: MembershipPlan) = legacyUpgradeMembership(plan)

    fun signInMembership() = legacySignInMembership()

    fun saveProfile() = legacySaveProfile()

    fun uploadPortrait(uri: Uri) = legacyUploadPortrait(uri)

    fun sendEmailBindCode() = legacySendEmailBindCode()

    fun bindEmail() = legacyBindEmail()

    fun unbindEmail() = legacyUnbindEmail()

    fun sendRegisterCode() = legacySendRegisterCode()

    fun register() = legacyRegister()

    fun findPassword() = legacyFindPassword()

    fun addCurrentDetailFavorite() = legacyAddCurrentDetailFavorite()

    fun cancelCurrentDetailFavorite() = legacyCancelCurrentDetailFavorite()

    fun dismissDetailActionMessage() = legacyDismissDetailActionMessage()

    fun login() = legacyLogin()

    fun logout() = legacyLogout()

    private fun loadRegisterPage(forceRefresh: Boolean = false) = legacyLoadRegisterPage(forceRefresh)

    private fun loadFindPasswordPage(forceRefresh: Boolean = false) = legacyLoadFindPasswordPage(forceRefresh)

    private fun loadAccountProfile() = legacyLoadAccountProfile()

    private fun loadFavoriteRecords(pageUrl: String? = null, append: Boolean = false) =
        legacyLoadFavoriteRecords(pageUrl, append)

    private fun loadHistoryRecords(pageUrl: String? = null, append: Boolean = false) =
        legacyLoadHistoryRecords(pageUrl, append)

    private fun enrichHistoryRecords(items: List<UserCenterItem>) = legacyEnrichHistoryRecords(items)

    private fun loadMembership() = legacyLoadMembership()

    private fun runAccountAction(
        block: suspend AppleCmsRepository.() -> String,
        onSuccess: () -> Unit
    ) = legacyRunAccountAction(block = block, onSuccess = onSuccess)

    private fun handleAccountSessionExpired(error: Throwable): Boolean =
        legacyHandleAccountSessionExpired(error)

    fun openHistoryRecord(item: UserCenterItem) = legacyOpenHistoryRecord(item)

    fun resumeHistoryRecord(item: UserCenterItem) = legacyResumeHistoryRecord(item)

    private fun openHistoryRecordDirectly(item: UserCenterItem) = legacyOpenHistoryRecordDirectly(item)

    fun loadDetail(vodId: String) = legacyLoadDetail(vodId)

    fun consumePendingDetailResume() {
        updateDetailState(detailStateWithoutPendingResume(currentDetailState()))
    }

    fun refreshPlayerSources() = legacyRefreshPlayerSources()

    fun refreshFollowContent(forceRefresh: Boolean = false) = legacyRefreshFollowContent(forceRefresh)

    fun rebuildFollowContent() = legacyRebuildFollowContent()

    fun selectSource(index: Int) = legacySelectSource(index)

    fun openPlayer(
        title: String,
        item: VodItem?,
        sources: List<PlaySource>,
        sourceIndex: Int,
        episodeIndex: Int,
        snapshot: PlaybackSnapshot = PlaybackSnapshot()
    ) = legacyOpenPlayer(title, item, sources, sourceIndex, episodeIndex, snapshot)

    fun selectPlayerEpisode(index: Int) = legacySelectPlayerEpisode(index)

    fun selectPlayerSource(index: Int) = legacySelectPlayerSource(index)

    fun playNextEpisode() = legacyPlayNextEpisode()

    fun adoptDetectedStream(streamUrl: String) = legacyAdoptDetectedStream(streamUrl)

    fun reportTakeoverFailure(message: String) = legacyReportTakeoverFailure(message)

    fun updatePlaybackSnapshot(snapshot: PlaybackSnapshot) = legacyUpdatePlaybackSnapshot(snapshot)

    fun syncFromFullscreen(result: FullscreenPlaybackResult) = legacySyncFromFullscreen(result)

    private fun recordCurrentPlayback() = legacyRecordCurrentPlayback()

    private fun resolveCurrentPlayerUrl() = legacyResolveCurrentPlayerUrl()

}

