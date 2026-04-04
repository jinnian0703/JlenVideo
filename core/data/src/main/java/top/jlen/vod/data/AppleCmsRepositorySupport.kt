package top.jlen.vod.data

import android.net.Uri
import androidx.core.text.HtmlCompat
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun decodeSiteText(raw: String): String {
    val cleaned = raw.trim()
    if (
        cleaned.isBlank() ||
            cleaned == "deleted" ||
            cleaned.equals("null", ignoreCase = true) ||
            cleaned.equals("undefined", ignoreCase = true) ||
            cleaned.equals("none", ignoreCase = true)
    ) return ""

    val decoded = runCatching {
        URLDecoder.decode(cleaned, StandardCharsets.UTF_8.name())
    }.getOrDefault(cleaned)

    return decoded
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun JsonObject.extractNoticeItems(): List<JsonElement> {
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

internal fun JsonObject.firstString(vararg names: String): String =
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
                ?.takeUnless {
                    it.equals("null", ignoreCase = true) ||
                        it.equals("undefined", ignoreCase = true) ||
                        it.equals("none", ignoreCase = true)
                }
                ?.takeIf(String::isNotBlank)
        }
        .firstOrNull()
        .orEmpty()

internal fun JsonObject.firstBoolean(vararg names: String): Boolean? =
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

internal fun JsonObject.firstInt(vararg names: String): Int? =
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

internal fun JsonObject.firstObject(vararg names: String): JsonObject? =
    names.asSequence()
        .mapNotNull { name ->
            get(name)
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
        }
        .firstOrNull()

internal fun JsonObject.firstObjectOrFirstArrayObject(vararg names: String): JsonObject? =
    names.asSequence()
        .mapNotNull { name ->
            val value = get(name) ?: return@mapNotNull null
            when {
                value.isJsonObject -> value.asJsonObject
                value.isJsonArray -> value.asJsonArray.firstOrNull { it.isJsonObject }?.asJsonObject
                else -> null
            }
        }
        .firstOrNull()

internal fun JsonObject.firstArray(vararg names: String): List<JsonElement> =
    names.asSequence()
        .mapNotNull { name ->
            get(name)
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.toList()
        }
        .firstOrNull()
        .orEmpty()

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

data class HomePayload(
    val slides: List<VodItem>,
    val hot: List<VodItem>,
    val featured: List<VodItem>,
    val latest: List<VodItem>,
    val sections: List<HomeSection>,
    val categories: List<AppleCmsCategory>,
    val selectedCategory: AppleCmsCategory?,
    val categoryVideos: List<VodItem>,
    val latestCursor: String,
    val latestHasMore: Boolean,
    val categoryCursor: String,
    val categoryHasMore: Boolean
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

internal data class PlayRoute(
    val sid: String,
    val nid: String
)

internal fun compareVersionNames(left: String, right: String): Int {
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
