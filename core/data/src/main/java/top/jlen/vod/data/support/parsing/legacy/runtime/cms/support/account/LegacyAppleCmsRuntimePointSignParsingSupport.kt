package top.jlen.vod.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun parseMembershipSignInInfo(root: JsonObject): MembershipSignInInfo {
    val payload = root.firstObject("data") ?: return MembershipSignInInfo()
    val signIn = payload.firstObject("sign_in")
    val user = payload.firstObject("user")
    val enabledValue = signIn?.firstInt("enabled")
        ?: payload.firstInt("sign_status")
        ?: user?.firstInt("sign_status")
    val signedTodayValue = signIn?.firstInt("signed_today")
        ?: payload.firstInt("signed_today")
        ?: user?.firstInt("signed_today")
    val rewardMin = decodeSiteText(
        signIn?.firstString("points_min").orEmpty().ifBlank {
            payload.firstString("sign_points_min").ifBlank {
                user?.firstString("sign_points_min").orEmpty()
            }
        }
    )
    val rewardMax = decodeSiteText(
        signIn?.firstString("points_max").orEmpty().ifBlank {
            payload.firstString("sign_points_max").ifBlank {
                user?.firstString("sign_points_max").orEmpty()
            }
        }
    )
    val rewardPoints = decodeSiteText(
        signIn?.firstString("reward_points", "today_reward_points").orEmpty().ifBlank {
            payload.firstString("today_reward_points").ifBlank {
                user?.firstString("today_reward_points").orEmpty()
            }
        }
    )
    val signedAt = decodeSiteText(
        signIn?.firstString("signed_at_text", "signed_at").orEmpty().ifBlank {
            payload.firstString("signed_at_text", "signed_at").ifBlank {
                user?.firstString("signed_at_text", "signed_at").orEmpty()
            }
        }
    )
    return MembershipSignInInfo(
        enabled = enabledValue == 1,
        signedToday = signedTodayValue == 1,
        rewardPoints = rewardPoints,
        rewardMinPoints = rewardMin,
        rewardMaxPoints = rewardMax,
        signedAt = signedAt
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
