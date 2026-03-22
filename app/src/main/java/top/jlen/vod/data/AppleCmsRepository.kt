package top.jlen.vod.data

import android.net.Uri
import android.util.Base64
import java.io.IOException
import java.net.URI
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
    private val client: OkHttpClient = createClient()
) {
    private val baseUrl = BuildConfig.APPLE_CMS_BASE_URL.trimEnd('/')
    private val api: AppleCmsApi = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AppleCmsApi::class.java)

    suspend fun loadHome(): HomePayload {
        val latestResponse = api.getLatest(page = 1)
        val latest = latestResponse.list.distinctBy { it.vodId }

        if (latest.isEmpty()) {
            throw IOException("首页内容解析失败")
        }

        val categories = latestResponse.categories
            .filter(::isBrowsableCategory)
            .distinctBy { it.typeId }

        return HomePayload(
            featured = latest.take(6),
            latest = latest,
            categories = categories,
            selectedCategory = categories.firstOrNull(),
            categoryVideos = latest,
            latestPage = latestResponse.safePage,
            latestPageCount = latestResponse.safePageCount,
            latestTotal = latestResponse.safeTotal,
            latestHasNextPage = latestResponse.hasNextPage
        )
    }

    suspend fun loadByCategory(typeId: String): List<VodItem> =
        loadCategoryPage(typeId = typeId, page = 1).items

    suspend fun loadLatestPage(page: Int): PagedVodItems =
        api.getLatest(page = page.coerceAtLeast(1)).toPagedVodItems()

    suspend fun loadCategoryPage(typeId: String, page: Int): PagedVodItems =
        api.getByType(typeId = typeId, page = page.coerceAtLeast(1)).toPagedVodItems()

    suspend fun search(keyword: String): List<VodItem> {
        return api.search(keyword = keyword.trim())
            .list
            .distinctBy { it.vodId }
            .take(60)
    }

    suspend fun loadDetail(vodId: String): VodItem? =
        parseDetail(fetchDocument("$baseUrl/voddetail/$vodId/"))

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

    private fun parseVodCards(document: Document): List<VodItem> =
        document.select("div.pic")
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

    private fun AppleCmsResponse.toPagedVodItems(): PagedVodItems =
        PagedVodItems(
            items = list.distinctBy { it.vodId },
            page = safePage,
            pageCount = safePageCount,
            totalItems = safeTotal,
            limit = safeLimit,
            hasNextPage = hasNextPage
        )

    private fun isBrowsableCategory(category: AppleCmsCategory): Boolean {
        val parentId = category.parentId.orEmpty()
        return category.typeId.isNotBlank() &&
            category.typeName.isNotBlank() &&
            parentId in setOf("1", "2", "3", "4")
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
            vodPlayUrl = playUrl
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
        vodPlayUrl: String = ""
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
            vodPlayUrl = vodPlayUrl
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

    companion object {
        private fun createClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))
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
