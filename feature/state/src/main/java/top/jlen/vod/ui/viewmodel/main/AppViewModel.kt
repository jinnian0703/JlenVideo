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
        searchState = searchStateWithHistory(searchState, searchHistoryStore.load())
        refreshCrashLog()
        refreshAccount()
        refreshHome()
        checkAppUpdate()
    }

    fun refreshAccount() {
        val session = repository.currentSession()
        accountState = refreshedAccountState(accountState, session)
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
                accountState = accountStateWithHydratedSession(accountState, page.session)
            }
        }
    }

    fun refreshCrashLog() {
        val latestCrashLog = CrashLogger.readLatest(getApplication())
        accountState = accountStateWithCrashLog(accountState, latestCrashLog)
    }

    fun clearCrashLog() {
        CrashLogger.clear(getApplication())
        accountState = accountStateAfterCrashLogCleared(accountState)
    }

    fun checkAppUpdate() {
        if (accountState.isUpdateLoading) return
        viewModelScope.launch {
            accountState = beginUpdateCheck(accountState)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadLatestRelease(AppRuntimeInfo.versionName)
                }
            }.onSuccess { updateInfo ->
                accountState = accountStateWithUpdateInfo(accountState, updateInfo)
            }.onFailure {
                accountState = accountStateWithUpdateError(accountState)
            }
        }
    }

    fun refreshNotices(forceRefresh: Boolean = false) {
        if (noticeState.isLoading && !forceRefresh) return
        val userId = accountState.session.userId
        viewModelScope.launch {
            noticeState = beginNoticeRefresh(noticeState)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadNotices(
                        appVersion = AppRuntimeInfo.versionName,
                        userId = userId,
                        forceRefresh = forceRefresh
                    )
                }
            }.onSuccess { notices ->
                val unreadNoticeIds = repository.unreadActiveNoticeIds(notices)
                noticeState = noticeStateWithLoadedNotices(
                    noticeState = noticeState,
                    notices = notices,
                    unreadNoticeIds = unreadNoticeIds,
                    pendingNotice = repository.pickPendingNotice(notices)
                )
            }.onFailure { error ->
                noticeState = noticeStateWithRefreshError(
                    noticeState = noticeState,
                    errorMessage = toUserFacingMessage(error, "公告加载失败", serviceLabel = "公告服务")
                )
            }
        }
    }

    fun dismissNoticeDialog() {
        val dismissedNotice = noticeState.dialogNotice
        dismissedNotice?.let(repository::markNoticeDismissed)
        noticeState = noticeStateAfterDialogDismiss(
            noticeState = noticeState,
            unreadNoticeIds = repository.unreadActiveNoticeIds(noticeState.notices)
        )
    }

    fun markNoticeOpened(noticeId: String) {
        val normalized = noticeId.trim()
        noticeState.notices
            .firstOrNull { it.id == normalized }
            ?.let(repository::markNoticeDismissed)
        noticeState = noticeStateAfterNoticeOpened(
            noticeState = noticeState,
            noticeId = normalized,
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
        homeState = loadingHomeState(cachedPayload)
        viewModelScope.launch {
            val shouldRefreshFromNetwork = forceRefresh || cachedPayload != null
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHome(forceRefresh = shouldRefreshFromNetwork) }
            }.onSuccess { payload ->
                homeState = homeStateFromPayload(payload)
            }.onFailure { error ->
                homeState = homeStateWithHomeError(
                    homeState = homeState,
                    cachedPayload = cachedPayload,
                    forceRefresh = forceRefresh,
                    errorMessage = toUserFacingMessage(error, "首页加载失败")
                )
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
            homeState = beginCategoryLoadState(homeState, category, filters)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadCategoryCursorPage(
                        typeId = category.typeId,
                        cursor = "",
                        filters = filters
                    )
                }
            }.onSuccess { payload ->
                homeState = homeStateWithCategoryPage(homeState, payload)
            }.onFailure { error ->
                homeState = homeStateWithCategoryError(
                    homeState,
                    toUserFacingMessage(error, "分类加载失败")
                )
            }
        }
    }

    fun loadMoreHome() {
        if (homeState.isHomeAppending) {
            return
        }
        if (homeState.homeVisibleCount < homeState.latest.size) {
            homeState = homeStateWithExpandedHomeVisibleCount(homeState)
            return
        }
        if (!homeState.hasMoreHomeItems) return
        viewModelScope.launch {
            val previousVisibleCount = homeState.homeVisibleCount
            homeState = beginHomeAppendState(homeState)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadLatestCursorPage(cursor = homeState.homeCursor)
                }
            }.onSuccess { payload ->
                homeState = homeStateWithAppendedHomePage(homeState, previousVisibleCount, payload)
            }.onFailure { error ->
                homeState = homeStateWithHomeAppendError(
                    homeState,
                    toUserFacingMessage(error, "继续加载首页失败")
                )
            }
        }
    }

    fun loadMoreCategory() {
        if (homeState.isCategoryAppending) {
            return
        }
        if (homeState.categoryVisibleCount < homeState.categoryVideos.size) {
            homeState = homeStateWithExpandedCategoryVisibleCount(homeState)
            return
        }
        if (!homeState.hasMoreCategoryItems) return
        val category = homeState.selectedCategory ?: return
        viewModelScope.launch {
            val previousVisibleCount = homeState.categoryVisibleCount
            homeState = beginCategoryAppendState(homeState)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadCategoryCursorPage(
                        typeId = category.typeId,
                        cursor = homeState.categoryCursor,
                        filters = homeState.selectedCategoryFilters
                    )
                }
            }.onSuccess { payload ->
                homeState = homeStateWithAppendedCategoryPage(homeState, previousVisibleCount, payload)
            }.onFailure { error ->
                homeState = homeStateWithCategoryAppendError(
                    homeState,
                    toUserFacingMessage(error, "继续加载分类失败")
                )
            }
        }
    }

    fun updateQuery(query: String) {
        searchState = searchStateWithQuery(searchState, query)
    }

    fun searchHistory(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return
        searchState = searchStateWithQuery(searchState, normalized)
    }

    fun clearSearchHistory() {
        searchHistoryStore.clear()
        searchState = searchStateWithHistory(searchState, emptyList())
    }

    fun search(keyword: String) {
        val normalized = keyword.trim()
        searchState = searchStateWithQuery(searchState, normalized)
        performSearch(normalized)
    }

    fun refreshHotSearches(forceRefresh: Boolean = false) {
        if (!forceRefresh && (searchState.hotSearchGroups.isNotEmpty() || searchState.isHotSearchLoading)) {
            return
        }
        viewModelScope.launch {
            searchState = startHotSearchLoading(searchState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHotSearchGroups(forceRefresh = forceRefresh) }
            }.onSuccess { groups ->
                searchState = searchStateWithHotSearchGroups(searchState, groups)
            }.onFailure { error ->
                searchState = searchStateWithHotSearchError(
                    searchState,
                    toUserFacingMessage(error, "热搜加载失败")
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
        val alreadyShowingSameQuery = shouldReuseSearchResults(current, normalized)
        if (alreadyShowingSameQuery) {
            if (current.query != normalized) {
                searchState = searchStateWithQuery(current, normalized)
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
            searchState = blankSearchState(searchState)
            return
        }
        searchJob?.cancel()
        searchEnrichJob?.cancel()
        val requestVersion = ++searchRequestVersion
        searchState = beginSearchState(searchState, query)
        searchJob = viewModelScope.launch searchLaunch@{
            try {
                val firstPage = withContext(Dispatchers.IO) {
                    repository.searchCursor(keyword = query, cursor = "")
                }
                if (requestVersion != searchRequestVersion) return@searchLaunch

                searchHistoryStore.save(query)
                searchState = searchStateWithFirstPage(
                    searchState = searchState,
                    history = searchHistoryStore.load(),
                    page = firstPage
                )

                if (firstPage.items.isNotEmpty()) {
                    searchEnrichJob = viewModelScope.launch searchEnrichLaunch@{
                        val enrichedResults = withContext(Dispatchers.IO) {
                            repository.enrichSearchResults(firstPage.items, limit = 8)
                        }
                        if (requestVersion != searchRequestVersion) return@searchEnrichLaunch
                        searchState = searchStateWithEnrichedResults(searchState, enrichedResults)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestVersion != searchRequestVersion) return@searchLaunch
                searchState = searchStateWithError(
                    searchState,
                    toUserFacingMessage(error, "搜索失败")
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
            searchState = beginSearchAppend(searchState)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.searchCursor(keyword = query, cursor = searchState.cursor)
                }
            }.onSuccess { page ->
                searchState = searchStateWithAppendedPage(searchState, page)
            }.onFailure { error ->
                searchState = searchStateWithAppendError(
                    searchState,
                    toUserFacingMessage(error, "继续加载搜索结果失败")
                )
            }
        }
    }

    fun updateLoginUserName(value: String) {
        accountState = accountStateWithUserName(accountState, value)
    }

    fun updateLoginPassword(value: String) {
        accountState = accountStateWithPassword(accountState, value)
    }

    fun setAccountAuthMode(mode: AccountAuthMode) {
        accountState = accountStateWithAuthMode(accountState, mode)
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
        accountState = accountStateWithRegisterEditor(
            accountState,
            transform(accountState.registerEditor)
        )
    }

    fun refreshRegisterCaptcha() {
        val captchaUrl = accountState.registerCaptchaUrl
        if (captchaUrl.isBlank()) {
            loadRegisterPage(forceRefresh = true)
            return
        }

        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadRegisterCaptcha(captchaUrl) }
            }.onSuccess { bytes ->
                accountState = accountStateWithRegisterCaptcha(accountState, bytes)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "验证码加载失败")
                )
            }
        }
    }

    fun updateFindPasswordEditor(transform: (FindPasswordEditor) -> FindPasswordEditor) {
        accountState = accountStateWithFindPasswordEditor(
            accountState,
            transform(accountState.findPasswordEditor)
        )
    }

    fun refreshFindPasswordCaptcha() {
        val captchaUrl = accountState.findPasswordCaptchaUrl
        if (captchaUrl.isBlank()) {
            loadFindPasswordPage(forceRefresh = true)
            return
        }

        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadFindPasswordCaptcha(captchaUrl) }
            }.onSuccess { bytes ->
                accountState = accountStateWithFindPasswordCaptcha(accountState, bytes)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "验证码加载失败")
                )
            }
        }
    }

    fun updateProfileEditor(transform: (UserProfileEditor) -> UserProfileEditor) {
        accountState = accountStateWithProfileEditor(
            accountState,
            transform(accountState.profileEditor)
        )
    }

    fun setProfileEditTab(editMode: Boolean) {
        accountState = accountStateWithProfileEditTab(accountState, editMode)
    }

    fun selectAccountSection(section: AccountSection, forceRefresh: Boolean = false) {
        accountState = selectAccountSectionState(accountState, section)
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
                accountState = accountStateRemovingFavorite(accountState, recordId)
                if (removedItem?.vodId == detailState.item?.vodId) {
                    detailState = detailStateWithoutFavorite(detailState)
                }
                selectAccountSection(AccountSection.Favorites, forceRefresh = true)
            }
        )
    }

    fun clearFavorites() {
        runAccountAction(
            block = { deleteUserRecordForApp(recordIds = emptyList(), type = 2, clearAll = true) },
            onSuccess = {
                accountState = accountStateClearingFavorites(accountState)
                if (detailState.item != null) {
                    detailState = detailStateWithoutFavorite(detailState)
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
                accountState = accountStateRemovingHistory(accountState, recordId)
                selectAccountSection(AccountSection.History, forceRefresh = true)
            }
        )
    }

    fun clearHistory() {
        runAccountAction(
            block = { deleteUserRecordForApp(recordIds = emptyList(), type = 4, clearAll = true) },
            onSuccess = {
                accountState = accountStateClearingHistory(accountState)
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
                accountState = accountStateAfterProfileSaved(accountState)
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
                refreshAccount()
            }
        )
    }

    fun uploadPortrait(uri: Uri) {
        if (!accountState.session.isLoggedIn) {
            accountState = accountStateWithValidationError(accountState, "璇峰厛鐧诲綍")
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
            accountState = accountStateWithValidationError(accountState, "请输入邮箱地址")
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
            accountState = accountStateWithValidationError(accountState, "请输入邮箱地址")
            return
        }
        if (code.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入邮箱验证码")
            return
        }
        runAccountAction(
            block = { bindEmail(email, code) },
            onSuccess = {
                accountState = accountStateAfterEmailBound(accountState, email)
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }
        )
    }

    fun unbindEmail() {
        runAccountAction(
            block = { unbindEmail() },
            onSuccess = {
                accountState = accountStateAfterEmailUnbound(accountState)
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }
        )
    }

    fun sendRegisterCode() {
        val editor = accountState.registerEditor
        val contact = editor.contact.trim()
        if (contact.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入${accountState.registerContactLabel}")
            return
        }

        if (editor.channel == "email" && !contact.contains("@")) {
            accountState = accountStateWithValidationError(accountState, "请输入正确的邮箱地址")
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
            accountState = accountStateWithValidationError(accountState, "请输入用户名")
            return
        }
        if (editor.password.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入密码")
            return
        }
        if (editor.confirmPassword.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请确认密码")
            return
        }
        if (editor.password != editor.confirmPassword) {
            accountState = accountStateWithValidationError(accountState, "两次输入的密码不一致")
            return
        }
        if (editor.contact.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入${accountState.registerContactLabel}")
            return
        }
        if (accountState.registerRequiresCode && editor.code.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入${accountState.registerCodeLabel}")
            return
        }
        if (accountState.registerRequiresVerify && editor.verify.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入图片验证码")
            return
        }

        runAccountAction(
            block = { registerForApp(editor.copy(channel = accountState.registerChannel)) },
            onSuccess = {
                accountState = accountStateAfterRegisterSuccess(accountState, editor.userName)
            }
        )
    }

    fun findPassword() {
        val editor = accountState.findPasswordEditor
        if (editor.userName.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "璇疯緭鍏ョ敤鎴峰悕")
            return
        }
        if (editor.question.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "璇疯緭鍏ュ瘑淇濋棶棰?")
            return
        }
        if (editor.answer.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "璇疯緭鍏ュ瘑淇濈瓟妗?")
            return
        }
        if (editor.password.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "璇疯緭鍏ユ柊瀵嗙爜")
            return
        }
        if (editor.confirmPassword.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "璇风‘璁ゆ柊瀵嗙爜")
            return
        }
        if (editor.password != editor.confirmPassword) {
            accountState = accountStateWithValidationError(accountState, "涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?")
            return
        }
        if (accountState.findPasswordRequiresVerify && editor.verify.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "璇疯緭鍏ュ浘鐗囬獙璇佺爜")
            return
        }

        runAccountAction(
            block = { findPasswordForApp(editor) },
            onSuccess = {
                accountState = accountStateAfterFindPasswordSuccess(accountState, editor.userName)
            }
        )
    }

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

    fun login() {
        val userName = accountState.userName.trim()
        val password = accountState.password
        if (userName.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入用户名")
            return
        }
        if (password.isBlank()) {
            accountState = accountStateWithValidationError(accountState, "请输入密码")
            return
        }

        viewModelScope.launch {
            accountState = beginLogin(accountState)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loginForApp(userName = userName, password = password)
                }
            }.onSuccess { session ->
                accountState = loggedInAccountState(accountState, session)
                selectAccountSection(AccountSection.Profile, forceRefresh = true)
            }.onFailure { error ->
                accountState = accountStateWithLoginError(
                    accountState,
                    toUserFacingMessage(error, "登录失败")
                )
            }
        }
    }

    fun logout() {
        if (accountState.isLoading) return
        viewModelScope.launch {
            accountState = beginLogout(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.logoutForApp() }
            }.onSuccess {
                accountState = loggedOutAccountState(accountState)
                refreshNotices(forceRefresh = true)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithLogoutError(
                    accountState,
                    toUserFacingMessage(error, "退出登录失败")
                )
            }
        }
    }

    private fun loadRegisterPage(forceRefresh: Boolean = false) {
        if (accountState.isContentLoading && !forceRefresh) return
        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadRegisterPageForApp() }
            }.onSuccess { page ->
                accountState = accountStateWithRegisterPage(accountState, page)
                if (page.requiresVerify && page.captchaUrl.isNotBlank()) {
                    refreshRegisterCaptcha()
                }
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "注册页面加载失败")
                )
            }
        }
    }

    private fun loadFindPasswordPage(forceRefresh: Boolean = false) {
        if (accountState.isContentLoading && !forceRefresh) return
        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadFindPasswordPageForApp() }
            }.onSuccess { page ->
                accountState = accountStateWithFindPasswordPage(accountState, page)
                if (page.requiresVerify && page.captchaUrl.isNotBlank()) {
                    refreshFindPasswordCaptcha()
                }
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "找回密码页面加载失败")
                )
            }
        }
    }

    private fun loadAccountProfile() {
        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadUserProfileForApp() }
            }.onSuccess { page ->
                accountState = accountStateWithProfilePage(accountState, page)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "加载资料失败")
                )
            }
        }
    }

    private fun loadFavoriteRecords(pageUrl: String? = null, append: Boolean = false) {
        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadFavoritePageForApp(pageUrl) }
            }.onSuccess { page ->
                accountState = accountStateWithFavoritePage(accountState, page, append)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "加载收藏失败")
                )
            }
        }
    }

    private fun loadHistoryRecords(pageUrl: String? = null, append: Boolean = false) {
        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHistoryPageForApp(pageUrl) }
            }.onSuccess { page ->
                val historyPageState = accountStateWithHistoryPage(accountState, page, append)
                accountState = historyPageState.accountState
                enrichHistoryRecords(historyPageState.mergedItems)
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "加载播放记录失败")
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
            accountState = accountStateWithEnrichedHistoryItems(accountState, enrichedByKey)
        }
    }

    private fun loadMembership() {
        viewModelScope.launch {
            accountState = beginAccountContentLoad(accountState)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadMembershipDataForApp() }
            }.onSuccess { page ->
                accountState = accountStateWithMembershipPage(
                    accountState = accountState,
                    page = page,
                    currentSession = repository.currentSession()
                )
            }.onFailure { error ->
                if (handleAccountSessionExpired(error)) return@onFailure
                accountState = accountStateWithContentError(
                    accountState,
                    toUserFacingMessage(error, "加载会员信息失败")
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

