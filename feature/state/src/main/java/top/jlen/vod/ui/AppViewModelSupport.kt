package top.jlen.vod.ui

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.abs
import javax.net.ssl.SSLException
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.CursorPagedVodItems
import top.jlen.vod.data.HomePayload
import top.jlen.vod.data.MembershipPage
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.UserCenterPage
import top.jlen.vod.data.UserCenterItem
import top.jlen.vod.data.UserProfilePage
import top.jlen.vod.data.VodItem

internal fun homeStateFromPayload(payload: HomePayload): HomeUiState {
    val defaultSelected = payload.categories.firstOrNull()
    val categoryVideos = payload.categoryVideos
    return HomeUiState(
        isLoading = false,
        slides = payload.slides,
        hot = payload.hot,
        featured = payload.featured,
        latest = payload.latest,
        sections = payload.sections,
        homeVisibleCount = payload.latest.initialGridVisibleCount(),
        homeCursor = payload.latestCursor,
        hasMoreHomeItems = payload.latestHasMore,
        homeFirstLoaded = true,
        categories = payload.categories,
        selectedCategory = defaultSelected,
        categoryVideos = categoryVideos,
        categoryVisibleCount = categoryVideos.initialGridVisibleCount(),
        categoryCursor = payload.categoryCursor,
        hasMoreCategoryItems = payload.categoryHasMore,
        categoryFirstLoaded = true,
        error = null
    )
}

internal fun loadingHomeState(cachedPayload: HomePayload?): HomeUiState =
    cachedPayload?.let(::homeStateFromPayload) ?: HomeUiState(isLoading = true)

internal fun homeStateWithHomeError(
    homeState: HomeUiState,
    cachedPayload: HomePayload?,
    forceRefresh: Boolean,
    errorMessage: String
): HomeUiState =
    if (cachedPayload != null && !forceRefresh) {
        homeState.copy(isLoading = false)
    } else {
        homeState.copy(
            isLoading = false,
            error = errorMessage
        )
    }

internal fun beginCategoryLoadState(
    homeState: HomeUiState,
    category: AppleCmsCategory,
    filters: Map<String, String>
): HomeUiState = homeState.copy(
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

internal fun homeStateWithCategoryPage(
    homeState: HomeUiState,
    page: CursorPagedVodItems
): HomeUiState = homeState.copy(
    categoryVideos = page.items,
    categoryVisibleCount = page.items.initialGridVisibleCount(),
    categoryCursor = page.nextCursor,
    hasMoreCategoryItems = page.hasMore,
    categoryFirstLoaded = true,
    isCategoryAppending = false,
    isCategoryLoading = false
)

internal fun homeStateWithCategoryError(
    homeState: HomeUiState,
    errorMessage: String
): HomeUiState = homeState.copy(
    isCategoryAppending = false,
    isCategoryLoading = false,
    error = errorMessage
)

internal fun homeStateWithExpandedHomeVisibleCount(homeState: HomeUiState): HomeUiState {
    val nextVisibleCount = homeState.homeVisibleCount + GRID_BATCH_ITEM_COUNT
    return homeState.copy(
        homeVisibleCount = nextVisibleCount.coerceAtMost(homeState.latest.size)
    )
}

internal fun beginHomeAppendState(homeState: HomeUiState): HomeUiState =
    homeState.copy(isHomeAppending = true, homeAppendError = null)

internal fun homeStateWithAppendedHomePage(
    homeState: HomeUiState,
    previousVisibleCount: Int,
    page: CursorPagedVodItems
): HomeUiState {
    val mergedLatest = (homeState.latest + page.items).distinctBy { it.vodId }
    return homeState.copy(
        latest = mergedLatest,
        homeVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
            .coerceAtLeast(mergedLatest.initialGridVisibleCount())
            .coerceAtMost(mergedLatest.size),
        homeCursor = page.nextCursor,
        hasMoreHomeItems = page.hasMore,
        isHomeAppending = false
    )
}

internal fun homeStateWithHomeAppendError(
    homeState: HomeUiState,
    errorMessage: String
): HomeUiState = homeState.copy(
    isHomeAppending = false,
    homeAppendError = errorMessage
)

internal fun homeStateWithExpandedCategoryVisibleCount(homeState: HomeUiState): HomeUiState {
    val nextVisibleCount = homeState.categoryVisibleCount + GRID_BATCH_ITEM_COUNT
    return homeState.copy(
        categoryVisibleCount = nextVisibleCount.coerceAtMost(homeState.categoryVideos.size)
    )
}

internal fun beginCategoryAppendState(homeState: HomeUiState): HomeUiState =
    homeState.copy(isCategoryAppending = true, categoryAppendError = null)

internal fun homeStateWithAppendedCategoryPage(
    homeState: HomeUiState,
    previousVisibleCount: Int,
    page: CursorPagedVodItems
): HomeUiState {
    val mergedVideos = (homeState.categoryVideos + page.items).distinctBy { it.vodId }
    return homeState.copy(
        categoryVideos = mergedVideos,
        categoryVisibleCount = (previousVisibleCount + GRID_BATCH_ITEM_COUNT)
            .coerceAtLeast(mergedVideos.initialGridVisibleCount())
            .coerceAtMost(mergedVideos.size),
        categoryCursor = page.nextCursor,
        hasMoreCategoryItems = page.hasMore,
        isCategoryAppending = false
    )
}

internal fun homeStateWithCategoryAppendError(
    homeState: HomeUiState,
    errorMessage: String
): HomeUiState = homeState.copy(
    isCategoryAppending = false,
    categoryAppendError = errorMessage
)

internal fun resolveHeartbeatVodId(playerState: PlayerUiState): String {
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

internal fun mergeAccountSession(
    session: AuthSession,
    currentSession: AuthSession
): AuthSession = session.copy(
    userName = session.userName.ifBlank { currentSession.userName },
    groupName = session.groupName.ifBlank { currentSession.groupName },
    portraitUrl = session.portraitUrl.ifBlank { currentSession.portraitUrl }
)

internal fun toUserFacingMessage(
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

internal fun normalizeFavoriteActionMessage(rawMessage: String): String {
    val message = rawMessage.trim()
    if (message.isBlank()) return "已加入收藏"
    return when {
        isDuplicateFavoriteMessage(message) -> "已在收藏中"
        message.contains("成功") || message.contains("获取成功") || message.contains("操作成功") -> "已加入收藏"
        else -> "已加入收藏"
    }
}

internal fun isDuplicateFavoriteMessage(rawMessage: String): Boolean {
    val message = rawMessage.trim()
    if (message.isBlank()) return false
    return message.contains("已收藏") ||
        message.contains("已经收藏") ||
        message.contains("已存在") ||
        message.contains("重复")
}

internal fun mergeAccountItems(
    current: List<UserCenterItem>,
    incoming: List<UserCenterItem>
): List<UserCenterItem> = (current + incoming)
    .distinctBy { item -> "${item.recordId}:${item.actionUrl}:${item.vodId}" }

internal fun searchStateWithQuery(searchState: SearchUiState, query: String): SearchUiState =
    searchState.copy(query = query, error = null)

internal fun startHotSearchLoading(searchState: SearchUiState): SearchUiState =
    searchState.copy(isHotSearchLoading = true, hotSearchError = null)

internal fun searchStateWithHotSearchGroups(
    searchState: SearchUiState,
    groups: List<top.jlen.vod.data.HotSearchGroup>
): SearchUiState = searchState.copy(
    isHotSearchLoading = false,
    hotSearchGroups = groups,
    hotSearchError = null
)

internal fun searchStateWithHotSearchError(
    searchState: SearchUiState,
    errorMessage: String
): SearchUiState = searchState.copy(
    isHotSearchLoading = false,
    hotSearchError = errorMessage
)

internal fun shouldReuseSearchResults(
    searchState: SearchUiState,
    normalizedQuery: String
): Boolean =
    searchState.submittedQuery == normalizedQuery &&
        (searchState.firstLoaded || searchState.isLoading || !searchState.error.isNullOrBlank())

internal fun beginSearchState(
    searchState: SearchUiState,
    query: String
): SearchUiState = searchState.copy(
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

internal fun blankSearchState(searchState: SearchUiState): SearchUiState = searchState.copy(
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

internal fun searchStateWithFirstPage(
    searchState: SearchUiState,
    history: List<String>,
    page: CursorPagedVodItems
): SearchUiState = searchState.copy(
    isLoading = false,
    history = history,
    results = page.items,
    cursor = page.nextCursor,
    hasMore = page.hasMore,
    firstLoaded = true,
    error = null
)

internal fun searchStateWithEnrichedResults(
    searchState: SearchUiState,
    results: List<VodItem>
): SearchUiState = searchState.copy(results = results)

internal fun searchStateWithError(
    searchState: SearchUiState,
    errorMessage: String
): SearchUiState = searchState.copy(
    isLoading = false,
    firstLoaded = true,
    error = errorMessage
)

internal fun beginSearchAppend(searchState: SearchUiState): SearchUiState =
    searchState.copy(isAppending = true, appendError = null)

internal fun searchStateWithAppendedPage(
    searchState: SearchUiState,
    page: CursorPagedVodItems
): SearchUiState {
    val mergedResults = (searchState.results + page.items).distinctBy { it.vodId }
    return searchState.copy(
        isAppending = false,
        results = mergedResults,
        cursor = page.nextCursor,
        hasMore = page.hasMore
    )
}

internal fun searchStateWithAppendError(
    searchState: SearchUiState,
    errorMessage: String
): SearchUiState = searchState.copy(
    isAppending = false,
    appendError = errorMessage
)

internal fun selectAccountSectionState(
    accountState: AccountUiState,
    section: AccountSection
): AccountUiState = accountState.copy(selectedSection = section, error = null, message = null)

internal fun accountStateWithProfilePage(
    accountState: AccountUiState,
    page: UserProfilePage
): AccountUiState = accountState.copy(
    isContentLoading = false,
    profileFields = page.fields,
    profileEditor = page.editor,
    session = mergeAccountSession(page.session, accountState.session)
)

internal fun accountStateWithFavoritePage(
    accountState: AccountUiState,
    page: UserCenterPage,
    append: Boolean
): AccountUiState = accountState.copy(
    isContentLoading = false,
    favoriteItems = mergeAccountItems(
        current = if (append) accountState.favoriteItems else emptyList(),
        incoming = page.items
    ),
    favoriteNextPageUrl = page.nextPageUrl
)

internal data class HistoryPageState(
    val accountState: AccountUiState,
    val mergedItems: List<UserCenterItem>
)

internal fun accountStateWithHistoryPage(
    accountState: AccountUiState,
    page: UserCenterPage,
    append: Boolean
): HistoryPageState {
    val mergedItems = mergeAccountItems(
        current = if (append) accountState.historyItems else emptyList(),
        incoming = page.items
    )
    return HistoryPageState(
        accountState = accountState.copy(
            isContentLoading = false,
            historyItems = mergedItems,
            historyNextPageUrl = page.nextPageUrl
        ),
        mergedItems = mergedItems
    )
}

internal fun accountStateWithMembershipPage(
    accountState: AccountUiState,
    page: MembershipPage,
    currentSession: AuthSession
): AccountUiState {
    val refreshedSession = mergeAccountSession(currentSession, accountState.session)
        .let { session -> session.copy(groupName = page.info.groupName.ifBlank { session.groupName }) }
    return accountState.copy(
        isContentLoading = false,
        session = refreshedSession,
        membershipInfo = page.info.copy(
            groupName = page.info.groupName.ifBlank { refreshedSession.groupName }
        ),
        membershipPlans = page.plans
    )
}

internal fun accountStateRemovingFavorite(
    accountState: AccountUiState,
    recordId: String
): AccountUiState = accountState.copy(
    favoriteItems = accountState.favoriteItems.filterNot { item -> item.recordId == recordId }
)

internal fun accountStateClearingFavorites(accountState: AccountUiState): AccountUiState = accountState.copy(
    favoriteItems = emptyList(),
    favoriteNextPageUrl = null
)

internal fun accountStateRemovingHistory(
    accountState: AccountUiState,
    recordId: String
): AccountUiState = accountState.copy(
    historyItems = accountState.historyItems.filterNot { item -> item.recordId == recordId }
)

internal fun accountStateClearingHistory(accountState: AccountUiState): AccountUiState = accountState.copy(
    historyItems = emptyList(),
    historyNextPageUrl = null
)

internal fun loggedOutAccountState(accountState: AccountUiState): AccountUiState = AccountUiState(
    userName = accountState.userName,
    session = AuthSession(),
    message = "已退出登录",
    updateInfo = accountState.updateInfo,
    hasCrashLog = accountState.hasCrashLog,
    latestCrashLog = accountState.latestCrashLog
)

internal fun expiredAccountState(accountState: AccountUiState): AccountUiState = AccountUiState(
    userName = accountState.userName,
    authMode = AccountAuthMode.Login,
    message = "登录已失效，请重新登录",
    updateInfo = accountState.updateInfo,
    hasCrashLog = accountState.hasCrashLog,
    latestCrashLog = accountState.latestCrashLog
)

internal fun historyRecordKey(item: UserCenterItem): String =
    listOf(item.recordId, item.actionUrl, item.playUrl, item.title)
        .joinToString("|")

internal fun List<VodItem>.initialGridVisibleCount(): Int =
    size.coerceAtMost(GRID_BATCH_ITEM_COUNT)

internal fun buildPlayerState(
    title: String,
    item: VodItem?,
    sources: List<PlaySource>,
    sourceIndex: Int,
    episodeIndex: Int
): PlayerUiState {
    val safeSourceIndex = sourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
    val safeEpisodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
    return PlayerUiState(
        title = title,
        item = item,
        sources = sources,
        selectedSourceIndex = safeSourceIndex,
        selectedEpisodeIndex = episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0)),
        playbackSnapshot = PlaybackSnapshot()
    )
}

internal fun updatePlayerEpisodeSelection(
    playerState: PlayerUiState,
    index: Int
): PlayerUiState? {
    val currentEpisodes = playerState.currentSource?.episodes.orEmpty()
    if (currentEpisodes.isEmpty()) return null
    return playerState.copy(
        selectedEpisodeIndex = index.coerceIn(0, currentEpisodes.lastIndex),
        playbackSnapshot = PlaybackSnapshot()
    )
}

internal fun updatePlayerSourceSelection(
    playerState: PlayerUiState,
    index: Int
): PlayerUiState? {
    if (playerState.sources.isEmpty()) return null
    val safeIndex = index.coerceIn(0, playerState.sources.lastIndex)
    val targetEpisodes = playerState.sources.getOrNull(safeIndex)?.episodes.orEmpty()
    val preservedEpisodeIndex = playerState.selectedEpisodeIndex
        .coerceIn(0, (targetEpisodes.size - 1).coerceAtLeast(0))
    return playerState.copy(
        selectedSourceIndex = safeIndex,
        selectedEpisodeIndex = preservedEpisodeIndex,
        playbackSnapshot = PlaybackSnapshot()
    )
}

internal fun applyDetectedStream(
    playerState: PlayerUiState,
    streamUrl: String
): PlayerUiState? {
    if (streamUrl.isBlank()) return null
    return playerState.copy(
        resolvedUrl = streamUrl,
        isResolving = false,
        useWebPlayer = false,
        resolveError = null
    )
}

internal fun applyTakeoverFailure(
    playerState: PlayerUiState,
    message: String
): PlayerUiState = playerState.copy(
    isResolving = false,
    useWebPlayer = false,
    resolveError = message.ifBlank { "该线路暂不支持，请换个线路试试" }
)

internal fun hasMeaningfulPlaybackChange(
    currentSnapshot: PlaybackSnapshot,
    incomingSnapshot: PlaybackSnapshot
): Boolean =
    abs(incomingSnapshot.positionMs - currentSnapshot.positionMs) >= UiMotion.SnapshotPositionThresholdMillis ||
        abs(incomingSnapshot.speed - currentSnapshot.speed) > 0.01f ||
        incomingSnapshot.playWhenReady != currentSnapshot.playWhenReady

internal data class FullscreenSyncState(
    val playerState: PlayerUiState,
    val shouldResolveCurrentUrl: Boolean
)

internal fun syncPlayerStateFromFullscreen(
    playerState: PlayerUiState,
    result: FullscreenPlaybackResult
): FullscreenSyncState {
    val safeEpisodes = playerState.episodes
    val previousEpisodeIndex = playerState.selectedEpisodeIndex
    val safeEpisodeIndex = result.episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0))
    val nextState = playerState.copy(
        selectedEpisodeIndex = safeEpisodeIndex,
        resolvedUrl = result.resolvedUrl.ifBlank {
            if (safeEpisodeIndex == previousEpisodeIndex) playerState.resolvedUrl else ""
        },
        useWebPlayer = false,
        isResolving = false,
        resolveError = null,
        playbackSnapshot = result.snapshot
    )
    return FullscreenSyncState(
        playerState = nextState,
        shouldResolveCurrentUrl = result.resolvedUrl.isBlank() && safeEpisodeIndex != previousEpisodeIndex
    )
}

internal const val GRID_BATCH_ROWS = 12
internal const val GRID_BATCH_COLUMNS = 3
internal const val GRID_BATCH_ITEM_COUNT = GRID_BATCH_ROWS * GRID_BATCH_COLUMNS

data class SearchResultScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)
