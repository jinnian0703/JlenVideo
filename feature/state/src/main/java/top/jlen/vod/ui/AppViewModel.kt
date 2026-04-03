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

class AppViewModel(application: Application) : AndroidViewModel(application) {
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

    init {
        searchState = searchState.copy(history = searchHistoryStore.load())
        refreshCrashLog()
        refreshAccount()
        refreshHome()
        checkAppUpdate()
    }

    fun refreshAccount() {
        val session = repository.currentSession()
        accountState = if (session.isLoggedIn) {
            accountState.copy(
                session = mergeAccountSession(session, accountState.session),
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
            if (hasEnteredAccountScreen) {
                hydrateAccountSession()
                selectAccountSection(accountState.selectedSection, forceRefresh = true)
            }
        }
    }

    fun ensureAccountScreenReady() {
        if (hasEnteredAccountScreen) return
        hasEnteredAccountScreen = true
        if (!accountState.session.isLoggedIn) return
        hydrateAccountSession()
        selectAccountSection(accountState.selectedSection, forceRefresh = true)
    }

    private fun hydrateAccountSession() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.loadUserProfileForApp() }
            }.onSuccess { page ->
                accountState = accountState.copy(
                    session = mergeAccountSession(page.session, accountState.session)
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
                    repository.loadLatestRelease(AppRuntimeInfo.versionName)
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
                        appVersion = AppRuntimeInfo.versionName,
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
        val dismissedNotice = noticeState.dialogNotice
        dismissedNotice?.let(repository::markNoticeDismissed)
        noticeState = noticeState.copy(
            dialogNotice = null,
            unreadNoticeIds = repository.unreadActiveNoticeIds(noticeState.notices)
        )
    }

    fun markNoticeOpened(noticeId: String) {
        val normalized = noticeId.trim()
        noticeState.notices
            .firstOrNull { it.id == normalized }
            ?.let(repository::markNoticeDismissed)
        noticeState = noticeState.copy(
            dialogNotice = noticeState.dialogNotice?.takeUnless { it.id == normalized },
            unreadNoticeIds = repository.unreadActiveNoticeIds(noticeState.notices)
        )
    }

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

    fun refreshHome(forceRefresh: Boolean = false) {
        refreshNotices(forceRefresh = forceRefresh)
        val cachedPayload = if (!forceRefresh) {
            repository.peekHomePayload(allowStale = true)
        } else {
            null
        }
        if (cachedPayload != null) {
            homeState = homeStateFromPayload(payload = cachedPayload)
        } else {
            homeState = HomeUiState(isLoading = true)
        }
        viewModelScope.launch {
            val shouldRefreshFromNetwork = forceRefresh || cachedPayload != null
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHome(forceRefresh = shouldRefreshFromNetwork) }
            }.onSuccess { payload ->
                homeState = homeStateFromPayload(payload)
            }.onFailure { error ->
                homeState = if (cachedPayload != null && !forceRefresh) {
                    homeState.copy(isLoading = false)
                } else {
                    homeState.copy(
                    isLoading = false,
                    error = toUserFacingMessage(error, "首页加载失败")
                    )
                }
            }
        }
    }

    fun refreshHomeAndClearCaches() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearRuntimeCaches() }
            searchResultScrollPositions.clear()
            refreshHome(forceRefresh = true)
        }
    }

    fun selectCategory(category: AppleCmsCategory, forceRefresh: Boolean = false) {
        val sameCategory = category.typeId == homeState.selectedCategory?.typeId
        if (!forceRefresh && sameCategory && homeState.categoryFirstLoaded) {
            return
        }
        loadCategoryContent(
            category = category,
            filters = emptyMap()
        )
    }

    fun updateCategoryFilter(key: String, value: String) {
        val category = homeState.selectedCategory ?: return
        val normalizedKey = key.trim()
        if (normalizedKey.isBlank()) return
        val normalizedValue = value.trim()
        val updatedFilters = homeState.selectedCategoryFilters.toMutableMap().apply {
            if (normalizedValue.isBlank()) {
                remove(normalizedKey)
            } else {
                put(normalizedKey, normalizedValue)
            }
        }
        if (updatedFilters == homeState.selectedCategoryFilters && homeState.categoryFirstLoaded) {
            return
        }
        loadCategoryContent(
            category = category,
            filters = updatedFilters
        )
    }

    private fun loadCategoryContent(
        category: AppleCmsCategory,
        filters: Map<String, String>
    ) {
        viewModelScope.launch {
            homeState = homeState.copy(
                selectedCategory = category,
                selectedCategoryFilters = filters,
                categoryVideos = emptyList(),
                categoryVisibleCount = 0,
                categoryCursor = "",
                hasMoreCategoryItems = true,
                categoryFirstLoaded = false,
                isCategoryLoading = true,
                isCategoryAppending = false,
                categoryAppendError = null,
                error = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadCategoryCursorPage(
                        typeId = category.typeId,
                        cursor = "",
                        filters = filters
                    )
                }
            }.onSuccess { payload ->
                homeState = homeState.copy(
                    categoryVideos = payload.items,
                    categoryVisibleCount = payload.items.initialGridVisibleCount(),
                    categoryCursor = payload.nextCursor,
                    hasMoreCategoryItems = payload.hasMore,
                    categoryFirstLoaded = true,
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
        if (!homeState.hasMoreHomeItems) return
        viewModelScope.launch {
            val previousVisibleCount = homeState.homeVisibleCount
            homeState = homeState.copy(isHomeAppending = true, homeAppendError = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadLatestCursorPage(cursor = homeState.homeCursor)
                }
            }.onSuccess { payload ->
                val mergedLatest = (homeState.latest + payload.items).distinctBy { it.vodId }
                homeState = homeState.copy(
                    latest = mergedLatest,
                    homeVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
                        .coerceAtLeast(mergedLatest.initialGridVisibleCount())
                        .coerceAtMost(mergedLatest.size),
                    homeCursor = payload.nextCursor,
                    hasMoreHomeItems = payload.hasMore,
                    isHomeAppending = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isHomeAppending = false,
                    homeAppendError = toUserFacingMessage(error, "继续加载首页失败")
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
        if (!homeState.hasMoreCategoryItems) return
        val category = homeState.selectedCategory ?: return
        viewModelScope.launch {
            val previousVisibleCount = homeState.categoryVisibleCount
            homeState = homeState.copy(isCategoryAppending = true, categoryAppendError = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadCategoryCursorPage(
                        typeId = category.typeId,
                        cursor = homeState.categoryCursor,
                        filters = homeState.selectedCategoryFilters
                    )
                }
            }.onSuccess { payload ->
                val mergedVideos = (homeState.categoryVideos + payload.items).distinctBy { it.vodId }
                homeState = homeState.copy(
                    categoryVideos = mergedVideos,
                    categoryVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
                        .coerceAtLeast(mergedVideos.initialGridVisibleCount())
                        .coerceAtMost(mergedVideos.size),
                    categoryCursor = payload.nextCursor,
                    hasMoreCategoryItems = payload.hasMore,
                    isCategoryAppending = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isCategoryAppending = false,
                    categoryAppendError = toUserFacingMessage(error, "继续加载分类失败")
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
                (current.firstLoaded || current.isLoading || !current.error.isNullOrBlank())
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
                isLoading = false,
                isAppending = false,
                results = emptyList(),
                cursor = "",
                hasMore = false,
                firstLoaded = false,
                appendError = null,
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
            isAppending = false,
            cursor = "",
            hasMore = true,
            firstLoaded = false,
            results = emptyList(),
            appendError = null,
            error = null
        )
        searchJob = viewModelScope.launch searchLaunch@{
            try {
                val firstPage = withContext(Dispatchers.IO) {
                    repository.searchCursor(keyword = query, cursor = "")
                }
                if (requestVersion != searchRequestVersion) return@searchLaunch

                searchHistoryStore.save(query)
                searchState = searchState.copy(
                    isLoading = false,
                    history = searchHistoryStore.load(),
                    results = firstPage.items,
                    cursor = firstPage.nextCursor,
                    hasMore = firstPage.hasMore,
                    firstLoaded = true,
                    error = null
                )

                if (firstPage.items.isNotEmpty()) {
                    searchEnrichJob = viewModelScope.launch searchEnrichLaunch@{
                        val enrichedResults = withContext(Dispatchers.IO) {
                            repository.enrichSearchResults(firstPage.items, limit = 8)
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
                    firstLoaded = true,
                    error = toUserFacingMessage(error, "搜索失败")
                )
            }
        }
    }

    fun loadMoreSearchResults() {
        val query = searchState.submittedQuery.trim()
        if (
            query.isBlank() ||
                searchState.isLoading ||
                searchState.isAppending ||
                !searchState.hasMore
        ) {
            return
        }

        viewModelScope.launch {
            searchState = searchState.copy(isAppending = true, appendError = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.searchCursor(keyword = query, cursor = searchState.cursor)
                }
            }.onSuccess { page ->
                val mergedResults = (searchState.results + page.items).distinctBy { it.vodId }
                searchState = searchState.copy(
                    isAppending = false,
                    results = mergedResults,
                    cursor = page.nextCursor,
                    hasMore = page.hasMore
                )
            }.onFailure { error ->
                searchState = searchState.copy(
                    isAppending = false,
                    appendError = toUserFacingMessage(error, "继续加载搜索结果失败")
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
        if (forceRefresh || homeState.selectedCategoryFilters.isNotEmpty()) {
            loadCategoryContent(
                category = selectedCategory,
                filters = homeState.selectedCategoryFilters
            )
        } else {
            selectCategory(selectedCategory, forceRefresh = forceRefresh)
        }
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
                    session = mergeAccountSession(page.session, accountState.session)
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
                val refreshedSession = mergeAccountSession(
                    repository.currentSession(),
                    accountState.session
                ).let { session ->
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
        val targetEpisodes = playerState.sources.getOrNull(safeIndex)?.episodes.orEmpty()
        val preservedEpisodeIndex = playerState.selectedEpisodeIndex
            .coerceIn(0, (targetEpisodes.size - 1).coerceAtLeast(0))
        playerState = playerState.copy(
            selectedSourceIndex = safeIndex,
            selectedEpisodeIndex = preservedEpisodeIndex,
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

