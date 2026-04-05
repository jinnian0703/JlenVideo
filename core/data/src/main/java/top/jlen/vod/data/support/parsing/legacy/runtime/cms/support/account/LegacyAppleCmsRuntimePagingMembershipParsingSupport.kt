package top.jlen.vod.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale

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
