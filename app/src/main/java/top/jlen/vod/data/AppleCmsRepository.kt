package top.jlen.vod.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import top.jlen.vod.BuildConfig
import top.jlen.vod.ui.PLAYER_DESKTOP_UA

class AppleCmsRepository(
    context: Context,
    private val cookieJar: PersistentCookieJar = PersistentCookieJar(context),
    private val client: OkHttpClient = createClient(cookieJar)
) {
    private val allCategoryTypeIds = listOf("1", "2", "3", "4")
    private val defaultCategories = listOf(
        AppleCmsCategory(typeId = "1", typeName = "电影", parentId = "1"),
        AppleCmsCategory(typeId = "2", typeName = "连续剧", parentId = "2"),
        AppleCmsCategory(typeId = "3", typeName = "综艺", parentId = "3"),
        AppleCmsCategory(typeId = "4", typeName = "动漫", parentId = "4")
    )
    private val baseUrl = BuildConfig.APPLE_CMS_BASE_URL.trimEnd('/')
    private val gson = Gson()
    private val api: AppleCmsApi = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AppleCmsApi::class.java)

    suspend fun loadHome(): HomePayload {
        val latestPage = loadAllCategoryPage(page = 1)
        val latest = latestPage.items

        if (latest.isEmpty()) {
            throw IOException("首页内容解析失败")
        }

        val categories = defaultCategories
        val featured = runCatching { loadFeaturedFromHomePage() }
            .getOrDefault(emptyList())

        return HomePayload(
            featured = featured,
            latest = latest,
            categories = categories,
            selectedCategory = categories.firstOrNull(),
            categoryVideos = latest,
            latestPage = latestPage.page,
            latestPageCount = latestPage.pageCount,
            latestTotal = latestPage.totalItems,
            latestHasNextPage = latestPage.hasNextPage
        )
    }

    suspend fun loadByCategory(typeId: String): List<VodItem> =
        loadCategoryPage(typeId = typeId, page = 1).items

    suspend fun loadAllCategoryPage(page: Int): PagedVodItems {
        val safePage = page.coerceAtLeast(1)
        val responses = allCategoryTypeIds
            .map { typeId -> api.getByType(typeId = typeId, page = safePage) }

        val mergedItems = interleaveCategoryItems(responses)
        return PagedVodItems(
            items = mergedItems,
            page = safePage,
            pageCount = responses.maxOfOrNull { it.safePageCount } ?: safePage,
            totalItems = responses.sumOf { it.safeTotal },
            limit = responses.sumOf { it.safeLimit }.takeIf { it > 0 } ?: mergedItems.size,
            hasNextPage = responses.any { it.hasNextPage }
        )
    }

    suspend fun loadLatestPage(page: Int): PagedVodItems =
        api.getLatest(page = page.coerceAtLeast(1)).toPagedVodItems()

    suspend fun loadCategoryPage(typeId: String, page: Int): PagedVodItems =
        api.getByType(typeId = typeId, page = page.coerceAtLeast(1)).toPagedVodItems()

    suspend fun search(keyword: String): List<VodItem> {
        val encodedKeyword = Uri.encode(keyword.trim())
        val document = fetchDocument("$baseUrl/vodsearch/-------------/?wd=$encodedKeyword")
        return parseSearchResults(document)
            .distinctBy { it.vodId }
            .take(60)
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

            throw IOException(
                authResponse?.msg?.takeIf(String::isNotBlank)
                    ?: "登录失败，请检查账号或密码"
            )
        }
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
        val profileDocument = fetchUserDocument("/index.php/user/index.html")
        val editorDocument = fetchUserDocument("/index.php/user/info.html")
        return UserProfilePage(
            fields = parseUserProfileFields(profileDocument),
            editor = parseUserProfileEditor(editorDocument)
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

    suspend fun sendEmailBindCode(email: String): String {
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

    suspend fun loadDetail(vodId: String): VodItem? {
        val normalizedId = vodId.trim()
        if (normalizedId.isBlank()) return null

        if (normalizedId.all(Char::isDigit)) {
            val apiItem = runCatching {
                api.getDetail(vodId = normalizedId)
                    .list
                    .firstOrNull()
            }.getOrNull()
            if (apiItem != null) {
                return apiItem
            }
        }

        return parseDetail(fetchDocument("$baseUrl/voddetail/$normalizedId/"))
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

    private fun parseSearchResults(document: Document): List<VodItem> =
        document.select(".vod-list ul.row li")
            .mapNotNull { item ->
                val anchor = item.selectFirst(".pic a[href*=/voddetail/]") ?: return@mapNotNull null
                createVodItem(
                    detailHref = anchor.attr("href"),
                    title = item.selectFirst("h3 a")?.text().orEmpty().ifBlank { anchor.attr("title") },
                    imageUrl = item.selectFirst("[data-original]")?.attr("data-original")
                        ?: item.selectFirst("img")?.attr("src").orEmpty(),
                    remarks = item.selectFirst(".item-status")?.text().orEmpty()
                )
            }
            .distinctBy { it.vodId }

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

        val sourceCache = mutableMapOf<String, List<PlaySource>>()

        return items.map { item ->
            val resolvedVodId = item.vodId.ifBlank {
                extractVodIdFromUserUrl(item.playUrl.ifBlank { item.actionUrl })
            }
            if (resolvedVodId.isBlank() || item.sourceIndex < 0) {
                return@map item.copy(vodId = resolvedVodId.ifBlank { item.vodId })
            }

            val sources = sourceCache.getOrPut(resolvedVodId) {
                runCatching {
                    loadDetail(resolvedVodId)?.let(::parseSources).orEmpty()
                }.getOrDefault(emptyList())
            }
            val sourceName = sources.getOrNull(item.sourceIndex)?.name.orEmpty()

            item.copy(
                vodId = resolvedVodId,
                sourceName = sourceName,
                subtitle = replaceHistorySourceLabel(item.subtitle, sourceName)
            )
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

    private fun isBrowsableCategory(category: AppleCmsCategory): Boolean {
        val parentId = category.parentId.orEmpty()
        return category.typeId.isNotBlank() &&
            category.typeName.isNotBlank() &&
            parentId in allCategoryTypeIds
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
            val text = span.text().replace(Regex("\\s+"), " ").trim()
            val parts = text.split(Regex("[:\\uFF1A]"), limit = 2)
            if (parts.size == 2) {
                metaMap[parts[0].trim()] = parts[1].trim()
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
        referer: String = "$baseUrl/"
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PLAYER_DESKTOP_UA)
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

    private fun buildCategoryPageUrl(categorySlug: String, page: Int): String =
        "$baseUrl/vodshow/$categorySlug--------${page.coerceAtLeast(1)}---/"

    private fun hasCategoryNextPage(document: Document, categorySlug: String, page: Int): Boolean {
        val nextPageUrl = "/vodshow/$categorySlug--------${page + 1}---/"
        return document.select("a[href]").any { anchor ->
            val href = anchor.attr("href")
            href.contains(nextPageUrl) || anchor.text().contains("下一页")
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
        return normalizeUrl(value)
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

    private fun extractLabeledValues(
        document: Document,
        labels: List<String>,
        stopPhrases: List<String> = emptyList()
    ): Map<String, String> {
        val bodyText = decodeSiteText(document.body()?.text().orEmpty())
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

    private val VodItem.siteLogId: String
        get() = siteVodId.ifBlank { vodId.takeIf { it.all(Char::isDigit) }.orEmpty() }

    companion object {
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
    val featured: List<VodItem>,
    val latest: List<VodItem>,
    val categories: List<AppleCmsCategory>,
    val selectedCategory: AppleCmsCategory?,
    val categoryVideos: List<VodItem>,
    val latestPage: Int,
    val latestPageCount: Int,
    val latestTotal: Int,
    val latestHasNextPage: Boolean
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
