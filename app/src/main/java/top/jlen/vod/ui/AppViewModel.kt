package top.jlen.vod.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.jlen.vod.data.AppleCmsCategory
import top.jlen.vod.data.AppleCmsRepository
import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.Episode
import top.jlen.vod.data.PlaySource
import top.jlen.vod.data.VodItem

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppleCmsRepository(application)
    private val allCategory = AppleCmsCategory(typeId = "__all__", typeName = "全部分类")

    var homeState by mutableStateOf(HomeUiState())
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
        refreshAccount()
        refreshHome()
    }

    fun refreshAccount() {
        accountState = accountState.copy(
            session = repository.currentSession()
        )
    }

    fun refreshHome() {
        viewModelScope.launch {
            homeState = homeState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.loadHome() }
            }.onSuccess { payload ->
                val categories = listOf(allCategory) + payload.categories
                homeState = HomeUiState(
                    isLoading = false,
                    featured = payload.featured,
                    latest = payload.latest,
                    homeVisibleCount = payload.latest.size,
                    homePage = payload.latestPage,
                    homeTotalCount = payload.latestTotal,
                    hasMoreHomePages = payload.latestHasNextPage,
                    categories = categories,
                    selectedCategory = allCategory,
                    categoryVideos = payload.latest,
                    categoryVisibleCount = payload.latest.size,
                    categoryPage = payload.latestPage,
                    categoryTotalCount = payload.latestTotal,
                    hasMoreCategoryPages = payload.latestHasNextPage
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isLoading = false,
                    error = error.message ?: "首页加载失败"
                )
            }
        }
    }

    fun selectCategory(category: AppleCmsCategory) {
        if (category.typeId == allCategory.typeId) {
            homeState = homeState.copy(
                selectedCategory = category,
                categoryVideos = homeState.latest,
                categoryVisibleCount = homeState.latest.size,
                categoryPage = homeState.homePage,
                categoryTotalCount = homeState.homeTotalCount,
                hasMoreCategoryPages = homeState.hasMoreHomePages,
                isCategoryAppending = false,
                isCategoryLoading = false,
                error = null
            )
            return
        }
        if (category.typeId == homeState.selectedCategory?.typeId && homeState.categoryVideos.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            homeState = homeState.copy(
                selectedCategory = category,
                isCategoryLoading = true,
                isCategoryAppending = false,
                error = null
            )
            runCatching {
                withContext(Dispatchers.IO) { repository.loadCategoryPage(category.typeId, page = 1) }
            }.onSuccess { payload ->
                homeState = homeState.copy(
                    categoryVideos = payload.items,
                    categoryVisibleCount = payload.items.size,
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
                    error = error.message ?: "分类加载失败"
                )
            }
        }
    }

    fun loadMoreHome() {
        if (homeState.isHomeAppending || !homeState.hasMoreHomePages) {
            return
        }
        viewModelScope.launch {
            val nextPage = homeState.homePage + 1
            homeState = homeState.copy(isHomeAppending = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadAllCategoryPage(page = nextPage)
                }
            }.onSuccess { payload ->
                val mergedLatest = (homeState.latest + payload.items).distinctBy { it.vodId }
                val selectedAll = homeState.selectedCategory?.typeId == allCategory.typeId
                homeState = homeState.copy(
                    latest = mergedLatest,
                    homeVisibleCount = mergedLatest.size,
                    homePage = payload.page,
                    homeTotalCount = payload.totalItems,
                    hasMoreHomePages = payload.hasNextPage,
                    categoryVideos = if (selectedAll) mergedLatest else homeState.categoryVideos,
                    categoryVisibleCount = if (selectedAll) {
                        mergedLatest.size
                    } else {
                        homeState.categoryVisibleCount
                    },
                    categoryPage = if (selectedAll) payload.page else homeState.categoryPage,
                    categoryTotalCount = if (selectedAll) payload.totalItems else homeState.categoryTotalCount,
                    hasMoreCategoryPages = if (selectedAll) payload.hasNextPage else homeState.hasMoreCategoryPages,
                    isHomeAppending = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isHomeAppending = false,
                    error = error.message ?: "继续加载首页失败"
                )
            }
        }
    }

    fun loadMoreCategory() {
        if (homeState.selectedCategory?.typeId == allCategory.typeId) {
            if (homeState.isCategoryAppending || !homeState.hasMoreCategoryPages) {
                return
            }
            viewModelScope.launch {
                val nextPage = homeState.categoryPage + 1
                homeState = homeState.copy(isCategoryAppending = true, error = null)
                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.loadAllCategoryPage(page = nextPage)
                    }
                }.onSuccess { payload ->
                    val mergedVideos = (homeState.categoryVideos + payload.items).distinctBy { it.vodId }
                    homeState = homeState.copy(
                        latest = mergedVideos,
                        homeVisibleCount = mergedVideos.size,
                        homePage = payload.page,
                        homeTotalCount = payload.totalItems,
                        hasMoreHomePages = payload.hasNextPage,
                        categoryVideos = mergedVideos,
                        categoryVisibleCount = mergedVideos.size,
                        categoryPage = payload.page,
                        categoryTotalCount = payload.totalItems,
                        hasMoreCategoryPages = payload.hasNextPage,
                        isCategoryAppending = false
                    )
                }.onFailure { error ->
                    homeState = homeState.copy(
                        isCategoryAppending = false,
                        error = error.message ?: "继续加载全部分类失败"
                    )
                }
            }
            return
        }
        if (homeState.isCategoryAppending || !homeState.hasMoreCategoryPages) {
            return
        }
        val category = homeState.selectedCategory ?: return
        viewModelScope.launch {
            val nextPage = homeState.categoryPage + 1
            homeState = homeState.copy(isCategoryAppending = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadCategoryPage(typeId = category.typeId, page = nextPage)
                }
            }.onSuccess { payload ->
                val mergedVideos = (homeState.categoryVideos + payload.items).distinctBy { it.vodId }
                homeState = homeState.copy(
                    categoryVideos = mergedVideos,
                    categoryVisibleCount = mergedVideos.size,
                    categoryPage = payload.page,
                    categoryTotalCount = payload.totalItems,
                    hasMoreCategoryPages = payload.hasNextPage,
                    isCategoryAppending = false
                )
            }.onFailure { error ->
                homeState = homeState.copy(
                    isCategoryAppending = false,
                    error = error.message ?: "继续加载分类失败"
                )
            }
        }
    }

    fun updateQuery(query: String) {
        searchState = searchState.copy(query = query)
    }

    fun search() {
        val query = searchState.query.trim()
        if (query.isBlank()) {
            searchState = searchState.copy(
                results = emptyList(),
                error = "请输入影片名称"
            )
            return
        }
        viewModelScope.launch {
            searchState = searchState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.search(query) }
            }.onSuccess { results ->
                searchState = searchState.copy(
                    isLoading = false,
                    results = results,
                    error = if (results.isEmpty()) "没有找到相关结果" else null
                )
            }.onFailure { error ->
                searchState = searchState.copy(
                    isLoading = false,
                    error = error.message ?: "搜索失败"
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
            accountState = accountState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.login(userName = userName, password = password)
                }
            }.onSuccess { session ->
                accountState = accountState.copy(
                    isLoading = false,
                    session = session,
                    password = "",
                    error = null
                )
            }.onFailure { error ->
                accountState = accountState.copy(
                    isLoading = false,
                    error = error.message ?: "登录失败"
                )
            }
        }
    }

    fun logout() {
        if (accountState.isLoading) return
        viewModelScope.launch {
            accountState = accountState.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.logout() }
            }.onSuccess {
                accountState = AccountUiState(
                    userName = accountState.userName,
                    session = AuthSession()
                )
            }.onFailure { error ->
                accountState = accountState.copy(
                    isLoading = false,
                    error = error.message ?: "退出登录失败"
                )
            }
        }
    }

    fun loadDetail(vodId: String) {
        if (detailState.item?.vodId == vodId && detailState.sources.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            detailState = DetailUiState(isLoading = true, error = null)
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
                        selectedSourceIndex = 0
                    )
                }
            }.onFailure { error ->
                detailState = DetailUiState(
                    isLoading = false,
                    error = error.message ?: "详情加载失败"
                )
            }
        }
    }

    fun selectSource(index: Int) {
        detailState = detailState.copy(selectedSourceIndex = index)
    }

    fun openPlayer(title: String, sources: List<PlaySource>, sourceIndex: Int, episodeIndex: Int) {
        val safeSourceIndex = sourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
        val safeEpisodes = sources.getOrNull(safeSourceIndex)?.episodes.orEmpty()
        playerState = PlayerUiState(
            title = title,
            sources = sources,
            selectedSourceIndex = safeSourceIndex,
            selectedEpisodeIndex = episodeIndex.coerceIn(0, (safeEpisodes.size - 1).coerceAtLeast(0)),
            playbackSnapshot = PlaybackSnapshot()
        )
        resolveCurrentPlayerUrl()
    }

    fun selectPlayerEpisode(index: Int) {
        val currentEpisodes = playerState.currentSource?.episodes.orEmpty()
        if (currentEpisodes.isEmpty()) return
        playerState = playerState.copy(
            selectedEpisodeIndex = index.coerceIn(0, currentEpisodes.lastIndex),
            playbackSnapshot = PlaybackSnapshot()
        )
        resolveCurrentPlayerUrl()
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
            resolveError = message.ifBlank { "接管播放器失败，请切换线路重试" }
        )
    }

    fun updatePlaybackSnapshot(snapshot: PlaybackSnapshot) {
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
                    resolveError = "解析线路失败，请切换线路重试"
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
    val featured: List<VodItem> = emptyList(),
    val latest: List<VodItem> = emptyList(),
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
        get() = latest

    val hasMoreLatest: Boolean
        get() = hasMoreHomePages

    val visibleCategoryVideos: List<VodItem>
        get() = categoryVideos

    val hasMoreCategoryVideos: Boolean
        get() = hasMoreCategoryPages
}

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val results: List<VodItem> = emptyList()
)

data class AccountUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userName: String = "",
    val password: String = "",
    val session: AuthSession = AuthSession()
)

data class DetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val item: VodItem? = null,
    val sources: List<PlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0
) {
    val selectedSource: PlaySource?
        get() = sources.getOrNull(selectedSourceIndex)
}

data class PlayerUiState(
    val title: String = "",
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
