package top.jlen.vod.data

import android.net.Uri
import android.util.Base64
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import okhttp3.Cookie
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal fun parseUserCenterVod(
    row: JsonObject,
    gson: Gson,
    baseUrl: String
): VodItem? {
    val vodObject = row.firstObject("vod", "video", "item") ?: row
    val rawItem = runCatching { gson.fromJson(vodObject, VodItem::class.java) }.getOrNull() ?: return null
    val resolvedVodId = rawItem.vodId.ifBlank { vodObject.firstString("vod_id", "id") }
    val resolvedTypeName = rawItem.typeName.orEmpty().ifBlank {
        vodObject.firstObject("type")?.firstString("type_name").orEmpty()
    }
    if (resolvedVodId.isBlank() && rawItem.vodName.isBlank() && vodObject === row) {
        return null
    }
    return rawItem.copy(
        vodId = resolvedVodId,
        vodName = decodeSiteText(rawItem.vodName.ifBlank { vodObject.firstString("vod_name", "name", "title") }),
        vodPic = rawItem.vodPic?.let { normalizeAgainst(it, "$baseUrl/", baseUrl) },
        typeName = resolvedTypeName,
        siteVodId = rawItem.siteVodId.ifBlank { resolvedVodId },
        detailUrl = buildVodDetailUrl(rawItem.copy(vodId = resolvedVodId), baseUrl)
    )
}

internal fun extractVideoApiMessage(json: JsonObject, fallbackMessage: String): String {
    val code = json.firstInt("code", "status")
    val message = json.firstString("msg", "message")
    if (code != null && code !in setOf(1, 200)) {
        throw IOException(message.ifBlank { fallbackMessage })
    }
    return message.ifBlank { fallbackMessage }
}

internal fun normalizeLoginFailureMessage(rawMessage: String): String {
    val message = rawMessage.trim()
    return when {
        message.isBlank() -> ""
        message.contains("鑾峰彇鐢ㄦ埛淇℃伅澶辫触") -> "鐢ㄦ埛鍚嶄笉瀛樺湪鎴栧瘑鐮侀敊璇?"
        else -> message
    }
}

internal fun parsePortraitUploadResult(json: JsonObject): String {
    val code = json.firstInt("code", "status")
    val message = json.firstString("msg", "message")
    if (code == 401 || message.equals("login required", ignoreCase = true) || isLoginMessage(message)) {
        throw IOException("璇峰厛鐧诲綍")
    }
    if (code != null && code !in setOf(1, 200)) {
        throw IOException(message.ifBlank { "澶村儚涓婁紶澶辫触" })
    }
    val payload = unwrapApiPayload(json)
    val portrait = payload?.firstString("user_portrait_with_version", "user_portrait", "portrait", "avatar")
        .orEmpty()
    return if (portrait.isNotBlank() || message.isBlank() || message.equals("ok", ignoreCase = true)) {
        "澶村儚鏇存柊鎴愬姛"
    } else {
        message
    }
}

internal fun parseVideoApiResponseBody(body: String): JsonObject {
    val trimmed = body.trim()
    runCatching {
        return JsonParser.parseString(trimmed).asJsonObject
    }
    if (trimmed.equals("closed", ignoreCase = true)) {
        return JsonObject().apply {
            addProperty("code", 0)
            addProperty("msg", "api closed")
            add("data", com.google.gson.JsonNull.INSTANCE)
        }
    }
    if (
        trimmed.contains("login required", ignoreCase = true) ||
            trimmed.contains("/index.php/user/login", ignoreCase = true) ||
            trimmed.contains("user/login", ignoreCase = true)
    ) {
        return JsonObject().apply {
            addProperty("code", 401)
            addProperty("msg", "login required")
            add("data", com.google.gson.JsonNull.INSTANCE)
        }
    }
    throw IOException("视频 API 响应格式异常")
}

internal fun JsonObject.primitiveString(name: String): String =
    get(name)
        ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.let { primitive ->
            runCatching {
                val value = primitive.asJsonPrimitive
                when {
                    value.isString -> value.asString
                    value.isNumber -> value.asNumber.toString()
                    value.isBoolean -> value.asBoolean.toString()
                    else -> ""
                }
            }.getOrNull()
        }
        ?.trim()
        .orEmpty()

internal fun resolveNoticeActive(obj: JsonObject, startAt: String, endAt: String): Boolean {
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

internal fun resolveNoticeAlwaysShowDialog(obj: JsonObject): Boolean {
    obj.firstBoolean(
        "always_show_dialog",
        "always_show",
        "always_popup",
        "always_alert",
        "always_prompt",
        "repeat_prompt",
        "repeat_popup",
        "persistent_prompt",
        "is_repeat_prompt",
        "is_repeat_popup"
    )?.let { return it }

    obj.firstBoolean(
        "show_once",
        "popup_once",
        "alert_once",
        "prompt_once",
        "only_once",
        "dismiss_forever"
    )?.let { return !it }

    return when (
        obj.firstString(
            "dialog_mode",
            "popup_mode",
            "alert_mode",
            "prompt_mode",
            "notice_mode",
            "display_mode",
            "remind_mode",
            "show_mode"
        ).trim().lowercase(Locale.ROOT)
    ) {
        "always", "repeat", "persistent", "loop", "sticky", "forever", "every_time" -> true
        "once", "single", "one_time", "show_once", "popup_once" -> false
        else -> false
    }
}

internal fun normalizeNoticeText(raw: String): String {
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

internal fun buildNoticeStableId(
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

internal fun parseNoticeTimeToMillis(raw: String): Long? {
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

internal fun extractVodId(detailHref: String): String =
    detailHref.trim()
        .removeSuffix("/")
        .substringAfterLast("/voddetail/", "")
        .substringBefore('/')

internal fun extractCategorySlug(typeId: String): String =
    typeId.trim()
        .removeSuffix("/")
        .substringAfterLast("/vodtype/", typeId.trim().removeSuffix("/"))
        .substringAfterLast('/')

internal fun normalizeCategory(category: AppleCmsCategory): AppleCmsCategory {
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

internal fun formatMembershipExpiry(raw: String): String {
    val value = raw.trim()
    if (value.isBlank() || value == "0") return ""

    value.toLongOrNull()?.let { numeric ->
        if (numeric <= 0L) return ""
        val millis = if (value.length <= 10) numeric * 1000 else numeric
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(millis)
    }

    return decodeSiteText(value)
}

internal fun normalizeRecordTitle(rawTitle: String, rowText: String): String {
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

internal fun buildUserRecordSubtitle(rowText: String, title: String): String =
    rowText
        .removePrefix(title)
        .replace(title, "")
        .replace("继续观看", "")
        .replace("查看详情", "")
        .replace("删除", "")
        .replace("重播", "")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun buildUserRecordSubtitleEnhanced(
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

internal fun formatHistoryRouteLabel(route: PlayRoute): String {
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

internal fun replaceHistorySourceLabel(subtitle: String, sourceName: String): String {
    if (subtitle.isBlank() || sourceName.isBlank()) return subtitle
    val replaced = subtitle.replaceFirst(Regex("^线路\\d+"), sourceName)
    return if (replaced == subtitle) {
        "$sourceName | $subtitle"
    } else {
        replaced
    }
}

internal suspend fun collectAllUserCenterItems(
    loader: suspend (String?) -> UserCenterPage
): List<UserCenterItem> {
    val items = mutableListOf<UserCenterItem>()
    var nextPageToken: String? = null
    do {
        val page = loader(nextPageToken)
        items += page.items
        nextPageToken = page.nextPageUrl
    } while (!nextPageToken.isNullOrBlank())
    return items
}

internal fun parseUserCenterApiPage(pageUrl: String?, prefix: String): Int {
    val raw = pageUrl.orEmpty().trim()
    if (raw.startsWith("$prefix:")) {
        return raw.substringAfter(':').toIntOrNull()?.coerceAtLeast(1) ?: 1
    }
    return Uri.parse(raw.ifBlank { "https://localhost/" })
        .getQueryParameter("page")
        ?.toIntOrNull()
        ?.coerceAtLeast(1)
        ?: 1
}

internal fun buildUserCenterNextPage(prefix: String, page: Int, totalPages: Int): String? =
    if (page < totalPages) "$prefix:${page + 1}" else null

internal fun mergeMembershipPages(base: MembershipPage, fallback: MembershipPage?): MembershipPage {
    if (fallback == null) return base
    return MembershipPage(
        info = MembershipInfo(
            groupName = base.info.groupName.ifBlank { fallback.info.groupName },
            points = base.info.points.ifBlank { fallback.info.points },
            expiry = base.info.expiry.ifBlank { fallback.info.expiry }
        ),
        plans = (base.plans + fallback.plans)
            .filter { plan ->
                plan.groupId.isNotBlank() ||
                    plan.groupName.isNotBlank() ||
                    plan.duration.isNotBlank() ||
                    plan.points.isNotBlank()
            }
            .distinctBy { "${it.groupId}:${it.groupName}:${it.duration}:${it.points}" }
    )
}

internal fun parseAppCenterMembershipPlan(element: JsonElement): MembershipPlan? {
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

internal fun AppleCmsResponse.toPagedVodItems(): PagedVodItems =
    PagedVodItems(
        items = list.distinctBy { it.vodId },
        page = safePage,
        pageCount = safePageCount,
        totalItems = safeTotal,
        limit = safeLimit,
        hasNextPage = hasNextPage
    )

internal fun VideoApiEnvelope<VideoApiPagedRows<VodItem>>.toPagedVodItems(): PagedVodItems {
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

internal fun VideoApiEnvelope<VideoApiPagedRows<VodItem>>.toCursorPagedVodItems(): CursorPagedVodItems {
    val payload = data ?: return CursorPagedVodItems()
    val items = payload.rows.distinctBy { it.vodId }
    val nextCursor = payload.nextCursor.orEmpty().trim()
    return CursorPagedVodItems(
        items = items,
        limit = payload.limit.coerceAtLeast(items.size),
        nextCursor = nextCursor,
        hasMore = payload.hasMore?.let { it != 0 } ?: nextCursor.isNotBlank()
    )
}

internal fun interleaveCategoryItems(responses: List<AppleCmsResponse>): List<VodItem> {
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

internal fun buildMergedCategoryPage(
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

internal fun extractCategoryPageCount(document: Document, pagination: Element?): Int {
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

internal fun extractCategoryTotal(document: Document): Int {
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

internal fun isBrowsableCategory(category: AppleCmsCategory): Boolean {
    val parentId = category.parentId.orEmpty().trim()
    return category.typeId.isNotBlank() &&
        category.typeName.isNotBlank() &&
        (parentId.isBlank() || parentId == "0" || parentId == category.typeId)
}

internal fun parseCategories(homeDocument: Document, mapDocument: Document?): List<AppleCmsCategory> {
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

internal fun readLabeledValue(document: Document, label: String): Pair<String, String>? {
    val value = readLabeledText(document, label)
    return value.takeIf { it.isNotBlank() }?.let { label to it }
}

internal fun readLabeledText(document: Document, label: String): String {
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

internal fun extractDetailMeta(span: Element): Pair<String, String>? {
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

private val USER_CENTER_MEMBERSHIP_DURATIONS = listOf("day", "week", "month", "year")

internal fun parseMembershipInfoFromUserCenter(root: JsonObject): MembershipInfo? {
    val payload = unwrapUserCenterPayload(root)
    val userObject = payload.firstObject("user", "member", "info", "member_info", "membership_info") ?: payload
    val membershipObject = payload.firstObject(
        "membership",
        "member_info",
        "membership_info",
        "vip",
        "group",
        "current_group"
    ) ?: userObject

    val groupName = decodeSiteText(
        payload.firstString(
            "group_name",
            "member_name",
            "vip_name",
            "current_group_name"
        ).ifBlank {
            membershipObject.firstString(
                "group_name",
                "member_name",
                "vip_name",
                "current_group_name",
                "group"
            )
        }.ifBlank {
            userObject.firstString("group_name", "member_name", "vip_name", "group")
        }
    )
    val points = decodeSiteText(
        payload.firstString(
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
        }.ifBlank {
            userObject.firstString("user_points", "points", "score", "integral")
        }
    )
    val expiry = resolveMembershipExpiryText(payload, membershipObject, userObject)

    if (groupName.isBlank() && points.isBlank() && expiry.isBlank()) {
        return null
    }

    return MembershipInfo(
        groupName = groupName,
        points = points,
        expiry = expiry
    )
}

internal fun parseMembershipDurationFromText(text: String): String {
    val normalized = text.lowercase(Locale.ROOT)
    return when {
        "day" in normalized || "包天" in text -> "day"
        "week" in normalized || "包周" in text -> "week"
        "month" in normalized || "包月" in text -> "month"
        "year" in normalized || "包年" in text -> "year"
        else -> ""
    }
}

internal fun parseMembershipPointsFromText(text: String): String =
    Regex("(\\d{1,8})\\s*(?:积分|points|score)", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()

internal fun extractLooseMembershipValue(bodyText: String, labels: List<String>): String {
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

internal fun parseMembershipPlansFromUserCenter(root: JsonObject): List<MembershipPlan> {
    val payload = unwrapUserCenterPayload(root)
    val groupElements = buildList {
        addAll(payload.firstArray("groups", "group_list", "plans", "list", "items", "upgrade_plans", "membership_plans"))
        addAll(root.firstArray("groups", "group_list", "plans", "list", "items", "upgrade_plans", "membership_plans"))
    }

    return groupElements
        .flatMap(::parseUserCenterMembershipPlans)
        .distinctBy { "${it.groupId}:${it.duration}:${it.points}" }
}

internal fun parseUserCenterMembershipPlans(element: JsonElement): List<MembershipPlan> {
    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
    val groupId = obj.firstString("group_id", "id", "gid", "groupId")
    val groupName = decodeSiteText(obj.firstString("group_name", "name", "title", "label"))

    val plans = USER_CENTER_MEMBERSHIP_DURATIONS.mapNotNull { duration ->
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

internal fun extractMembershipPlanValue(obj: JsonObject, duration: String): String {
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

internal fun unwrapUserCenterPayload(root: JsonObject): JsonObject =
    root.firstObject("data", "info") ?: root

internal fun unwrapApiPayload(root: JsonObject): JsonObject? =
    root.firstObject("data", "info") ?: root.takeIf { it.entrySet().isNotEmpty() }

internal fun extractUserApiPayload(root: JsonObject): JsonObject? {
    val payload = unwrapApiPayload(root) ?: return null
    return payload.firstObject("user", "profile", "account", "member", "me", "info") ?: payload
}

internal fun resolveMembershipExpiryText(
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

internal fun buildProfileFields(
    userId: String = "",
    userName: String = "",
    groupName: String = "",
    points: String = "",
    expiry: String = "",
    email: String = "",
    phone: String = "",
    qq: String = ""
): List<Pair<String, String>> = buildList {
    userId.takeIf(String::isNotBlank)?.let { add("用户 ID" to it) }
    userName.takeIf(String::isNotBlank)?.let { add("用户名" to it) }
    groupName.takeIf(String::isNotBlank)?.let { add("会员组" to it) }
    points.takeIf(String::isNotBlank)?.let { add("积分" to it) }
    expiry.takeIf(String::isNotBlank)?.let { add("到期时间" to it) }
    email.takeIf(String::isNotBlank)?.let { add("邮箱" to it) }
    phone.takeIf(String::isNotBlank)?.let { add("手机号" to it) }
    qq.takeIf(String::isNotBlank)?.let { add("QQ号" to it) }
}

internal fun isUserLoginResponse(response: okhttp3.Response, body: String): Boolean {
    val resolvedPath = response.request.url.encodedPath
    return resolvedPath.contains("/index.php/user/login") ||
        body.contains("/index.php/user/login", ignoreCase = true) ||
        body.contains("login required", ignoreCase = true) ||
        isLoginMessage(body)
}

internal fun isLoginMessage(message: String): Boolean {
    val normalized = message.trim()
    return normalized.contains("请先登录") ||
        normalized.contains("未登录") ||
        normalized.contains("登录失效") ||
        normalized.contains("login required", ignoreCase = true)
}

internal fun extractLabeledValues(
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

internal fun parseNoticeItem(element: JsonElement): AppNotice? {
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
    val alwaysShowDialog = resolveNoticeAlwaysShowDialog(obj)
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
        alwaysShowDialog = alwaysShowDialog,
        startAt = startAt,
        endAt = endAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

