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
import top.jlen.vod.data.MembershipInfo
import top.jlen.vod.data.MembershipPlan
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.RegisterEditor
import top.jlen.vod.data.SearchHistoryStore
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfileEditor
import top.jlen.vod.data.VodItem

open class LegacyStateRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppleCmsRepository(application)
    private val searchHistoryStore = SearchHistoryStore(application)
    private val searchResultScrollPositions = mutableMapOf<String, SearchResultScrollPosition>()
    private var hasEnteredAccountScreen = false
    private var searchJob: Job? = null
    private var searchEnrichJob: Job? = null
    private var historyEnrichJob: Job? = null
    private var searchRequestVersion: Long = 0L
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
        onSuccess: () -> Unit
    ) {
        runAccountAction(block, onSuccess)
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

    internal fun nextSearchRequestVersion(): Long {
        searchRequestVersion += 1L
        return searchRequestVersion
    }

    internal fun currentSearchRequestVersion(): Long = searchRequestVersion

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

    fun reportHeartbeat(route: String) {
        val normalizedRoute = route.trim().ifBlank { "home" }
        val userId = accountState.session.userId
        val vodId = if (normalizedRoute == "player") resolveHeartbeatVodId(playerState) else ""
        val sid = if (normalizedRoute == "player") playerState.selectedSourceIndex + 1 else null
        val nid = if (normalizedRoute == "player") playerState.selectedEpisodeIndex + 1 else null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.reportHeartbeat(
                    route = normalizedRoute,
                    userId = userId,
                    vodId = vodId,
                    sid = sid,
                    nid = nid
                )
            }
        }
    }

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

    fun saveProfile() = legacySaveProfile()

    fun uploadPortrait(uri: Uri) = legacyUploadPortrait(uri)

    fun sendEmailBindCode() = legacySendEmailBindCode()

    fun bindEmail() = legacyBindEmail()

    fun unbindEmail() = legacyUnbindEmail()

    fun sendRegisterCode() = legacySendRegisterCode()

    fun register() = legacyRegister()

    fun findPassword() = legacyFindPassword()

    fun addCurrentDetailFavorite() {
        val item = detailState.item ?: return
        if (!accountState.session.isLoggedIn) {
            detailState = detailStateWithActionMessage(detailState, "请先登录后再收藏", true)
            return
        }
        if (detailState.isFavorited) {
            detailState = detailStateWithActionMessage(detailState, "已在收藏中", false)
            return
        }
        if (detailState.isActionLoading) return
        viewModelScope.launch {
            detailState = beginDetailFavoriteAction(detailState)
            runCatching {
                withContext(Dispatchers.IO) { repository.addFavoriteForApp(item) }
            }.onSuccess { message ->
                val normalizedMessage = normalizeFavoriteActionMessage(message)
                detailState = detailStateWithFavoriteSuccess(detailState, normalizedMessage)
                if (accountState.selectedSection == AccountSection.Favorites) {
                    selectAccountSection(AccountSection.Favorites, forceRefresh = true)
                }
            }.onFailure { error ->
                val isDuplicate = isDuplicateFavoriteMessage(error.message.orEmpty())
                detailState = detailStateWithFavoriteFailure(
                    detailState = detailState,
                    message = if (isDuplicate) "已在收藏中" else toUserFacingMessage(error, "收藏失败"),
                    isDuplicate = isDuplicate
                )
            }
        }
    }

    fun dismissDetailActionMessage() {
        if (detailState.actionMessage.isNullOrBlank()) return
        detailState = detailStateWithoutActionMessage(detailState)
    }

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
    ) {
        if (accountState.isActionLoading) return
        viewModelScope.launch {
            accountState = beginAccountAction(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.block() }
            }.onSuccess { message ->
                accountState = accountStateWithActionSuccess(accountState, message)
                onSuccess()
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithActionError(
                    accountState,
                    toUserFacingMessage(error, "操作失败")
                )
            }
        }
    }

    private fun handleAccountSessionExpired(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        val isExpired = message.contains("请先登录") || message.contains("登录已失效")
        if (!isExpired) return false

        repository.clearSession()
        accountState = expiredAccountState(accountState)
        return true
    }

    fun openHistoryRecord(item: UserCenterItem) {
        val resolvedVodId = resolveHistoryVodId(item)

        if (resolvedVodId.isBlank()) {
            openHistoryRecordDirectly(item)
            return
        }

        if (false) {
            playerState = failedHistoryPlayerState(item.title, "无法定位到影片详情")
            return
        }

        playerState = resolvingHistoryPlayerState(item.title)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.loadDetail(item.vodId) }
            }.onSuccess { detailItem ->
                if (detailItem == null) {
                    playerState = PlayerUiState(
                        title = item.title,
                        isResolving = false,
                        resolveError = "未找到影片详情"
                    )
                    return@onSuccess
                }

                val sources = repository.parseSources(detailItem)
                val selection = resolveHistoryResumeSelection(item, sources)

                openPlayer(
                    title = detailItem.displayTitle,
                    item = detailItem,
                    sources = sources,
                    sourceIndex = selection.sourceIndex,
                    episodeIndex = selection.episodeIndex
                )
            }.onFailure { error ->
                playerState = failedHistoryPlayerState(item.title, error.message ?: "继续观看失败")
            }
        }
    }

    fun resumeHistoryRecord(item: UserCenterItem) {
        val resolvedVodId = resolveHistoryVodId(item)

        if (resolvedVodId.isBlank()) {
            openHistoryRecordDirectly(item)
            return
        }

        playerState = resolvingHistoryPlayerState(item.title)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.loadDetail(resolvedVodId) }
            }.onSuccess { detailItem ->
                if (detailItem == null) {
                    openHistoryRecordDirectly(item)
                    return@onSuccess
                }

                val sources = repository.parseSources(detailItem)
                val selection = resolveHistoryResumeSelection(item, sources)

                openPlayer(
                    title = detailItem.displayTitle,
                    item = detailItem,
                    sources = sources,
                    sourceIndex = selection.sourceIndex,
                    episodeIndex = selection.episodeIndex
                )
            }.onFailure {
                openHistoryRecordDirectly(item)
            }
        }
    }

    private fun openHistoryRecordDirectly(item: UserCenterItem) {
        val resumeUrl = item.playUrl.ifBlank { item.actionUrl }
        if (resumeUrl.isBlank()) {
            playerState = failedHistoryPlayerState(item.title, "无法恢复该条播放记录")
            return
        }

        openPlayer(
            title = item.title,
            item = null,
            sources = buildHistoryFallbackSources(item, resumeUrl),
            sourceIndex = 0,
            episodeIndex = 0
        )
    }

    fun loadDetail(vodId: String) {
        viewModelScope.launch {
            val keepCurrentContent = detailState.item?.vodId == vodId
            detailState = beginDetailLoad(detailState, keepCurrentContent)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadDetail(vodId) }
            }.onSuccess { item ->
                if (item == null) {
                    detailState = missingDetailState()
                } else {
                    detailState = loadedDetailState(
                        item = item,
                        sources = repository.parseSources(item),
                        isFavorited = accountState.favoriteItems.any { favorite -> favorite.vodId == item.vodId }
                    )
                }
            }.onFailure { error ->
                detailState = detailStateWithLoadError(
                    toUserFacingMessage(error, "详情加载失败")
                )
            }
        }
    }

    fun refreshPlayerSources() {
        val currentItem = playerState.item ?: return
        val vodId = currentItem.vodId
        if (vodId.isBlank()) return

        val currentSourceName = playerState.currentSource?.name.orEmpty()
        val currentEpisodeUrl = playerState.currentEpisode?.url.orEmpty()
        val currentEpisodeName = playerState.currentEpisode?.name.orEmpty()

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.loadDetail(vodId) }
            }.onSuccess { detailItem ->
                if (detailItem == null || playerState.item?.vodId != vodId) {
                    return@onSuccess
                }

                val refreshedSources = repository.parseSources(detailItem)
                val refreshedState = playerStateWithRefreshedSources(
                    playerState = playerState,
                    detailItem = detailItem,
                    refreshedSources = refreshedSources,
                    currentSourceName = currentSourceName,
                    currentEpisodeUrl = currentEpisodeUrl,
                    currentEpisodeName = currentEpisodeName
                )
                playerState = refreshedState.playerState

                if (refreshedSources.isEmpty()) {
                    playerState = playerStateWithoutPlayableSource(playerState)
                    return@onSuccess
                }

                if (refreshedState.episodeChanged || playerState.resolvedUrl.isBlank()) {
                    resolveCurrentPlayerUrl()
                } else if (refreshedState.sourcesChanged) {
                    playerState = playerStateAfterSourceRefresh(playerState)
                }
            }
        }
    }

    fun selectSource(index: Int) {
        detailState = detailStateWithSelectedSource(detailState, index)
    }

    fun openPlayer(
        title: String,
        item: VodItem?,
        sources: List<PlaySource>,
        sourceIndex: Int,
        episodeIndex: Int
    ) {
        playerState = buildPlayerState(
            title = title,
            item = item,
            sources = sources,
            sourceIndex = sourceIndex,
            episodeIndex = episodeIndex
        )
        resolveCurrentPlayerUrl()
        recordCurrentPlayback()
    }

    fun selectPlayerEpisode(index: Int) {
        val updatedState = updatePlayerEpisodeSelection(playerState, index) ?: return
        playerState = updatedState
        resolveCurrentPlayerUrl()
        recordCurrentPlayback()
    }

    fun selectPlayerSource(index: Int) {
        val updatedState = updatePlayerSourceSelection(playerState, index) ?: return
        playerState = updatedState
        resolveCurrentPlayerUrl()
        recordCurrentPlayback()
    }

    fun playNextEpisode() {
        val nextIndex = playerState.selectedEpisodeIndex + 1
        if (nextIndex <= playerState.episodes.lastIndex) {
            selectPlayerEpisode(nextIndex)
        }
    }

    fun adoptDetectedStream(streamUrl: String) {
        playerState = applyDetectedStream(playerState, streamUrl) ?: return
    }

    fun reportTakeoverFailure(message: String) {
        playerState = applyTakeoverFailure(playerState, message)
    }

    fun updatePlaybackSnapshot(snapshot: PlaybackSnapshot) {
        if (!hasMeaningfulPlaybackChange(playerState.playbackSnapshot, snapshot)) return
        playerState = playerStateWithPlaybackSnapshot(playerState, snapshot)
    }

    fun syncFromFullscreen(result: FullscreenPlaybackResult) {
        val syncState = syncPlayerStateFromFullscreen(playerState, result)
        playerState = syncState.playerState
        if (syncState.shouldResolveCurrentUrl) {
            resolveCurrentPlayerUrl()
        }
    }

    private fun recordCurrentPlayback() {
        val item = playerState.item ?: return
        val episodePageUrl = playerState.episodePageUrl
        if (!accountState.session.isLoggedIn || episodePageUrl.isBlank()) return

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.addPlayRecordForApp(item, episodePageUrl) }
            }.onSuccess {
                if (accountState.selectedSection == AccountSection.History) {
                    selectAccountSection(AccountSection.History, forceRefresh = true)
                }
            }
        }
    }

    private fun resolveCurrentPlayerUrl() {
        val currentEpisode = playerState.currentEpisode ?: run {
            playerState = playerStateWithoutEpisode(playerState.title)
            return
        }
        val episodePageUrl = currentEpisode.url
        playerState = beginPlayerResolution(playerState)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.resolvePlayUrl(episodePageUrl) }
            }.onSuccess { resolved ->
                if (playerState.currentEpisode?.url != episodePageUrl) return@onSuccess
                playerState = playerStateWithResolvedUrl(playerState, resolved)
            }.onFailure {
                if (playerState.currentEpisode?.url != episodePageUrl) return@onFailure
                playerState = playerStateWithWebFallback(playerState, episodePageUrl)
            }
        }
    }

}

