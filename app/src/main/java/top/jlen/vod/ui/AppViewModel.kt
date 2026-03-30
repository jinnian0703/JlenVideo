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
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import top.jlen.vod.data.AppNotice
import top.jlen.vod.BuildConfig
import top.jlen.vod.CrashLogger
import top.jlen.vod.data.AppUpdateInfo
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AppleCmsRepository
import top.jlen.vod.data.AuthSession
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

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppleCmsRepository(application)
    private val searchHistoryStore = SearchHistoryStore(application)
    private val searchResultScrollPositions = mutableMapOf<String, SearchResultScrollPosition>()
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

    init {
        searchState = searchState.copy(history = searchHistoryStore.load())
        refreshCrashLog()
        refreshAccount()
        refreshNotices()
        refreshHome()
        checkAppUpdate()
    }

    fun refreshAccount() {
        val session = repository.currentSession()
        accountState = if (session.isLoggedIn) {
            accountState.copy(
                session = mergeAccountSession(session),
                error = null
            )
        } else {
            AccountUiState(
                userName = accountState.userName,
                session = session,
                updateInfo = accountState.updateInfo,
                hasCrashLog = accountState.hasCrashLog,
                latestCrashLog = accountState.latestCrashLog
            )
        }
        if (session.isLoggedIn) {
            hydrateAccountSession()
            selectAccountSection(accountState.selectedSection, forceRefresh = true)
        }
    }

    private fun hydrateAccountSession() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.loadUserProfile() }
            }.onSuccess { page ->
                accountState = accountState.copy(
                    session = mergeAccountSession(page.session)
                )
            }
        }
    }

    fun refreshCrashLog() {
        val latestCrashLog = CrashLogger.readLatest(getApplication())
        accountState = accountState.copy(
            hasCrashLog = latestCrashLog.isNotBlank(),
            latestCrashLog = latestCrashLog
        )
    }

    fun clearCrashLog() {
        CrashLogger.clear(getApplication())
        accountState = accountState.copy(
            hasCrashLog = false,
            latestCrashLog = "",
            message = "已清空崩溃日志",
            error = null
        )
    }

    fun checkAppUpdate() {
        if (accountState.isUpdateLoading) return
        viewModelScope.launch {
            accountState = accountState.copy(isUpdateLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadLatestRelease(BuildConfig.VERSION_NAME)
                }
            }.onSuccess { updateInfo ->
                accountState = accountState.copy(
                    isUpdateLoading = false,
                    updateInfo = updateInfo,
                    error = null
                )
            }.onFailure {
                accountState = accountState.copy(
                    isUpdateLoading = false,
                    message = "检查更新失败，请稍后重试"
                )
            }
        }
    }

    fun refreshNotices(forceRefresh: Boolean = false) {
        if (noticeState.isLoading && !forceRefresh) return
        val userId = accountState.session.userId
        viewModelScope.launch {
            noticeState = noticeState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadNotices(
                        appVersion = BuildConfig.VERSION_NAME,
                        userId = userId,
                        forceRefresh = forceRefresh
                    )
                }
            }.onSuccess { notices ->
                val currentDialogId = noticeState.dialogNotice?.id.orEmpty()
                val preservedDialog = notices.firstOrNull { it.id == currentDialogId }
                val unreadNoticeIds = repository.unreadActiveNoticeIds(notices)
                noticeState = noticeState.copy(
                    isLoading = false,
                    error = null,
                    notices = notices,
                    unreadNoticeIds = unreadNoticeIds,
                    dialogNotice = preservedDialog ?: repository.pickPendingNotice(notices)
                )
            }.onFailure { error ->
                noticeState = noticeState.copy(
                    isLoading = false,
                    error = toUserFacingMessage(error, "公告加载失败", serviceLabel = "公告服务")
                )
            }
        }
    }

    fun dismissNoticeDialog() {
        val dismissedId = noticeState.dialogNotice?.id
            ?.takeIf(String::isNotBlank)
        dismissedId?.let(repository::markNoticeDismissed)
        noticeState = noticeState.copy(
            dialogNotice = null,
            unreadNoticeIds = dismissedId?.let { noticeState.unreadNoticeIds - it } ?: noticeState.unreadNoticeIds
        )
    }

    fun markNoticeOpened(noticeId: String) {
        val normalized = noticeId.trim()
        normalized
            .takeIf(String::isNotBlank)
            ?.let(repository::markNoticeDismissed)
        noticeState = noticeState.copy(
            dialogNotice = noticeState.dialogNotice?.takeUnless { it.id == normalized },
            unreadNoticeIds = if (normalized.isBlank()) noticeState.unreadNoticeIds else noticeState.unreadNoticeIds - normalized
        )
    }

    fun findNotice(noticeId: String): AppNotice? =
        noticeState.notices.firstOrNull { it.id == noticeId }

    private fun resolveHeartbeatVodId(): String {
        val item = playerState.item
        return item?.siteVodId
            .orEmpty()
            .ifBlank { item?.vodId?.takeIf { it.all(Char::isDigit) }.orEmpty() }
            .ifBlank {
                Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
                    .find(playerState.episodePageUrl)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                    .takeIf { it.all(Char::isDigit) }
                    .orEmpty()
            }
    }

    fun reportHeartbeat(route: String) {
        val normalizedRoute = route.trim().ifBlank { "home" }
        val userId = accountState.session.userId
        val vodId = if (normalizedRoute == "player") resolveHeartbeatVodId() else ""
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

    fun refreshHome(forceRefresh: Boolean = false) {
        refreshNotices(forceRefresh = forceRefresh)
        viewModelScope.launch {
            homeState = homeState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHome(forceRefresh = forceRefresh) }
            }.onSuccess { payload ->
                val categories = payload.categories
                val defaultSelected = categories.firstOrNull()
                val cachedSelectedPage = defaultSelected?.let {
                    repository.peekCategoryPage(typeId = it.typeId, page = 1)
                }
                homeState = HomeUiState(
                    isLoading = false,
                    slides = payload.slides,
                    hot = payload.hot,
                    featured = payload.featured,
                    latest = payload.latest,
                    sections = payload.sections,
                    homeVisibleCount = payload.latest.initialGridVisibleCount(),
                    homePage = payload.latestPage,
                    homeTotalCount = payload.latestTotal,
                    hasMoreHomePages = payload.latestHasNextPage,
                    categories = categories,
                    selectedCategory = defaultSelected,
                    categoryVideos = cachedSelectedPage?.items ?: payload.categoryVideos,
                    categoryVisibleCount = (cachedSelectedPage?.items ?: payload.categoryVideos).initialGridVisibleCount(),
                    categoryPage = cachedSelectedPage?.page ?: payload.categoryPage,
                    categoryTotalCount = cachedSelectedPage?.totalItems ?: payload.categoryTotal,
                    hasMoreCategoryPages = cachedSelectedPage?.hasNextPage ?: payload.categoryHasNextPage
                )
                viewModelScope.launch(Dispatchers.IO) {
                    repository.prewarmCategoryFirstPages(forceRefresh = false)
                }
                if (defaultSelected != null && (cachedSelectedPage == null || forceRefresh)) {
                    selectCategory(defaultSelected, forceRefresh = forceRefresh)
                }
            }.onFailure { error ->
                homeState = homeState.copy(
                    isLoading = false,
                    error = toUserFacingMessage(error, "首页加载失败")
                )
            }
        }
    }

    fun selectCategory(category: AppleCmsCategory, forceRefresh: Boolean = false) {
        if (!forceRefresh && category.typeId == homeState.selectedCategory?.typeId && homeState.categoryVideos.isNotEmpty()) {
            return
        }
        val cachedPayload = if (!forceRefresh) {
            repository.peekCategoryPage(typeId = category.typeId, page = 1)
        } else {
            null
        }
        if (cachedPayload != null && cachedPayload.items.isNotEmpty()) {
            homeState = homeState.copy(
                selectedCategory = category,
                categoryVideos = cachedPayload.items,
                categoryVisibleCount = cachedPayload.items.initialGridVisibleCount(),
                categoryPage = cachedPayload.page,
                categoryTotalCount = cachedPayload.totalItems,
                hasMoreCategoryPages = cachedPayload.hasNextPage,
                isCategoryAppending = false,
                isCategoryLoading = false,
                error = null
            )
        }
        viewModelScope.launch {
            homeState = homeState.copy(
                selectedCategory = category,
                isCategoryLoading = cachedPayload == null || forceRefresh,
                isCategoryAppending = false,
                error = null
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.loadCategoryPage(category.typeId, page = 1, forceRefresh = forceRefresh) }
            }.onSuccess { payload ->
                homeState = homeState.copy(
                    categoryVideos = payload.items,
                    categoryVisibleCount = payload.items.initialGridVisibleCount(),
                    categoryPage = payload.page,
                    categoryTotalCount = payload.totalItems,
                    hasMoreCategoryPages = payload.hasNextPage,
                    isCategoryAppending = false,
                    isCategoryLoading = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isCategoryAppending = false,
                    isCategoryLoading = false,
                    error = toUserFacingMessage(error, "分类加载失败")
                )
            }
        }
    }

    fun loadMoreHome() {
        if (homeState.isHomeAppending) {
            return
        }
        val nextVisibleCount = homeState.homeVisibleCount + GRID_BATCH_ITEM_COUNT
        if (homeState.homeVisibleCount < homeState.latest.size) {
            homeState = homeState.copy(
                homeVisibleCount = nextVisibleCount.coerceAtMost(homeState.latest.size)
            )
            return
        }
        if (!homeState.hasMoreHomePages) return
        viewModelScope.launch {
            val nextPage = homeState.homePage + 1
            val previousVisibleCount = homeState.homeVisibleCount
            homeState = homeState.copy(isHomeAppending = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadAllCategoryPage(page = nextPage)
                }
            }.onSuccess { payload ->
                val mergedLatest = (homeState.latest + payload.items).distinctBy { it.vodId }
                homeState = homeState.copy(
                    latest = mergedLatest,
                    homeVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
                        .coerceAtLeast(mergedLatest.initialGridVisibleCount())
                        .coerceAtMost(mergedLatest.size),
                    homePage = payload.page,
                    homeTotalCount = payload.totalItems,
                    hasMoreHomePages = payload.hasNextPage,
                    isHomeAppending = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isHomeAppending = false,
                    error = toUserFacingMessage(error, "继续加载首页失败")
                )
            }
        }
    }

    fun loadMoreCategory() {
        if (homeState.isCategoryAppending) {
            return
        }
        val nextVisibleCount = homeState.categoryVisibleCount + GRID_BATCH_ITEM_COUNT
        if (homeState.categoryVisibleCount < homeState.categoryVideos.size) {
            homeState = homeState.copy(
                categoryVisibleCount = nextVisibleCount.coerceAtMost(homeState.categoryVideos.size)
            )
            return
        }
        if (!homeState.hasMoreCategoryPages) return
        val category = homeState.selectedCategory ?: return
        viewModelScope.launch {
            val nextPage = homeState.categoryPage + 1
            val previousVisibleCount = homeState.categoryVisibleCount
            homeState = homeState.copy(isCategoryAppending = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadCategoryPage(typeId = category.typeId, page = nextPage)
                }
            }.onSuccess { payload ->
                val mergedVideos = (homeState.categoryVideos + payload.items).distinctBy { it.vodId }
                homeState = homeState.copy(
                    categoryVideos = mergedVideos,
                    categoryVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
                        .coerceAtLeast(mergedVideos.initialGridVisibleCount())
                        .coerceAtMost(mergedVideos.size),
                    categoryPage = payload.page,
                    categoryTotalCount = payload.totalItems,
                    hasMoreCategoryPages = payload.hasNextPage,
                    isCategoryAppending = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isCategoryAppending = false,
                    error = toUserFacingMessage(error, "继续加载分类失败")
                )
            }
        }
    }

    fun updateQuery(query: String) {
        searchState = searchState.copy(query = query, error = null)
    }

    fun searchHistory(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return
        searchState = searchState.copy(query = normalized, error = null)
    }

    fun clearSearchHistory() {
        searchHistoryStore.clear()
        searchState = searchState.copy(history = emptyList())
    }

    fun search(keyword: String) {
        val normalized = keyword.trim()
        searchState = searchState.copy(query = normalized, error = null)
        performSearch(normalized)
    }

    fun refreshHotSearches(forceRefresh: Boolean = false) {
        if (!forceRefresh && (searchState.hotSearchGroups.isNotEmpty() || searchState.isHotSearchLoading)) {
            return
        }
        viewModelScope.launch {
            searchState = searchState.copy(isHotSearchLoading = true, hotSearchError = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHotSearchGroups(forceRefresh = forceRefresh) }
            }.onSuccess { groups ->
                searchState = searchState.copy(
                    isHotSearchLoading = false,
                    hotSearchGroups = groups,
                    hotSearchError = null
                )
            }.onFailure { error ->
                searchState = searchState.copy(
                    isHotSearchLoading = false,
                    hotSearchError = toUserFacingMessage(error, "热搜加载失败")
                )
            }
        }
    }

    fun search() {
        performSearch(searchState.query)
    }

    fun getSearchResultScroll(query: String): SearchResultScrollPosition =
        searchResultScrollPositions[query.trim()]
            ?: SearchResultScrollPosition()

    fun updateSearchResultScroll(query: String, index: Int, offset: Int) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val current = searchResultScrollPositions[normalized]
        if (current?.index == index && current.offset == offset) return
        searchResultScrollPositions[normalized] = SearchResultScrollPosition(index = index, offset = offset)
    }

    fun ensureSearchResults(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val current = searchState
        val alreadyShowingSameQuery =
            current.submittedQuery == normalized &&
                (current.results.isNotEmpty() || current.isLoading || !current.error.isNullOrBlank())
        if (alreadyShowingSameQuery) {
            if (current.query != normalized) {
                searchState = current.copy(query = normalized)
            }
            return
        }
        performSearch(normalized)
    }

    private fun performSearch(keyword: String) {
        val query = keyword.trim()
        if (query.isBlank()) {
            searchJob?.cancel()
            searchEnrichJob?.cancel()
            searchState = searchState.copy(
                submittedQuery = "",
                results = emptyList(),
                error = "请输入影片名称"
            )
            return
        }
        searchJob?.cancel()
        searchEnrichJob?.cancel()
        val requestVersion = ++searchRequestVersion
        searchState = searchState.copy(
            query = query,
            submittedQuery = query,
            isLoading = true,
            results = emptyList(),
            error = null
        )
        searchJob = viewModelScope.launch searchLaunch@{
            try {
                val results = withContext(Dispatchers.IO) { repository.search(query) }
                if (requestVersion != searchRequestVersion) return@searchLaunch

                searchHistoryStore.save(query)
                searchState = searchState.copy(
                    isLoading = false,
                    history = searchHistoryStore.load(),
                    results = results,
                    error = null
                )

                if (results.isNotEmpty()) {
                    searchEnrichJob = viewModelScope.launch searchEnrichLaunch@{
                        val enrichedResults = withContext(Dispatchers.IO) {
                            repository.enrichSearchResults(results, limit = 8)
                        }
                        if (requestVersion != searchRequestVersion) return@searchEnrichLaunch
                        searchState = searchState.copy(results = enrichedResults)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestVersion != searchRequestVersion) return@searchLaunch
                searchState = searchState.copy(
                    isLoading = false,
                    error = toUserFacingMessage(error, "搜索失败")
                )
            }
        }
    }

    fun updateLoginUserName(value: String) {
        accountState = accountState.copy(userName = value)
    }

    fun updateLoginPassword(value: String) {
        accountState = accountState.copy(password = value)
    }

    fun setAccountAuthMode(mode: AccountAuthMode) {
        accountState = accountState.copy(
            authMode = mode,
            error = null,
            message = null
        )
        when (mode) {
            AccountAuthMode.Login -> Unit
            AccountAuthMode.Register -> loadRegisterPage(forceRefresh = true)
            AccountAuthMode.FindPassword -> loadFindPasswordPage(forceRefresh = true)
            AccountAuthMode.About -> refreshCrashLog()
        }
    }

    fun refreshCategoryTab(forceRefresh: Boolean = false) {
        val selectedCategory = homeState.selectedCategory ?: homeState.categories.firstOrNull() ?: return
        selectCategory(selectedCategory, forceRefresh = forceRefresh)
    }

    fun updateRegisterEditor(transform: (RegisterEditor) -> RegisterEditor) {
        accountState = accountState.copy(
            registerEditor = transform(accountState.registerEditor),
            error = null,
            message = null
        )
    }

    fun refreshRegisterCaptcha() {
        val captchaUrl = accountState.registerCaptchaUrl
        if (captchaUrl.isBlank()) {
            loadRegisterPage(forceRefresh = true)
            return
        }

        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadRegisterCaptcha(captchaUrl) }
            }.onSuccess { bytes ->
                accountState = accountState.copy(
                    isContentLoading = false,
                    registerCaptcha = bytes
                )
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "验证码加载失败")
                )
            }
        }
    }

    fun updateFindPasswordEditor(transform: (FindPasswordEditor) -> FindPasswordEditor) {
        accountState = accountState.copy(
            findPasswordEditor = transform(accountState.findPasswordEditor),
            error = null,
            message = null
        )
    }

    fun refreshFindPasswordCaptcha() {
        val captchaUrl = accountState.findPasswordCaptchaUrl
        if (captchaUrl.isBlank()) {
            loadFindPasswordPage(forceRefresh = true)
            return
        }

        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadFindPasswordCaptcha(captchaUrl) }
            }.onSuccess { bytes ->
                accountState = accountState.copy(
                    isContentLoading = false,
                    findPasswordCaptcha = bytes
                )
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "验证码加载失败")
                )
            }
        }
    }

    fun updateProfileEditor(transform: (UserProfileEditor) -> UserProfileEditor) {
        accountState = accountState.copy(
            profileEditor = transform(accountState.profileEditor),
            error = null,
            message = null
        )
    }

    fun setProfileEditTab(editMode: Boolean) {
        accountState = accountState.copy(isProfileEditTab = editMode)
    }

    fun selectAccountSection(section: AccountSection, forceRefresh: Boolean = false) {
        accountState = accountState.copy(selectedSection = section, error = null, message = null)
        if (!accountState.session.isLoggedIn) return
        when (section) {
            AccountSection.Profile -> {
                if (forceRefresh || accountState.profileFields.isEmpty()) {
                    loadAccountProfile()
                }
            }
            AccountSection.Favorites -> {
                if (forceRefresh || accountState.favoriteItems.isEmpty()) {
                    loadFavoriteRecords()
                }
            }
            AccountSection.History -> {
                if (forceRefresh || accountState.historyItems.isEmpty()) {
                    loadHistoryRecords()
                }
            }
            AccountSection.Member -> {
                if (forceRefresh || accountState.membershipPlans.isEmpty()) {
                    loadMembership()
                }
            }
            AccountSection.About -> refreshCrashLog()
        }
    }

    fun refreshSelectedAccountSection() {
        selectAccountSection(accountState.selectedSection, forceRefresh = true)
    }

    fun loadMoreFavorites() {
        if (accountState.isContentLoading || accountState.favoriteNextPageUrl.isNullOrBlank()) return
        loadFavoriteRecords(pageUrl = accountState.favoriteNextPageUrl, append = true)
    }

    fun loadMoreHistory() {
        if (accountState.isContentLoading || accountState.historyNextPageUrl.isNullOrBlank()) return
        loadHistoryRecords(pageUrl = accountState.historyNextPageUrl, append = true)
    }

    fun deleteFavorite(recordId: String) {
        if (recordId.isBlank()) return
        runAccountAction(
            block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 2, clearAll = false) },
            onSuccess = {
                val removedItem = accountState.favoriteItems.firstOrNull { item -> item.recordId == recordId }
                accountState = accountState.copy(
                    favoriteItems = accountState.favoriteItems.filterNot { item -> item.recordId == recordId }
                )
                if (removedItem?.vodId == detailState.item?.vodId) {
                    detailState = detailState.copy(isFavorited = false)
                }
                selectAccountSection(AccountSection.Favorites, forceRefresh = true)
            }
        )
    }

    fun clearFavorites() {
        runAccountAction(
            block = { deleteUserRecordForApp(recordIds = emptyList(), type = 2, clearAll = true) },
            onSuccess = {
                accountState = accountState.copy(
                    favoriteItems = emptyList(),
                    favoriteNextPageUrl = null
                )
                if (detailState.item != null) {
                    detailState = detailState.copy(isFavorited = false)
                }
                selectAccountSection(AccountSection.Favorites, forceRefresh = true)
            }
        )
    }

    fun deleteHistory(recordId: String) {
        if (recordId.isBlank()) return
        runAccountAction(
            block = { deleteUserRecordForApp(recordIds = listOf(recordId), type = 4, clearAll = false) },
            onSuccess = {
            accountState = accountState.copy(
                historyItems = accountState.historyItems.filterNot { item -> item.recordId == recordId }
            )
            selectAccountSection(AccountSection.History, forceRefresh = true)
            }
        )
    }

    fun clearHistory() {
        runAccountAction(
            block = { deleteUserRecordForApp(recordIds = emptyList(), type = 4, clearAll = true) },
            onSuccess = {
            accountState = accountState.copy(
                historyItems = emptyList(),
                historyNextPageUrl = null
            )
            selectAccountSection(AccountSection.History, forceRefresh = true)
            }
        )
    }

    fun upgradeMembership(plan: MembershipPlan) {
        if (plan.groupId.isBlank() || plan.duration.isBlank()) return
        runAccountAction(
            block = { upgradeMembership(plan) },
            onSuccess = { selectAccountSection(AccountSection.Member, forceRefresh = true) }
        )
    }

    fun saveProfile() {
        runAccountAction(
            block = { saveUserProfile(accountState.profileEditor) },
            onSuccess = {
                accountState = accountState.copy(
                    profileEditor = accountState.profileEditor.copy(
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )
                )
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
                refreshAccount()
            }
        )
    }

    fun uploadPortrait(uri: Uri) {
        if (!accountState.session.isLoggedIn) {
            accountState = accountState.copy(error = "璇峰厛鐧诲綍")
            return
        }
        runAccountAction(
            block = { uploadPortraitOptimized(uri) },
            onSuccess = {
                refreshNotices(forceRefresh = true)
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }
        )
    }

    fun sendEmailBindCode() {
        val email = accountState.profileEditor.pendingEmail.trim()
        if (email.isBlank()) {
            accountState = accountState.copy(error = "请输入邮箱地址")
            return
        }
        runAccountAction(
            block = { sendEmailBindCode(email) },
            onSuccess = { }
        )
    }

    fun bindEmail() {
        val email = accountState.profileEditor.pendingEmail.trim()
        val code = accountState.profileEditor.emailCode.trim()
        if (email.isBlank()) {
            accountState = accountState.copy(error = "请输入邮箱地址")
            return
        }
        if (code.isBlank()) {
            accountState = accountState.copy(error = "请输入邮箱验证码")
            return
        }
        runAccountAction(
            block = { bindEmail(email, code) },
            onSuccess = {
                accountState = accountState.copy(
                    profileEditor = accountState.profileEditor.copy(
                        email = email,
                        pendingEmail = "",
                        emailCode = ""
                    )
                )
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }
        )
    }

    fun unbindEmail() {
        runAccountAction(
            block = { unbindEmail() },
            onSuccess = {
                accountState = accountState.copy(
                    isProfileEditTab = true,
                    profileEditor = accountState.profileEditor.copy(
                        email = "",
                        pendingEmail = "",
                        emailCode = ""
                    )
                )
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }
        )
    }

    fun sendRegisterCode() {
        val editor = accountState.registerEditor
        val contact = editor.contact.trim()
        if (contact.isBlank()) {
            accountState = accountState.copy(error = "请输入${accountState.registerContactLabel}")
            return
        }

        if (editor.channel == "email" && !contact.contains("@")) {
            accountState = accountState.copy(error = "请输入正确的邮箱地址")
            return
        }

        runAccountAction(
            block = { sendRegisterCodeForApp(editor.channel, contact) },
            onSuccess = { }
        )
    }

    fun register() {
        val editor = accountState.registerEditor
        if (editor.userName.isBlank()) {
            accountState = accountState.copy(error = "请输入用户名")
            return
        }
        if (editor.password.isBlank()) {
            accountState = accountState.copy(error = "请输入密码")
            return
        }
        if (editor.confirmPassword.isBlank()) {
            accountState = accountState.copy(error = "请确认密码")
            return
        }
        if (editor.password != editor.confirmPassword) {
            accountState = accountState.copy(error = "两次输入的密码不一致")
            return
        }
        if (editor.contact.isBlank()) {
            accountState = accountState.copy(error = "请输入${accountState.registerContactLabel}")
            return
        }
        if (accountState.registerRequiresCode && editor.code.isBlank()) {
            accountState = accountState.copy(error = "请输入${accountState.registerCodeLabel}")
            return
        }
        if (accountState.registerRequiresVerify && editor.verify.isBlank()) {
            accountState = accountState.copy(error = "请输入图片验证码")
            return
        }

        runAccountAction(
            block = { registerForApp(editor.copy(channel = accountState.registerChannel)) },
            onSuccess = {
                accountState = accountState.copy(
                    authMode = AccountAuthMode.Login,
                    userName = editor.userName,
                    password = "",
                    registerEditor = RegisterEditor(channel = accountState.registerChannel)
                )
            }
        )
    }

    fun findPassword() {
        val editor = accountState.findPasswordEditor
        if (editor.userName.isBlank()) {
            accountState = accountState.copy(error = "璇疯緭鍏ョ敤鎴峰悕")
            return
        }
        if (editor.question.isBlank()) {
            accountState = accountState.copy(error = "璇疯緭鍏ュ瘑淇濋棶棰?")
            return
        }
        if (editor.answer.isBlank()) {
            accountState = accountState.copy(error = "璇疯緭鍏ュ瘑淇濈瓟妗?")
            return
        }
        if (editor.password.isBlank()) {
            accountState = accountState.copy(error = "璇疯緭鍏ユ柊瀵嗙爜")
            return
        }
        if (editor.confirmPassword.isBlank()) {
            accountState = accountState.copy(error = "璇风‘璁ゆ柊瀵嗙爜")
            return
        }
        if (editor.password != editor.confirmPassword) {
            accountState = accountState.copy(error = "涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?")
            return
        }
        if (accountState.findPasswordRequiresVerify && editor.verify.isBlank()) {
            accountState = accountState.copy(error = "璇疯緭鍏ュ浘鐗囬獙璇佺爜")
            return
        }

        runAccountAction(
            block = { findPasswordForApp(editor) },
            onSuccess = {
                accountState = accountState.copy(
                    authMode = AccountAuthMode.Login,
                    userName = editor.userName,
                    password = "",
                    findPasswordEditor = FindPasswordEditor()
                )
            }
        )
    }

    fun addCurrentDetailFavorite() {
        val item = detailState.item ?: return
        if (!accountState.session.isLoggedIn) {
            detailState = detailState.copy(
                actionMessage = "请先登录后再收藏",
                isActionError = true
            )
            return
        }
        if (detailState.isFavorited) {
            detailState = detailState.copy(
                actionMessage = "已在收藏中",
                isActionError = false
            )
            return
        }
        if (detailState.isActionLoading) return
        viewModelScope.launch {
            detailState = detailState.copy(
                isActionLoading = true,
                actionMessage = null,
                isActionError = false
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.addFavoriteForApp(item) }
            }.onSuccess { message ->
                val normalizedMessage = normalizeFavoriteActionMessage(message)
                detailState = detailState.copy(
                    isActionLoading = false,
                    actionMessage = normalizedMessage,
                    isActionError = false,
                    isFavorited = true
                )
                if (accountState.selectedSection == AccountSection.Favorites) {
                    selectAccountSection(AccountSection.Favorites, forceRefresh = true)
                }
            }.onFailure { error ->
                val isDuplicate = isDuplicateFavoriteMessage(error.message.orEmpty())
                detailState = detailState.copy(
                    isActionLoading = false,
                    actionMessage = if (isDuplicate) "已在收藏中" else toUserFacingMessage(error, "收藏失败"),
                    isActionError = !isDuplicate,
                    isFavorited = isDuplicate || detailState.isFavorited
                )
            }
        }
    }

    fun dismissDetailActionMessage() {
        if (detailState.actionMessage.isNullOrBlank()) return
        detailState = detailState.copy(actionMessage = null, isActionError = false)
    }

    fun login() {
        val userName = accountState.userName.trim()
        val password = accountState.password
        if (userName.isBlank()) {
            accountState = accountState.copy(error = "请输入用户名")
            return
        }
        if (password.isBlank()) {
            accountState = accountState.copy(error = "请输入密码")
            return
        }

        viewModelScope.launch {
            accountState = accountState.copy(isLoading = true, error = null, message = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loginForApp(userName = userName, password = password)
                }
            }.onSuccess { session ->
                accountState = accountState.copy(
                    isLoading = false,
                    session = session,
                    password = "",
                    error = null,
                    message = "登录成功"
                )
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }.onFailure { error ->
                accountState = accountState.copy(
                    isLoading = false,
                    message = null,
                    error = toUserFacingMessage(error, "登录失败")
                )
            }
        }
    }

    fun logout() {
        if (accountState.isLoading) return
        viewModelScope.launch {
            accountState = accountState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.logoutForApp() }
            }.onSuccess {
                accountState = AccountUiState(
                    userName = accountState.userName,
                    session = AuthSession(),
                    message = "已退出登录",
                    updateInfo = accountState.updateInfo,
                    hasCrashLog = accountState.hasCrashLog,
                    latestCrashLog = accountState.latestCrashLog
                )
                refreshNotices(forceRefresh = true)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isLoading = false,
                    error = toUserFacingMessage(error, "退出登录失败")
                )
            }
        }
    }

    private fun loadRegisterPage(forceRefresh: Boolean = false) {
        if (accountState.isContentLoading && !forceRefresh) return
        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadRegisterPageForApp() }
            }.onSuccess { page ->
                accountState = accountState.copy(
                    isContentLoading = false,
                    registerChannel = page.channel,
                    registerContactLabel = page.contactLabel,
                    registerCodeLabel = page.codeLabel,
                    registerRequiresCode = page.requiresCode,
                    registerRequiresVerify = page.requiresVerify,
                    registerCaptchaUrl = page.captchaUrl,
                    registerCaptcha = page.captchaBytes,
                    registerEditor = accountState.registerEditor.copy(channel = page.channel)
                )
                if (page.requiresVerify && page.captchaUrl.isNotBlank()) {
                    refreshRegisterCaptcha()
                }
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "注册页面加载失败")
                )
            }
        }
    }

    private fun loadFindPasswordPage(forceRefresh: Boolean = false) {
        if (accountState.isContentLoading && !forceRefresh) return
        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadFindPasswordPageForApp() }
            }.onSuccess { page ->
                accountState = accountState.copy(
                    isContentLoading = false,
                    findPasswordRequiresVerify = page.requiresVerify,
                    findPasswordCaptchaUrl = page.captchaUrl,
                    findPasswordCaptcha = page.captchaBytes
                )
                if (page.requiresVerify && page.captchaUrl.isNotBlank()) {
                    refreshFindPasswordCaptcha()
                }
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "找回密码页面加载失败")
                )
            }
        }
    }

    private fun loadAccountProfile() {
        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadUserProfileForApp() }
            }.onSuccess { page ->
                accountState = accountState.copy(
                    isContentLoading = false,
                    profileFields = page.fields,
                    profileEditor = page.editor,
                    session = mergeAccountSession(page.session)
                )
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "加载资料失败")
                )
            }
        }
    }

    private fun loadFavoriteRecords(pageUrl: String? = null, append: Boolean = false) {
        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadFavoritePageForApp(pageUrl) }
            }.onSuccess { page ->
                accountState = accountState.copy(
                    isContentLoading = false,
                    favoriteItems = mergeAccountItems(
                        current = if (append) accountState.favoriteItems else emptyList(),
                        incoming = page.items
                    ),
                    favoriteNextPageUrl = page.nextPageUrl
                )
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "加载收藏失败")
                )
            }
        }
    }

    private fun loadHistoryRecords(pageUrl: String? = null, append: Boolean = false) {
        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHistoryPageForApp(pageUrl) }
            }.onSuccess { page ->
                val mergedItems = mergeAccountItems(
                    current = if (append) accountState.historyItems else emptyList(),
                    incoming = page.items
                )
                accountState = accountState.copy(
                    isContentLoading = false,
                    historyItems = mergedItems,
                    historyNextPageUrl = page.nextPageUrl
                )
                enrichHistoryRecords(mergedItems)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "加载播放记录失败")
                )
            }
        }
    }

    private fun enrichHistoryRecords(items: List<UserCenterItem>) {
        val targetItems = items.filter { item ->
            item.sourceIndex >= 0 &&
                item.sourceName.isBlank() &&
                (item.vodId.isNotBlank() || item.playUrl.isNotBlank() || item.actionUrl.isNotBlank())
        }
        if (targetItems.isEmpty()) return

        historyEnrichJob?.cancel()
        val requestVersion = ++historyEnrichVersion
        historyEnrichJob = viewModelScope.launch {
            val enrichedItems = runCatching {
                withContext(Dispatchers.IO) { repository.enrichHistoryItems(targetItems) }
            }.getOrNull() ?: return@launch
            if (requestVersion != historyEnrichVersion) return@launch

            val enrichedByKey = enrichedItems.associateBy(::historyRecordKey)
            accountState = accountState.copy(
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
        }
    }

    private fun loadMembership() {
        viewModelScope.launch {
            accountState = accountState.copy(isContentLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadMembershipDataForApp() }
            }.onSuccess { page ->
                val refreshedSession = mergeAccountSession(repository.currentSession()).let { session ->
                    session.copy(groupName = page.info.groupName.ifBlank { session.groupName })
                }
                accountState = accountState.copy(
                    isContentLoading = false,
                    session = refreshedSession,
                    membershipInfo = page.info.copy(
                        groupName = page.info.groupName.ifBlank {
                            refreshedSession.groupName
                        }
                    ),
                    membershipPlans = page.plans
                )
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isContentLoading = false,
                    error = toUserFacingMessage(error, "加载会员信息失败")
                )
            }
        }
    }

    private fun runAccountAction(
        block: suspend AppleCmsRepository.() -> String,
        onSuccess: () -> Unit
    ) {
        if (accountState.isActionLoading) return
        viewModelScope.launch {
            accountState = accountState.copy(isActionLoading = true, error = null, message = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.block() }
            }.onSuccess { message ->
                accountState = accountState.copy(
                    isActionLoading = false,
                    message = message.ifBlank { "操作成功" }
                )
                onSuccess()
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountState.copy(
                    isActionLoading = false,
                    error = toUserFacingMessage(error, "操作失败")
                )
            }
        }
    }

    private fun mergeAccountSession(session: AuthSession): AuthSession =
        session.copy(
            userName = session.userName.ifBlank { accountState.session.userName },
            groupName = session.groupName.ifBlank { accountState.session.groupName },
            portraitUrl = session.portraitUrl.ifBlank { accountState.session.portraitUrl }
        )

    private fun handleAccountSessionExpired(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        val isExpired = message.contains("请先登录") || message.contains("登录已失效")
        if (!isExpired) return false

        repository.clearSession()
        accountState = AccountUiState(
            userName = accountState.userName,
            authMode = AccountAuthMode.Login,
            message = "登录已失效，请重新登录",
            updateInfo = accountState.updateInfo,
            hasCrashLog = accountState.hasCrashLog,
            latestCrashLog = accountState.latestCrashLog
        )
        return true
    }

    private fun toUserFacingMessage(
        error: Throwable,
        fallback: String,
        serviceLabel: String? = null
    ): String {
        val host = serviceLabel?.trim()?.takeIf(String::isNotBlank) ?: "内容服务"
        val rawMessage = error.message.orEmpty().trim()

        return when {
            error is UnknownHostException || rawMessage.contains("Unable to resolve host", ignoreCase = true) ->
                "无法连接到 $host，请检查网络或站点状态"

            error is SocketTimeoutException ||
                error is InterruptedIOException ||
                rawMessage.contains("timeout", ignoreCase = true) ||
                rawMessage.contains("timed out", ignoreCase = true) ->
                "连接 $host 超时，请稍后重试"

            error is ConnectException ||
                rawMessage.contains("failed to connect", ignoreCase = true) ||
                rawMessage.contains("connection refused", ignoreCase = true) ->
                "无法连接到 $host，请稍后重试"

            error is SSLException || rawMessage.contains("ssl", ignoreCase = true) ->
                "与 $host 的安全连接失败，请稍后重试"

            rawMessage.any { it in '\u4e00'..'\u9fff' } -> rawMessage

            fallback.isNotBlank() -> "$fallback，请稍后重试"

            else -> "请求失败，请稍后重试"
        }
    }

    private fun normalizeFavoriteActionMessage(rawMessage: String): String {
        val message = rawMessage.trim()
        if (message.isBlank()) return "已加入收藏"
        return when {
            isDuplicateFavoriteMessage(message) -> "已在收藏中"
            message.contains("成功") || message.contains("获取成功") || message.contains("操作成功") -> "已加入收藏"
            else -> "已加入收藏"
        }
    }

    private fun isDuplicateFavoriteMessage(rawMessage: String): Boolean {
        val message = rawMessage.trim()
        if (message.isBlank()) return false
        return message.contains("已收藏") ||
            message.contains("已经收藏") ||
            message.contains("已存在") ||
            message.contains("重复")
    }

    private fun mergeAccountItems(
        current: List<UserCenterItem>,
        incoming: List<UserCenterItem>
    ): List<UserCenterItem> = (current + incoming)
        .distinctBy { item -> "${item.recordId}:${item.actionUrl}:${item.vodId}" }

    private fun historyRecordKey(item: UserCenterItem): String =
        listOf(item.recordId, item.actionUrl, item.playUrl, item.title)
            .joinToString("|")

    fun openHistoryRecord(item: UserCenterItem) {
        val resolvedVodId = item.vodId.ifBlank {
            Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
                .find(item.playUrl.ifBlank { item.actionUrl })
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }

        if (resolvedVodId.isBlank()) {
            openHistoryRecordDirectly(item)
            return
        }

        if (false) {
            playerState = PlayerUiState(
                title = item.title,
                isResolving = false,
                resolveError = "无法定位到影片详情"
            )
            return
        }

        playerState = PlayerUiState(
            title = item.title,
            isResolving = true,
            resolveError = null
        )

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
                val safeSourceIndex = item.sourceIndex.coerceIn(0, (sources.lastIndex).coerceAtLeast(0))
                val episodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
                val matchedEpisodeIndex = episodes.indexOfFirst { episode ->
                    item.playUrl.isNotBlank() && episode.url == item.playUrl
                }
                val safeEpisodeIndex = when {
                    matchedEpisodeIndex >= 0 -> matchedEpisodeIndex
                    item.episodeIndex >= 0 -> item.episodeIndex.coerceIn(0, (episodes.lastIndex).coerceAtLeast(0))
                    else -> 0
                }

                openPlayer(
                    title = detailItem.displayTitle,
                    item = detailItem,
                    sources = sources,
                    sourceIndex = safeSourceIndex,
                    episodeIndex = safeEpisodeIndex
                )
            }.onFailure { error ->
                playerState = PlayerUiState(
                    title = item.title,
                    isResolving = false,
                    resolveError = error.message ?: "继续观看失败"
                )
            }
        }
    }

    fun resumeHistoryRecord(item: UserCenterItem) {
        val resolvedVodId = item.vodId.ifBlank {
            Regex("""/vodplay/([^/]+?)-\d+-\d+(?:\.html)?/?(?:\?.*)?$""")
                .find(item.playUrl.ifBlank { item.actionUrl })
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        }

        if (resolvedVodId.isBlank()) {
            openHistoryRecordDirectly(item)
            return
        }

        playerState = PlayerUiState(
            title = item.title,
            isResolving = true,
            resolveError = null
        )

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.loadDetail(resolvedVodId) }
            }.onSuccess { detailItem ->
                if (detailItem == null) {
                    openHistoryRecordDirectly(item)
                    return@onSuccess
                }

                val sources = repository.parseSources(detailItem)
                val safeSourceIndex = item.sourceIndex.coerceIn(0, (sources.lastIndex).coerceAtLeast(0))
                val episodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
                val matchedEpisodeIndex = episodes.indexOfFirst { episode ->
                    item.playUrl.isNotBlank() && episode.url == item.playUrl
                }
                val safeEpisodeIndex = when {
                    matchedEpisodeIndex >= 0 -> matchedEpisodeIndex
                    item.episodeIndex >= 0 -> item.episodeIndex.coerceIn(0, (episodes.lastIndex).coerceAtLeast(0))
                    else -> 0
                }

                openPlayer(
                    title = detailItem.displayTitle,
                    item = detailItem,
                    sources = sources,
                    sourceIndex = safeSourceIndex,
                    episodeIndex = safeEpisodeIndex
                )
            }.onFailure {
                openHistoryRecordDirectly(item)
            }
        }
    }

    private fun openHistoryRecordDirectly(item: UserCenterItem) {
        val resumeUrl = item.playUrl.ifBlank { item.actionUrl }
        if (resumeUrl.isBlank()) {
            playerState = PlayerUiState(
                title = item.title,
                isResolving = false,
                resolveError = "无法恢复该条播放记录"
            )
            return
        }

        openPlayer(
            title = item.title,
            item = null,
            sources = listOf(
                PlaySource(
                    name = item.sourceName.ifBlank { "继续观看" },
                    episodes = listOf(
                        Episode(
                            name = item.subtitle.substringBefore("|").trim().ifBlank { "继续观看" },
                            url = resumeUrl
                        )
                    )
                )
            ),
            sourceIndex = 0,
            episodeIndex = 0
        )
    }

    fun loadDetail(vodId: String) {
        viewModelScope.launch {
            val keepCurrentContent = detailState.item?.vodId == vodId
            detailState = if (keepCurrentContent) {
                detailState.copy(isLoading = true, error = null)
            } else {
                DetailUiState(isLoading = true, error = null)
            }
            runCatching {
                withContext(Dispatchers.IO) { repository.loadDetail(vodId) }
            }.onSuccess { item ->
                if (item == null) {
                    detailState = DetailUiState(isLoading = false, error = "未找到影片详情")
                } else {
                    detailState = DetailUiState(
                        isLoading = false,
                        item = item,
                        sources = repository.parseSources(item),
                        selectedSourceIndex = 0,
                        actionMessage = null,
                        isActionLoading = false,
                        isFavorited = accountState.favoriteItems.any { favorite -> favorite.vodId == item.vodId }
                    )
                }
            }.onFailure { error ->
                detailState = DetailUiState(
                    isLoading = false,
                    error = toUserFacingMessage(error, "详情加载失败")
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
                val previousSources = playerState.sources
                val resolvedSourceIndex = when {
                    refreshedSources.isEmpty() -> 0
                    else -> {
                        val matchedByName = refreshedSources.indexOfFirst { it.name == currentSourceName }
                        when {
                            matchedByName >= 0 -> matchedByName
                            else -> playerState.selectedSourceIndex.coerceIn(0, refreshedSources.lastIndex)
                        }
                    }
                }
                val resolvedEpisodes = refreshedSources.getOrNull(resolvedSourceIndex)?.episodes.orEmpty()
                val resolvedEpisodeIndex = when {
                    resolvedEpisodes.isEmpty() -> 0
                    else -> {
                        val matchedByUrl = resolvedEpisodes.indexOfFirst { it.url == currentEpisodeUrl }
                        val matchedByName = resolvedEpisodes.indexOfFirst { it.name == currentEpisodeName }
                        when {
                            matchedByUrl >= 0 -> matchedByUrl
                            matchedByName >= 0 -> matchedByName
                            else -> playerState.selectedEpisodeIndex.coerceIn(0, resolvedEpisodes.lastIndex)
                        }
                    }
                }
                val refreshedEpisodeUrl = resolvedEpisodes.getOrNull(resolvedEpisodeIndex)?.url.orEmpty()
                val sourcesChanged = previousSources != refreshedSources
                val episodeChanged = refreshedEpisodeUrl != currentEpisodeUrl

                playerState = playerState.copy(
                    title = detailItem.displayTitle,
                    item = detailItem,
                    sources = refreshedSources,
                    selectedSourceIndex = resolvedSourceIndex,
                    selectedEpisodeIndex = resolvedEpisodeIndex,
                    playbackSnapshot = if (episodeChanged) PlaybackSnapshot() else playerState.playbackSnapshot,
                    resolveError = if (refreshedSources.isEmpty()) "暂无可播放线路" else null
                )

                if (refreshedSources.isEmpty()) {
                    playerState = playerState.copy(
                        isResolving = false,
                        resolvedUrl = "",
                        useWebPlayer = false
                    )
                    return@onSuccess
                }

                if (episodeChanged || playerState.resolvedUrl.isBlank()) {
                    resolveCurrentPlayerUrl()
                } else if (sourcesChanged) {
                    playerState = playerState.copy(
                        isResolving = false,
                        useWebPlayer = false,
                        resolveError = null
                    )
                }
            }
        }
    }

    fun selectSource(index: Int) {
        detailState = detailState.copy(selectedSourceIndex = index)
    }

    fun openPlayer(
        title: String,
        item: VodItem?,
        sources: List<PlaySource>,
        sourceIndex: Int,
        episodeIndex: Int
    ) {
        val safeSourceIndex = sourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
        val safeEpisodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
        playerState = PlayerUiState(
            title = title,
            item = item,
            sources = sources,
            selectedSourceIndex = safeSourceIndex,
            selectedEpisodeIndex = episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0)),
            playbackSnapshot = PlaybackSnapshot()
        )
        resolveCurrentPlayerUrl()
        recordCurrentPlayback()
    }

    fun selectPlayerEpisode(index: Int) {
        val currentEpisodes = playerState.currentSource?.episodes.orEmpty()
        if (currentEpisodes.isEmpty()) return
        playerState = playerState.copy(
            selectedEpisodeIndex = index.coerceIn(0, currentEpisodes.lastIndex),
            playbackSnapshot = PlaybackSnapshot()
        )
        resolveCurrentPlayerUrl()
        recordCurrentPlayback()
    }

    fun selectPlayerSource(index: Int) {
        if (playerState.sources.isEmpty()) return
        val safeIndex = index.coerceIn(0, playerState.sources.lastIndex)
        playerState = playerState.copy(
            selectedSourceIndex = safeIndex,
            selectedEpisodeIndex = 0,
            playbackSnapshot = PlaybackSnapshot()
        )
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
        if (streamUrl.isBlank()) return
        playerState = playerState.copy(
            resolvedUrl = streamUrl,
            isResolving = false,
            useWebPlayer = false,
            resolveError = null
        )
    }

    fun reportTakeoverFailure(message: String) {
        playerState = playerState.copy(
            isResolving = false,
            useWebPlayer = false,
            resolveError = message.ifBlank { "该线路暂不支持，请换个线路试试" }
        )
    }

    fun updatePlaybackSnapshot(snapshot: PlaybackSnapshot) {
        val currentSnapshot = playerState.playbackSnapshot
        val hasMeaningfulChange =
            kotlin.math.abs(snapshot.positionMs - currentSnapshot.positionMs) >= UiMotion.SnapshotPositionThresholdMillis ||
                kotlin.math.abs(snapshot.speed - currentSnapshot.speed) > 0.01f ||
                snapshot.playWhenReady != currentSnapshot.playWhenReady
        if (!hasMeaningfulChange) return
        playerState = playerState.copy(playbackSnapshot = snapshot)
    }

    fun syncFromFullscreen(result: FullscreenPlaybackResult) {
        val safeEpisodes = playerState.episodes
        val safeEpisodeIndex = result.episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0))
        playerState = playerState.copy(
            selectedEpisodeIndex = safeEpisodeIndex,
            resolvedUrl = result.resolvedUrl.ifBlank {
                if (safeEpisodeIndex == playerState.selectedEpisodeIndex) playerState.resolvedUrl else ""
            },
            useWebPlayer = false,
            isResolving = false,
            resolveError = null,
            playbackSnapshot = result.snapshot
        )
        if (result.resolvedUrl.isBlank() && safeEpisodeIndex != playerState.selectedEpisodeIndex) {
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
            playerState = playerState.copy(
                isResolving = false,
                resolvedUrl = "",
                useWebPlayer = false,
                resolveError = "暂无可播放地址"
            )
            return
        }
        val episodePageUrl = currentEpisode.url
        playerState = playerState.copy(
            isResolving = true,
            resolvedUrl = "",
            useWebPlayer = false,
            resolveError = null
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.resolvePlayUrl(episodePageUrl) }
            }.onSuccess { resolved ->
                if (playerState.currentEpisode?.url != episodePageUrl) return@onSuccess
                playerState = playerState.copy(
                    isResolving = false,
                    resolvedUrl = resolved.url,
                    useWebPlayer = resolved.useWebPlayer,
                    resolveError = if (resolved.url.isBlank()) "解析播放地址失败" else null
                )
            }.onFailure {
                if (playerState.currentEpisode?.url != episodePageUrl) return@onFailure
                playerState = playerState.copy(
                    isResolving = false,
                    resolvedUrl = episodePageUrl,
                    useWebPlayer = true,
                    resolveError = "该线路暂不支持，请换个线路试试"
                )
            }
        }
    }

}

data class HomeUiState(
    val isLoading: Boolean = true,
    val isHomeAppending: Boolean = false,
    val isCategoryLoading: Boolean = false,
    val isCategoryAppending: Boolean = false,
    val error: String? = null,
    val slides: List<VodItem> = emptyList(),
    val hot: List<VodItem> = emptyList(),
    val featured: List<VodItem> = emptyList(),
    val latest: List<VodItem> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val homeVisibleCount: Int = 0,
    val homePage: Int = 1,
    val homeTotalCount: Int = 0,
    val hasMoreHomePages: Boolean = false,
    val categories: List<AppleCmsCategory> = emptyList(),
    val selectedCategory: AppleCmsCategory? = null,
    val categoryVideos: List<VodItem> = emptyList(),
    val categoryVisibleCount: Int = 0,
    val categoryPage: Int = 1,
    val categoryTotalCount: Int = 0,
    val hasMoreCategoryPages: Boolean = false
) {
    val visibleLatest: List<VodItem>
        get() = latest.take(homeVisibleCount.coerceIn(0, latest.size))

    val hasMoreLatest: Boolean
        get() = homeVisibleCount < latest.size || hasMoreHomePages

    val visibleCategoryVideos: List<VodItem>
        get() = categoryVideos.take(categoryVisibleCount.coerceIn(0, categoryVideos.size))

    val hasMoreCategoryVideos: Boolean
        get() = categoryVisibleCount < categoryVideos.size || hasMoreCategoryPages
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

private fun List<VodItem>.initialGridVisibleCount(): Int =
    size.coerceAtMost(GRID_BATCH_ITEM_COUNT)

private const val GRID_BATCH_ROWS = 12
private const val GRID_BATCH_COLUMNS = 3
private const val GRID_BATCH_ITEM_COUNT = GRID_BATCH_ROWS * GRID_BATCH_COLUMNS

data class SearchResultScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    val isHotSearchLoading: Boolean = false,
    val error: String? = null,
    val hotSearchError: String? = null,
    val history: List<String> = emptyList(),
    val hotSearchGroups: List<HotSearchGroup> = emptyList(),
    val results: List<VodItem> = emptyList()
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
    val registerContactLabel: String = "邮箱",
    val registerCodeLabel: String = "邮箱验证码",
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

data class PlaybackSnapshot(
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val playWhenReady: Boolean = true
)

data class FullscreenPlaybackResult(
    val episodeIndex: Int,
    val resolvedUrl: String,
    val snapshot: PlaybackSnapshot
)
