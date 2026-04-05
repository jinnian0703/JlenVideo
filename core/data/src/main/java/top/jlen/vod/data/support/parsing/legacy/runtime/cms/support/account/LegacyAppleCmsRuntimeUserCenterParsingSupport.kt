package top.jlen.vod.data

import android.net.Uri
import com.google.gson.JsonElement
import java.text.SimpleDateFormat
import java.util.Locale

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
            .distinctBy { "${it.groupId}:${it.groupName}:${it.duration}:${it.points}" },
        signInInfo = MembershipSignInInfo(
            enabled = base.signInInfo.enabled || fallback.signInInfo.enabled,
            signedToday = base.signInInfo.signedToday || fallback.signInInfo.signedToday,
            rewardPoints = base.signInInfo.rewardPoints.ifBlank { fallback.signInInfo.rewardPoints },
            rewardMinPoints = base.signInInfo.rewardMinPoints.ifBlank { fallback.signInInfo.rewardMinPoints },
            rewardMaxPoints = base.signInInfo.rewardMaxPoints.ifBlank { fallback.signInInfo.rewardMaxPoints },
            signedAt = base.signInInfo.signedAt.ifBlank { fallback.signInInfo.signedAt }
        ),
        pointLogs = if (base.pointLogs.isNotEmpty()) base.pointLogs else fallback.pointLogs
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
