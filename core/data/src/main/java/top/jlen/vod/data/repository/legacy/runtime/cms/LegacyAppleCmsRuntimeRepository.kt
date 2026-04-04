package top.jlen.vod.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import top.jlen.vod.AppConfig
import top.jlen.vod.AppRuntimeInfo
import top.jlen.vod.PLAYER_DESKTOP_UA
import javax.net.ssl.SSLException

open class LegacyAppleCmsRuntimeRepository(
    context: Context,
    private val cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    private val client: OkHttpClient = createClient(cookieJar)
) {
    private val appContext = context.applicationContext
    private val pageCachePrefs = appContext.getSharedPreferences("library_page_cache", Context.MODE_PRIVATE)
    private val homeCachePrefs = appContext.getSharedPreferences("home_cache_store", Context.MODE_PRIVATE)
    private val noticePrefs = appContext.getSharedPreferences("app_notice_store", Context.MODE_PRIVATE)
    private val heartbeatPrefs = appContext.getSharedPreferences("app_heartbeat_store", Context.MODE_PRIVATE)
    private val defaultCategories = listOf(
        AppleCmsCategory(typeId = "GCCCCG", typeName = "电影", parentId = "GCCCCG"),
        AppleCmsCategory(typeId = "GCCCCT", typeName = "连续剧", parentId = "GCCCCT"),
        AppleCmsCategory(typeId = "GCCCCV", typeName = "综艺", parentId = "GCCCCV"),
        AppleCmsCategory(typeId = "GCCCCW", typeName = "动漫", parentId = "GCCCCW")
    )
    private val baseUrl = AppConfig.appleCmsBaseUrl.trimEnd('/')
    private val fallbackApiBaseUrl = AppConfig.appleCmsFallbackBaseUrl
        .trimEnd('/')
        .takeIf { it.isNotBlank() && !it.equals(baseUrl, ignoreCase = true) }
    private val gson = Gson()
    private val categoryPageCache = ConcurrentHashMap<String, CachedValue<PagedVodItems>>()
    private val detailCache = ConcurrentHashMap<String, CachedValue<VodItem?>>()
    private val searchCache = ConcurrentHashMap<String, CachedValue<List<VodItem>>>()
    private val historySourceCache = ConcurrentHashMap<String, CachedValue<List<PlaySource>>>()
    private val previewItemCache = ConcurrentHashMap<String, CachedValue<VodItem>>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Any>>()
    @Volatile
    private var homeCache: CachedValue<HomePayload>? = null
    @Volatile
    private var hotSearchCache: CachedValue<List<HotSearchGroup>>? = null
    @Volatile
    private var noticeCache: CachedValue<List<AppNotice>>? = null
    @Volatile
    private var browsableCategoriesCache: CachedValue<List<AppleCmsCategory>>? = null
    @Volatile
    private var lastMemoryCacheCleanupAt = 0L
    @Volatile
    private var lastDiskCacheCleanupAt = 0L
    private val primaryApi: AppleCmsApi = createApi(baseUrl)
    private val backupApi: AppleCmsApi? = fallbackApiBaseUrl?.let(::createApi)
    @Volatile
    private var preferBackupApiUntilMs = 0L

    private data class PortraitUploadPayload(
        val bytes: ByteArray,
        val fileName: String,
        val mimeType: String
    )

    internal data class CachedValue<T>(
        val value: T,
        val timestampMs: Long
    )

    private data class PersistedPageCache(
        val timestampMs: Long,
        val payload: PagedVodItems
    )

    private data class PersistedHomeCache(
        val timestampMs: Long,
        val payload: HomePayload
    )

    private data class AppCenterUserSnapshot(
        val session: AuthSession = AuthSession(),
        val profileFields: List<Pair<String, String>> = emptyList(),
        val profileEditor: UserProfileEditor = UserProfileEditor(),
        val membershipInfo: MembershipInfo = MembershipInfo(),
        val membershipPlans: List<MembershipPlan> = emptyList()
    )

    internal fun runtimeClearHomeCacheEntry() {
        homeCache = null
    }

    internal fun runtimeClearHotSearchCacheEntry() {
        hotSearchCache = null
    }

    internal fun runtimeClearNoticeCacheEntry() {
        noticeCache = null
    }

    internal fun runtimeClearBrowsableCategoriesCacheEntry() {
        browsableCategoriesCache = null
    }

    internal fun runtimeClearCategoryPageCache() {
        categoryPageCache.clear()
    }

    internal fun runtimeClearDetailCache() {
        detailCache.clear()
    }

    internal fun runtimeClearSearchCache() {
        searchCache.clear()
    }

    internal fun runtimeClearHistorySourceCache() {
        historySourceCache.clear()
    }

    internal fun runtimeClearPreviewItemCache() {
        previewItemCache.clear()
    }

    internal fun runtimeClearInFlightRequests() {
        inFlightRequests.clear()
    }

    internal fun runtimeResetRequestPreference() {
        preferBackupApiUntilMs = 0L
    }

    internal fun runtimeResetCleanupTimestamps() {
        lastMemoryCacheCleanupAt = 0L
        lastDiskCacheCleanupAt = 0L
    }

    internal fun runtimeClearPersistedPageCache() {
        pageCachePrefs.edit().clear().apply()
    }

    internal fun runtimeClearPersistedHomeCache() {
        homeCachePrefs.edit().clear().apply()
    }

    internal fun runtimePeekHomeCacheEntry(): CachedValue<HomePayload>? = homeCache

    internal fun runtimeUpdateHomeCacheEntry(value: CachedValue<HomePayload>?) {
        homeCache = value
    }

    internal fun runtimeReadPersistedHomeCache(): CachedValue<HomePayload>? = readPersistedHomeCache()

    internal fun runtimeHomeCacheTtlMs(allowStale: Boolean): Long =
        if (allowStale) DISK_HOME_CACHE_TTL_MS else HOME_CACHE_TTL_MS

    internal fun runtimeIsCacheValid(timestampMs: Long, ttlMs: Long): Boolean =
        isCacheValid(timestampMs, ttlMs)

    internal suspend fun <T> runtimeAwaitSharedRequest(
        key: String,
        block: suspend () -> T
    ): T = awaitSharedRequest(key, block)

    internal suspend fun runtimeLoadLatestCursorPage(cursor: String): CursorPagedVodItems =
        loadLatestCursorPage(cursor)

    internal suspend fun runtimeLoadRecommendedPreviewItems(limit: Int): List<VodItem> {
        val rawItems = requestApi { getRecommendations(limit = limit) }
            .data
            ?.rows
            .orEmpty()
            .distinctBy { it.vodId }
        return filterPlayablePreviewItems(rawItems)
    }

    internal suspend fun runtimeLoadBrowsableCategories(
        homeDocument: Document? = null,
        forceRefresh: Boolean = false
    ): List<AppleCmsCategory> = loadBrowsableCategories(homeDocument, forceRefresh)

    internal fun runtimeGetCachedBrowsableCategories(): List<AppleCmsCategory> =
        getCachedBrowsableCategories()

    internal fun runtimeDefaultCategories(): List<AppleCmsCategory> = defaultCategories

    internal fun runtimeNormalizeCategory(category: AppleCmsCategory): AppleCmsCategory =
        normalizeCategory(category)

    internal suspend fun runtimeLoadCategoryCursorPage(
        typeId: String,
        cursor: String
    ): CursorPagedVodItems = loadCategoryCursorPage(typeId, cursor)

    internal fun runtimeRememberPreviewItems(items: Collection<VodItem>) {
        rememberPreviewItems(items)
    }

    internal fun runtimeCacheHomePayload(payload: HomePayload) {
        cacheHomePayload(payload)
    }

    internal fun runtimeCleanupCachesIfNeeded() {
        cleanupCachesIfNeeded()
    }

    internal suspend fun runtimeFetchHomeDocument(): Document =
        fetchDocument("$baseUrl/")

    internal fun runtimeParseLevelOneItemsFromHomePage(
        document: Document,
        limit: Int
    ): List<VodItem> = parseLevelOneItemsFromHomePage(document, limit)

    internal fun runtimePeekCategoryPageCacheEntry(cacheKey: String): CachedValue<PagedVodItems>? =
        categoryPageCache[cacheKey]

    internal fun runtimeUpdateCategoryPageCacheEntry(
        cacheKey: String,
        value: CachedValue<PagedVodItems>
    ) {
        categoryPageCache[cacheKey] = value
    }

    internal fun runtimeReadPersistedCategoryPageCache(cacheKey: String): CachedValue<PagedVodItems>? =
        readPersistedPageCache(cacheKey)

    internal fun runtimePageCacheTtlMs(allowStale: Boolean = false): Long =
        if (allowStale) DISK_PAGE_CACHE_TTL_MS else PAGE_CACHE_TTL_MS

    internal suspend fun runtimeGetBrowsableCategories(forceRefresh: Boolean = false): List<AppleCmsCategory> =
        getBrowsableCategories(forceRefresh = forceRefresh)

    internal suspend fun runtimeLoadCategoryPage(
        typeId: String,
        page: Int,
        forceRefresh: Boolean = false
    ): PagedVodItems = loadCategoryPage(typeId, page, forceRefresh)

    internal fun runtimeBuildMergedCategoryPage(
        pages: List<PagedVodItems>,
        page: Int
    ): PagedVodItems = buildMergedCategoryPage(pages, page)

    internal fun runtimeCacheCategoryPagePayload(cacheKey: String, payload: PagedVodItems) {
        cachePagePayload(cacheKey, payload)
    }

    internal fun runtimeBaseUrl(): String = baseUrl

    internal fun runtimeHttpClient(): OkHttpClient = client

    internal fun runtimeGson(): Gson = gson

    internal fun currentNoticeCacheEntry(): CachedValue<List<AppNotice>>? = noticeCache

    internal fun updateNoticeCacheEntry(value: CachedValue<List<AppNotice>>?) {
        noticeCache = value
    }

    internal fun runtimeNoticePrefs(): SharedPreferences = noticePrefs

    internal fun runtimeHeartbeatPrefs(): SharedPreferences = heartbeatPrefs

    internal fun runtimeAppCenterApiUrl(): String = APP_CENTER_API_URL

    internal fun runtimeDismissedNoticeIdsKey(): String = KEY_DISMISSED_NOTICE_IDS

    internal fun runtimeHeartbeatDeviceIdKey(): String = HEARTBEAT_DEVICE_ID_KEY

    internal fun runtimeAppendTimestamp(url: String): String = appendTimestamp(url)

    internal fun runtimeResolveUrl(pathOrUrl: String): String = resolveUrl(pathOrUrl)

    internal fun runtimeNormalizeUrl(raw: String): String = normalizeUrl(raw)

    internal suspend fun runtimeFetchDocument(url: String): Document = fetchDocument(url)

    internal suspend fun runtimeSubmitPublicAction(
        url: String,
        referer: String,
        formBody: FormBody
    ): String = submitPublicAction(url, referer, formBody)

    internal fun runtimeParseAuthResponse(body: String): AuthResponse? =
        runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()

    internal fun runtimeExtractNoticeItems(json: JsonObject): List<JsonElement> =
        json.extractNoticeItems()

    internal fun runtimeParseNoticeItem(json: JsonObject): AppNotice? =
        parseNoticeItem(json)

    internal fun runtimeParseNoticeTimeToMillis(value: String): Long? =
        parseNoticeTimeToMillis(value)

    internal fun runtimeNoticeCacheTtlMs(): Long = NOTICE_CACHE_TTL_MS

    private fun clearMemoryCaches() = legacyClearMemoryCaches()

    private fun clearAllAppCaches() = legacyClearAllAppCaches()

    fun clearProcessMemoryCaches() = legacyClearProcessMemoryCaches()

    fun clearRuntimeCaches() = legacyClearRuntimeCaches()

    fun peekHomePayload(allowStale: Boolean = false): HomePayload? = legacyPeekHomePayload(allowStale)

    suspend fun loadHome(forceRefresh: Boolean = false): HomePayload = legacyLoadHome(forceRefresh)

    private suspend fun loadEmergencyHome(): HomePayload = legacyLoadEmergencyHome()

    private suspend fun loadFreshHome(forceRefresh: Boolean): HomePayload =
        legacyLoadFreshHome(forceRefresh)
/*
        val (latestPage, recommendedItems) = coroutineScope {
            val latestDeferred = async {
                runCatching {
                    loadLatestCursorPage(cursor = "")
                }.getOrElse {
                    CursorPagedVodItems()
                }
            }
            val recommendationsDeferred = async {
                runCatching {
                    val rawItems = requestApi { getRecommendations(limit = 16) }
                        .data
                        ?.rows
                        .orEmpty()
                        .distinctBy { it.vodId }
                    filterPlayablePreviewItems(rawItems)
                }.getOrDefault(emptyList())
            }
            latestDeferred.await() to recommendationsDeferred.await()
        }
        val homeDocument = if (recommendedItems.isEmpty()) {
            runCatching { fetchDocument("$baseUrl/") }.getOrNull()
        } else {
            null
        }
        val latest = latestPage.items
        val featured = recommendedItems.ifEmpty {
            homeDocument?.let { parseLevelOneItemsFromHomePage(it, limit = 16) }.orEmpty()
        }
        val categories = loadBrowsableCategories(homeDocument = homeDocument, forceRefresh = forceRefresh)
        val selectedCategory = categories.firstOrNull()
        val selectedCategoryPage = selectedCategory?.let { category ->
            runCatching {
                loadCategoryCursorPage(typeId = category.typeId, cursor = "")
            }.getOrNull()
        }
        rememberPreviewItems(latest + featured + selectedCategoryPage?.items.orEmpty())

        if (latest.isEmpty() && featured.isEmpty() && categories.isEmpty()) {
            throw IOException("首页内容解析失败")
        }
        return HomePayload(
            slides = emptyList(),
            hot = emptyList(),
            featured = featured,
            latest = latest,
            sections = emptyList(),
            categories = categories,
            selectedCategory = selectedCategory,
            categoryVideos = selectedCategoryPage?.items.orEmpty(),
            latestCursor = latestPage.nextCursor,
            latestHasMore = latestPage.hasMore,
            categoryCursor = selectedCategoryPage?.nextCursor.orEmpty(),
            categoryHasMore = selectedCategoryPage?.hasMore ?: false
        ).also { payload ->
            cacheHomePayload(payload)
            cleanupCachesIfNeeded()
        }
    }
*/

    suspend fun loadByCategory(typeId: String): List<VodItem> = legacyLoadByCategory(typeId)

    suspend fun loadAllCategoryPage(page: Int, forceRefresh: Boolean = false): PagedVodItems =
        legacyLoadAllCategoryPage(page, forceRefresh)

    private suspend fun loadFreshAllCategoryPage(page: Int, forceRefresh: Boolean): PagedVodItems =
        legacyLoadFreshAllCategoryPage(page, forceRefresh)

    fun peekAllCategoryPage(page: Int): PagedVodItems? = legacyPeekAllCategoryPage(page)

    fun peekCategoryPage(typeId: String, page: Int, allowStale: Boolean = false): PagedVodItems? =
        legacyPeekCategoryPage(typeId, page, allowStale)

    suspend fun prewarmCategoryFirstPages(forceRefresh: Boolean = false) =
        legacyPrewarmCategoryFirstPages(forceRefresh)

    suspend fun loadLatestCursorPage(cursor: String): CursorPagedVodItems {
        val payload = requestApi {
            getCursorList(
                linkedMapOf(
                    "limit" to HOME_CURSOR_PAGE_LIMIT.toString(),
                    "sort" to "time",
                    "cursor" to cursor
                )
            )
        }.toCursorPagedVodItems()
        val playableItems = filterPlayablePreviewItems(payload.items)
        return payload.copy(items = playableItems).also { filtered ->
            rememberPreviewItems(filtered.items)
        }
    }

    suspend fun loadLatestPage(page: Int): PagedVodItems {
        val payload = requestApi { getLatest(page = page.coerceAtLeast(1)) }
            .toPagedVodItems()
        val playableItems = filterPlayablePreviewItems(payload.items)
        return payload.copy(items = playableItems).also { filtered ->
            rememberPreviewItems(filtered.items)
        }
    }

    private suspend fun loadLatestUpdatesFromLabelPage(): PagedVodItems {
        val document = fetchDocument("$baseUrl/label/new/")
        val items = parseLabelNewItems(document)
        if (items.isEmpty()) {
            throw IOException("最近更新解析失败")
        }
        return PagedVodItems(
            items = items,
            page = 1,
            pageCount = 1,
            totalItems = items.size,
            limit = items.size,
            hasNextPage = false
        )
    }

    suspend fun loadCategoryPage(typeId: String, page: Int, forceRefresh: Boolean = false): PagedVodItems {
        val safePage = page.coerceAtLeast(1)
        val cacheKey = "$typeId:$safePage"
        if (!forceRefresh) {
            categoryPageCache[cacheKey]
                ?.takeIf { isCacheValid(it.timestampMs, PAGE_CACHE_TTL_MS) }
                ?.value
                ?.let { return it }
            readPersistedPageCache(cacheKey)
                ?.takeIf { isCacheValid(it.timestampMs, DISK_PAGE_CACHE_TTL_MS) }
                ?.also { cached ->
                    categoryPageCache[cacheKey] = cached
                    return cached.value
                }
        }
        return if (forceRefresh) {
            loadFreshCategoryPage(typeId = typeId, page = safePage)
        } else {
            awaitSharedRequest("category_page:$cacheKey") {
                categoryPageCache[cacheKey]
                    ?.takeIf { isCacheValid(it.timestampMs, PAGE_CACHE_TTL_MS) }
                    ?.value
                    ?: readPersistedPageCache(cacheKey)
                        ?.takeIf { isCacheValid(it.timestampMs, DISK_PAGE_CACHE_TTL_MS) }
                        ?.also { cached -> categoryPageCache[cacheKey] = cached }
                        ?.value
                    ?: loadFreshCategoryPage(typeId = typeId, page = safePage)
            }
        }
    }

    private suspend fun loadFreshCategoryPage(typeId: String, page: Int): PagedVodItems {
        val cacheKey = "$typeId:$page"
        val payload = runCatching {
            typeId.takeIf { it.all(Char::isDigit) }
                ?.let { numericTypeId ->
                    requestApi {
                        getByType(typeId = numericTypeId, page = page)
                    }.toPagedVodItems()
                }
                ?: throw IOException("Non-numeric category id")
        }.getOrElse {
            loadCategoryPageFromWeb(typeId = typeId, page = page)
        }
        return payload.also { cachePagePayload(cacheKey, it) }
    }

    suspend fun loadCategoryCursorPage(
        typeId: String,
        cursor: String,
        filters: Map<String, String> = emptyMap()
    ): CursorPagedVodItems {
        val queryParameters = linkedMapOf(
            "type_id" to typeId.trim(),
            "limit" to CATEGORY_CURSOR_PAGE_LIMIT.toString(),
            "cursor" to cursor
        ).apply {
            filters.forEach { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isNotBlank() && normalizedValue.isNotBlank()) {
                    put(normalizedKey, normalizedValue)
                }
            }
        }

        return requestApi { getCursorList(queryParameters) }
            .toCursorPagedVodItems()
            .also { rememberPreviewItems(it.items) }
    }

    suspend fun search(keyword: String, forceRefresh: Boolean = false): List<VodItem> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return emptyList()
        val cacheKey = normalizedKeyword.lowercase()
        if (!forceRefresh) {
            searchCache[cacheKey]
                ?.takeIf { isCacheValid(it.timestampMs, SEARCH_CACHE_TTL_MS) }
                ?.value
                ?.let { return it }
        }

        return if (forceRefresh) {
            performSearch(normalizedKeyword, cacheKey)
        } else {
            awaitSharedRequest("search:$cacheKey") {
                searchCache[cacheKey]
                    ?.takeIf { isCacheValid(it.timestampMs, SEARCH_CACHE_TTL_MS) }
                    ?.value
                    ?: performSearch(normalizedKeyword, cacheKey)
            }
        }
    }

    private suspend fun performSearch(keyword: String, cacheKey: String): List<VodItem> {
        val encodedKeyword = Uri.encode(keyword)
        return runCatching {
            requestApi { search(keyword = keyword) }
                .data
                ?.rows
                .orEmpty()
                .distinctBy { it.vodId }
                .take(60)
        }.getOrElse {
            runCatching {
                val document = fetchDocument("$baseUrl/vodsearch/-------------/?wd=$encodedKeyword")
                parseSearchResults(document, keyword)
                    .distinctBy { it.vodId }
                    .take(60)
            }.getOrDefault(emptyList())
        }.also { results ->
            searchCache[cacheKey] = CachedValue(
            value = results,
            timestampMs = System.currentTimeMillis()
        )
        rememberPreviewItems(results)
        cleanupCachesIfNeeded()
    }
    }

    suspend fun searchCursor(keyword: String, cursor: String): CursorPagedVodItems {
        val query = keyword.trim()
        if (query.isBlank()) return CursorPagedVodItems()
        return requestApi {
            searchCursor(
                linkedMapOf(
                    "q" to query,
                    "limit" to SEARCH_CURSOR_PAGE_LIMIT.toString(),
                    "cursor" to cursor
                )
            )
        }.toCursorPagedVodItems().also { rememberPreviewItems(it.items) }
    }

    suspend fun enrichSearchResults(items: List<VodItem>, limit: Int = 8): List<VodItem> {
        if (items.isEmpty()) return items
        val enrichTargets = items.take(limit)
        val enrichedById = coroutineScope {
            enrichTargets.map { item ->
                async {
                    val detailItem = runCatching { loadDetail(item.vodId) }.getOrNull()
                    val description = detailItem?.description
                        ?.takeIf { it.isNotBlank() && it != "暂无简介" }
                        .orEmpty()
                    item.vodId to if (description.isNotBlank()) {
                        item.copy(
                            vodBlurb = description,
                            vodContent = description
                        )
                    } else {
                        item
                    }
                }
            }.awaitAll().toMap()
        }
        return items.map { item -> enrichedById[item.vodId] ?: item }
    }

    suspend fun loadHotSearchGroups(forceRefresh: Boolean = false): List<HotSearchGroup> {
        if (!forceRefresh) {
            hotSearchCache
                ?.takeIf { isCacheValid(it.timestampMs, HOT_SEARCH_CACHE_TTL_MS) }
                ?.value
                ?.let { return it }
        }

        return if (forceRefresh) {
            loadFreshHotSearchGroups()
        } else {
            awaitSharedRequest("hot_search") {
                hotSearchCache
                    ?.takeIf { isCacheValid(it.timestampMs, HOT_SEARCH_CACHE_TTL_MS) }
                    ?.value
                    ?: loadFreshHotSearchGroups()
            }
        }
    }

    private suspend fun loadFreshHotSearchGroups(): List<HotSearchGroup> {
        val groups = coroutineScope {
            listOf(
                async {
                    runCatching { loadTencentHotSearchGroup(limit = 10) }
                        .getOrNull()
                },
                async {
                    runCatching { loadIqiyiHotSearchGroup(limit = 10) }
                        .getOrNull()
                },
                async {
                    runCatching { loadYoukuHotSearchGroup(limit = 10) }
                        .getOrNull()
                },
                async {
                    runCatching { loadMgtvHotSearchGroup(limit = 10) }
                        .getOrNull()
                }
            ).awaitAll()
                .filterNotNull()
                .filter { it.items.isNotEmpty() }
        }

        hotSearchCache = CachedValue(
            value = groups,
            timestampMs = System.currentTimeMillis()
        )
        cleanupCachesIfNeeded()
        return groups
    }

    suspend fun loadLatestRelease(currentVersion: String): AppUpdateInfo {
        val request = Request.Builder()
            .url("https://api.github.com/repos/jinnian0703/JlenVideo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("妫€鏌ユ洿鏂板け璐ワ細HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JsonParser.parseString(body).asJsonObject
            val latestVersion = json.get("tag_name")?.asString.orEmpty()
                .removePrefix("v")
                .trim()
            val releasePageUrl = json.get("html_url")?.asString.orEmpty().trim()
            val notes = json.get("body")?.asString.orEmpty().trim()
            val downloadUrl = json.getAsJsonArray("assets")
                ?.firstOrNull()
                ?.asJsonObject
                ?.get("browser_download_url")
                ?.asString
                .orEmpty()

            return AppUpdateInfo(
                currentVersion = currentVersion.trim(),
                latestVersion = latestVersion,
                releasePageUrl = releasePageUrl,
                downloadUrl = downloadUrl,
                notes = notes,
                hasUpdate = compareVersionNames(latestVersion, currentVersion) > 0
            )
        }
    }

    fun currentSession(): AuthSession {
        val cookies = cookieJar.snapshot()
        val userId = cookies.firstCookieValue("user_id")
        val userName = cookies.firstCookieValue("user_name")
        val groupName = cookies.firstCookieValue("group_name")
        val portraitUrl = cookies.firstCookieValue("user_portrait")

        return AuthSession(
            isLoggedIn = userId.isNotBlank() && userName.isNotBlank(),
            userId = userId,
            userName = decodeSiteText(userName),
            groupName = decodeSiteText(groupName),
            portraitUrl = normalizePortraitUrl(portraitUrl)
        )
    }

    fun clearSession() {
        cookieJar.clear()
    }

    private fun requireLoggedInUserId(): String =
        currentSession().userId
            .trim()
            .takeIf(String::isNotBlank)
            ?: throw IOException("请先登录")

    private fun userActionBaseCandidates(): List<String> = listOf(baseUrl)

    private suspend fun executeUserRequest(
        path: String,
        refererPath: String,
        requestBody: okhttp3.RequestBody,
        extraHeaders: Map<String, String> = emptyMap(),
        acceptedStatusCodes: Set<Int> = emptySet()
    ): String {
        var lastError: Exception? = null

        for (candidateBase in userActionBaseCandidates()) {
            val requestBuilder = Request.Builder()
                .url(candidateBase + path)
                .header("Referer", candidateBase + refererPath)

            extraHeaders.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            val request = requestBuilder
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code !in acceptedStatusCodes) {
                        throw IOException("HTTP ${response.code}")
                    }
                    return response.body?.string().orEmpty()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error
            }
        }

        throw lastError ?: IOException("用户服务请求失败")
    }

    private suspend fun requestVideoApiJson(
        path: String,
        queryParameters: Map<String, String> = emptyMap(),
        formBody: FormBody? = null
    ): JsonObject =
        performVideoApiJsonRequest(
            base = baseUrl,
            path = path,
            queryParameters = queryParameters,
            formBody = formBody
        )

    private fun performVideoApiJsonRequest(
        base: String,
        path: String,
        queryParameters: Map<String, String>,
        formBody: FormBody?
    ): JsonObject {
        val url = Uri.parse("$base/$path")
            .buildUpon()
            .apply {
                queryParameters.forEach { (name, value) ->
                    appendQueryParameter(name, value)
                }
            }
            .build()
            .toString()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")

        val request = if (formBody == null) {
            requestBuilder.get().build()
        } else {
            requestBuilder
                .post(formBody)
                .build()
        }

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return parseVideoApiResponseBody(body)
        }
    }

    private fun extractVideoApiMessage(json: JsonObject, fallbackMessage: String): String =
        top.jlen.vod.data.extractVideoApiMessage(json, fallbackMessage)

    fun reportHeartbeat(
        route: String,
        userId: String = currentSession().userId,
        vodId: String = "",
        sid: Int? = null,
        nid: Int? = null
    ) = legacyReportHeartbeat(route, userId, vodId, sid, nid)

    suspend fun login(userName: String, password: String): AuthSession =
        legacyLogin(userName, password)

    suspend fun loadNotices(
        appVersion: String = AppRuntimeInfo.versionName,
        userId: String = "",
        forceRefresh: Boolean = false
    ): List<AppNotice> = legacyLoadNotices(appVersion, userId, forceRefresh)

    fun pickPendingNotice(notices: List<AppNotice>): AppNotice? = legacyPickPendingNotice(notices)

    fun unreadActiveNoticeIds(notices: List<AppNotice>): Set<String> =
        legacyUnreadActiveNoticeIds(notices)

    fun markNoticeDismissed(notice: AppNotice) = legacyMarkNoticeDismissed(notice)

    fun markNoticeDismissed(noticeId: String) = legacyMarkNoticeDismissed(noticeId)

    suspend fun loadRegisterPage(): RegisterPage = legacyLoadRegisterPage()

    suspend fun loadRegisterCaptcha(captchaUrl: String): ByteArray =
        legacyLoadRegisterCaptcha(captchaUrl)

    suspend fun loadFindPasswordPage(): FindPasswordPage = legacyLoadFindPasswordPage()

    suspend fun loadFindPasswordCaptcha(captchaUrl: String): ByteArray =
        legacyLoadFindPasswordCaptcha(captchaUrl)

    suspend fun findPassword(editor: FindPasswordEditor): String = legacyFindPassword(editor)

    suspend fun sendRegisterCode(channel: String, contact: String): String =
        legacySendRegisterCode(channel, contact)

    suspend fun register(editor: RegisterEditor): String = legacyRegister(editor)

    suspend fun logout() = legacyLogout()

    private fun reportHeartbeatOriginal(
        route: String,
        userId: String = currentSession().userId,
        vodId: String = "",
        sid: Int? = null,
        nid: Int? = null
    ) {
        val request = Request.Builder()
            .url(
                Uri.parse(APP_CENTER_API_URL)
                    .buildUpon()
                    .appendQueryParameter("action", "heartbeat")
                    .build()
                    .toString()
            )
            .post(
                FormBody.Builder()
                    .add("device_id", ensureHeartbeatDeviceIdOriginal())
                    .add("platform", "android")
                    .add("app_version", AppRuntimeInfo.versionName)
                    .add("route", route.trim().ifBlank { "home" })
                    .apply {
                        userId.trim()
                            .takeIf(String::isNotBlank)
                            ?.let { add("user_id", it) }
                        vodId.trim()
                            .takeIf(String::isNotBlank)
                            ?.let { add("vod_id", it) }
                        sid
                            ?.takeIf { it > 0 }
                            ?.let { add("sid", it.toString()) }
                        nid
                            ?.takeIf { it > 0 }
                            ?.let { add("nid", it.toString()) }
                    }
                    .build()
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("蹇冭烦涓婃姤澶辫触锛欻TTP ${response.code}")
            }
        }
    }

    private suspend fun loginOriginal(userName: String, password: String): AuthSession {
        val payload = FormBody.Builder()
            .add("user_name", userName.trim())
            .add("user_pwd", password)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/index.php/user/login")
            .header("Referer", "$baseUrl/index.php/user/login.html")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(payload)
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                throw IOException("登录失败：HTTP ${it.code}")
            }
            val body = it.body?.string().orEmpty()
            val authResponse = runCatching {
                gson.fromJson(body, AuthResponse::class.java)
            }.getOrNull()

            val session = currentSession()
            if (session.isLoggedIn) {
                return session
            }

            val failureMessage = authResponse?.msg
                ?.takeIf(String::isNotBlank)
                ?.let(::normalizeLoginFailureMessage)

            throw IOException(
                failureMessage
                    ?: "登录失败，请检查账号或密码"
            )
        }
    }

    private suspend fun loadNoticesOriginal(
        appVersion: String = AppRuntimeInfo.versionName,
        userId: String = "",
        forceRefresh: Boolean = false
    ): List<AppNotice> {
        if (!forceRefresh) {
            noticeCache
                ?.takeIf { isCacheValid(it.timestampMs, NOTICE_CACHE_TTL_MS) }
                ?.value
                ?.let { return it }
        }

        val requestKey = buildString {
            append("notices:")
            append(appVersion.trim())
            append(':')
            append(userId.trim())
        }

        return if (forceRefresh) {
            loadFreshNoticesOriginal(appVersion = appVersion, userId = userId)
        } else {
            awaitSharedRequest(requestKey) {
                noticeCache
                    ?.takeIf { isCacheValid(it.timestampMs, NOTICE_CACHE_TTL_MS) }
                    ?.value
                    ?: loadFreshNoticesOriginal(appVersion = appVersion, userId = userId)
            }
        }
    }

    private fun pickPendingNoticeOriginal(notices: List<AppNotice>): AppNotice? {
        val dismissedIds = noticePrefs.getStringSet(KEY_DISMISSED_NOTICE_IDS, emptySet()).orEmpty()
        return notices.firstOrNull { notice ->
            notice.isActive &&
                notice.id.isNotBlank() &&
                (notice.alwaysShowDialog || !dismissedIds.contains(notice.id))
        }
    }

    private fun unreadActiveNoticeIdsOriginal(notices: List<AppNotice>): Set<String> {
        val dismissedIds = noticePrefs.getStringSet(KEY_DISMISSED_NOTICE_IDS, emptySet()).orEmpty()
        return notices.asSequence()
            .filter { notice ->
                notice.isActive &&
                    notice.id.isNotBlank() &&
                    (notice.alwaysShowDialog || !dismissedIds.contains(notice.id))
            }
            .map { it.id }
            .toSet()
    }

    private fun markNoticeDismissedOriginal(notice: AppNotice) {
        if (notice.alwaysShowDialog) return
        markNoticeDismissed(notice.id)
    }

    private fun markNoticeDismissedOriginal(noticeId: String) {
        val normalized = noticeId.trim()
        if (normalized.isBlank()) return
        val currentIds = noticePrefs.getStringSet(KEY_DISMISSED_NOTICE_IDS, emptySet()).orEmpty()
        if (currentIds.contains(normalized)) return
        noticePrefs.edit()
            .putStringSet(KEY_DISMISSED_NOTICE_IDS, currentIds + normalized)
            .apply()
    }

    private fun loadFreshNoticesOriginal(appVersion: String, userId: String): List<AppNotice> {
        val url = Uri.parse(APP_CENTER_API_URL)
            .buildUpon()
            .appendQueryParameter("action", "notices")
            .appendQueryParameter("app_version", appVersion.trim().ifBlank { AppRuntimeInfo.versionName })
            .apply {
                userId.trim()
                    .takeIf(String::isNotBlank)
                    ?.let { appendQueryParameter("user_id", it) }
            }
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("公告加载失败：HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JsonParser.parseString(body).asJsonObject
            val items = json.extractNoticeItems()
            val notices = items.mapNotNull(::parseNoticeItem)
                .sortedWith(
                    compareByDescending<AppNotice> { it.isPinned }
                        .thenByDescending { it.isActive }
                        .thenByDescending { parseNoticeTimeToMillis(it.updatedAt) ?: parseNoticeTimeToMillis(it.createdAt) ?: 0L }
                        .thenByDescending { it.id }
                )

            noticeCache = CachedValue(
                value = notices,
                timestampMs = System.currentTimeMillis()
            )
            cleanupCachesIfNeeded()
            return notices
        }
    }

    private fun ensureHeartbeatDeviceIdOriginal(): String {
        heartbeatPrefs.getString(HEARTBEAT_DEVICE_ID_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        val generated = "android-${UUID.randomUUID()}"
        heartbeatPrefs.edit().putString(HEARTBEAT_DEVICE_ID_KEY, generated).apply()
        return generated
    }

    private fun normalizeLoginFailureMessageOriginal(rawMessage: String): String {
        val message = rawMessage.trim()
        return when {
            message.isBlank() -> ""
            message.contains("获取用户信息失败") -> "用户名不存在或密码错误"
            else -> message
        }
    }

    private suspend fun loadRegisterPageOriginal(): RegisterPage {
        val document = fetchDocument("$baseUrl/index.php/user/reg.html")
        val channel = document.selectFirst("input[name=ac]")?.attr("value")?.trim().orEmpty()
            .ifBlank { "email" }
        val requiresCode = document.selectFirst("input[name=code]") != null
        val requiresVerify = document.selectFirst("input[name=verify]") != null
        val captchaUrl = document.selectFirst("img.ewave-verify-img, img[src*=/verify/]")
            ?.attr("src")
            .orEmpty()

        return RegisterPage(
            channel = channel,
            contactLabel = if (channel == "phone") "手机号" else "邮箱",
            codeLabel = if (channel == "phone") "手机验证码" else "邮箱验证码",
            requiresCode = requiresCode,
            requiresVerify = requiresVerify,
            captchaUrl = resolveUrl(captchaUrl),
            captchaBytes = null
        )
    }

    private suspend fun loadRegisterCaptchaOriginal(captchaUrl: String): ByteArray {
        val request = Request.Builder()
            .url(appendTimestamp(captchaUrl))
            .header("Referer", "$baseUrl/index.php/user/reg.html")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("加载验证码失败：HTTP ${response.code}")
            }
            return response.body?.bytes() ?: throw IOException("加载验证码失败")
        }
    }

    private suspend fun loadFindPasswordPageOriginal(): FindPasswordPage {
        val document = fetchDocument("$baseUrl/index.php/user/findpass.html")
        val requiresVerify = document.selectFirst("input[name=verify]") != null
        val captchaUrl = document.selectFirst("img[src*=/verify/], img.mac_verify_img")
            ?.attr("src")
            .orEmpty()

        return FindPasswordPage(
            requiresVerify = requiresVerify,
            captchaUrl = resolveUrl(captchaUrl),
            captchaBytes = null
        )
    }

    private suspend fun loadFindPasswordCaptchaOriginal(captchaUrl: String): ByteArray {
        val request = Request.Builder()
            .url(appendTimestamp(captchaUrl))
            .header("Referer", "$baseUrl/index.php/user/findpass.html")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("鍔犺浇楠岃瘉鐮佸け璐ワ細HTTP ${response.code}")
            }
            return response.body?.bytes() ?: throw IOException("鍔犺浇楠岃瘉鐮佸け璐?")
        }
    }

    private suspend fun findPasswordOriginal(editor: FindPasswordEditor): String {
        val form = FormBody.Builder()
            .add("user_name", editor.userName.trim())
            .add("user_question", editor.question.trim())
            .add("user_answer", editor.answer.trim())
            .add("user_pwd", editor.password)
            .add("user_pwd2", editor.confirmPassword)
            .add("verify", editor.verify.trim())
            .build()
        return submitPublicAction(
            url = "$baseUrl/index.php/user/findpass",
            referer = "$baseUrl/index.php/user/findpass.html",
            formBody = form
        )
    }

    suspend fun uploadPortrait(uri: Uri): String {
        val resolver = appContext.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("鏃犳硶璇诲彇澶村儚鏂囦欢")
        if (bytes.isEmpty()) {
            throw IOException("澶村儚鏂囦欢涓虹┖")
        }

        val mimeType = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
            .orEmpty()
            .ifBlank { "portrait.jpg" }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/index.php/user/portrait")
            .header("Referer", "$baseUrl/index.php/user/portrait")
            .header("Origin", baseUrl)
            .header("User-Agent", PLAYER_DESKTOP_UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (isUserLoginResponse(response, responseBody)) {
                throw IOException("请先登录")
            }
            if (!response.isSuccessful) {
                throw IOException("涓婁紶澶村儚澶辫触锛欻TTP ${response.code}")
            }
            val authResponse = runCatching { gson.fromJson(responseBody, AuthResponse::class.java) }.getOrNull()
            if (authResponse != null && authResponse.msg.isNotBlank()) {
                if (authResponse.code == 1) {
                    return authResponse.msg
                }
                if (isLoginMessage(authResponse.msg) || authResponse.url.orEmpty().contains("/index.php/user/login")) {
                    throw IOException("请先登录")
                }
                throw IOException(authResponse.msg)
            }
            if (responseBody.contains("user_portrait") || responseBody.contains("鎴愬姛")) {
                return "澶村儚淇敼鎴愬姛"
            }
            return "澶村儚宸叉洿鏂?"
        }
    }

    suspend fun uploadPortraitOptimized(uri: Uri): String {
        val payload = preparePortraitUpload(uri)
        runCatching { uploadPortraitViaVideoApi(payload) }
            .getOrNull()
            ?.let { return it }

        runCatching { uploadPortraitViaVideoApiBase64(payload) }
            .getOrNull()
            ?.let { return it }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                payload.fileName,
                payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/index.php/user/portrait")
            .header("Referer", "$baseUrl/index.php/user/head.html")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/plain, */*")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("上传头像失败：HTTP ${response.code}")
            }

            val responseBody = response.body?.string().orEmpty()
            val authResponse = runCatching { gson.fromJson(responseBody, AuthResponse::class.java) }.getOrNull()
            if (authResponse != null && authResponse.msg.isNotBlank()) {
                if (authResponse.code == 1) {
                    return authResponse.msg
                }
                throw IOException(authResponse.msg)
            }
            if (responseBody.contains("未登录")) {
                throw IOException("请先登录")
            }
            if (responseBody.contains("user_portrait") || responseBody.contains("成功")) {
                return "头像更新成功"
            }
            throw IOException("头像上传失败，请换一张图片重试")
        }
    }

    private fun uploadPortraitViaVideoApi(payload: PortraitUploadPayload): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                payload.fileName,
                payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api.php/video/portrait")
            .header("Referer", "$baseUrl/")
            .header("Origin", baseUrl)
            .header("User-Agent", PLAYER_DESKTOP_UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return parsePortraitUploadResult(parseVideoApiResponseBody(responseBody))
        }
    }

    private fun uploadPortraitViaVideoApiBase64(payload: PortraitUploadPayload): String {
        val base64Payload = buildString {
            append("data:")
            append(payload.mimeType.ifBlank { "image/jpeg" })
            append(";base64,")
            append(Base64.encodeToString(payload.bytes, Base64.NO_WRAP))
        }
        val body = FormBody.Builder()
            .add("imgdata", base64Payload)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api.php/video/portrait")
            .header("Referer", "$baseUrl/")
            .header("Origin", baseUrl)
            .header("User-Agent", PLAYER_DESKTOP_UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return parsePortraitUploadResult(parseVideoApiResponseBody(responseBody))
        }
    }

    private fun parsePortraitUploadResult(json: JsonObject): String {
        val code = json.firstInt("code", "status")
        val message = json.firstString("msg", "message")
        if (code == 401 || message.equals("login required", ignoreCase = true) || isLoginMessage(message)) {
            throw IOException("请先登录")
        }
        if (code != null && code !in setOf(1, 200)) {
            throw IOException(message.ifBlank { "头像上传失败" })
        }
        val payload = unwrapApiPayload(json)
        val portrait = payload?.firstString("user_portrait_with_version", "user_portrait", "portrait", "avatar")
            .orEmpty()
        return if (portrait.isNotBlank() || message.isBlank() || message.equals("ok", ignoreCase = true)) {
            "头像更新成功"
        } else {
            message
        }
    }

    private suspend fun sendRegisterCodeOriginal(channel: String, contact: String): String {
        val form = FormBody.Builder()
            .add("ac", channel.trim())
            .add("to", contact.trim())
            .build()
        return submitPublicAction(
            url = normalizeUrl("/user/reg_msg/"),
            referer = "$baseUrl/index.php/user/reg.html",
            formBody = form
        )
    }

    private suspend fun registerOriginal(editor: RegisterEditor): String {
        val form = FormBody.Builder()
            .add("user_name", editor.userName.trim())
            .add("user_pwd", editor.password)
            .add("user_pwd2", editor.confirmPassword)
            .add("ac", editor.channel.trim())
            .add("to", editor.contact.trim())
            .add("code", editor.code.trim())
            .add("verify", editor.verify.trim())
            .build()
        return submitPublicAction(
            url = normalizeUrl("/user/reg/"),
            referer = "$baseUrl/index.php/user/reg.html",
            formBody = form
        )
    }

    private suspend fun logoutOriginal() {
        val request = Request.Builder()
            .url("$baseUrl/index.php/user/logout")
            .header("Referer", "$baseUrl/index.php/user")
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).execute().use {
            if (!it.isSuccessful && it.code != 302) {
                throw IOException("退出登录失败：HTTP ${it.code}")
            }
        }
        cookieJar.clear()
    }

    suspend fun loadUserProfile(): UserProfilePage {
        runCatching { loadUserProfileFromUserDetailApi(currentSession()) }
            .getOrNull()
            ?.let { return it }

        runCatching { loadUserProfileFromVideoMemberInfoApi() }
            .getOrNull()
            ?.let { return it }

        val profileDocument = fetchUserDocument("/index.php/user/index.html")
        val editorDocument = fetchUserDocument("/index.php/user/info.html")
        return UserProfilePage(
            fields = parseUserProfileFields(profileDocument),
            editor = parseUserProfileEditor(editorDocument),
            session = parseUserProfileSession(profileDocument)
        )
    }

    suspend fun loadFavoritePage(pageUrl: String? = null): UserCenterPage =
        parseUserCenterPageEnhanced(
            document = fetchUserDocument(pageUrl ?: "/index.php/user/favs.html")
        )

    suspend fun loadHistoryPage(pageUrl: String? = null): UserCenterPage =
        parseUserCenterPageEnhanced(
            document = fetchUserDocument(pageUrl ?: "/index.php/user/plays.html")
        )

    suspend fun loadMembershipPage(): MembershipPage {
        runCatching { loadMembershipPageFromVideoMemberInfoApi() }
            .getOrNull()
            ?.let { return it }

        currentSession().userId
            .takeIf(String::isNotBlank)
            ?.let { userId ->
                runCatching { loadMembershipInfoFromUserDetailApi(userId) }
                    .getOrNull()
                    ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() }
                    ?.let { return it }
            }

        runCatching { loadMembershipPageFromUserCenterJson() }
            .getOrNull()
            ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() || it.plans.isNotEmpty() }
            ?.let { return it }

        runCatching { loadMembershipPageFromAppCenter() }
            .getOrNull()
            ?.let { return it }

        return loadMembershipPageFromHtml()
    }

    private suspend fun loadMembershipPageFromHtml(): MembershipPage {
        val document = fetchUserDocument("/index.php/user/upgrade.html")
        val infoMap = extractLabeledValues(
            document = document,
            labels = listOf("当前分组", "剩余积分", "到期时间"),
            stopPhrases = listOf("点击需要的会员组和时长进行购买升级", "购买升级")
        )
        return MembershipPage(
            info = MembershipInfo(
                groupName = infoMap["当前分组"].orEmpty(),
                points = infoMap["剩余积分"].orEmpty(),
                expiry = infoMap["到期时间"].orEmpty()
            ),
            plans = parseMembershipPlansFromHtml(document)
        )
    }

    suspend fun saveUserProfile(editor: UserProfileEditor): String {
        runCatching { submitUserProfileToAppCenter(editor) }
            .getOrNull()
            ?.let { return it }

        val form = FormBody.Builder()
            .add("user_pwd", editor.currentPassword)
            .add("user_pwd1", editor.newPassword)
            .add("user_pwd2", editor.confirmPassword)
            .add("user_qq", editor.qq)
            .add("user_email", editor.email)
            .add("user_phone", editor.phone)
            .add("user_question", editor.question)
            .add("user_answer", editor.answer)
            .build()
        return submitUserAction(
            url = "$baseUrl/index.php/user/info",
            referer = "$baseUrl/index.php/user/info.html",
            formBody = form
        )
    }

    private suspend fun loadUserProfileFromAppCenter(): UserProfilePage {
        val snapshot = loadAppCenterUserSnapshot()
        return UserProfilePage(
            fields = snapshot.profileFields,
            editor = snapshot.profileEditor,
            session = snapshot.session
        )
    }

    private suspend fun loadMembershipPageFromAppCenter(): MembershipPage {
        val snapshot = loadAppCenterUserSnapshot()
        return MembershipPage(
            info = snapshot.membershipInfo,
            plans = snapshot.membershipPlans
        )
    }

    private suspend fun loadUserProfileFromVideoMemberInfoApi(): UserProfilePage {
        val snapshot = loadVideoMemberInfoSnapshot()
        return UserProfilePage(
            fields = snapshot.profileFields,
            editor = snapshot.profileEditor,
            session = snapshot.session
        )
    }

    private suspend fun loadMembershipPageFromVideoMemberInfoApi(): MembershipPage {
        val snapshot = loadVideoMemberInfoSnapshot()
        return MembershipPage(
            info = snapshot.membershipInfo,
            plans = snapshot.membershipPlans
        )
    }

    private suspend fun submitUserProfileToAppCenter(editor: UserProfileEditor): String {
        return submitAppCenterUserProfileMutation(
            FormBody.Builder()
            .add("user_pwd", editor.currentPassword)
            .add("user_pwd1", editor.newPassword)
            .add("user_pwd2", editor.confirmPassword)
            .add("user_qq", editor.qq)
            .add("user_email", editor.email)
            .add("user_phone", editor.phone)
            .add("user_question", editor.question)
            .add("user_answer", editor.answer)
            .add("current_password", editor.currentPassword)
            .add("new_password", editor.newPassword)
            .add("confirm_password", editor.confirmPassword)
            .add("qq", editor.qq)
            .add("email", editor.email)
            .add("phone", editor.phone)
            .add("question", editor.question)
            .add("answer", editor.answer)
            .build()
        )
    }

    private suspend fun submitAppCenterUserProfileMutation(formBody: FormBody): String {
        val json = requestAppCenterJson(action = "user_profile", formBody = formBody)
        val code = json.firstInt("code", "status")
        val message = json.firstString("msg", "message")
        if (code != null && code !in setOf(1, 200)) {
            throw IOException(message.ifBlank { "操作失败" })
        }
        if (message.contains("fail", ignoreCase = true) || message.contains("error", ignoreCase = true)) {
            throw IOException(message)
        }
        return message.ifBlank { "操作成功" }
    }

    private suspend fun loadAppCenterUserSnapshot(): AppCenterUserSnapshot {
        val json = requestAppCenterJson(action = "me")
        return parseAppCenterUserSnapshot(json)
            ?: throw IOException("内容服务返回的用户资料为空")
    }

    private suspend fun loadVideoMemberInfoSnapshot(): AppCenterUserSnapshot {
        val json = requestVideoApiJson(path = "api.php/video/memberInfo")
        val code = json.firstInt("code", "status")
        val message = json.firstString("msg", "message")
        if (code == 401 || message.equals("login required", ignoreCase = true)) {
            throw IOException("璇峰厛鐧诲綍")
        }
        if (code != null && code !in setOf(1, 200)) {
            throw IOException(message.ifBlank { "浼氬憳淇℃伅鍔犺浇澶辫触" })
        }
        return parseVideoMemberInfoSnapshot(json)
            ?: throw IOException("浼氬憳淇℃伅涓虹┖")
    }

    suspend fun sendEmailBindCode(email: String): String {
        runCatching {
            submitAppCenterUserProfileMutation(
                FormBody.Builder()
                    .add("ac", "email")
                    .add("to", email.trim())
                    .add("op", "send_bind_code")
                    .add("action", "send_bind_code")
                    .build()
            )
        }.getOrNull()?.let { return it }

        val form = FormBody.Builder()
            .add("ac", "email")
            .add("to", email.trim())
            .build()
        return submitUserAction(
            url = "$baseUrl/index.php/user/bindmsg",
            referer = "$baseUrl/index.php/user/bind?ac=email",
            formBody = form
        )
    }

    suspend fun bindEmail(email: String, code: String): String {
        runCatching {
            submitAppCenterUserProfileMutation(
                FormBody.Builder()
                    .add("ac", "email")
                    .add("to", email.trim())
                    .add("code", code.trim())
                    .add("op", "bind_email")
                    .add("action", "bind_email")
                    .build()
            )
        }.getOrNull()?.let { return it }

        val form = FormBody.Builder()
            .add("ac", "email")
            .add("to", email.trim())
            .add("code", code.trim())
            .build()
        return submitUserAction(
            url = "$baseUrl/index.php/user/bind",
            referer = "$baseUrl/index.php/user/bind?ac=email",
            formBody = form
        )
    }

    suspend fun unbindEmail(): String {
        runCatching {
            submitAppCenterUserProfileMutation(
                FormBody.Builder()
                    .add("ac", "email")
                    .add("op", "unbind_email")
                    .add("action", "unbind_email")
                    .build()
            )
        }.getOrNull()?.let { return it }

        val form = FormBody.Builder()
            .add("ac", "email")
            .build()
        return submitUserAction(
            url = "$baseUrl/index.php/user/unbind",
            referer = "$baseUrl/index.php/user/info.html",
            formBody = form
        )
    }

    suspend fun addFavorite(item: VodItem): String =
        submitUserUlog(
            siteVodId = item.siteLogId,
            type = 2
        )

    suspend fun addPlayRecord(item: VodItem, episodePageUrl: String): String {
        val route = parsePlayRoute(episodePageUrl)
        return submitUserUlog(
            siteVodId = item.siteLogId,
            type = 4,
            sid = route?.sid.orEmpty(),
            nid = route?.nid.orEmpty()
        )
    }

    private suspend fun submitUserUlog(
        siteVodId: String,
        type: Int,
        sid: String = "",
        nid: String = ""
    ): String {
        if (siteVodId.isBlank()) {
            throw IOException("未找到站内影片编号")
        }

        val url = buildString {
            append("$baseUrl/index.php/user/ajax_ulog/?ac=set&mid=1")
            append("&id=").append(Uri.encode(siteVodId))
            append("&type=").append(type)
            if (sid.isNotBlank()) append("&sid=").append(Uri.encode(sid))
            if (nid.isNotBlank()) append("&nid=").append(Uri.encode(nid))
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("请求失败：HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val authResponse = runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()
            if (authResponse != null && (authResponse.msg.isNotBlank() || authResponse.code != 0)) {
                if (authResponse.code == 1) {
                    return authResponse.msg.ifBlank { "操作成功" }
                }
                throw IOException(authResponse.msg.ifBlank { "操作失败" })
            }
            if (body.contains("未登录")) {
                throw IOException("请先登录")
            }
            return "操作成功"
        }
    }
    suspend fun deleteUserRecord(recordIds: List<String>, type: Int, clearAll: Boolean): String {
        val form = FormBody.Builder()
            .add("ids", recordIds.joinToString(","))
            .add("type", type.toString())
            .add("all", if (clearAll) "1" else "0")
            .build()
        return submitUserAction(
            url = "$baseUrl/index.php/user/ulog_del",
            referer = "$baseUrl/index.php/user/${if (type == 2) "favs" else "plays"}.html",
            formBody = form
        )
    }

    suspend fun upgradeMembership(plan: MembershipPlan): String {
        runCatching {
            submitAppCenterUserProfileMutation(
                FormBody.Builder()
                    .add("group_id", plan.groupId)
                    .add("long", plan.duration)
                    .add("op", "upgrade_membership")
                    .add("action", "upgrade_membership")
                    .build()
            )
        }.getOrNull()?.let { return it }

        val form = FormBody.Builder()
            .add("group_id", plan.groupId)
            .add("long", plan.duration)
            .build()
        return submitUserAction(
            url = "$baseUrl/index.php/user/upgrade",
            referer = "$baseUrl/index.php/user/upgrade.html",
            formBody = form
        )
    }

    suspend fun loginForApp(userName: String, password: String): AuthSession {
        val body = executeUserRequest(
            path = "/index.php/user/login",
            refererPath = "/index.php/user/login.html",
            requestBody = FormBody.Builder()
                .add("user_name", userName.trim())
                .add("user_pwd", password)
                .build(),
            extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        val authResponse = runCatching {
            gson.fromJson(body, AuthResponse::class.java)
        }.getOrNull()

        val session = currentSession()
        if (session.isLoggedIn) {
            return session
        }

        runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrNull()
            ?.let { json ->
                if (json.firstInt("code", "status") in setOf(1, 200)) {
                    val data = json.firstObject("data", "info", "user")
                    val userObject = data?.firstObject("user", "profile", "account", "member") ?: data
                    val userId = userObject?.firstString("user_id", "uid", "id", "userId").orEmpty()
                    if (userId.isNotBlank()) {
                        return AuthSession(
                            isLoggedIn = true,
                            userId = userId,
                            userName = decodeSiteText(
                                userObject?.firstString("user_name", "username", "nickname", "name").orEmpty()
                                    .ifBlank { userName }
                            ),
                            groupName = decodeSiteText(
                                userObject?.firstString("group_name", "group", "member_name").orEmpty()
                            ),
                            portraitUrl = normalizePortraitUrl(
                                userObject?.firstString("user_portrait", "portrait", "avatar").orEmpty()
                            )
                        )
                    }
                }
            }

        val failureMessage = authResponse?.msg
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizeLoginFailureMessage)

        throw IOException(failureMessage ?: "登录失败，请检查账号或密码")
    }

    suspend fun loadRegisterPageForApp(): RegisterPage =
        RegisterPage(
            channel = "email",
            contactLabel = "邮箱",
            codeLabel = "邮箱验证码",
            requiresCode = true,
            requiresVerify = false,
            captchaUrl = "",
            captchaBytes = null
        )

    suspend fun loadFindPasswordPageForApp(): FindPasswordPage =
        FindPasswordPage(
            requiresVerify = false,
            captchaUrl = "",
            captchaBytes = null
        )

    suspend fun sendRegisterCodeForApp(channel: String, contact: String): String {
        val body = executeUserRequest(
            path = "/index.php/user/reg_msg",
            refererPath = "/index.php/user/reg.html",
            requestBody = FormBody.Builder()
                .add("ac", channel.trim())
                .add("to", contact.trim())
                .build(),
            extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        val authResponse = runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()
        return authResponse?.msg?.takeIf(String::isNotBlank) ?: "验证码发送成功"
    }

    suspend fun registerForApp(editor: RegisterEditor): String {
        val body = executeUserRequest(
            path = "/index.php/user/reg",
            refererPath = "/index.php/user/reg.html",
            requestBody = FormBody.Builder()
                .add("user_name", editor.userName.trim())
                .add("user_pwd", editor.password)
                .add("user_pwd2", editor.confirmPassword)
                .add("ac", editor.channel.trim())
                .add("to", editor.contact.trim())
                .add("code", editor.code.trim())
                .add("verify", editor.verify.trim())
                .build(),
            extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        val authResponse = runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()
        if (authResponse != null && authResponse.code != 1) {
            throw IOException(authResponse.msg.ifBlank { "注册失败" })
        }
        return authResponse?.msg?.takeIf(String::isNotBlank) ?: "注册成功"
    }

    suspend fun findPasswordForApp(editor: FindPasswordEditor): String {
        val body = executeUserRequest(
            path = "/index.php/user/findpass",
            refererPath = "/index.php/user/findpass.html",
            requestBody = FormBody.Builder()
                .add("user_name", editor.userName.trim())
                .add("user_question", editor.question.trim())
                .add("user_answer", editor.answer.trim())
                .add("user_pwd", editor.password)
                .add("user_pwd2", editor.confirmPassword)
                .add("verify", editor.verify.trim())
                .build(),
            extraHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        val authResponse = runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()
        if (authResponse != null && authResponse.code != 1) {
            throw IOException(authResponse.msg.ifBlank { "找回密码失败" })
        }
        return authResponse?.msg?.takeIf(String::isNotBlank) ?: "密码已重置"
    }

    suspend fun logoutForApp() {
        runCatching {
            executeUserRequest(
                path = "/index.php/user/logout",
                refererPath = "/index.php/user",
                requestBody = FormBody.Builder().build(),
                acceptedStatusCodes = setOf(302)
            )
        }
        clearSession()
    }

    suspend fun loadUserProfileForApp(): UserProfilePage {
        runCatching { loadUserProfileFromUserDetailApi(currentSession()) }
            .getOrNull()
            ?.takeIf { it.session.isLoggedIn }
            ?.let { return enrichUserProfilePageSession(it) }

        runCatching { loadUserProfileFromVideoMemberInfoApi() }
            .getOrNull()
            ?.takeIf { it.session.isLoggedIn }
            ?.let { return enrichUserProfilePageSession(it) }

        runCatching { loadUserProfileFromAppCenter() }
            .getOrNull()
            ?.takeIf { it.session.isLoggedIn }
            ?.let { return enrichUserProfilePageSession(it) }

        runCatching { loadUserProfile() }
            .getOrNull()
            ?.takeIf { it.session.isLoggedIn || currentSession().isLoggedIn }
            ?.let { return enrichUserProfilePageSession(it) }

        val session = currentSession()
        if (!session.isLoggedIn) {
            throw IOException("请先登录")
        }

        return UserProfilePage(
            fields = buildProfileFields(
                userId = session.userId,
                userName = session.userName,
                groupName = session.groupName
            ),
            editor = UserProfileEditor(),
            session = session
        )
    }

    private suspend fun enrichUserProfilePageSession(page: UserProfilePage): UserProfilePage {
        val htmlSession = runCatching {
            parseUserProfileSession(fetchUserDocument("/index.php/user/index.html"))
        }.getOrNull() ?: return page

        return page.copy(
            session = page.session.copy(
                isLoggedIn = page.session.isLoggedIn || htmlSession.isLoggedIn,
                userId = page.session.userId.ifBlank { htmlSession.userId },
                userName = htmlSession.userName.ifBlank { page.session.userName },
                groupName = htmlSession.groupName.ifBlank { page.session.groupName },
                portraitUrl = htmlSession.portraitUrl.ifBlank { page.session.portraitUrl }
            )
        )
    }

    suspend fun loadFavoritePageForApp(pageUrl: String? = null): UserCenterPage {
        val userId = requireLoggedInUserId()
        val page = parseUserCenterApiPage(pageUrl, prefix = "favorites")
        val json = requestVideoApiJson(
            path = "api.php/video/favorites",
            queryParameters = mapOf(
                "user_id" to userId,
                "page" to page.toString(),
                "limit" to USER_CENTER_PAGE_LIMIT.toString()
            )
        )
        return parseFavoritePageFromApi(json, page = page)
    }

    suspend fun loadHistoryPageForApp(pageUrl: String? = null): UserCenterPage {
        val userId = requireLoggedInUserId()
        val page = parseUserCenterApiPage(pageUrl, prefix = "history")
        val json = requestVideoApiJson(
            path = "api.php/video/history",
            queryParameters = mapOf(
                "user_id" to userId,
                "page" to page.toString(),
                "limit" to USER_CENTER_PAGE_LIMIT.toString()
            )
        )
        return parseHistoryPageFromApi(json, page = page)
    }

    suspend fun loadMembershipPageForApp(): MembershipPage {
        runCatching { loadMembershipPageFromVideoMemberInfoApi() }
            .getOrNull()
            ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() || it.plans.isNotEmpty() }
            ?.let { return it }

        currentSession().userId
            .takeIf(String::isNotBlank)
            ?.let { userId ->
                runCatching { loadMembershipInfoFromUserDetailApi(userId) }
                    .getOrNull()
                    ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() }
                    ?.let { return it }
            }

        runCatching { loadMembershipPageFromUserCenterJson() }
            .getOrNull()
            ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() || it.plans.isNotEmpty() }
            ?.let { return it }

        runCatching { loadMembershipPageFromAppCenter() }
            .getOrNull()
            ?.let { return it }

        val session = currentSession()
        if (!session.isLoggedIn) {
            throw IOException("请先登录")
        }

        return MembershipPage(
            info = MembershipInfo(groupName = session.groupName),
            plans = emptyList()
        )
    }

    suspend fun loadMembershipDataForApp(): MembershipPage {
        val session = currentSession()
        if (!session.isLoggedIn) {
            throw IOException("璇峰厛鐧诲綍")
        }

        return mergeMembershipPages(
            base = MembershipPage(info = MembershipInfo(groupName = session.groupName)),
            fallback = runCatching { loadMembershipPageFromVideoMemberInfoApi() }.getOrNull()
        ).let { merged ->
            mergeMembershipPages(
                base = merged,
                fallback = runCatching { loadMembershipInfoFromUserDetailApi(session.userId) }.getOrNull()
            )
        }.let { merged ->
            mergeMembershipPages(
                base = merged,
                fallback = runCatching { loadMembershipPageFromUserCenterJson() }.getOrNull()
            )
        }.let { merged ->
            mergeMembershipPages(
                base = merged,
                fallback = runCatching { loadMembershipPageFromAppCenter() }.getOrNull()
            )
        }.let { merged ->
            mergeMembershipPages(
                base = merged,
                fallback = runCatching { loadMembershipInfoFromProfileHtml() }.getOrNull()
            )
        }.let { merged ->
            mergeMembershipPages(
                base = merged,
                fallback = runCatching { loadMembershipPageFromHtml() }.getOrNull()
            )
        }
    }

    suspend fun addFavoriteForApp(item: VodItem): String {
        val userId = requireLoggedInUserId()
        val vodId = resolveUserActionVodId(item)
        val json = requestVideoApiJson(
            path = "api.php/video/favorite",
            formBody = FormBody.Builder()
                .add("user_id", userId)
                .add("vod_id", vodId)
                .add("action", "add")
                .build()
        )
        return extractVideoApiMessage(json, "已加入收藏")
    }

    suspend fun addPlayRecordForApp(item: VodItem, episodePageUrl: String): String {
        val userId = requireLoggedInUserId()
        val vodId = resolveUserActionVodId(item)
        val route = parsePlayRoute(episodePageUrl)
        val formBuilder = FormBody.Builder()
            .add("user_id", userId)
            .add("vod_id", vodId)
            .add("action", "add")
        route?.sid?.takeIf(String::isNotBlank)?.let { formBuilder.add("sid", it) }
        route?.nid?.takeIf(String::isNotBlank)?.let { formBuilder.add("nid", it) }
        val json = requestVideoApiJson(
            path = "api.php/video/historyRecord",
            formBody = formBuilder.build()
        )
        return extractVideoApiMessage(json, "播放记录已同步")
    }

    suspend fun deleteUserRecordForApp(recordIds: List<String>, type: Int, clearAll: Boolean): String {
        val userId = requireLoggedInUserId()
        return when (type) {
            2 -> {
                val targetVodIds = if (clearAll) {
                    collectAllUserCenterItems { token -> loadFavoritePageForApp(token) }
                        .map { it.recordId }
                } else {
                    recordIds
                }
                targetVodIds
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .forEach { vodId ->
                        val json = requestVideoApiJson(
                            path = "api.php/video/favorite",
                            formBody = FormBody.Builder()
                                .add("user_id", userId)
                                .add("vod_id", vodId)
                                .add("action", "delete")
                                .build()
                        )
                        extractVideoApiMessage(json, "操作成功")
                    }
                if (targetVodIds.isEmpty()) "没有可删除的收藏" else "操作成功"
            }
            4 -> {
                val targetHistoryIds = if (clearAll) {
                    collectAllUserCenterItems { token -> loadHistoryPageForApp(token) }
                        .map { it.recordId }
                } else {
                    recordIds
                }
                targetHistoryIds
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .forEach { ulogId ->
                        val json = requestVideoApiJson(
                            path = "api.php/video/historyRecord",
                            formBody = FormBody.Builder()
                                .add("user_id", userId)
                                .add("ulog_id", ulogId)
                                .add("action", "delete")
                                .build()
                        )
                        extractVideoApiMessage(json, "操作成功")
                    }
                if (targetHistoryIds.isEmpty()) "没有可删除的记录" else "操作成功"
            }
            else -> deleteUserRecord(recordIds, type, clearAll)
        }
    }

    private fun parseFavoritePageFromApi(json: JsonObject, page: Int): UserCenterPage {
        extractVideoApiMessage(json, "加载收藏失败")
        val data = unwrapApiPayload(json) ?: return UserCenterPage()
        val rows = data.firstArray("rows", "list", "items")
        val totalPages = data.firstInt("total_pages", "page_count", "pageCount")
            ?.coerceAtLeast(page)
            ?: page
        val items = rows.mapNotNull { element ->
            val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val vod = parseUserCenterVod(row) ?: return@mapNotNull null
            rememberPreviewItems(listOf(vod))
            UserCenterItem(
                recordId = vod.vodId,
                vodId = vod.vodId,
                title = vod.displayTitle,
                subtitle = vod.subtitle,
                actionLabel = "查看详情",
                actionUrl = buildVodDetailUrl(vod)
            )
        }
        return UserCenterPage(
            items = items.distinctBy { "${it.recordId}:${it.vodId}" },
            nextPageUrl = buildUserCenterNextPage("favorites", page, totalPages)
        )
    }

    private fun parseHistoryPageFromApi(json: JsonObject, page: Int): UserCenterPage {
        extractVideoApiMessage(json, "加载播放记录失败")
        val data = unwrapApiPayload(json) ?: return UserCenterPage()
        val rows = data.firstArray("rows", "list", "items")
        val totalPages = data.firstInt("total_pages", "page_count", "pageCount")
            ?.coerceAtLeast(page)
            ?: page
        val items = rows.mapNotNull { element ->
            val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val vod = parseUserCenterVod(row) ?: return@mapNotNull null
            val currentPlay = row.firstObjectOrFirstArrayObject("current_play")
            val sourceIndex = (
                currentPlay?.firstInt("sid")
                    ?: row.firstInt("ulog_sid", "sid")
                    ?: 0
                ) - 1
            val episodeIndex = (
                currentPlay?.firstInt("nid")
                    ?: row.firstInt("ulog_nid", "nid")
                    ?: 0
                ) - 1
            val sources = parseSources(vod)
            val resolvedSource = sources.getOrNull(sourceIndex)
            val sourceName = sanitizeUserFacingToken(
                decodeSiteText(
                currentPlay?.firstString("from", "source_name").orEmpty()
                    .ifBlank { resolvedSource?.name.orEmpty() }
                )
            )
            val episodeName = sanitizeUserFacingToken(
                decodeSiteText(
                currentPlay?.firstString("name", "episode_name").orEmpty().ifBlank {
                    resolvedSource?.episodes?.getOrNull(episodeIndex)?.name.orEmpty()
                }
                )
            )
            rememberPreviewItems(listOf(vod))
            UserCenterItem(
                recordId = row.firstString("ulog_id", "id"),
                vodId = vod.vodId,
                title = vod.displayTitle,
                subtitle = sanitizeUserFacingComposite(
                    listOf(
                    sourceName.takeIf(String::isNotBlank),
                    episodeName.takeIf(String::isNotBlank),
                    vod.subtitle.takeIf(String::isNotBlank)
                    ).joinToString(" | ")
                ),
                actionLabel = if (currentPlay?.firstString("url", "source_url").isNullOrBlank()) "查看详情" else "继续观看",
                actionUrl = buildVodDetailUrl(vod),
                playUrl = currentPlay?.firstString("url", "source_url").orEmpty(),
                sourceName = sourceName,
                sourceIndex = sourceIndex,
                episodeIndex = episodeIndex
            )
        }
        return UserCenterPage(
            items = items.distinctBy { "${it.recordId}:${it.playUrl}:${it.vodId}" },
            nextPageUrl = buildUserCenterNextPage("history", page, totalPages)
        )
    }

    private fun parseUserCenterVod(row: JsonObject): VodItem? =
        top.jlen.vod.data.parseUserCenterVod(row, gson, baseUrl)

    private fun buildVodDetailUrl(item: VodItem): String =
        top.jlen.vod.data.buildVodDetailUrl(item, baseUrl)

    private fun resolveUserActionVodId(item: VodItem): String =
        item.vodId
            .takeIf { it.all(Char::isDigit) }
            .orEmpty()
            .ifBlank { item.siteLogId }
            .ifBlank { throw IOException("未找到影片编号") }

    suspend fun loadDetail(vodId: String, forceRefresh: Boolean = false): VodItem? {
        val normalizedId = vodId.trim()
        if (normalizedId.isBlank()) return null
        if (!forceRefresh) {
            detailCache[normalizedId]
                ?.takeIf { isCacheValid(it.timestampMs, DETAIL_CACHE_TTL_MS) }
                ?.value
                ?.let { return it }
        }

        return if (forceRefresh) {
            loadFreshDetail(normalizedId)
        } else {
            awaitSharedRequest("detail:$normalizedId") {
                detailCache[normalizedId]
                    ?.takeIf { isCacheValid(it.timestampMs, DETAIL_CACHE_TTL_MS) }
                    ?.value
                    ?: loadFreshDetail(normalizedId)
            }
        }
    }

    private suspend fun loadFreshDetail(normalizedId: String): VodItem? {
        if (normalizedId.all(Char::isDigit)) {
            val previewItem = findPreviewItem(normalizedId)
            val apiItem = loadDetailFromApi(normalizedId)
            val resolvedItem = when {
                apiItem == null -> previewItem?.let { preview ->
                    resolveDetailMismatch(preview, excludedVodId = normalizedId)
                        ?.let { mergePreviewIntoDetail(preview, it) }
                        ?: preview
                }
                previewItem != null && !detailMatchesPreview(apiItem, previewItem) ->
                    resolveDetailMismatch(previewItem, excludedVodId = normalizedId)
                        ?.let { mergePreviewIntoDetail(previewItem, it) }
                        ?: previewItem
                previewItem != null -> mergePreviewIntoDetail(previewItem, apiItem)
                else -> apiItem
            }
            detailCache[normalizedId] = CachedValue(
                value = resolvedItem,
                timestampMs = System.currentTimeMillis()
            )
            resolvedItem?.let { rememberPreviewItems(listOf(it)) }
            cleanupCachesIfNeeded()
            return resolvedItem
        }

        return parseDetail(fetchDocument("$baseUrl/voddetail/$normalizedId/")).also { item ->
            detailCache[normalizedId] = CachedValue(
                value = item,
                timestampMs = System.currentTimeMillis()
            )
            cleanupCachesIfNeeded()
        }
    }

    private suspend fun loadDetailFromApi(vodId: String): VodItem? =
        requestApi { getDetail(vodId = vodId) }
            .data
            ?.takeIf { it.isJsonObject }
            ?.let { json -> gson.fromJson(json, VodItem::class.java) }

    private suspend fun resolveDetailMismatch(
        previewItem: VodItem,
        excludedVodId: String
    ): VodItem? {
        val targetTitle = canonicalTitle(previewItem.vodName)
        if (targetTitle.isBlank()) return null

        val candidates = runCatching {
            requestApi { search(keyword = previewItem.vodName, page = 1, limit = 10) }
                .data
                ?.rows
                .orEmpty()
        }.getOrDefault(emptyList())
            .filter { candidate ->
                candidate.vodId != excludedVodId &&
                    canonicalTitle(candidate.vodName) == targetTitle
            }
            .sortedByDescending { candidate ->
                searchCandidateScore(candidate, previewItem)
            }

        for (candidate in candidates.take(5)) {
            val detail = runCatching { loadDetailFromApi(candidate.vodId) }.getOrNull() ?: continue
            if (detailMatchesPreview(detail, previewItem)) {
                return detail
            }
        }
        return null
    }

    private suspend fun filterPlayablePreviewItems(items: List<VodItem>): List<VodItem> {
        if (items.isEmpty()) return emptyList()
        return coroutineScope {
            items.map { previewItem ->
                async {
                    val resolved = runCatching {
                        resolvePlayableDetailForPreview(previewItem)
                    }.getOrNull()
                    if (resolved != null && parseSources(resolved).isNotEmpty()) {
                        detailCache[previewItem.vodId] = CachedValue(
                            value = resolved,
                            timestampMs = System.currentTimeMillis()
                        )
                        rememberPreviewItems(listOf(resolved))
                        previewItem
                    } else {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun resolvePlayableDetailForPreview(previewItem: VodItem): VodItem? {
        detailCache[previewItem.vodId]
            ?.takeIf { isCacheValid(it.timestampMs, DETAIL_CACHE_TTL_MS) }
            ?.value
            ?.takeIf { parseSources(it).isNotEmpty() }
            ?.let { return it }

        val apiItem = loadDetailFromApi(previewItem.vodId)
        return when {
            apiItem == null -> resolveDetailMismatch(previewItem, excludedVodId = previewItem.vodId)
                ?.let { mergePreviewIntoDetail(previewItem, it) }
            !detailMatchesPreview(apiItem, previewItem) ->
                resolveDetailMismatch(previewItem, excludedVodId = previewItem.vodId)
                    ?.let { mergePreviewIntoDetail(previewItem, it) }
            else -> mergePreviewIntoDetail(previewItem, apiItem)
        }
    }

    suspend fun resolvePlayUrl(playPageUrl: String): ResolvedPlayUrl {
        val normalizedPageUrl = resolveUrl(playPageUrl)
        if (isDirectMediaUrl(normalizedPageUrl)) {
            return ResolvedPlayUrl(url = normalizedPageUrl, useWebPlayer = false)
        }

        val html = fetchHtml(normalizedPageUrl, referer = "$baseUrl/")
        val playerConfig = extractPlayerConfig(html)
        val rawUrl = playerConfig?.first.orEmpty()
        val encrypt = playerConfig?.second ?: 0
        val decodedUrl = decodePlayerUrl(rawUrl, encrypt)
        val firstResolvedUrl = normalizeAgainst(decodedUrl.ifBlank { normalizedPageUrl }, normalizedPageUrl)
        val resolvedUrl = resolveNestedMediaUrl(
            candidateUrl = firstResolvedUrl,
            referer = normalizedPageUrl,
            depth = 0
        )
        val useWebPlayer = !isDirectMediaUrl(resolvedUrl)

        return ResolvedPlayUrl(
            url = if (useWebPlayer) normalizedPageUrl else resolvedUrl,
            useWebPlayer = useWebPlayer
        )
    }

    fun parseSources(item: VodItem): List<PlaySource> {
        val sourceNames = item.vodPlayFrom
            .orEmpty()
            .split("$$$")
            .map(::sanitizeUserFacingToken)
            .filter { it.isNotBlank() }

        val groups = item.vodPlayUrl
            .orEmpty()
            .split("$$$")
            .map { rawGroup ->
                rawGroup.split("#")
                    .mapNotNull { rawEpisode ->
                        val pair = rawEpisode.split("$", limit = 2)
                        val url = pair.getOrNull(1).orEmpty().trim()
                        if (url.isBlank()) {
                            null
                        } else {
                            Episode(
                                name = pair.firstOrNull().orEmpty().ifBlank { "播放" },
                                url = normalizeUrl(url)
                            )
                        }
                    }
            }
            .filter { it.isNotEmpty() }
            .map { episodes ->
                episodes.map { episode ->
                    episode.copy(name = sanitizeUserFacingToken(episode.name).ifBlank { "播放" })
                }
            }

        return groups.mapIndexed { index, episodes ->
            PlaySource(
                name = sourceNames.getOrNull(index).orEmpty().ifBlank { "线路 ${index + 1}" },
                episodes = episodes
            )
        }
    }

    private suspend fun loadLevelItemsFromHomePage(limit: Int): List<VodItem> {
        val document = fetchDocument("$baseUrl/")
        val bannerItems = parseBannerItems(document)
            .distinctBy { it.vodId }
        if (bannerItems.isNotEmpty()) {
            return bannerItems.take(limit)
        }

        val featuredSection = document.select(".layout-box .vod-list")
            .firstOrNull { section ->
                section.selectFirst("h2")?.text()
                    ?.replace(Regex("\\s+"), "")
                    ?.contains("推荐") == true
            }

        return featuredSection
            ?.let(::parseVodCards)
            .orEmpty()
            .distinctBy { it.vodId }
            .take(limit)
    }

    private suspend fun loadFeaturedFromHomePage(): List<VodItem> {
        val document = fetchDocument("$baseUrl/")
        val hotItems = document.select(".slide-list.vod-list, .slide-list")
            .asSequence()
            .map { section -> parseVodCards(section) }
            .firstOrNull { items -> items.isNotEmpty() }
            .orEmpty()

        return (parseBannerItems(document) + hotItems)
            .distinctBy { it.vodId }
            .take(12)
    }

    private fun parseBannerItems(document: Document): List<VodItem> =
        document.select(".banner-box a.swiper-slide[href*=/voddetail/]")
            .mapNotNull { element ->
                createVodItem(
                    detailHref = element.attr("href"),
                    title = element.selectFirst(".swiper-pagination-html h3")?.text()
                        .orEmpty()
                        .ifBlank { element.attr("title") },
                    imageUrl = element.attr("data-background"),
                    remarks = element.selectFirst(".swiper-pagination-html p")?.text().orEmpty()
                )
            }
            .distinctBy { it.vodId }

    private fun parseLevelOneItemsFromHomePage(document: Document, limit: Int): List<VodItem> {
        val bannerItems = parseBannerItems(document)
            .distinctBy { it.vodId }
        if (bannerItems.isNotEmpty()) {
            return bannerItems.take(limit)
        }

        val featuredSection = document.select(".layout-box .vod-list")
            .firstOrNull { section ->
                section.selectFirst("h2")?.text()
                    ?.replace(Regex("\\s+"), "")
                    ?.contains("鎺ㄨ崘") == true
            }

        return featuredSection
            ?.let(::parseVodCards)
            .orEmpty()
            .distinctBy { it.vodId }
            .take(limit)
    }

    private fun loadTencentHotSearchGroup(limit: Int): HotSearchGroup {
        val sourceUrl = "https://v.qq.com/biu/ranks/?t=hotsearch"
        val document = fetchDocument(sourceUrl)
        val items = document.select(".mod_rank_search_list .hotlist li a")
            .mapIndexedNotNull { index, anchor ->
                val keyword = sanitizeHotSearchKeyword(
                    platform = "腾讯视频",
                    raw = decodeSiteText(
                    anchor.selectFirst(".name")?.text().orEmpty()
                        .ifBlank { anchor.attr("title") }
                    )
                )
                if (keyword.isBlank()) return@mapIndexedNotNull null
                HotSearchItem(
                    rank = anchor.selectFirst(".num")?.text()?.toIntOrNull() ?: (index + 1),
                    keyword = keyword,
                    platform = "腾讯视频",
                    sourceUrl = normalizeAgainst(anchor.attr("href"), sourceUrl)
                )
            }
            .distinctBy { it.keyword }
            .take(limit)
        return HotSearchGroup(
            platform = "腾讯视频",
            items = items
        )
    }

    private fun loadIqiyiHotSearchGroup(limit: Int): HotSearchGroup {
        val sourceUrl = "https://www.iqiyi.com/ranks1PCW/home"
        val document = fetchDocument(sourceUrl)
        val items = document.select("a.rvi__box")
            .mapIndexedNotNull { index, anchor ->
                val titleNode = anchor.selectFirst(".rvi__tit1") ?: return@mapIndexedNotNull null
                val keyword = sanitizeHotSearchKeyword(
                    platform = "爱奇艺",
                    raw = decodeSiteText(
                    titleNode.attr("title").ifBlank { titleNode.ownText() }
                    ).replace(Regex("^\\d+"), "").trim()
                )
                if (keyword.isBlank()) return@mapIndexedNotNull null
                HotSearchItem(
                    rank = index + 1,
                    keyword = keyword,
                    platform = "爱奇艺",
                    sourceUrl = normalizeAgainst(anchor.attr("href"), sourceUrl)
                )
            }
            .distinctBy { it.keyword }
            .take(limit)
        return HotSearchGroup(
            platform = "爱奇艺",
            items = items
        )
    }

    private fun loadYoukuHotSearchGroup(limit: Int): HotSearchGroup {
        val sourceUrl = "https://m.youku.com/"
        val html = fetchHtml(
            url = sourceUrl,
            referer = sourceUrl,
            userAgent = HOT_SEARCH_MOBILE_UA
        )
        val layoutJson = extractAssignedJsonObject(
            html = html,
            variableName = "window.layoutData"
        )

        if (layoutJson.isBlank()) {
            return HotSearchGroup(platform = "\u4f18\u9177")
        }

        val root = JsonParser.parseString(layoutJson).asJsonObject
        val moduleList = root.getAsJsonObject("__INITIAL_DATA__")
            ?.getAsJsonObject("data")
            ?.getAsJsonArray("moduleList")
            ?: return HotSearchGroup(platform = "\u4f18\u9177")

        var fallbackComponent: com.google.gson.JsonObject? = null
        var hotComponent: com.google.gson.JsonObject? = null
        for (moduleElement in moduleList) {
            val components = moduleElement.asJsonObject.getAsJsonArray("components") ?: continue
            for (componentElement in components) {
                val component = componentElement.asJsonObject
                val tag = component.getAsJsonObject("template")
                    ?.get("tag")
                    ?.asString
                    .orEmpty()
                if (fallbackComponent == null && tag == "PHONE_BASE_B") {
                    fallbackComponent = component
                }

                val itemMap = component.getAsJsonArray("itemMap") ?: continue
                if (itemMap.size() == 0) continue
                val previewItem = itemMap[0].asJsonObject
                val trackInfo = previewItem.getAsJsonObject("trackInfo")
                val objectTitle = trackInfo?.get("object_title")?.asString.orEmpty()
                val resourceId = trackInfo?.get("ucd_res_id")?.asString.orEmpty()
                if (objectTitle.contains("\u70ed\u64ad") ||
                    resourceId.contains("_HOT", ignoreCase = true)
                ) {
                    hotComponent = component
                    break
                }
            }
            if (hotComponent != null) break
        }

        val items = buildList {
            val itemMap = (hotComponent ?: fallbackComponent)
                ?.getAsJsonArray("itemMap")
                ?: return@buildList
            for ((index, itemElement) in itemMap.withIndex()) {
                val item = itemElement.asJsonObject
                val keyword = sanitizeHotSearchKeyword(
                    platform = "优酷",
                    raw = decodeSiteText(item.get("title")?.asString.orEmpty())
                )
                if (keyword.isBlank()) continue
                add(
                    HotSearchItem(
                        rank = index + 1,
                        keyword = keyword,
                        platform = "\u4f18\u9177",
                        sourceUrl = sourceUrl
                    )
                )
                if (size >= limit) break
            }
        }.distinctBy { it.keyword }

        return HotSearchGroup(
            platform = "\u4f18\u9177",
            items = items
        )
    }

    private fun loadMgtvHotSearchGroup(limit: Int): HotSearchGroup {
        val sourceUrl = "https://www.mgtv.com/"
        val document = fetchDocument(sourceUrl)
        val section = document.select(".m-list-single")
            .firstOrNull { element ->
                element.selectFirst(".title span")
                    ?.text()
                    ?.contains("\u70ed\u64ad") == true
            }
            ?: document.selectFirst(".m-list-single")

        val items = section?.select(".hitv_horizontal-title a")
            ?.mapIndexedNotNull { index, anchor ->
                val keyword = sanitizeHotSearchKeyword(
                    platform = "芒果TV",
                    raw = decodeSiteText(
                    anchor.attr("title").ifBlank { anchor.text() }
                    )
                )
                if (keyword.isBlank()) return@mapIndexedNotNull null
                HotSearchItem(
                    rank = index + 1,
                    keyword = keyword,
                    platform = "\u8292\u679cTV",
                    sourceUrl = normalizeAgainst(anchor.attr("href"), sourceUrl)
                )
            }
            .orEmpty()
            .distinctBy { it.keyword }
            .take(limit)

        return HotSearchGroup(
            platform = "\u8292\u679cTV",
            items = items
        )
    }

    private fun sanitizeHotSearchKeyword(platform: String, raw: String): String {
        val normalized = raw
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("#")
            .trim()
        if (normalized.isBlank()) return ""

        val withoutEmojiSuffix = normalized
            .replace(Regex("[\\p{So}\\p{Cn}]+.*$"), "")
            .trim()

        val cleaned = when (platform) {
            "优酷" -> withoutEmojiSuffix
            "芒果TV" -> withoutEmojiSuffix
                .replace(Regex("\\s+加更版$"), "")
                .replace(Regex("·[^·]*vlog$", RegexOption.IGNORE_CASE), "")
                .trim()
            else -> withoutEmojiSuffix
        }

        return cleaned
            .replace(Regex("[\\p{Punct}·・：:：、，。！!？?]+$"), "")
            .trim()
    }

    private fun parseVodCards(root: Element): List<VodItem> =
        root.select("div.pic")
            .mapNotNull { card ->
                val detailAnchor = card.selectFirst("a[href*=/voddetail/]") ?: return@mapNotNull null
                val container: Element = card.parent() ?: card
                createVodItem(
                    detailHref = detailAnchor.attr("href"),
                    title = container.selectFirst("h3 a")?.text()
                        .orEmpty()
                        .ifBlank { detailAnchor.attr("title") },
                    imageUrl = card.selectFirst("[data-original]")?.attr("data-original")
                        ?: card.selectFirst("img")?.attr("data-original")
                        ?: card.selectFirst("img")?.attr("src").orEmpty(),
                    remarks = container.selectFirst(".item-status, .public-prt")?.text().orEmpty(),
                    description = container.selectFirst(".public-list-subtitle, .text-muted")?.text().orEmpty()
                )
            }
            .distinctBy { it.vodId }

    private fun parseLatestItems(document: Document): List<VodItem> =
        document.select("li.ranking-item a[href*=/voddetail/]")
            .mapNotNull { anchor ->
                createVodItem(
                    detailHref = anchor.attr("href"),
                    title = anchor.attr("title").ifBlank {
                        anchor.selectFirst(".ranking-item-info h4")?.text().orEmpty()
                    },
                    imageUrl = anchor.selectFirst(".ranking-item-cover .img-wrapper")?.attr("data-original")
                        ?: anchor.selectFirst(".ranking-item-cover img")?.attr("data-original")
                        ?: anchor.selectFirst(".ranking-item-cover img")?.attr("src").orEmpty(),
                    remarks = anchor.select(".ranking-item-info p.text-overflow")
                        .lastOrNull()
                        ?.text()
                        .orEmpty(),
                    description = anchor.selectFirst(".ranking-item-hits")?.text().orEmpty()
                )
            }
            .distinctBy { it.vodId }

    private fun parseSearchResults(document: Document, keyword: String = ""): List<VodItem> {
        val pageTitle = document.title()
        val keywordValue = document.selectFirst("input[name=wd]")?.attr("value").orEmpty().trim()
        val isSearchPage = pageTitle.contains("搜索") ||
            keywordValue.isNotBlank() ||
            document.select(".layout-box .vod-list h2").any { it.text().contains("搜索") }

        if (!isSearchPage) return emptyList()

        val searchSection = document.select(".layout-box .vod-list")
            .firstOrNull { section ->
                val heading = section.selectFirst("h2")?.text().orEmpty()
                heading.contains("搜索") ||
                    keywordValue.isNotBlank() && heading.contains(keywordValue) ||
                    keyword.isNotBlank() && heading.contains(keyword)
            }

        val items = (searchSection ?: document).select(".vod-list ul.row > li, ul.row > li.col-xs-4, ul.row > li")
            .mapNotNull { item ->
                val anchor = item.selectFirst(".pic a[href*=/voddetail/]") ?: return@mapNotNull null
                val description = listOf(
                    ".public-list-subtitle",
                    ".vod-content",
                    ".detail",
                    ".desc",
                    ".module-item-note"
                ).asSequence()
                    .mapNotNull { selector ->
                        item.selectFirst(selector)
                            ?.text()
                            ?.replace(Regex("\\s+"), " ")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    }
                    .firstOrNull()
                    .orEmpty()
                createVodItem(
                    detailHref = anchor.attr("href"),
                    title = item.selectFirst("h3 a")?.text().orEmpty().ifBlank { anchor.attr("title") },
                    imageUrl = item.selectFirst("[data-original]")?.attr("data-original")
                        ?: item.selectFirst("img")?.attr("data-original")
                        ?: item.selectFirst("img")?.attr("src").orEmpty(),
                    remarks = item.selectFirst(".item-status, .public-prt, .remarks")?.text().orEmpty(),
                    description = description
                )
            }

        return items.distinctBy { it.vodId }
    }

    private fun parseLabelNewItems(document: Document): List<VodItem> {
        val scopedRoot = document.select("div, ul, ol, section")
            .filter { root -> root.select("a[href*=/voddetail/]").size >= 12 }
            .maxByOrNull { root -> root.select("a[href*=/voddetail/]").size }
            ?: document

        return scopedRoot.select("a[href*=/voddetail/]")
            .mapNotNull { anchor ->
                val title = anchor.attr("title").trim().ifBlank { anchor.text().trim() }
                if (title.isBlank()) return@mapNotNull null

                val container = anchor.parents().firstOrNull { parent ->
                    parent.select("a[href*=/voddetail/]").size == 1
                } ?: anchor.parent() ?: anchor

                val remarks = container.text()
                    .replace(title, "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                createVodItem(
                    detailHref = anchor.attr("href"),
                    title = title,
                    imageUrl = container.selectFirst("[data-original]")?.attr("data-original")
                        ?: container.selectFirst("img")?.attr("data-original")
                        ?: container.selectFirst("img")?.attr("src").orEmpty(),
                    remarks = remarks
                )
            }
            .distinctBy { it.vodId }
            .take(100)
    }

    private fun parseUserCenterPage(document: Document): UserCenterPage {
        val items = document.select("input[name='ids[]']")
            .mapNotNull { input ->
                val row = input.parents().firstOrNull { parent ->
                    parent.select("input[name='ids[]']").size == 1 &&
                        parent.select("a[href*=/voddetail/], a[href*=/vodplay/]").isNotEmpty()
                } ?: return@mapNotNull null

                val detailAnchor = row.selectFirst("a[href*=/voddetail/]")
                val playAnchor = row.selectFirst("a[href*=/vodplay/]")
                val actionUrl = (playAnchor?.attr("href") ?: detailAnchor?.attr("href")).orEmpty()
                if (actionUrl.isBlank()) return@mapNotNull null

                val rawTitle = listOfNotNull(
                    detailAnchor?.attr("title"),
                    detailAnchor?.text(),
                    row.selectFirst("h3 a, h4 a, h5 a, .title a, .name a")?.text(),
                    row.selectFirst("h3, h4, h5, .title, .name")?.text()
                ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
                val rowText = decodeSiteText(row.text())
                val title = normalizeRecordTitle(rawTitle, rowText)
                val subtitle = buildUserRecordSubtitle(rowText, title)
                val detailHref = detailAnchor?.attr("href").orEmpty()

                return@mapNotNull UserCenterItem(
                    recordId = input.attr("value").trim(),
                    vodId = extractVodId(detailHref.ifBlank { extractVodIdFromUserUrl(actionUrl) }),
                    title = title.ifBlank { "未命名条目" },
                    subtitle = subtitle,
                    actionLabel = if (playAnchor != null) "继续观看" else "查看详情",
                    actionUrl = normalizeUrl(actionUrl)
                )
                /*
                val primaryAnchor = row.selectFirst("a[href*=/vodplay/], a[href*=/voddetail/]")
                    ?: return@mapNotNull null
                val title = primaryAnchor.text()
                    .replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
                    .trim()
                val rowText = row.text()
                val subtitle = rowText
                    .replace(primaryAnchor.text(), "")
                    .replace("删除", "")
                    .replace("重新观看", "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val actionUrl = primaryAnchor.attr("href")

                UserCenterItem(
                    recordId = input.attr("value").trim(),
                    vodId = extractVodIdFromUserUrl(actionUrl),
                    title = title.ifBlank { "未命名条目" },
                    subtitle = subtitle,
                    actionLabel = if (actionUrl.contains("/vodplay/")) "继续观看" else "查看详情",
                    actionUrl = normalizeUrl(actionUrl)
                )
            */
            }

        val nextPageUrl = document.select("a[href]")
            .firstOrNull { anchor ->
                val text = anchor.text().trim()
                anchor.attr("href").isNotBlank() &&
                    !anchor.attr("href").startsWith("javascript", ignoreCase = true) &&
                    (text.contains("下一页") || text == ">" || text == "›")
            }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeUrl)

        return UserCenterPage(
            items = items.distinctBy { "${it.recordId}:${it.actionUrl}" },
            nextPageUrl = nextPageUrl
        )
    }

    private fun parseUserCenterPageEnhanced(document: Document): UserCenterPage {
        val items = document.select("input[name='ids[]']")
            .mapNotNull { input ->
                val row = input.parents().firstOrNull { parent ->
                    parent.select("input[name='ids[]']").size == 1 &&
                        parent.select("a[href*=/voddetail/], a[href*=/vodplay/]").isNotEmpty()
                } ?: return@mapNotNull null

                val detailAnchor = row.selectFirst("a[href*=/voddetail/]")
                val playAnchor = row.selectFirst("a[href*=/vodplay/]")
                val actionUrl = normalizeUrl((playAnchor?.attr("href") ?: detailAnchor?.attr("href")).orEmpty())
                if (actionUrl.isBlank()) return@mapNotNull null

                val rawTitle = listOfNotNull(
                    detailAnchor?.attr("title"),
                    detailAnchor?.text(),
                    row.selectFirst("h3 a, h4 a, h5 a, .title a, .name a")?.text(),
                    row.selectFirst("h3, h4, h5, .title, .name")?.text()
                ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
                val rowText = decodeSiteText(row.text())
                val title = normalizeRecordTitle(rawTitle, rowText)
                val playUrl = normalizeUrl(playAnchor?.attr("href").orEmpty())
                val route = parsePlayRoute(playUrl.ifBlank { actionUrl })
                val detailHref = detailAnchor?.attr("href").orEmpty()

                UserCenterItem(
                    recordId = input.attr("value").trim(),
                    vodId = extractVodId(detailHref.ifBlank { extractVodIdFromUserUrl(actionUrl) }),
                    title = title.ifBlank { "未命名条目" },
                    subtitle = buildUserRecordSubtitleEnhanced(rowText, title, route),
                    actionLabel = if (playAnchor != null) "继续观看" else "查看详情",
                    actionUrl = actionUrl,
                    playUrl = playUrl,
                    sourceName = "",
                    sourceIndex = route?.sid?.toIntOrNull()?.minus(1) ?: -1,
                    episodeIndex = route?.nid?.toIntOrNull()?.minus(1) ?: -1
                )
            }

        val nextPageUrl = document.select("a[href]")
            .firstOrNull { anchor ->
                val text = anchor.text().trim()
                anchor.attr("href").isNotBlank() &&
                    !anchor.attr("href").startsWith("javascript", ignoreCase = true) &&
                    (text.contains("下一页") || text == ">" || text == "›")
            }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeUrl)

        return UserCenterPage(
            items = items.distinctBy { "${it.recordId}:${it.actionUrl}" },
            nextPageUrl = nextPageUrl
        )
    }

    suspend fun enrichHistoryItems(items: List<UserCenterItem>): List<UserCenterItem> {
        if (items.isEmpty()) return items

        val normalizedItems = items.map { item ->
            item.copy(
                vodId = item.vodId.ifBlank {
                    extractVodIdFromUserUrl(item.playUrl.ifBlank { item.actionUrl })
                }
            )
        }

        val targetVodIds = normalizedItems
            .asSequence()
            .filter { item -> item.vodId.isNotBlank() && item.sourceIndex >= 0 }
            .map { item -> item.vodId }
            .distinct()
            .toList()

        if (targetVodIds.isEmpty()) return normalizedItems

        val sourcesByVodId = coroutineScope {
            targetVodIds
                .map { vodId ->
                    async {
                        vodId to loadHistorySources(vodId)
                    }
                }
                .awaitAll()
                .toMap()
        }

        return normalizedItems.map { item ->
            val sourceName = sourcesByVodId[item.vodId]
                ?.getOrNull(item.sourceIndex)
                ?.name
                .orEmpty()
            if (sourceName.isBlank()) {
                item
            } else {
                item.copy(
                    sourceName = sourceName,
                    subtitle = replaceHistorySourceLabel(item.subtitle, sourceName)
                )
            }
        }
    }

    private suspend fun loadHistorySources(vodId: String): List<PlaySource> {
        historySourceCache[vodId]
            ?.takeIf { isCacheValid(it.timestampMs, HISTORY_SOURCE_CACHE_TTL_MS) }
            ?.value
            ?.let { return it }

        return awaitSharedRequest("history_sources:$vodId") {
            historySourceCache[vodId]
                ?.takeIf { isCacheValid(it.timestampMs, HISTORY_SOURCE_CACHE_TTL_MS) }
                ?.value
                ?: runCatching {
                    loadDetail(vodId)?.let(::parseSources).orEmpty()
                }.getOrDefault(emptyList()).also { sources ->
                    historySourceCache[vodId] = CachedValue(
                        value = sources,
                        timestampMs = System.currentTimeMillis()
                    )
                    cleanupCachesIfNeeded()
                }
        }
    }

    private fun cachePagePayload(cacheKey: String, payload: PagedVodItems) {
        if (!isAppCacheEnabled()) return
        val cachedValue = CachedValue(
            value = payload,
            timestampMs = System.currentTimeMillis()
        )
        categoryPageCache[cacheKey] = cachedValue
        rememberPreviewItems(payload.items)
        pageCachePrefs.edit()
            .putString(
                cacheKey,
                gson.toJson(
                    PersistedPageCache(
                        timestampMs = cachedValue.timestampMs,
                        payload = payload
                    )
                )
            )
            .apply()
        cleanupCachesIfNeeded()
    }

    private fun readPersistedPageCache(cacheKey: String): CachedValue<PagedVodItems>? {
        if (!isAppCacheEnabled()) return null
        val raw = pageCachePrefs.getString(cacheKey, null).orEmpty()
        if (raw.isBlank()) return null
        val persisted = parsePersistedPageCache(raw) ?: return null
        return CachedValue(
            value = persisted.payload,
            timestampMs = persisted.timestampMs
        )
    }

    private fun cacheHomePayload(payload: HomePayload) {
        if (!isAppCacheEnabled()) return
        val cachedValue = CachedValue(
            value = payload,
            timestampMs = System.currentTimeMillis()
        )
        homeCache = cachedValue
        homeCachePrefs.edit()
            .putString(
                HOME_CACHE_PREF_KEY,
                gson.toJson(
                    PersistedHomeCache(
                        timestampMs = cachedValue.timestampMs,
                        payload = payload
                    )
                )
            )
            .apply()
    }

    private fun readPersistedHomeCache(): CachedValue<HomePayload>? {
        if (!isAppCacheEnabled()) return null
        val raw = homeCachePrefs.getString(HOME_CACHE_PREF_KEY, null).orEmpty()
        if (raw.isBlank()) return null
        val root = runCatching {
            JsonParser.parseString(raw).asJsonObject
        }.getOrNull() ?: return null
        val payloadObject = root.getAsJsonObject("payload") ?: return null
        if (
            !payloadObject.has("latestCursor") ||
                !payloadObject.has("latestHasMore") ||
                !payloadObject.has("categoryCursor") ||
                !payloadObject.has("categoryHasMore")
        ) {
            homeCachePrefs.edit().remove(HOME_CACHE_PREF_KEY).apply()
            homeCache = null
            return null
        }
        val persisted = runCatching {
            gson.fromJson(raw, PersistedHomeCache::class.java)
        }.getOrNull() ?: return null
        return CachedValue(
            value = persisted.payload,
            timestampMs = persisted.timestampMs
        )
    }

    private fun parsePersistedPageCache(raw: String): PersistedPageCache? =
        runCatching {
            gson.fromJson(raw, PersistedPageCache::class.java)
        }.getOrNull()

    private fun cleanupCachesIfNeeded() {
        if (!isAppCacheEnabled()) {
            clearAllAppCaches()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastMemoryCacheCleanupAt >= MEMORY_CACHE_CLEANUP_INTERVAL_MS) {
            cleanupMemoryCaches(now)
            lastMemoryCacheCleanupAt = now
        }
        if (now - lastDiskCacheCleanupAt >= DISK_CACHE_CLEANUP_INTERVAL_MS) {
            cleanupDiskPageCache(now)
            lastDiskCacheCleanupAt = now
        }
    }

    private fun cleanupMemoryCaches(now: Long) {
        pruneExpiredEntries(categoryPageCache, now, PAGE_CACHE_TTL_MS)
        pruneExpiredEntries(detailCache, now, DETAIL_CACHE_TTL_MS)
        pruneExpiredEntries(searchCache, now, SEARCH_CACHE_TTL_MS)
        pruneExpiredEntries(historySourceCache, now, HISTORY_SOURCE_CACHE_TTL_MS)
        pruneExpiredEntries(previewItemCache, now, PREVIEW_ITEM_CACHE_TTL_MS)
        trimToSize(categoryPageCache, MAX_MEMORY_PAGE_CACHE_ENTRIES)
        trimToSize(detailCache, MAX_DETAIL_CACHE_ENTRIES)
        trimToSize(searchCache, MAX_SEARCH_CACHE_ENTRIES)
        trimToSize(historySourceCache, MAX_HISTORY_SOURCE_CACHE_ENTRIES)
        trimToSize(previewItemCache, MAX_PREVIEW_ITEM_CACHE_ENTRIES)

        if (homeCache?.let { !isCacheValid(it.timestampMs, HOME_CACHE_TTL_MS, now) } == true) {
            homeCache = null
        }
        if (hotSearchCache?.let { !isCacheValid(it.timestampMs, HOT_SEARCH_CACHE_TTL_MS, now) } == true) {
            hotSearchCache = null
        }
        if (noticeCache?.let { !isCacheValid(it.timestampMs, NOTICE_CACHE_TTL_MS, now) } == true) {
            noticeCache = null
        }
        if (browsableCategoriesCache?.let { !isCacheValid(it.timestampMs, CATEGORY_CACHE_TTL_MS, now) } == true) {
            browsableCategoriesCache = null
        }
    }

    private fun cleanupDiskPageCache(now: Long) {
        val snapshot = pageCachePrefs.all
        if (snapshot.isEmpty()) return

        val survivors = ArrayList<Pair<String, Long>>(snapshot.size)
        val staleKeys = mutableListOf<String>()
        snapshot.forEach { (key, value) ->
            val raw = value as? String
            val persisted = raw?.let(::parsePersistedPageCache)
            when {
                persisted == null -> staleKeys += key
                !isCacheValid(persisted.timestampMs, DISK_PAGE_CACHE_TTL_MS, now) -> staleKeys += key
                else -> survivors += key to persisted.timestampMs
            }
        }

        if (survivors.size > MAX_DISK_PAGE_CACHE_ENTRIES) {
            survivors
                .sortedByDescending { it.second }
                .drop(MAX_DISK_PAGE_CACHE_ENTRIES)
                .mapTo(staleKeys) { it.first }
        }

        if (staleKeys.isEmpty()) return
        pageCachePrefs.edit().apply {
            staleKeys.distinct().forEach(::remove)
        }.apply()
    }

    private fun <T> pruneExpiredEntries(
        cache: ConcurrentHashMap<String, CachedValue<T>>,
        now: Long,
        ttlMs: Long
    ) {
        cache.entries.removeIf { (_, value) -> !isCacheValid(value.timestampMs, ttlMs, now) }
    }

    private fun <T> trimToSize(
        cache: ConcurrentHashMap<String, CachedValue<T>>,
        maxSize: Int
    ) {
        if (cache.size <= maxSize) return
        cache.entries
            .sortedByDescending { it.value.timestampMs }
            .drop(maxSize)
            .forEach { entry -> cache.remove(entry.key, entry.value) }
    }

    private fun rememberPreviewItems(items: Collection<VodItem>) {
        if (!isAppCacheEnabled()) return
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val vodId = item.vodId.trim()
            if (vodId.isNotBlank()) {
                previewItemCache[vodId] = CachedValue(item, now)
            }
        }
    }

    private fun findPreviewItem(vodId: String): VodItem? =
        if (!isAppCacheEnabled()) null
        else previewItemCache[vodId]
            ?.takeIf { isCacheValid(it.timestampMs, PREVIEW_ITEM_CACHE_TTL_MS) }
            ?.value

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> awaitSharedRequest(
        key: String,
        block: suspend () -> T
    ): T = coroutineScope {
        val existing = inFlightRequests[key] as Deferred<T>?
        if (existing != null) {
            return@coroutineScope existing.await()
        }

        val deferred = async(start = CoroutineStart.LAZY) { block() }
        val deferredAny = deferred as Deferred<Any>
        val active = (inFlightRequests.putIfAbsent(key, deferredAny) as Deferred<T>?) ?: deferred
        if (active === deferred) {
            deferred.start()
        }
        try {
            active.await()
        } finally {
            if (active === deferred) {
                inFlightRequests.remove(key, deferredAny)
            }
        }
    }

    private suspend fun loadBrowsableCategories(
        homeDocument: Document? = null,
        forceRefresh: Boolean = false
    ): List<AppleCmsCategory> {
        if (!forceRefresh) {
            browsableCategoriesCache
                ?.takeIf { isCacheValid(it.timestampMs, CATEGORY_CACHE_TTL_MS) }
                ?.value
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        if (!forceRefresh && homeDocument == null) {
            return awaitSharedRequest("browsable_categories") {
                browsableCategoriesCache
                    ?.takeIf { isCacheValid(it.timestampMs, CATEGORY_CACHE_TTL_MS) }
                    ?.value
                    ?.takeIf { it.isNotEmpty() }
                    ?: loadBrowsableCategories(forceRefresh = true)
            }
        }
        val apiCategories = runCatching { requestApi { getCategories() }.data.orEmpty() }
            .getOrDefault(emptyList())
            .map(::normalizeCategory)
            .filter(::isBrowsableCategory)
            .distinctBy { it.typeId }

        if (apiCategories.isNotEmpty()) {
            return apiCategories.also { categories ->
                browsableCategoriesCache = CachedValue(
                    value = categories,
                    timestampMs = System.currentTimeMillis()
                )
                cleanupCachesIfNeeded()
            }
        }

        val resolvedHomeDocument = homeDocument ?: runCatching { fetchDocument("$baseUrl/") }.getOrNull()
        val mapDocument = runCatching { fetchDocument("$baseUrl/map/") }.getOrNull()

        val parsedCategories = resolvedHomeDocument
            ?.let { parseCategories(it, mapDocument) }
            .orEmpty()
            .map(::normalizeCategory)
            .filter(::isBrowsableCategory)
            .distinctBy { it.typeId }

        if (parsedCategories.isNotEmpty()) {
            return parsedCategories.also { categories ->
                browsableCategoriesCache = CachedValue(
                    value = categories,
                    timestampMs = System.currentTimeMillis()
                )
                cleanupCachesIfNeeded()
            }
        }

        return defaultCategories.map(::normalizeCategory).also { categories ->
            browsableCategoriesCache = CachedValue(
                value = categories,
                timestampMs = System.currentTimeMillis()
            )
            cleanupCachesIfNeeded()
        }
    }

    private suspend fun getBrowsableCategories(forceRefresh: Boolean = false): List<AppleCmsCategory> =
        loadBrowsableCategories(forceRefresh = forceRefresh)

    private fun getCachedBrowsableCategories(): List<AppleCmsCategory> =
        browsableCategoriesCache
            ?.takeIf { isCacheValid(it.timestampMs, CATEGORY_CACHE_TTL_MS) }
            ?.value
            ?.takeIf { it.isNotEmpty() }
            ?: defaultCategories.map(::normalizeCategory)

    private suspend fun loadCategoryPageFromWeb(typeId: String, page: Int): PagedVodItems {
        val safePage = page.coerceAtLeast(1)
        val document = fetchDocument(buildCategoryBrowseUrl(typeId = typeId, page = safePage))
        return parseCategoryPage(document = document, page = safePage, typeId = typeId)
    }

    private fun buildCategoryBrowseUrl(typeId: String, page: Int): String {
        val slug = extractCategorySlug(typeId).ifBlank { typeId.trim().removeSuffix("/") }
        return if (page <= 1) {
            "$baseUrl/vodshow/$slug-----------/"
        } else {
            "$baseUrl/vodshow/$slug--------$page---/"
        }
    }

    private fun parseCategoryPage(document: Document, page: Int, typeId: String): PagedVodItems {
        val listSection = document.select(".layout-box .vod-list")
            .firstOrNull { section ->
                section.select("ul.row > li .pic a[href*=/voddetail/]").isNotEmpty()
            }
            ?: document
        val items = parseVodCards(listSection)
        if (items.isEmpty()) {
            throw IOException("分类页面解析失败：$typeId")
        }

        val pagination = document.selectFirst("ul.ewave-page")
        val pageCount = extractCategoryPageCount(document, pagination).coerceAtLeast(page)
        val totalItems = extractCategoryTotal(document).coerceAtLeast(items.size)
        val hasNextPage = pagination?.select("a[href]")
            ?.any { anchor -> anchor.text().contains("下一页") }
            ?: (page < pageCount)

        return PagedVodItems(
            items = items,
            page = page,
            pageCount = pageCount,
            totalItems = totalItems,
            limit = items.size,
            hasNextPage = hasNextPage
        )
    }

    private suspend fun fetchUserDocument(pathOrUrl: String): Document {
        val document = fetchDocument(resolveUrl(pathOrUrl))
        if (document.select(".ewave-jump-msg, .panel-body").any { it.text().contains("未登录") }) {
            throw IOException("请先登录")
        }
        return document
    }

    private suspend fun submitUserAction(
        url: String,
        referer: String,
        formBody: FormBody
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("请求失败：HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val authResponse = runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()
            if (authResponse != null && (authResponse.msg.isNotBlank() || authResponse.code != 0)) {
                if (authResponse.code == 1) {
                    return authResponse.msg.ifBlank { "操作成功" }
                }
                throw IOException(authResponse.msg.ifBlank { "操作失败" })
            }
            if (body.contains("未登录")) {
                throw IOException("请先登录")
            }
            return "操作成功"
        }
    }

    private suspend fun submitPublicAction(
        url: String,
        referer: String,
        formBody: FormBody
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("请求失败：HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val authResponse = runCatching { gson.fromJson(body, AuthResponse::class.java) }.getOrNull()
            if (authResponse != null) {
                if (authResponse.code == 1) {
                    return authResponse.msg.ifBlank { "操作成功" }
                }
                throw IOException(authResponse.msg.ifBlank { "操作失败" })
            }
            throw IOException("操作失败")
        }
    }

    private fun parseDetail(document: Document): VodItem? {
        val infoRoot = document.selectFirst(".vod-info") ?: return null
        val metaMap = LinkedHashMap<String, String>()

        infoRoot.select(".info p.row span").forEach { span ->
            extractDetailMeta(span)?.let { (label, value) ->
                metaMap[label] = value
            }
        }

        val sources = parseDetailSources(document)
        val playFrom = sources.joinToString("$$$") { it.name }
        val playUrl = sources.joinToString("$$$") { source ->
            source.episodes.joinToString("#") { episode ->
                "${episode.name}$${episode.url}"
            }
        }

        return createVodItem(
            detailHref = infoRoot.selectFirst(".info h3 a[href*=/voddetail/]")?.attr("href")
                .orEmpty()
                .ifBlank { document.location() },
            title = infoRoot.selectFirst(".info h3 a, .info h1, h1")?.text().orEmpty(),
            imageUrl = infoRoot.selectFirst(".pic img")?.attr("data-original")
                .orEmpty()
                .ifBlank { infoRoot.selectFirst(".pic img")?.attr("src").orEmpty() },
            remarks = metaMap["状态"].orEmpty(),
            description = infoRoot.selectFirst(".text")?.text()
                .orEmpty()
                .removePrefix("简介：")
                .trim(),
            typeName = metaMap["分类"].orEmpty(),
            area = metaMap["地区"].orEmpty(),
            year = metaMap["年份"].orEmpty(),
            director = metaMap["导演"].orEmpty(),
            actor = metaMap["主演"].orEmpty(),
            vodPlayFrom = playFrom,
            vodPlayUrl = playUrl,
            siteVodId = document.selectFirst(".fav-btn[data-id], .mac_hits[data-id], .mac_ulog_set[data-id]")
                ?.attr("data-id")
                .orEmpty(),
            detailUrl = document.location()
        )
    }

    private fun parseDetailSources(document: Document): List<PlaySource> {
        val sourceNames = document.select(".playlist-tab .ewave-tab")
            .map { it.ownText().trim().ifBlank { it.text().trim() } }

        val sources = document.select(".ewave-playlist-content")
            .mapIndexedNotNull { index, listElement ->
                val episodes = listElement.select("a[href*=/vodplay/]")
                    .map { anchor ->
                        Episode(
                            name = anchor.text().trim().ifBlank { "播放" },
                            url = normalizeUrl(anchor.attr("href"))
                        )
                    }
                    .distinctBy { it.url }
                if (episodes.isEmpty()) {
                    null
                } else {
                    PlaySource(
                        name = sourceNames.getOrNull(index).orEmpty().ifBlank { "线路 ${index + 1}" },
                        episodes = episodes
                    )
                }
            }

        if (sources.isNotEmpty()) return sources

        val fallbackEpisodes = document.select("a[href*=/vodplay/]")
            .map { anchor ->
                Episode(
                    name = anchor.text().trim().ifBlank { "播放" },
                    url = normalizeUrl(anchor.attr("href"))
                )
            }
            .distinctBy { it.url }

        return if (fallbackEpisodes.isEmpty()) emptyList()
        else listOf(PlaySource(name = "默认线路", episodes = fallbackEpisodes))
    }

    private fun createVodItem(
        detailHref: String,
        title: String,
        imageUrl: String,
        remarks: String = "",
        description: String = "",
        typeName: String = "",
        area: String = "",
        year: String = "",
        director: String = "",
        actor: String = "",
        vodPlayFrom: String = "",
        vodPlayUrl: String = "",
        siteVodId: String = "",
        detailUrl: String = ""
    ): VodItem? {
        val vodId = extractVodId(detailHref)
        if (vodId.isBlank() || title.isBlank()) return null

        return VodItem(
            vodId = vodId,
            vodName = title,
            vodPic = normalizeUrl(imageUrl),
            vodRemarks = remarks,
            vodBlurb = description,
            typeName = typeName,
            vodArea = area,
            vodYear = year,
            vodDirector = director,
            vodActor = actor,
            vodPlayFrom = vodPlayFrom,
            vodPlayUrl = vodPlayUrl,
            siteVodId = siteVodId.ifBlank { vodId.takeIf { it.all(Char::isDigit) }.orEmpty() },
            detailUrl = normalizeUrl(detailUrl.ifBlank { detailHref })
        )
    }

    private fun fetchDocument(url: String, postBody: FormBody? = null): Document =
        Jsoup.parse(fetchHtml(url, postBody = postBody), url)

    private fun fetchHtml(
        url: String,
        postBody: FormBody? = null,
        referer: String = "$baseUrl/",
        userAgent: String = PLAYER_DESKTOP_UA
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .apply {
                if (postBody == null) get() else post(postBody)
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("$url -> HTTP ${response.code}")
                }
                response.body?.string().orEmpty().ifBlank {
                    throw IOException("$url -> empty body")
                }
            }
        } catch (e: Exception) {
            throw IOException(e.message ?: "站点请求失败", e)
        }
    }

    private fun decodePlayerUrl(rawUrl: String, encrypt: Int): String =
        top.jlen.vod.data.decodePlayerUrl(rawUrl, encrypt)

    private fun extractPlayerConfig(html: String): Pair<String, Int>? =
        top.jlen.vod.data.extractPlayerConfig(html)

    private suspend fun resolveNestedMediaUrl(
        candidateUrl: String,
        referer: String,
        depth: Int
    ): String {
        if (candidateUrl.isBlank() || isDirectMediaUrl(candidateUrl) || depth >= 3) {
            return candidateUrl
        }

        val html = runCatching { fetchHtml(candidateUrl, referer = referer) }.getOrNull().orEmpty()
        if (html.isBlank()) return candidateUrl

        val nestedCandidates = extractEmbeddedMediaUrls(html)
            .map { normalizeAgainst(it, candidateUrl) }
            .filter { it.isNotBlank() && it != candidateUrl }
            .distinct()

        nestedCandidates.firstOrNull { isDirectMediaUrl(it) }?.let { return it }

        for (nextUrl in nestedCandidates) {
            val resolved = resolveNestedMediaUrl(
                candidateUrl = nextUrl,
                referer = candidateUrl,
                depth = depth + 1
            )
            if (isDirectMediaUrl(resolved)) {
                return resolved
            }
        }

        return candidateUrl
    }

    private fun extractEmbeddedMediaUrls(html: String): List<String> =
        top.jlen.vod.data.extractEmbeddedMediaUrls(html)

    private fun extractAssignedJsonObject(html: String, variableName: String): String =
        top.jlen.vod.data.extractAssignedJsonObject(html, variableName)

    private fun extractVodIdFromUserUrl(pathOrUrl: String): String =
        top.jlen.vod.data.extractVodIdFromUserUrl(pathOrUrl, baseUrl)

    private fun resolveUrl(pathOrUrl: String): String =
        top.jlen.vod.data.resolveUrl(baseUrl, pathOrUrl)

    private fun normalizeAgainst(raw: String, base: String): String =
        top.jlen.vod.data.normalizeAgainst(raw, base, baseUrl)

    private fun normalizeUrl(raw: String): String =
        top.jlen.vod.data.normalizeUrl(baseUrl, raw)

    private fun normalizePortraitUrl(raw: String): String =
        top.jlen.vod.data.normalizePortraitUrl(baseUrl, raw)

    private fun createApi(baseUrl: String): AppleCmsApi =
        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AppleCmsApi::class.java)

    private suspend fun <T> requestApi(block: suspend AppleCmsApi.() -> T): T =
        primaryApi.block()

    private fun shouldFailoverToBackup(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            when (cause) {
                is HttpException -> cause.code() in TRANSIENT_API_STATUS_CODES
                is JsonParseException,
                is EOFException,
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is SSLException,
                is IOException -> true
                else -> false
            }
        }

    private fun parseUserProfileSession(document: Document): AuthSession {
        val cookieSession = currentSession()
        val portraitUrl = normalizePortraitUrl(
            document.selectFirst(".dyxs-user__name img.face, img.face, .face")
                ?.attr("src")
                .orEmpty()
        )
        val userName = decodeSiteText(
            document.selectFirst(".dyxs-user__name h3 a, .dyxs-user__name h3, h3 a")
                ?.text()
                .orEmpty()
        )
        val groupName = decodeSiteText(
            document.selectFirst(".dyxs-user__head .pull-right")
                ?.text()
                .orEmpty()
        )
        return cookieSession.copy(
            isLoggedIn = cookieSession.isLoggedIn || userName.isNotBlank(),
            userName = userName.ifBlank { cookieSession.userName },
            groupName = groupName.ifBlank { cookieSession.groupName },
            portraitUrl = portraitUrl.ifBlank { cookieSession.portraitUrl }
        )
    }

    private fun preparePortraitUpload(uri: Uri): PortraitUploadPayload {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
            .orEmpty()
            .substringBeforeLast('.', "")
            .ifBlank { "portrait" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1280)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: throw IOException("无法读取头像图片")

        val output = ByteArrayOutputStream()
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                throw IOException("头像图片处理失败")
            }
        } finally {
            bitmap.recycle()
        }

        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            throw IOException("头像图片处理失败")
        }

        return PortraitUploadPayload(
            bytes = bytes,
            fileName = "$displayName.jpg",
            mimeType = "image/jpeg"
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimension || currentHeight > maxDimension) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun parseUserProfileFields(document: Document): List<Pair<String, String>> {
        val labels = listOf(
            "用户名",
            "所属用户组",
            "会员期限",
            "QQ号码",
            "Email地址",
            "注册时间",
            "登录IP",
            "登录时间",
            "账户积分"
        )
        val values = extractLabeledValues(
            document = document,
            labels = labels,
            stopPhrases = listOf("推广注册链接", "推广访问链接", "友情链接")
        )
        return labels.mapNotNull { label ->
            values[label]
                ?.takeIf { it.isNotBlank() }
                ?.let { label to it }
        }
    }

    private fun parseUserProfileEditor(document: Document): UserProfileEditor =
        UserProfileEditor(
            qq = decodeSiteText(document.selectFirst("input[name=user_qq]")?.attr("value").orEmpty()),
            email = normalizeBoundEmail(
                decodeSiteText(document.selectFirst("input[name=user_email]")?.attr("value").orEmpty())
            ),
            phone = decodeSiteText(document.selectFirst("input[name=user_phone]")?.attr("value").orEmpty()),
            question = decodeSiteText(document.selectFirst("input[name=user_question]")?.attr("value").orEmpty()),
            answer = decodeSiteText(document.selectFirst("input[name=user_answer]")?.attr("value").orEmpty())
        )

    private fun normalizeBoundEmail(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.contains("@") && trimmed.contains(".")) trimmed else ""
    }

    private suspend fun requestAppCenterJson(
        action: String,
        formBody: FormBody? = null
    ): JsonObject {
        val url = Uri.parse(APP_CENTER_API_URL)
            .buildUpon()
            .appendQueryParameter("action", action.trim())
            .build()
            .toString()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (formBody == null) {
                    get()
                } else {
                    header("X-Requested-With", "XMLHttpRequest")
                    post(formBody)
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching {
                JsonParser.parseString(body).asJsonObject
            }.getOrNull()

            val code = json?.firstInt("code", "status")
            val message = json?.firstString("msg", "message")

            if (response.code == 401 || code == 401 || message.equals("login required", ignoreCase = true)) {
                throw IOException("请先登录")
            }

            if (!response.isSuccessful) {
                throw IOException(message?.takeIf(String::isNotBlank) ?: "内容服务请求失败：HTTP ${response.code}")
            }

            return json ?: throw IOException("内容服务返回了无法解析的数据")
        }
    }

    private suspend fun requestUserCenterJson(
        path: String,
        queryParameters: Map<String, String> = emptyMap(),
        formBody: FormBody? = null
    ): JsonObject {
        var lastError: Exception? = null

        for (candidateBase in userActionBaseCandidates()) {
            try {
                return performUserCenterJsonRequest(
                    base = candidateBase,
                    path = path,
                    queryParameters = queryParameters,
                    formBody = formBody
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error
            }
        }

        throw lastError ?: IOException("鐢ㄦ埛涓績璇锋眰澶辫触")
    }

    private fun performUserCenterJsonRequest(
        base: String,
        path: String,
        queryParameters: Map<String, String>,
        formBody: FormBody?
    ): JsonObject {
        val requestQueryParameters = linkedMapOf("format" to "json")
        requestQueryParameters.putAll(queryParameters)

        val url = Uri.parse(base + path)
            .buildUpon()
            .apply {
                requestQueryParameters.forEach { (name, value) ->
                    appendQueryParameter(name, value)
                }
            }
            .build()
            .toString()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Referer", base + path)
            .header("Accept", "application/json")

        val request = if (formBody == null) {
            requestBuilder
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()
        } else {
            requestBuilder
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()
        }

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val resolvedPath = response.request.url.encodedPath

            if (resolvedPath.contains("/index.php/user/login")) {
                throw IOException("璇峰厛鐧诲綍")
            }

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            return runCatching {
                JsonParser.parseString(body).asJsonObject
            }.getOrElse {
                if (
                    body.contains("/index.php/user/login", ignoreCase = true) ||
                    body.contains("user/login", ignoreCase = true)
                ) {
                    throw IOException("璇峰厛鐧诲綍")
                }
                throw IOException("鐢ㄦ埛涓績杩斿洖浜嗘棤娉曡В鏋愮殑鏁版嵁")
            }
        }
    }

    private fun parseAppCenterUserSnapshot(root: JsonObject): AppCenterUserSnapshot? {
        val data = root.firstObject("data") ?: return null
        val userObject = data.firstObject("user", "profile", "account", "member", "me", "info") ?: data
        val membershipObject = data.firstObject(
            "membership",
            "member_info",
            "membership_info",
            "vip",
            "group"
        ) ?: userObject

        val isLoggedIn = data.firstBoolean("is_login", "isLogin", "logged_in", "loggedIn")
            ?: userObject.firstBoolean("is_login", "isLogin", "logged_in", "loggedIn")
            ?: true
        if (!isLoggedIn) {
            throw IOException("请先登录")
        }

        val userId = userObject.firstString("user_id", "uid", "id", "userId")
        val userName = decodeSiteText(
            userObject.firstString("user_name", "username", "nickname", "name", "userName")
        )
        val groupName = decodeSiteText(
            membershipObject.firstString(
                "group_name",
                "member_name",
                "vip_name",
                "current_group_name",
                "group"
            ).ifBlank {
                userObject.firstString("group_name", "group", "member_name", "vip_name")
            }
        )
        val portraitUrl = normalizePortraitUrl(
            userObject.firstString(
                "user_portrait",
                "portrait",
                "avatar",
                "avatar_url",
                "headimg",
                "face"
            )
        )
        val qq = decodeSiteText(userObject.firstString("user_qq", "qq", "im_qq"))
        val email = normalizeBoundEmail(
            decodeSiteText(userObject.firstString("user_email", "email", "mail"))
        )
        val phone = decodeSiteText(userObject.firstString("user_phone", "phone", "mobile"))
        val question = decodeSiteText(userObject.firstString("user_question", "question", "security_question"))
        val answer = decodeSiteText(userObject.firstString("user_answer", "answer", "security_answer"))
        val points = decodeSiteText(
            membershipObject.firstString(
                "points",
                "score",
                "user_points",
                "integral",
                "point_balance"
            ).ifBlank {
                userObject.firstString("points", "score", "user_points", "integral")
            }
        )
        val expiry = decodeSiteText(
            membershipObject.firstString(
                "expiry",
                "expire_time",
                "expire_at",
                "group_expiry",
                "vip_expire_time",
                "member_expire_time"
            ).ifBlank {
                userObject.firstString(
                    "expiry",
                    "expire_time",
                    "expire_at",
                    "group_expiry",
                    "vip_expire_time",
                    "member_expire_time"
                )
            }
        )

        val plans = buildList {
            addAll(data.firstArray("membership_plans", "plans", "plan_list", "upgrade_plans", "groups", "group_list"))
            addAll(userObject.firstArray("membership_plans", "plans", "plan_list", "upgrade_plans", "groups", "group_list"))
            addAll(membershipObject.firstArray("membership_plans", "plans", "plan_list", "upgrade_plans", "groups", "group_list"))
        }.mapNotNull(::parseAppCenterMembershipPlan)
            .distinctBy { "${it.groupId}:${it.duration}:${it.points}" }

        val session = AuthSession(
            isLoggedIn = isLoggedIn,
            userId = userId,
            userName = userName,
            groupName = groupName,
            portraitUrl = portraitUrl
        )
        val membershipInfo = MembershipInfo(
            groupName = groupName,
            points = points,
            expiry = expiry
        )
        val profileFields = buildProfileFields(
            userId = userId,
            userName = userName,
            groupName = groupName,
            points = points,
            expiry = expiry,
            email = email,
            phone = phone,
            qq = qq
        )

        if (
            session.userId.isBlank() &&
            session.userName.isBlank() &&
            membershipInfo.groupName.isBlank() &&
            membershipInfo.points.isBlank() &&
            membershipInfo.expiry.isBlank() &&
            plans.isEmpty()
        ) {
            return null
        }

        return AppCenterUserSnapshot(
            session = session,
            profileFields = profileFields,
            profileEditor = UserProfileEditor(
                qq = qq,
                email = email,
                phone = phone,
                question = question,
                answer = answer
            ),
            membershipInfo = membershipInfo,
            membershipPlans = plans
        )
    }

    private fun parseVideoMemberInfoSnapshot(root: JsonObject): AppCenterUserSnapshot? {
        val data = root.firstObject("data") ?: return null
        val userObject = data.firstObject("user", "member", "profile", "account", "info") ?: data
        val membershipObject = userObject.firstObject("current_group", "group", "membership", "member_info")
            ?: userObject

        val userId = userObject.firstString("user_id", "uid", "id", "userId")
        val userName = decodeSiteText(
            userObject.firstString("user_name", "username", "nickname", "name", "userName")
        )
        val groupName = decodeSiteText(
            userObject.firstString(
                "group_name",
                "member_name",
                "vip_name",
                "current_group_name",
                "group"
            ).ifBlank {
                membershipObject.firstString(
                    "group_name",
                    "member_name",
                    "vip_name",
                    "current_group_name",
                    "group"
                )
            }
        )
        val portraitUrl = normalizePortraitUrl(
            userObject.firstString(
                "user_portrait",
                "portrait",
                "avatar",
                "avatar_url",
                "headimg",
                "face"
            )
        )
        val qq = decodeSiteText(userObject.firstString("user_qq", "qq", "im_qq"))
        val email = normalizeBoundEmail(
            decodeSiteText(userObject.firstString("user_email", "email", "mail"))
        )
        val phone = decodeSiteText(userObject.firstString("user_phone", "phone", "mobile"))
        val question = decodeSiteText(userObject.firstString("user_question", "question", "security_question"))
        val answer = decodeSiteText(userObject.firstString("user_answer", "answer", "security_answer"))
        val points = decodeSiteText(
            userObject.firstString(
                "user_points",
                "points",
                "score",
                "integral",
                "point_balance"
            ).ifBlank {
                membershipObject.firstString(
                    "user_points",
                    "points",
                    "score",
                    "integral",
                    "point_balance"
                )
            }
        )
        val expiry = resolveMembershipExpiryText(
            payload = data,
            membershipObject = membershipObject,
            userObject = userObject
        )

        val plans = data.firstArray("packages", "plans", "list", "items", "groups", "group_list")
            .flatMap(::parseUserCenterMembershipPlans)
            .distinctBy { "${it.groupId}:${it.duration}:${it.points}" }

        val session = AuthSession(
            isLoggedIn = true,
            userId = userId,
            userName = userName,
            groupName = groupName,
            portraitUrl = portraitUrl
        )
        val membershipInfo = MembershipInfo(
            groupName = groupName,
            points = points,
            expiry = expiry
        )
        val profileFields = buildProfileFields(
            userId = userId,
            userName = userName,
            groupName = groupName,
            points = points,
            expiry = expiry,
            email = email,
            phone = phone,
            qq = qq
        )

        if (
            session.userId.isBlank() &&
            session.userName.isBlank() &&
            membershipInfo.groupName.isBlank() &&
            membershipInfo.points.isBlank() &&
            membershipInfo.expiry.isBlank() &&
            plans.isEmpty()
        ) {
            return null
        }

        return AppCenterUserSnapshot(
            session = session,
            profileFields = profileFields,
            profileEditor = UserProfileEditor(
                qq = qq,
                email = email,
                phone = phone,
                question = question,
                answer = answer
            ),
            membershipInfo = membershipInfo,
            membershipPlans = plans
        )
    }

    private suspend fun loadMembershipPageFromUserCenterJson(): MembershipPage {
        val memberInfoJson = requestUserCenterJson(path = "/index.php/user/member_info")
        val upgradeJson = requestUserCenterJson(path = "/index.php/user/upgrade")

        return MembershipPage(
            info = parseMembershipInfoFromUserCenter(memberInfoJson) ?: MembershipInfo(),
            plans = parseMembershipPlansFromUserCenter(upgradeJson)
        )
    }

    private suspend fun loadUserProfileFromUserDetailApi(session: AuthSession): UserProfilePage {
        val normalizedUserId = session.userId.trim().takeIf(String::isNotBlank)
            ?: throw IOException("璇峰厛鐧诲綍")
        val json = requestVideoApiJson(
            path = "api.php/user/get_detail",
            queryParameters = mapOf("id" to normalizedUserId)
        )
        val code = json.firstInt("code", "status")
        if (code != null && code !in setOf(1, 200)) {
            throw IOException(json.firstString("msg", "message").ifBlank { "鑾峰彇鐢ㄦ埛璇︽儏澶辫触" })
        }

        val payload = extractUserApiPayload(json)
            ?: throw IOException("鐢ㄦ埛璇︽儏涓虹┖")
        val points = decodeSiteText(payload.firstString("user_points", "points", "score", "integral", "point_balance"))
        val groupName = decodeSiteText(payload.firstString("group_name", "member_name", "vip_name", "group"))
            .ifBlank { session.groupName }
        val portraitUrl = normalizePortraitUrl(
            payload.firstString("user_portrait", "portrait", "avatar", "avatar_url", "headimg", "face")
        ).ifBlank { session.portraitUrl }
        val email = normalizeBoundEmail(
            decodeSiteText(payload.firstString("user_email", "email", "mail"))
        )
        val phone = decodeSiteText(payload.firstString("user_phone", "phone", "mobile"))
        val qq = decodeSiteText(payload.firstString("user_qq", "qq", "im_qq"))
        val expiry = decodeSiteText(
            payload.firstString("user_end_time_text", "end_time_text", "expire_text", "expiry_text")
                .ifBlank {
                    formatMembershipExpiry(
                        payload.firstString(
                            "user_end_time",
                            "end_time",
                            "expire_time",
                            "expire_at",
                            "group_expiry",
                            "vip_expire_time",
                            "member_expire_time"
                        )
                    )
                }
        )

        return UserProfilePage(
            fields = buildProfileFields(
                userId = normalizedUserId,
                userName = decodeSiteText(payload.firstString("user_name", "username", "nickname", "name"))
                    .ifBlank { session.userName },
                groupName = groupName,
                points = points,
                expiry = expiry,
                email = email,
                phone = phone,
                qq = qq
            ),
            editor = UserProfileEditor(
                qq = qq,
                email = email,
                phone = phone
            ),
            session = session.copy(
                userName = decodeSiteText(payload.firstString("user_name", "username", "nickname", "name"))
                    .ifBlank { session.userName },
                groupName = groupName,
                portraitUrl = portraitUrl
            )
        )
    }

    private suspend fun loadMembershipInfoFromProfileHtml(): MembershipPage {
        val profileDocument = fetchUserDocument("/index.php/user/index.html")
        val session = parseUserProfileSession(profileDocument)
        val profileFields = parseUserProfileFields(profileDocument)
        val profileBodyText = decodeSiteText(profileDocument.body().text())
        val groupName = profileFields
            .firstOrNull { (label, _) ->
                label.contains("鎵€灞") || label.contains("鐢ㄦ埛缁") || label.contains("分组") || label.contains("用户组")
            }
            ?.second
            .orEmpty()
            .ifBlank {
                extractLooseMembershipValue(
                    bodyText = profileBodyText,
                    labels = listOf("当前分组", "所属用户组", "会员组", "用户组")
                )
            }
            .ifBlank { session.groupName }
        val expiry = profileFields
            .firstOrNull { (label, _) -> label.contains("浼氬憳鏈") || label.contains("鍒版湡") || label.contains("期限") }
            ?.second
            .orEmpty()
            .ifBlank {
                extractLooseMembershipValue(
                    bodyText = profileBodyText,
                    labels = listOf("到期时间", "会员期限", "会员到期", "到期")
                )
            }
        val points = profileFields
            .firstOrNull { (label, _) -> label.contains("绉垎") || label.contains("璐︽埛") || label.contains("积分") }
            ?.second
            .orEmpty()
            .ifBlank {
                extractLooseMembershipValue(
                    bodyText = profileBodyText,
                    labels = listOf("剩余积分", "账户积分", "积分余额", "积分")
                )
            }

        return MembershipPage(
            info = MembershipInfo(
                groupName = decodeSiteText(groupName),
                points = decodeSiteText(points),
                expiry = decodeSiteText(expiry)
            ),
            plans = emptyList()
        )
    }

    private suspend fun loadMembershipInfoFromUserDetailApi(userId: String): MembershipPage {
        val normalizedUserId = userId.trim().takeIf(String::isNotBlank) ?: return MembershipPage()
        val json = requestVideoApiJson(
            path = "api.php/user/get_detail",
            queryParameters = mapOf("id" to normalizedUserId)
        )
        val code = json.firstInt("code", "status")
        if (code != null && code !in setOf(1, 200)) {
            return MembershipPage()
        }

        val payload = extractUserApiPayload(json) ?: return MembershipPage()
        val points = decodeSiteText(payload.firstString("user_points", "points", "score", "integral", "point_balance"))
        val groupName = decodeSiteText(payload.firstString("group_name", "member_name", "vip_name", "group"))
        val expiry = decodeSiteText(
            payload.firstString("user_end_time_text", "end_time_text", "expire_text", "expiry_text")
                .ifBlank {
                    formatMembershipExpiry(
                        payload.firstString(
                            "user_end_time",
                            "end_time",
                            "expire_time",
                            "expire_at",
                            "group_expiry",
                            "vip_expire_time",
                            "member_expire_time"
                        )
                    )
                }
        )

        return MembershipPage(
            info = MembershipInfo(
                groupName = groupName,
                points = points,
                expiry = expiry
            ),
            plans = emptyList()
        )
    }

    private fun parseMembershipPlansFromHtml(document: Document): List<MembershipPlan> {
        val attributePlans = document.select(
            "[data-id][data-points][data-long], " +
                "[data-id][data-duration][data-points], " +
                "[data-group-id][data-long], " +
                "[data-group_id][data-long], " +
                "[data-gid][data-long], " +
                "[data-id][data-long], " +
                "[data-id][data-duration]"
        ).mapNotNull(::parseMembershipPlanFromHtmlElement)

        val scriptPlans = document.select("script, [onclick]")
            .flatMap { element ->
                buildList {
                    element.data().takeIf(String::isNotBlank)?.let(::add)
                    element.attr("onclick").takeIf(String::isNotBlank)?.let(::add)
                }
            }
            .flatMap(::parseMembershipPlansFromScript)

        return (attributePlans + scriptPlans)
            .filter { it.groupId.isNotBlank() || it.groupName.isNotBlank() || it.duration.isNotBlank() || it.points.isNotBlank() }
            .distinctBy { "${it.groupId}:${it.groupName}:${it.duration}:${it.points}" }
    }

    private fun parseMembershipPlanFromHtmlElement(element: Element): MembershipPlan? {
        val groupId = element.attr("data-id").trim()
            .ifBlank { element.attr("data-group-id").trim() }
            .ifBlank { element.attr("data-group_id").trim() }
            .ifBlank { element.attr("data-gid").trim() }
        val groupName = decodeSiteText(
            element.attr("data-name").trim()
                .ifBlank { element.attr("data-title").trim() }
                .ifBlank { element.attr("data-group-name").trim() }
                .ifBlank { element.ownText().trim() }
        )
        val duration = decodeSiteText(
            element.attr("data-long").trim()
                .ifBlank { element.attr("data-duration").trim() }
                .ifBlank { parseMembershipDurationFromText(element.text()) }
        )
        val points = decodeSiteText(
            element.attr("data-points").trim()
                .ifBlank { element.attr("data-price").trim() }
                .ifBlank { element.attr("data-score").trim() }
                .ifBlank { parseMembershipPointsFromText(element.text()) }
        )

        if (groupId.isBlank() && groupName.isBlank() && duration.isBlank() && points.isBlank()) {
            return null
        }

        return MembershipPlan(
            groupId = groupId,
            groupName = groupName,
            duration = duration,
            points = points
        )
    }

    private fun parseMembershipPlansFromScript(script: String): List<MembershipPlan> {
        val normalized = script.trim()
        if (normalized.isBlank()) return emptyList()

        val duration = Regex("(?i)\\b(day|week|month|year)\\b")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val groupId = Regex("(?i)(?:group_id|groupId|gid|data-id|data-group-id)\\D{0,8}(\\d{1,6})")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val points = Regex("(?i)(?:points|price|score|need_points|cost_points)\\D{0,8}(\\d{1,8})")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

        if (duration.isBlank() || points.isBlank()) {
            return emptyList()
        }

        return listOf(
            MembershipPlan(
                groupId = groupId,
                groupName = "",
                duration = duration,
                points = points
            )
        )
    }

    private fun parseMembershipDurationFromText(text: String): String {
        val normalized = text.lowercase(Locale.ROOT)
        return when {
            "day" in normalized || "鍖呭ぉ" in text -> "day"
            "week" in normalized || "鍖呭懆" in text -> "week"
            "month" in normalized || "鍖呮湀" in text -> "month"
            "year" in normalized || "鍖呭勾" in text -> "year"
            else -> ""
        }
    }

    private fun parseMembershipPointsFromText(text: String): String =
        Regex("(\\d{1,8})\\s*(?:绉垎|points|score)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    private fun extractLooseMembershipValue(bodyText: String, labels: List<String>): String {
        if (bodyText.isBlank()) return ""
        return labels.asSequence()
            .mapNotNull { label ->
                Regex("${Regex.escape(label)}\\s*[：: ]+([^：:]+?)(?=\\s+(?:当前分组|所属用户组|会员组|用户组|剩余积分|账户积分|积分余额|积分|到期时间|会员期限|会员到期|到期|QQ|Email|注册时间|登录IP|登录时间|$))")
                    .find(bodyText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.let(::decodeSiteText)
                    ?.takeIf(String::isNotBlank)
            }
            .firstOrNull()
            .orEmpty()
    }

    private fun parseMembershipPlansFromUserCenter(root: JsonObject): List<MembershipPlan> {
        val payload = unwrapUserCenterPayload(root)
        val groupElements = buildList {
            addAll(payload.firstArray("groups", "group_list", "plans", "list", "items", "upgrade_plans", "membership_plans"))
            addAll(root.firstArray("groups", "group_list", "plans", "list", "items", "upgrade_plans", "membership_plans"))
        }

        return groupElements
            .flatMap(::parseUserCenterMembershipPlans)
            .distinctBy { "${it.groupId}:${it.duration}:${it.points}" }
    }

    private fun parseUserCenterMembershipPlans(element: JsonElement): List<MembershipPlan> {
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val groupId = obj.firstString("group_id", "id", "gid", "groupId")
        val groupName = decodeSiteText(obj.firstString("group_name", "name", "title", "label"))

        val plans = SUPPORTED_MEMBERSHIP_DURATIONS.mapNotNull { duration ->
            val points = decodeSiteText(
                obj.firstString(
                    "${duration}_points",
                    "points_$duration",
                    "group_points_$duration",
                    "${duration}_price",
                    "price_$duration",
                    "group_price_$duration",
                    "${duration}_score",
                    "score_$duration"
                ).ifBlank {
                    obj.primitiveString(duration).takeIf { it.any(Char::isDigit) }.orEmpty()
                }.ifBlank {
                    extractMembershipPlanValue(obj, duration)
                }
            )
            points
                .takeIf(String::isNotBlank)
                ?.takeIf { it != "0" && !it.equals("false", ignoreCase = true) }
                ?.let {
                    MembershipPlan(
                        groupId = groupId,
                        groupName = groupName,
                        duration = duration,
                        points = it
                    )
                }
        }

        if (plans.isNotEmpty()) {
            return plans
        }

        return parseAppCenterMembershipPlan(obj)
            ?.takeIf { it.groupId.isNotBlank() || it.groupName.isNotBlank() || it.points.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }

    private fun extractMembershipPlanValue(obj: JsonObject, duration: String): String {
        val nestedContainers = sequenceOf(
            obj.get("points"),
            obj.get("prices"),
            obj.get("price"),
            obj.get("cost"),
            obj.get("costs"),
            obj.get("amounts")
        )

        return nestedContainers
            .mapNotNull { element -> element?.takeIf { it.isJsonObject }?.asJsonObject }
            .mapNotNull { container ->
                container.firstString(
                    duration,
                    "${duration}_points",
                    "points_$duration",
                    "${duration}_price",
                    "price_$duration",
                    "group_points_$duration",
                    "group_price_$duration"
                ).takeIf(String::isNotBlank)
            }
            .firstOrNull()
            .orEmpty()
    }

    private fun unwrapUserCenterPayload(root: JsonObject): JsonObject =
        root.firstObject("data", "info") ?: root

    private fun unwrapApiPayload(root: JsonObject): JsonObject? =
        root.firstObject("data", "info") ?: root.takeIf { it.entrySet().isNotEmpty() }

    private fun extractUserApiPayload(root: JsonObject): JsonObject? {
        val payload = unwrapApiPayload(root) ?: return null
        return payload.firstObject("user", "profile", "account", "member", "me", "info") ?: payload
    }

    private fun resolveMembershipExpiryText(
        payload: JsonObject,
        membershipObject: JsonObject,
        userObject: JsonObject
    ): String {
        val textValue = decodeSiteText(
            payload.firstString(
                "user_end_time_text",
                "end_time_text",
                "expire_text",
                "expiry_text"
            ).ifBlank {
                membershipObject.firstString(
                    "user_end_time_text",
                    "end_time_text",
                    "expire_text",
                    "expiry_text"
                )
            }.ifBlank {
                userObject.firstString(
                    "user_end_time_text",
                    "end_time_text",
                    "expire_text",
                    "expiry_text"
                )
            }
        )
        if (textValue.isNotBlank()) return textValue

        val rawValue = payload.firstString(
            "user_end_time",
            "end_time",
            "expire_time",
            "expire_at",
            "group_expiry",
            "vip_expire_time",
            "member_expire_time"
        ).ifBlank {
            membershipObject.firstString(
                "user_end_time",
                "end_time",
                "expire_time",
                "expire_at",
                "group_expiry",
                "vip_expire_time",
                "member_expire_time"
            )
        }.ifBlank {
            userObject.firstString(
                "user_end_time",
                "end_time",
                "expire_time",
                "expire_at",
                "group_expiry",
                "vip_expire_time",
                "member_expire_time"
            )
        }
        return formatMembershipExpiry(rawValue)
    }

    private fun parsePlayRoute(episodePageUrl: String): PlayRoute? {
        val normalized = resolveUrl(episodePageUrl)
        val match = Regex("""/vodplay/[^/]+?-(\d+)-(\d+)(?:\.html)?/?(?:\?.*)?$""")
            .find(normalized)
            ?: return null
        return PlayRoute(
            sid = match.groupValues.getOrNull(1).orEmpty(),
            nid = match.groupValues.getOrNull(2).orEmpty()
        )
    }

    private val VodItem.siteLogId: String
        get() = siteVodId.ifBlank { vodId.takeIf { it.all(Char::isDigit) }.orEmpty() }

    companion object {
        private const val APP_CENTER_API_URL = "https://user.jlen.top/api.php"
        private const val HOME_CACHE_PREF_KEY = "home_payload"
        private val SUPPORTED_MEMBERSHIP_DURATIONS = listOf("day", "week", "month", "year")
        private const val DISABLE_APP_CACHE = false
        private const val KEY_DISMISSED_NOTICE_IDS = "dismissed_notice_ids"
        private const val HEARTBEAT_DEVICE_ID_KEY = "device_id"
        private const val HOME_CURSOR_PAGE_LIMIT = 20
        private const val CATEGORY_CURSOR_PAGE_LIMIT = 20
        private const val SEARCH_CURSOR_PAGE_LIMIT = 20
        private const val HOME_CACHE_TTL_MS = 60_000L
        private const val DISK_HOME_CACHE_TTL_MS = 43_200_000L
        private const val NOTICE_CACHE_TTL_MS = 60_000L
        private const val CATEGORY_CACHE_TTL_MS = 300_000L
        private const val PAGE_CACHE_TTL_MS = 300_000L
        private const val DISK_PAGE_CACHE_TTL_MS = 43_200_000L
        private const val SEARCH_CACHE_TTL_MS = 30_000L
        private const val DETAIL_CACHE_TTL_MS = 60_000L
        private const val HISTORY_SOURCE_CACHE_TTL_MS = 600_000L
        private const val PREVIEW_ITEM_CACHE_TTL_MS = 600_000L
        private const val HOT_SEARCH_CACHE_TTL_MS = 300_000L
        private const val MEMORY_CACHE_CLEANUP_INTERVAL_MS = 60_000L
        private const val DISK_CACHE_CLEANUP_INTERVAL_MS = 300_000L
        private const val API_FAILOVER_COOLDOWN_MS = 300_000L
        private const val USER_CENTER_PAGE_LIMIT = 20
        private const val MAX_MEMORY_PAGE_CACHE_ENTRIES = 24
        private const val MAX_DISK_PAGE_CACHE_ENTRIES = 48
        private const val MAX_DETAIL_CACHE_ENTRIES = 48
        private const val MAX_SEARCH_CACHE_ENTRIES = 24
        private const val MAX_HISTORY_SOURCE_CACHE_ENTRIES = 96
        private const val MAX_PREVIEW_ITEM_CACHE_ENTRIES = 256
        private val TRANSIENT_API_STATUS_CODES = setOf(500, 502, 503, 504, 521, 522, 523, 524)
        private const val HOT_SEARCH_MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
                "Mobile/15E148 Safari/604.1"

        private fun isAppCacheEnabled(): Boolean = !DISABLE_APP_CACHE

        fun clearAllCaches(context: Context) {
            AppleCmsRepository(context.applicationContext).clearRuntimeCaches()
        }

        fun clearMemoryCaches(context: Context) {
            AppleCmsRepository(context.applicationContext).clearProcessMemoryCaches()
        }

        private fun isCacheValid(timestampMs: Long, ttlMs: Long): Boolean =
            isCacheValid(timestampMs, ttlMs, System.currentTimeMillis())

        private fun isCacheValid(timestampMs: Long, ttlMs: Long, now: Long): Boolean =
            isAppCacheEnabled() && ttlMs > 0 && now - timestampMs <= ttlMs

        internal fun createClient(cookieJar: PersistentCookieJar): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))
                .cookieJar(cookieJar)
                .addInterceptor(logging)
                .build()
        }
    }
}

