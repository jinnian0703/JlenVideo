package top.jlen.vod.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun parseMembershipSignInInfo(root: JsonObject): MembershipSignInInfo {
    val payload = root.firstObject("data") ?: return MembershipSignInInfo()
    val signIn = payload.firstObject("sign_in") ?: return MembershipSignInInfo()
    return MembershipSignInInfo(
        enabled = signIn.firstBoolean("enabled") == true,
        signedToday = signIn.firstBoolean("signed_today") == true,
        rewardPoints = decodeSiteText(
            signIn.firstString(
                "reward_points",
                "today_reward_points"
            )
        ),
        rewardMinPoints = decodeSiteText(
            signIn.firstString(
                "points_min"
            )
        ),
        rewardMaxPoints = decodeSiteText(
            signIn.firstString(
                "points_max"
            )
        ),
        signedAt = decodeSiteText(
            signIn.firstString(
                "signed_at_text",
                "signed_at"
            )
        )
    )
}

internal fun parsePointLogItems(root: JsonObject): List<PointLogItem> =
    (root.firstObject("data") ?: root)
        .firstArray("rows", "list", "items")
        .mapNotNull(::parsePointLogItem)

internal fun parsePointLogItem(element: JsonElement): PointLogItem? {
    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val points = decodeSiteText(obj.firstString("plog_points", "points", "score", "integral"))
    val pointsText = decodeSiteText(
        obj.firstString("plog_points_text", "points_text", "score_text", "integral_text")
    )
    return PointLogItem(
        logId = obj.firstString("plog_id", "id", "log_id"),
        type = obj.firstString("plog_type", "type"),
        typeText = decodeSiteText(obj.firstString("plog_type_text", "type_text", "type_name")),
        points = points,
        pointsText = pointsText,
        isIncome = obj.firstBoolean("is_income", "income", "is_add")
            ?: when {
                pointsText.startsWith("+") -> true
                pointsText.startsWith("-") -> false
                else -> points.trim().toIntOrNull()?.let { it >= 0 } ?: false
            },
        remarks = decodeSiteText(
            obj.firstString("plog_remarks", "remarks", "remark", "desc", "description")
        ),
        time = obj.firstString("plog_time", "time", "created_at"),
        timeText = decodeSiteText(obj.firstString("plog_time_text", "time_text", "created_at_text"))
    ).takeIf { item ->
        item.logId.isNotBlank() ||
            item.typeText.isNotBlank() ||
            item.points.isNotBlank() ||
            item.pointsText.isNotBlank() ||
            item.remarks.isNotBlank() ||
            item.timeText.isNotBlank()
    }
}
