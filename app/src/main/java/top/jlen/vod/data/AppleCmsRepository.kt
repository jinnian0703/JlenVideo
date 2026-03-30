package top.jlen.vod.data

import android.content.Context
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
import top.jlen.vod.BuildConfig
import top.jlen.vod.ui.PLAYER_DESKTOP_UA
import javax.net.ssl.SSLException

class AppleCmsRepository(
    context: Context,
    private val cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    private val client: OkHttpClient = createClient(cookieJar)
) {
    private val appContext = context.applicationContext
    private val pageCachePrefs = appContext.getSharedPreferences("library_page_cache", Context.MODE_PRIVATE)
    private val noticePrefs = appContext.getSharedPreferences("app_notice_store", Context.MODE_PRIVATE)
    private val heartbeatPrefs = appContext.getSharedPreferences("app_heartbeat_store", Context.MODE_PRIVATE)
    private val defaultCategories = listOf(
        AppleCmsCategory(typeId = "GCCCCG", typeName = "电影", parentId = "GCCCCG"),
        AppleCmsCategory(typeId = "GCCCCT", typeName = "连续剧", parentId = "GCCCCT"),
        AppleCmsCategory(typeId = "GCCCCV", typeName = "综艺", parentId = "GCCCCV"),
        AppleCmsCategory(typeId = "GCCCCW", typeName = "动漫", parentId = "GCCCCW")
    )
    private val baseUrl = BuildConfig.APPLE_CMS_BASE_URL.trimEnd('/')
    private val fallbackApiBaseUrl = BuildConfig.APPLE_CMS_FALLBACK_BASE_URL
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

    private data class CachedValue<T>(
        val value: T,
        val timestampMs: Long
    )

    private data class PersistedPageCache(
        val timestampMs: Long,
        val payload: PagedVodItems
    )

    private data class AppCenterUserSnapshot(
        val session: AuthSession = AuthSession(),
        val profileFields: List<Pair<String, String>> = emptyList(),
        val profileEditor: UserProfileEditor = UserProfileEditor(),
        val membershipInfo: MembershipInfo = MembershipInfo(),
        val membershipPlans: List<MembershipPlan> = emptyList()
    )

    suspend fun loadHome(forceRefresh: Boolean = false): HomePayload {
        if (!forceRefresh) {
            homeCache
                ?.takeIf { isCacheValid(it.timestampMs, HOME_CACHE_TTL_MS) }
                ?.value
                ?.let { return it }
        }

        return runCatching {
            if (forceRefresh) {
                loadFreshHome(forceRefresh = true)
            } else {
                awaitSharedRequest("home") {
                    homeCache
                        ?.takeIf { isCacheValid(it.timestampMs, HOME_CACHE_TTL_MS) }
                        ?.value
                        ?: loadFreshHome(forceRefresh = false)
                }
            }
        }.getOrElse {
            loadEmergencyHome()
        }
    }

    private suspend fun loadEmergencyHome(): HomePayload {
        val cachedHome = homeCache?.value
        val latestPage = runCatching { loadLatestPage(page = 1) }
            .getOrNull()
            ?: peekAllCategoryPage(1)
            ?: readPersistedPageCache("all:1")?.value
            ?: PagedVodItems(
                items = emptyList(),
                page = 1,
                pageCount = 1,
                totalItems = 0,
                limit = 0,
                hasNextPage = false
            )
        val recommendedItems = runCatching {
            val rawItems = requestApi { getRecommendations(limit = 16) }
                .data
                ?.rows
                .orEmpty()
                .distinctBy { it.vodId }
            filterPlayablePreviewItems(rawItems)
        }.getOrElse {
            cachedHome?.featured.orEmpty()
        }
        val categories = runCatching { loadBrowsableCategories(forceRefresh = false) }
            .getOrElse { getCachedBrowsableCategories() }
            .ifEmpty { defaultCategories.map(::normalizeCategory) }
        val selectedCategory = categories.firstOrNull()
        val categoryPage = selectedCategory?.let { category ->
            peekCategoryPage(typeId = category.typeId, page = 1)
                ?: readPersistedPageCache("${category.typeId}:1")?.value
        } ?: PagedVodItems(
            items = emptyList(),
            page = 1,
            pageCount = 1,
            totalItems = 0,
            limit = 0,
            hasNextPage = false
        )
        val latestItems = latestPage.items.ifEmpty { recommendedItems.take(36) }
        val featuredItems = recommendedItems
            .ifEmpty { cachedHome?.featured.orEmpty() }
            .ifEmpty { latestItems.take(16) }
        rememberPreviewItems(buildList {
            addAll(latestItems)
            addAll(featuredItems)
            addAll(categoryPage.items)
        })

        return HomePayload(
            slides = emptyList(),
            hot = emptyList(),
            featured = featuredItems,
            latest = latestItems,
            sections = emptyList(),
            categories = categories,
            selectedCategory = selectedCategory,
            categoryVideos = categoryPage.items,
            latestPage = latestPage.page,
            latestPageCount = latestPage.pageCount,
            latestTotal = latestPage.totalItems,
            latestHasNextPage = latestPage.hasNextPage,
            categoryPage = categoryPage.page,
            categoryPageCount = categoryPage.pageCount,
            categoryTotal = categoryPage.totalItems,
            categoryHasNextPage = categoryPage.hasNextPage
        ).also { payload ->
            homeCache = CachedValue(
                value = payload,
                timestampMs = System.currentTimeMillis()
            )
            cleanupCachesIfNeeded()
        }
    }

    private suspend fun loadFreshHome(forceRefresh: Boolean): HomePayload {
        val (latestPage, recommendedItems) = coroutineScope {
            val latestDeferred = async {
                runCatching {
                    loadLatestPage(page = 1)
                }.recoverCatching {
                    loadLatestUpdatesFromLabelPage()
                }.getOrElse {
                    loadAllCategoryPage(page = 1, forceRefresh = forceRefresh)
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
        rememberPreviewItems(latest + featured)

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
            selectedCategory = categories.firstOrNull(),
            categoryVideos = emptyList(),
            latestPage = latestPage.page,
            latestPageCount = latestPage.pageCount,
            latestTotal = latestPage.totalItems,
            latestHasNextPage = latestPage.hasNextPage,
            categoryPage = 1,
            categoryPageCount = 1,
            categoryTotal = 0,
            categoryHasNextPage = true
        ).also { payload ->
            homeCache = CachedValue(
                value = payload,
                timestampMs = System.currentTimeMillis()
            )
            cleanupCachesIfNeeded()
        }
    }

    suspend fun loadByCategory(typeId: String): List<VodItem> =
        loadCategoryPage(typeId = typeId, page = 1).items

    suspend fun loadAllCategoryPage(page: Int, forceRefresh: Boolean = false): PagedVodItems {
        val safePage = page.coerceAtLeast(1)
        val cacheKey = "all:$safePage"
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
            loadFreshAllCategoryPage(page = safePage, forceRefresh = true)
        } else {
            awaitSharedRequest("all_page:$safePage") {
                categoryPageCache[cacheKey]
                    ?.takeIf { isCacheValid(it.timestampMs, PAGE_CACHE_TTL_MS) }
                    ?.value
                    ?: readPersistedPageCache(cacheKey)
                        ?.takeIf { isCacheValid(it.timestampMs, DISK_PAGE_CACHE_TTL_MS) }
                        ?.also { cached -> categoryPageCache[cacheKey] = cached }
                        ?.value
                    ?: loadFreshAllCategoryPage(page = safePage, forceRefresh = false)
            }
        }
    }

    private suspend fun loadFreshAllCategoryPage(page: Int, forceRefresh: Boolean): PagedVodItems {
        val pages = coroutineScope {
            getBrowsableCategories(forceRefresh = forceRefresh)
                .map { category -> async { loadCategoryPage(category.typeId, page, forceRefresh = forceRefresh) } }
                .awaitAll()
        }
        return buildMergedCategoryPage(pages, page).also { payload ->
            cachePagePayload("all:$page", payload)
        }
    }

    fun peekAllCategoryPage(page: Int): PagedVodItems? {
        val safePage = page.coerceAtLeast(1)
        val allCacheKey = "all:$safePage"
        categoryPageCache[allCacheKey]
            ?.takeIf { isCacheValid(it.timestampMs, PAGE_CACHE_TTL_MS) }
            ?.value
            ?.let { return it }
        readPersistedPageCache(allCacheKey)
            ?.takeIf { isCacheValid(it.timestampMs, DISK_PAGE_CACHE_TTL_MS) }
            ?.also { cached ->
                categoryPageCache[allCacheKey] = cached
                return cached.value
            }

        val cachedPages = getCachedBrowsableCategories().mapNotNull { category ->
            categoryPageCache["${category.typeId}:$safePage"]
                ?.takeIf { isCacheValid(it.timestampMs, PAGE_CACHE_TTL_MS) }
                ?.value
                ?: readPersistedPageCache("${category.typeId}:$safePage")
                    ?.takeIf { isCacheValid(it.timestampMs, DISK_PAGE_CACHE_TTL_MS) }
                    ?.also { cached -> categoryPageCache["${category.typeId}:$safePage"] = cached }
                    ?.value
        }
        if (cachedPages.isEmpty()) return null
        return buildMergedCategoryPage(cachedPages, safePage).also { payload ->
            cachePagePayload(allCacheKey, payload)
        }
    }

    fun peekCategoryPage(typeId: String, page: Int): PagedVodItems? {
        val safePage = page.coerceAtLeast(1)
        val cacheKey = "$typeId:$safePage"
        categoryPageCache[cacheKey]
            ?.takeIf { isCacheValid(it.timestampMs, PAGE_CACHE_TTL_MS) }
            ?.value
            ?.let { return it }
        readPersistedPageCache(cacheKey)
            ?.takeIf { isCacheValid(it.timestampMs, DISK_PAGE_CACHE_TTL_MS) }
            ?.also { cached -> categoryPageCache[cacheKey] = cached }
            ?.value
            ?.let { return it }
        return null
    }

    suspend fun prewarmCategoryFirstPages(forceRefresh: Boolean = false) {
        val categories = getBrowsableCategories(forceRefresh = forceRefresh)
        coroutineScope {
            categories.map { category ->
                async {
                    runCatching {
                        loadCategoryPage(
                            typeId = category.typeId,
                            page = 1,
                            forceRefresh = forceRefresh
                        )
                    }
                }
            }.awaitAll()
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

    fun reportHeartbeat(
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
                    .add("device_id", ensureHeartbeatDeviceId())
                    .add("platform", "android")
                    .add("app_version", BuildConfig.VERSION_NAME)
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

    suspend fun login(userName: String, password: String): AuthSession {
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

    suspend fun loadNotices(
        appVersion: String = BuildConfig.VERSION_NAME,
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
            loadFreshNotices(appVersion = appVersion, userId = userId)
        } else {
            awaitSharedRequest(requestKey) {
                noticeCache
                    ?.takeIf { isCacheValid(it.timestampMs, NOTICE_CACHE_TTL_MS) }
                    ?.value
                    ?: loadFreshNotices(appVersion = appVersion, userId = userId)
            }
        }
    }

    fun pickPendingNotice(notices: List<AppNotice>): AppNotice? {
        val dismissedIds = noticePrefs.getStringSet(KEY_DISMISSED_NOTICE_IDS, emptySet()).orEmpty()
        return notices.firstOrNull { notice ->
            notice.isActive && notice.id.isNotBlank() && !dismissedIds.contains(notice.id)
        }
    }

    fun unreadActiveNoticeIds(notices: List<AppNotice>): Set<String> {
        val dismissedIds = noticePrefs.getStringSet(KEY_DISMISSED_NOTICE_IDS, emptySet()).orEmpty()
        return notices.asSequence()
            .filter { it.isActive && it.id.isNotBlank() && !dismissedIds.contains(it.id) }
            .map { it.id }
            .toSet()
    }

    fun markNoticeDismissed(noticeId: String) {
        val normalized = noticeId.trim()
        if (normalized.isBlank()) return
        val currentIds = noticePrefs.getStringSet(KEY_DISMISSED_NOTICE_IDS, emptySet()).orEmpty()
        if (currentIds.contains(normalized)) return
        noticePrefs.edit()
            .putStringSet(KEY_DISMISSED_NOTICE_IDS, currentIds + normalized)
            .apply()
    }

    private fun loadFreshNotices(appVersion: String, userId: String): List<AppNotice> {
        val url = Uri.parse(APP_CENTER_API_URL)
            .buildUpon()
            .appendQueryParameter("action", "notices")
            .appendQueryParameter("app_version", appVersion.trim().ifBlank { BuildConfig.VERSION_NAME })
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

    private fun ensureHeartbeatDeviceId(): String {
        heartbeatPrefs.getString(HEARTBEAT_DEVICE_ID_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        val generated = "android-${UUID.randomUUID()}"
        heartbeatPrefs.edit().putString(HEARTBEAT_DEVICE_ID_KEY, generated).apply()
        return generated
    }

    private fun normalizeLoginFailureMessage(rawMessage: String): String {
        val message = rawMessage.trim()
        return when {
            message.isBlank() -> ""
            message.contains("获取用户信息失败") -> "用户名不存在或密码错误"
            else -> message
        }
    }

    suspend fun loadRegisterPage(): RegisterPage {
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

    suspend fun loadRegisterCaptcha(captchaUrl: String): ByteArray {
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

    suspend fun loadFindPasswordPage(): FindPasswordPage {
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

    suspend fun loadFindPasswordCaptcha(captchaUrl: String): ByteArray {
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

    suspend fun findPassword(editor: FindPasswordEditor): String {
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
            .header("Referer", "$baseUrl/index.php/user/head.html")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("涓婁紶澶村儚澶辫触锛欻TTP ${response.code}")
            }

            val responseBody = response.body?.string().orEmpty()
            val authResponse = runCatching { gson.fromJson(responseBody, AuthResponse::class.java) }.getOrNull()
            if (authResponse != null && authResponse.msg.isNotBlank()) {
                if (authResponse.code == 1) {
                    return authResponse.msg
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

    suspend fun sendRegisterCode(channel: String, contact: String): String {
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

    suspend fun register(editor: RegisterEditor): String {
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

    suspend fun logout() {
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
        runCatching { loadUserProfileFromAppCenter() }
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
        runCatching { loadMembershipPageFromAppCenter() }
            .getOrNull()
            ?.let { return it }

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
            plans = document.select("[data-id][data-name][data-points][data-long]")
                .map { element ->
                    MembershipPlan(
                        groupId = element.attr("data-id").trim(),
                        groupName = decodeSiteText(element.attr("data-name").trim()),
                        duration = decodeSiteText(element.attr("data-long").trim()),
                        points = decodeSiteText(element.attr("data-points").trim())
                    )
                }
                .distinctBy { "${it.groupId}:${it.duration}" }
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

    private fun detailMatchesPreview(detail: VodItem, preview: VodItem): Boolean {
        val detailTitle = canonicalTitle(detail.vodName)
        val previewTitle = canonicalTitle(preview.vodName)
        if (detailTitle.isBlank() || previewTitle.isBlank()) return false
        if (detailTitle != previewTitle) return false
        val previewYear = preview.vodYear.orEmpty().trim()
        val detailYear = detail.vodYear.orEmpty().trim()
        return previewYear.isBlank() || detailYear.isBlank() || previewYear == detailYear
    }

    private fun searchCandidateScore(candidate: VodItem, preview: VodItem): Int {
        var score = 0
        if (canonicalTitle(candidate.vodName) == canonicalTitle(preview.vodName)) score += 100
        if (candidate.vodPic.orEmpty() == preview.vodPic.orEmpty()) score += 25
        if (candidate.vodYear.orEmpty() == preview.vodYear.orEmpty() && preview.vodYear.orEmpty().isNotBlank()) {
            score += 10
        }
        return score
    }

    private fun mergePreviewIntoDetail(preview: VodItem, detail: VodItem): VodItem =
        detail.copy(
            vodId = preview.vodId.ifBlank { detail.vodId },
            vodName = preview.vodName.ifBlank { detail.vodName },
            vodSub = preview.vodSub ?: detail.vodSub,
            vodPic = preview.vodPic ?: detail.vodPic,
            vodRemarks = preview.vodRemarks ?: detail.vodRemarks,
            vodBlurb = preview.vodBlurb ?: detail.vodBlurb,
            vodContent = preview.vodContent ?: detail.vodContent,
            vodYear = preview.vodYear ?: detail.vodYear,
            vodArea = preview.vodArea ?: detail.vodArea,
            vodLang = preview.vodLang ?: detail.vodLang,
            typeName = preview.typeName ?: detail.typeName,
            siteVodId = preview.siteVodId.ifBlank { detail.siteVodId },
            detailUrl = preview.detailUrl.ifBlank { detail.detailUrl }
        )

    private fun canonicalTitle(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\p{Punct}·：:～~]+"), "")
            .trim()

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
            .map { it.trim() }
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

    private fun AppleCmsResponse.toPagedVodItems(): PagedVodItems =
        PagedVodItems(
            items = list.distinctBy { it.vodId },
            page = safePage,
            pageCount = safePageCount,
            totalItems = safeTotal,
            limit = safeLimit,
            hasNextPage = hasNextPage
        )

    private fun VideoApiEnvelope<VideoApiPagedRows<VodItem>>.toPagedVodItems(): PagedVodItems {
        val payload = data ?: return PagedVodItems(
            items = emptyList(),
            page = 1,
            pageCount = 1,
            totalItems = 0,
            limit = 0,
            hasNextPage = false
        )
        val safePage = payload.page.coerceAtLeast(1)
        val safePageCount = payload.totalPages.coerceAtLeast(safePage)
        val items = payload.rows.distinctBy { it.vodId }
        val safeLimit = payload.limit.coerceAtLeast(items.size)
        val safeTotal = payload.total.coerceAtLeast(items.size)
        return PagedVodItems(
            items = items,
            page = safePage,
            pageCount = safePageCount,
            totalItems = safeTotal,
            limit = safeLimit,
            hasNextPage = safePage < safePageCount
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

    private fun interleaveCategoryItems(responses: List<AppleCmsResponse>): List<VodItem> {
        val buckets = responses.map { response ->
            ArrayDeque(response.list.distinctBy { it.vodId })
        }
        val seenIds = LinkedHashSet<String>()
        val mergedItems = mutableListOf<VodItem>()

        while (buckets.any { it.isNotEmpty() }) {
            buckets.forEach { bucket ->
                while (bucket.isNotEmpty()) {
                    val item = bucket.removeFirst()
                    if (seenIds.add(item.vodId)) {
                        mergedItems += item
                        break
                    }
                }
            }
        }

        return mergedItems
    }

    private fun buildMergedCategoryPage(
        pages: List<PagedVodItems>,
        page: Int
    ): PagedVodItems {
        val mergedItems = interleaveCategoryItems(pages.map { pagePayload ->
            AppleCmsResponse(
                page = pagePayload.page,
                pageCount = pagePayload.pageCount,
                limit = pagePayload.limit,
                total = pagePayload.totalItems,
                list = pagePayload.items
            )
        })
        return PagedVodItems(
            items = mergedItems,
            page = page,
            pageCount = pages.maxOfOrNull { it.pageCount } ?: page,
            totalItems = pages.sumOf { it.totalItems },
            limit = pages.sumOf { it.limit }.takeIf { it > 0 } ?: mergedItems.size,
            hasNextPage = pages.any { it.hasNextPage }
        )
    }

    private fun cachePagePayload(cacheKey: String, payload: PagedVodItems) {
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
        val raw = pageCachePrefs.getString(cacheKey, null).orEmpty()
        if (raw.isBlank()) return null
        val persisted = parsePersistedPageCache(raw) ?: return null
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
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val vodId = item.vodId.trim()
            if (vodId.isNotBlank()) {
                previewItemCache[vodId] = CachedValue(item, now)
            }
        }
    }

    private fun findPreviewItem(vodId: String): VodItem? =
        previewItemCache[vodId]
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

    private fun isBrowsableCategory(category: AppleCmsCategory): Boolean {
        val parentId = category.parentId.orEmpty().trim()
        return category.typeId.isNotBlank() &&
            category.typeName.isNotBlank() &&
            (parentId.isBlank() || parentId == "0" || parentId == category.typeId)
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

    private fun extractCategoryPageCount(document: Document, pagination: Element?): Int {
        val mobileCount = pagination?.selectFirst("li.active .num")
            ?.text()
            .orEmpty()
            .substringAfter('/')
            .substringBefore(' ')
            .toIntOrNull()
        if (mobileCount != null && mobileCount > 0) return mobileCount

        val numericLinks = pagination?.select("a[href]")
            .orEmpty()
            .mapNotNull { anchor ->
                anchor.text().trim().toIntOrNull()
            }
        val maxNumericLink = numericLinks.maxOrNull()
        if (maxNumericLink != null && maxNumericLink > 0) return maxNumericLink

        val titleCount = Regex("""第(\d+)页""")
            .find(document.title())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return titleCount ?: 1
    }

    private fun extractCategoryTotal(document: Document): Int {
        val scriptTotal = document.select("script")
            .asSequence()
            .mapNotNull { script ->
                Regex("""ewave-total"\)\.text\((\d+)\)""")
                    .find(script.html())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .firstOrNull()
        if (scriptTotal != null && scriptTotal > 0) return scriptTotal

        val headerTotal = document.selectFirst(".vod-list h2 .small")
            ?.text()
            .orEmpty()
            .let { text ->
                Regex("""共\s*(\d+)\s*个视频""")
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
        return headerTotal ?: 0
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

    private fun readLabeledValue(document: Document, label: String): Pair<String, String>? {
        val value = readLabeledText(document, label)
        return value.takeIf { it.isNotBlank() }?.let { label to it }
    }

    private fun readLabeledText(document: Document, label: String): String {
        document.select("p, li, div, span").forEach { element ->
            val text = element.text()
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            val match = Regex("$label[：: ]+(.+)").find(text)
            if (match != null) {
                return match.groupValues.getOrNull(1).orEmpty().trim()
            }
        }
        return ""
    }

    private fun parseCategories(homeDocument: Document, mapDocument: Document?): List<AppleCmsCategory> {
        val homeCategories = homeDocument.select(".clist-left-tabs-title[href*=/vodtype/]")
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                if (href.isBlank()) null else AppleCmsCategory(typeId = href, typeName = anchor.text())
            }
            .distinctBy { it.typeId }

        if (homeCategories.isNotEmpty()) return homeCategories

        return mapDocument?.select(".vod-list h2 a[href*=/vodtype/]")
            .orEmpty()
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                if (href.isBlank()) null else AppleCmsCategory(typeId = href, typeName = anchor.text())
            }
            .distinctBy { it.typeId }
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

    private fun extractDetailMeta(span: Element): Pair<String, String>? {
        val normalizedOwnText = span.ownText().replace(Regex("\\s+"), " ").trim()
        val normalizedText = span.text().replace(Regex("\\s+"), " ").trim()
        val label = normalizedOwnText
            .substringBefore('：')
            .substringBefore(':')
            .trim()
            .ifBlank {
                normalizedText.substringBefore('：').substringBefore(':').trim()
            }
        if (label.isBlank()) return null

        val childValues = span.children()
            .mapNotNull { child ->
                child.text()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
            .distinct()

        if (childValues.isNotEmpty()) {
            return label to childValues.joinToString(" / ")
        }

        val parts = normalizedText.split(Regex("[:\\uFF1A]"), limit = 2)
        if (parts.size != 2) return null
        return label to parts[1].trim()
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

    private fun decodePlayerUrl(rawUrl: String, encrypt: Int): String {
        val cleaned = rawUrl.trim().replace("\\/", "/")
        if (cleaned.isBlank()) return ""

        return when (encrypt) {
            1 -> Uri.decode(cleaned)
            2 -> runCatching {
                val decodedBase64 = String(Base64.decode(cleaned, Base64.DEFAULT))
                Uri.decode(decodedBase64)
            }.getOrElse { cleaned }
            else -> cleaned
        }
    }

    private fun extractPlayerConfig(html: String): Pair<String, Int>? {
        val block = Regex("""player_aaaa\s*=\s*(\{.*?\})</script>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (block.isBlank()) return null

        val rawUrl = Regex(""""url"\s*:\s*"([^"]*)"""")
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val encrypt = Regex(""""encrypt"\s*:\s*(\d+)""")
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

        return rawUrl to encrypt
    }

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

    private fun extractEmbeddedMediaUrls(html: String): List<String> {
        val patterns = listOf(
            Regex("""const\s+url\s*=\s*"([^"]+)""""),
            Regex("""var\s+url\s*=\s*"([^"]+)""""),
            Regex(""""url"\s*:\s*"([^"]+)""""),
            Regex("""src\s*:\s*"([^"]+\.m3u8[^"]*)""""),
            Regex("""loadSource\(\s*"([^"]+)"\s*\)"""),
            Regex("""video\.src\s*=\s*"([^"]+)""""),
            Regex("""<iframe[^>]+src=["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""["']((?:https?:)?//[^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']((?:https?:)?//[^"']+\.mp4[^"']*)["']"""),
            Regex("""["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']([^"']+\.mp4[^"']*)["']"""),
            Regex("""<source[^>]+src=["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
        )
        return buildList {
            patterns.forEach { regex ->
                regex.findAll(html).forEach { match ->
                    match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    private fun extractAssignedJsonObject(html: String, variableName: String): String {
        val markerIndex = html.indexOf("$variableName=")
        if (markerIndex < 0) return ""

        val startIndex = html.indexOf('{', markerIndex)
        if (startIndex < 0) return ""

        var depth = 0
        var inString = false
        var escaped = false
        for (index in startIndex until html.length) {
            val char = html[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return html.substring(startIndex, index + 1)
                    }
                }
            }
        }

        return ""
    }

    private fun extractVodId(detailHref: String): String =
        detailHref.trim()
            .removeSuffix("/")
            .substringAfterLast("/voddetail/", "")
            .substringBefore('/')

    private fun extractCategorySlug(typeId: String): String =
        typeId.trim()
            .removeSuffix("/")
            .substringAfterLast("/vodtype/", typeId.trim().removeSuffix("/"))
            .substringAfterLast('/')

    private fun normalizeCategory(category: AppleCmsCategory): AppleCmsCategory {
        val normalizedTypeId = extractCategorySlug(category.typeId)
            .ifBlank { category.typeId.trim().removeSuffix("/") }
        val rawParentId = category.parentId.orEmpty().trim()
        val normalizedParentId = when {
            rawParentId.isBlank() -> null
            rawParentId == "0" -> "0"
            else -> extractCategorySlug(rawParentId).ifBlank { rawParentId.removeSuffix("/") }
        }
        return category.copy(
            typeId = normalizedTypeId,
            typeName = category.typeName.trim(),
            parentId = normalizedParentId
        )
    }

    private fun extractVodIdFromUserUrl(pathOrUrl: String): String {
        val normalized = resolveUrl(pathOrUrl)
        return when {
            normalized.contains("/voddetail/") -> extractVodId(normalized)
            normalized.contains("/vodplay/") -> normalized
                .substringAfter("/vodplay/")
                .substringBefore('/')
                .substringBefore('-')
            else -> ""
        }
    }

    private fun resolveUrl(pathOrUrl: String): String {
        val value = pathOrUrl.trim()
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value
        }
        return normalizeUrl(value)
    }

    private fun normalizeAgainst(raw: String, base: String): String {
        val value = raw.trim().replace("\\/", "/")
        if (value.isBlank()) return ""
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:$value"
        return runCatching { URI(base).resolve(value).toString() }
            .getOrElse { resolveUrl(value) }
    }

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:$value"
        return if (value.startsWith("/")) {
            baseUrl + value
        } else {
            Uri.parse("$baseUrl/").buildUpon().appendEncodedPath(value).build().toString()
        }
    }

    private fun appendTimestamp(url: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}t=${System.currentTimeMillis()}"
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".m3u8") ||
            lower.endsWith(".mp4") ||
            lower.contains(".m3u8?") ||
            lower.contains("/index.m3u8")
    }

    private fun List<okhttp3.Cookie>.firstCookieValue(name: String): String =
        firstOrNull { it.name == name }
            ?.value
            .orEmpty()
            .takeUnless { it == "deleted" }
            .orEmpty()

    private fun normalizePortraitUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank() || value == "deleted") return ""
        return appendTimestamp(normalizeUrl(value))
    }

    private fun createApi(baseUrl: String): AppleCmsApi =
        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AppleCmsApi::class.java)

    private suspend fun <T> requestApi(block: suspend AppleCmsApi.() -> T): T {
        val resolvedBackupApi = backupApi ?: return primaryApi.block()
        val now = System.currentTimeMillis()
        if (now < preferBackupApiUntilMs) {
            return try {
                resolvedBackupApi.block()
            } catch (backupError: Exception) {
                if (backupError is CancellationException) throw backupError
                try {
                    primaryApi.block().also {
                        preferBackupApiUntilMs = 0L
                    }
                } catch (primaryError: Exception) {
                    if (primaryError is CancellationException) throw primaryError
                    primaryError.addSuppressed(backupError)
                    throw primaryError
                }
            }
        }

        try {
            return primaryApi.block().also {
                preferBackupApiUntilMs = 0L
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!shouldFailoverToBackup(error)) throw error
            preferBackupApiUntilMs = System.currentTimeMillis() + API_FAILOVER_COOLDOWN_MS
            return try {
                resolvedBackupApi.block()
            } catch (fallbackError: Exception) {
                if (fallbackError is CancellationException) throw fallbackError
                fallbackError.addSuppressed(error)
                throw fallbackError
            }
        }
    }

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
        val profileFields = buildList {
            userId.takeIf(String::isNotBlank)?.let { add("User ID" to it) }
            userName.takeIf(String::isNotBlank)?.let { add("Username" to it) }
            groupName.takeIf(String::isNotBlank)?.let { add("Group" to it) }
            points.takeIf(String::isNotBlank)?.let { add("Points" to it) }
            expiry.takeIf(String::isNotBlank)?.let { add("Expiry" to it) }
            email.takeIf(String::isNotBlank)?.let { add("Email" to it) }
            phone.takeIf(String::isNotBlank)?.let { add("Phone" to it) }
            qq.takeIf(String::isNotBlank)?.let { add("QQ" to it) }
        }

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

    private fun parseAppCenterMembershipPlan(element: JsonElement): MembershipPlan? {
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val groupId = obj.firstString("group_id", "id", "gid", "groupId")
        val groupName = decodeSiteText(obj.firstString("group_name", "name", "title", "label"))
        val duration = decodeSiteText(
            obj.firstString("duration", "long", "period", "days", "months", "month_label", "long_name")
        )
        val points = decodeSiteText(
            obj.firstString("points", "need_points", "cost_points", "price", "score")
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

    private fun extractLabeledValues(
        document: Document,
        labels: List<String>,
        stopPhrases: List<String> = emptyList()
    ): Map<String, String> {
        val bodyText = decodeSiteText(document.body().text())
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (bodyText.isBlank()) return emptyMap()

        data class LabelHit(val label: String, val start: Int, val valueStart: Int)

        val hits = labels.mapNotNull { label ->
            Regex("${Regex.escape(label)}(?:[:： ]+)?")
                .find(bodyText)
                ?.let { match -> LabelHit(label, match.range.first, match.range.last + 1) }
        }.sortedBy { it.start }

        if (hits.isEmpty()) return emptyMap()

        return buildMap {
            hits.forEachIndexed { index, hit ->
                val nextStart = hits.getOrNull(index + 1)?.start ?: bodyText.length
                val rawValue = bodyText.substring(hit.valueStart, nextStart)
                val cleaned = stopPhrases.fold(rawValue) { acc, stop -> acc.substringBefore(stop) }
                    .replace(Regex("^[:：\\s]+"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleaned.isNotBlank()) {
                    put(hit.label, cleaned)
                }
            }
        }
    }

    private fun normalizeRecordTitle(rawTitle: String, rowText: String): String {
        val decodedTitle = decodeSiteText(rawTitle)
            .replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
            .trim()
        if (decodedTitle.isNotBlank()) return decodedTitle

        return rowText
            .substringBefore("类型：")
            .substringBefore("积分：")
            .substringBefore("时间：")
            .substringBefore("[")
            .replace("继续观看", "")
            .replace("查看详情", "")
            .replace("删除", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildUserRecordSubtitle(rowText: String, title: String): String =
        rowText
            .removePrefix(title)
            .replace(title, "")
            .replace("继续观看", "")
            .replace("查看详情", "")
            .replace("删除", "")
            .replace("重播", "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun buildUserRecordSubtitleEnhanced(
        rowText: String,
        title: String,
        route: PlayRoute?
    ): String {
        val metaText = rowText
            .removePrefix(title)
            .replace(title, "")
            .replace(Regex("\\[\\d+-\\d+-\\d+]"), "")
            .replace("继续观看", "")
            .replace("查看详情", "")
            .replace("删除", "")
            .replace("重播", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return listOfNotNull(
            route?.let(::formatHistoryRouteLabel),
            metaText.takeIf { it.isNotBlank() }
        ).joinToString(" | ")
    }

    private fun formatHistoryRouteLabel(route: PlayRoute): String {
        val episodeNumber = route.nid.toIntOrNull()
        val sourceNumber = route.sid.toIntOrNull()
        return buildString {
            if (sourceNumber != null && sourceNumber > 0) {
                append("线路")
                append(sourceNumber)
            }
            if (episodeNumber != null && episodeNumber > 0) {
                if (isNotEmpty()) append(" · ")
                append("第")
                append(episodeNumber)
                append("集")
            }
        }.ifBlank {
            listOf(route.sid.takeIf { it.isNotBlank() }, route.nid.takeIf { it.isNotBlank() })
                .joinToString(" - ")
        }
    }

    private fun replaceHistorySourceLabel(subtitle: String, sourceName: String): String {
        if (subtitle.isBlank() || sourceName.isBlank()) return subtitle
        val replaced = subtitle.replaceFirst(Regex("^线路\\d+"), sourceName)
        return if (replaced == subtitle) {
            "$sourceName | $subtitle"
        } else {
            replaced
        }
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

    private fun decodeSiteText(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isBlank() || cleaned == "deleted") return ""

        val decoded = runCatching {
            URLDecoder.decode(cleaned, StandardCharsets.UTF_8.name())
        }.getOrDefault(cleaned)

        return decoded
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun JsonObject.extractNoticeItems(): List<JsonElement> {
        val candidates = listOfNotNull(
            getAsJsonObject("data")?.getAsJsonArray("items"),
            getAsJsonArray("items"),
            getAsJsonObject("data")?.getAsJsonArray("list"),
            getAsJsonArray("list"),
            getAsJsonObject("data")?.getAsJsonArray("rows"),
            getAsJsonArray("rows")
        )
        return candidates.firstOrNull()?.toList().orEmpty()
    }

    private fun parseNoticeItem(element: JsonElement): AppNotice? {
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val title = obj.firstString("title", "notice_title", "name", "subject")
        val rawContent = obj.firstString("content", "notice_content", "body", "description", "detail")
        val content = normalizeNoticeText(rawContent)
        val summary = normalizeNoticeText(
            obj.firstString("summary", "subtitle", "excerpt", "remark")
        )
        if (title.isBlank() && content.isBlank() && summary.isBlank()) return null

        val startAt = obj.firstString(
            "start_time",
            "start_at",
            "begin_time",
            "begin_at",
            "valid_from",
            "publish_at",
            "publish_time",
            "published_at"
        )
        val endAt = obj.firstString(
            "end_time",
            "end_at",
            "expire_time",
            "expire_at",
            "valid_to",
            "off_time",
            "offline_at"
        )
        val createdAt = obj.firstString("created_at", "add_time", "created", "created_time")
        val updatedAt = obj.firstString("updated_at", "update_time", "updated", "modified_at")
        val isPinned = obj.firstBoolean("is_top", "isTop", "top", "pinned", "is_pinned", "sticky") ?: false
        val isActive = resolveNoticeActive(obj, startAt = startAt, endAt = endAt)
        val rawId = obj.firstString("id", "notice_id", "announcement_id", "nid")
        val resolvedId = rawId.ifBlank {
            buildNoticeStableId(
                title = title,
                content = content,
                startAt = startAt,
                endAt = endAt,
                createdAt = createdAt
            )
        }

        return AppNotice(
            id = resolvedId,
            title = title.ifBlank { "公告" },
            htmlContent = rawContent.trim(),
            content = content,
            summary = summary,
            isPinned = isPinned,
            isActive = isActive,
            startAt = startAt,
            endAt = endAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun JsonObject.firstString(vararg names: String): String =
        names.asSequence()
            .mapNotNull { name ->
                get(name)
                    ?.takeIf { !it.isJsonNull }
                    ?.let { value ->
                        runCatching {
                            when {
                                value.asJsonPrimitive.isString -> value.asString
                                value.asJsonPrimitive.isNumber -> value.asNumber.toString()
                                value.asJsonPrimitive.isBoolean -> value.asBoolean.toString()
                                else -> value.toString()
                            }
                        }.getOrNull()
                    }
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            .firstOrNull()
            .orEmpty()

    private fun JsonObject.firstBoolean(vararg names: String): Boolean? =
        names.asSequence()
            .mapNotNull { name ->
                val value = get(name)?.takeIf { !it.isJsonNull } ?: return@mapNotNull null
                runCatching {
                    val primitive = value.asJsonPrimitive
                    when {
                        primitive.isBoolean -> primitive.asBoolean
                        primitive.isNumber -> primitive.asInt != 0
                        primitive.isString -> {
                            when (primitive.asString.trim().lowercase(Locale.ROOT)) {
                                "1", "true", "yes", "on", "enabled", "active" -> true
                                "0", "false", "no", "off", "disabled", "inactive" -> false
                                else -> null
                            }
                        }
                        else -> null
                    }
                }.getOrNull()
            }
            .firstOrNull()

    private fun JsonObject.firstInt(vararg names: String): Int? =
        names.asSequence()
            .mapNotNull { name ->
                val value = get(name)?.takeIf { !it.isJsonNull } ?: return@mapNotNull null
                runCatching {
                    val primitive = value.asJsonPrimitive
                    when {
                        primitive.isNumber -> primitive.asInt
                        primitive.isString -> primitive.asString.trim().toIntOrNull()
                        primitive.isBoolean -> if (primitive.asBoolean) 1 else 0
                        else -> null
                    }
                }.getOrNull()
            }
            .firstOrNull()

    private fun JsonObject.firstObject(vararg names: String): JsonObject? =
        names.asSequence()
            .mapNotNull { name ->
                get(name)
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
            }
            .firstOrNull()

    private fun JsonObject.firstArray(vararg names: String): List<JsonElement> =
        names.asSequence()
            .mapNotNull { name ->
                get(name)
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.toList()
            }
            .firstOrNull()
            .orEmpty()

    private fun resolveNoticeActive(obj: JsonObject, startAt: String, endAt: String): Boolean {
        val statusValue = obj.firstString("status", "state", "enabled", "publish_status")
            .trim()
            .lowercase(Locale.ROOT)
        if (statusValue in setOf("0", "false", "inactive", "disabled", "draft", "offline", "expired")) {
            return false
        }

        val now = System.currentTimeMillis()
        val startMs = parseNoticeTimeToMillis(startAt)
        if (startMs != null && now < startMs) return false
        val endMs = parseNoticeTimeToMillis(endAt)
        if (endMs != null && now > endMs) return false
        return true
    }

    private fun normalizeNoticeText(raw: String): String {
        if (raw.isBlank()) return ""
        val normalizedHtml = raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
        return HtmlCompat.fromHtml(normalizedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun buildNoticeStableId(
        title: String,
        content: String,
        startAt: String,
        endAt: String,
        createdAt: String
    ): String {
        val seed = listOf(title, content.take(120), startAt, endAt, createdAt).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }.take(24)
    }

    private fun parseNoticeTimeToMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isBlank()) return null
        value.toLongOrNull()?.let { numeric ->
            return if (value.length <= 10) numeric * 1000 else numeric
        }

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy.MM.dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        return patterns.asSequence()
            .mapNotNull { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.getDefault()).apply {
                        isLenient = false
                        if (pattern.contains("X") || pattern.endsWith("'Z'")) {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                    }.parse(value)?.time
                }.getOrNull()
            }
            .firstOrNull()
    }

    private val VodItem.siteLogId: String
        get() = siteVodId.ifBlank { vodId.takeIf { it.all(Char::isDigit) }.orEmpty() }

    companion object {
        private const val APP_CENTER_API_URL = "https://user.jlen.top/api.php"
        private const val KEY_DISMISSED_NOTICE_IDS = "dismissed_notice_ids"
        private const val HEARTBEAT_DEVICE_ID_KEY = "device_id"
        private const val HOME_CACHE_TTL_MS = 60_000L
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

        private fun isCacheValid(timestampMs: Long, ttlMs: Long): Boolean =
            isCacheValid(timestampMs, ttlMs, System.currentTimeMillis())

        private fun isCacheValid(timestampMs: Long, ttlMs: Long, now: Long): Boolean =
            now - timestampMs <= ttlMs

        private fun createClient(cookieJar: PersistentCookieJar): OkHttpClient {
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

data class HomePayload(
    val slides: List<VodItem>,
    val hot: List<VodItem>,
    val featured: List<VodItem>,
    val latest: List<VodItem>,
    val sections: List<HomeSection>,
    val categories: List<AppleCmsCategory>,
    val selectedCategory: AppleCmsCategory?,
    val categoryVideos: List<VodItem>,
    val latestPage: Int,
    val latestPageCount: Int,
    val latestTotal: Int,
    val latestHasNextPage: Boolean,
    val categoryPage: Int,
    val categoryPageCount: Int,
    val categoryTotal: Int,
    val categoryHasNextPage: Boolean
)

data class HomeSection(
    val title: String,
    val typeId: String,
    val items: List<VodItem>
)

data class PagedVodItems(
    val items: List<VodItem>,
    val page: Int,
    val pageCount: Int,
    val totalItems: Int,
    val limit: Int,
    val hasNextPage: Boolean
)

data class ResolvedPlayUrl(
    val url: String,
    val useWebPlayer: Boolean
)

private data class PlayRoute(
    val sid: String,
    val nid: String
)

private fun compareVersionNames(left: String, right: String): Int {
    val leftParts = left.removePrefix("v").split('.', '-', '_')
    val rightParts = right.removePrefix("v").split('.', '-', '_')
    val maxSize = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
        val rightPart = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }
    return 0
}
