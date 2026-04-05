package top.jlen.vod.data

import java.io.IOException
import okhttp3.FormBody

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadUserProfile(): UserProfilePage {
    runCatching { runtimeLoadUserProfileFromUserDetailApi(currentSession()) }
        .getOrNull()
        ?.let { return it }

    runCatching { runtimeLoadUserProfileFromVideoMemberInfoApi() }
        .getOrNull()
        ?.let { return it }

    val profileDocument = runtimeFetchUserDocument("/index.php/user/index.html")
    val editorDocument = runtimeFetchUserDocument("/index.php/user/info.html")
    return UserProfilePage(
        fields = runtimeParseUserProfileFields(profileDocument),
        editor = runtimeParseUserProfileEditor(editorDocument),
        session = runtimeParseUserProfileSession(profileDocument)
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadFavoritePage(
    pageUrl: String? = null
): UserCenterPage = runtimeParseUserCenterPageEnhanced(
    document = runtimeFetchUserDocument(pageUrl ?: "/index.php/user/favs.html")
)

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadHistoryPage(
    pageUrl: String? = null
): UserCenterPage = runtimeParseUserCenterPageEnhanced(
    document = runtimeFetchUserDocument(pageUrl ?: "/index.php/user/plays.html")
)

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyLoadMembershipPage(): MembershipPage {
    runCatching { runtimeLoadMembershipPageFromVideoMemberInfoApi() }
        .getOrNull()
        ?.let { return it }

    currentSession().userId
        .takeIf(String::isNotBlank)
        ?.let { userId ->
            runCatching { runtimeLoadMembershipInfoFromUserDetailApi(userId) }
                .getOrNull()
                ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() }
                ?.let { return it }
        }

    runCatching { runtimeLoadMembershipPageFromUserCenterJson() }
        .getOrNull()
        ?.takeIf { it.info.groupName.isNotBlank() || it.info.points.isNotBlank() || it.info.expiry.isNotBlank() || it.plans.isNotEmpty() }
        ?.let { return it }

    runCatching { runtimeLoadMembershipPageFromAppCenter() }
        .getOrNull()
        ?.let { return it }

    return runtimeLoadMembershipPageFromHtml()
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacySaveUserProfile(
    editor: UserProfileEditor
): String {
    runCatching { runtimeSubmitUserProfileToAppCenter(editor) }
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
    return runtimeSubmitUserAction(
        url = "${runtimeBaseUrl()}/index.php/user/info",
        referer = "${runtimeBaseUrl()}/index.php/user/info.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacySendEmailBindCode(email: String): String {
    runCatching {
        val json = runtimeRequestVideoApiJson(
            path = "api.php/video/bindMsg",
            formBody = FormBody.Builder()
                .add("ac", "email")
                .add("to", email.trim())
                .build()
        )
        runtimeExtractVideoApiMessage(json, "验证码已发送")
    }.getOrNull()?.let { return it }

    runCatching {
        runtimeSubmitAppCenterUserProfileMutation(
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
    return runtimeSubmitUserAction(
        url = "${runtimeBaseUrl()}/index.php/user/bindmsg",
        referer = "${runtimeBaseUrl()}/index.php/user/bind?ac=email",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyBindEmail(
    email: String,
    code: String
): String {
    runCatching {
        val json = runtimeRequestVideoApiJson(
            path = "api.php/video/bind",
            formBody = FormBody.Builder()
                .add("ac", "email")
                .add("to", email.trim())
                .add("code", code.trim())
                .build()
        )
        runtimeExtractVideoApiMessage(json, "邮箱已绑定")
    }.getOrNull()?.let { return it }

    runCatching {
        runtimeSubmitAppCenterUserProfileMutation(
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
    return runtimeSubmitUserAction(
        url = "${runtimeBaseUrl()}/index.php/user/bind",
        referer = "${runtimeBaseUrl()}/index.php/user/bind?ac=email",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyUnbindEmail(): String {
    runCatching {
        val json = runtimeRequestVideoApiJson(
            path = "api.php/video/unbind",
            formBody = FormBody.Builder()
                .add("ac", "email")
                .build()
        )
        runtimeExtractVideoApiMessage(json, "邮箱已解绑")
    }.getOrNull()?.let { return it }

    runCatching {
        runtimeSubmitAppCenterUserProfileMutation(
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
    return runtimeSubmitUserAction(
        url = "${runtimeBaseUrl()}/index.php/user/unbind",
        referer = "${runtimeBaseUrl()}/index.php/user/info.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyAddFavorite(item: VodItem): String =
    runtimeSubmitUserUlog(
        siteVodId = runtimeResolveSiteLogId(item),
        type = 2
    )

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyAddPlayRecord(
    item: VodItem,
    episodePageUrl: String
): String {
    val route = runtimeParsePlayRoute(episodePageUrl)
    return runtimeSubmitUserUlog(
        siteVodId = runtimeResolveSiteLogId(item),
        type = 4,
        sid = route?.sid.orEmpty(),
        nid = route?.nid.orEmpty()
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyDeleteUserRecord(
    recordIds: List<String>,
    type: Int,
    clearAll: Boolean
): String {
    val form = FormBody.Builder()
        .add("ids", recordIds.joinToString(","))
        .add("type", type.toString())
        .add("all", if (clearAll) "1" else "0")
        .build()
    return runtimeSubmitUserAction(
        url = "${runtimeBaseUrl()}/index.php/user/ulog_del",
        referer = "${runtimeBaseUrl()}/index.php/user/${if (type == 2) "favs" else "plays"}.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacyUpgradeMembership(
    plan: MembershipPlan
): String {
    runCatching {
        val json = runtimeRequestVideoApiJson(
            path = "api.php/video/upgrade",
            formBody = FormBody.Builder()
                .add("group_id", plan.groupId)
                .add("long", plan.duration)
                .build()
        )
        runtimeExtractVideoApiMessage(json, "会员信息已更新")
    }.getOrNull()?.let { return it }

    val form = FormBody.Builder()
        .add("group_id", plan.groupId)
        .add("long", plan.duration)
        .build()
    return runtimeSubmitUserAction(
        url = "${runtimeBaseUrl()}/index.php/user/upgrade",
        referer = "${runtimeBaseUrl()}/index.php/user/upgrade.html",
        formBody = form
    )
}

internal suspend fun LegacyAppleCmsRuntimeRepositoryCore.legacySignInMembership(): String {
    val json = runtimeRequestVideoApiJson(
        path = "api.php/video/signIn",
        formBody = FormBody.Builder().build()
    )
    val payload = json.firstObject("data", "info") ?: json
    val rewardPoints = payload.firstString("reward_points", "today_reward_points", "reward")
    val currentPoints = payload.firstString("current_points", "points", "user_points")
    return when {
        rewardPoints.isNotBlank() && currentPoints.isNotBlank() ->
            "签到成功，获得 $rewardPoints 积分，当前积分 $currentPoints"
        rewardPoints.isNotBlank() -> "签到成功，获得 $rewardPoints 积分"
        else -> runtimeExtractVideoApiMessage(json, "签到成功")
    }
}
