package top.jlen.vod.data

import androidx.core.text.HtmlCompat
import com.google.gson.annotations.SerializedName

data class AppleCmsResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val message: String = "",
    @SerializedName("page") val page: Int = 1,
    @SerializedName("pagecount") val pageCount: Int = 1,
    @SerializedName("limit") val limit: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("class") val categories: List<AppleCmsCategory> = emptyList(),
    @SerializedName("list") val list: List<VodItem> = emptyList()
) {
    val safePage: Int
        get() = page.coerceAtLeast(1)

    val safePageCount: Int
        get() = pageCount.coerceAtLeast(safePage)

    val safeLimit: Int
        get() = limit.coerceAtLeast(0)

    val safeTotal: Int
        get() = total.coerceAtLeast(list.size)

    val hasNextPage: Boolean
        get() = safePage < safePageCount
}

data class AppleCmsCategory(
    @SerializedName("type_id") val typeId: String = "",
    @SerializedName("type_name") val typeName: String = "",
    @SerializedName("type_pid") val parentId: String? = null
)

data class VodItem(
    @SerializedName("vod_id") val vodId: String = "",
    @SerializedName("vod_name") val vodName: String = "",
    @SerializedName("vod_sub") val vodSub: String? = null,
    @SerializedName("vod_pic") val vodPic: String? = null,
    @SerializedName("vod_tag") val vodTag: String? = null,
    @SerializedName("vod_class") val vodClass: String? = null,
    @SerializedName("vod_remarks") val vodRemarks: String? = null,
    @SerializedName("vod_blurb") val vodBlurb: String? = null,
    @SerializedName("vod_content") val vodContent: String? = null,
    @SerializedName("vod_pubdate") val vodPubdate: String? = null,
    @SerializedName("vod_year") val vodYear: String? = null,
    @SerializedName("vod_area") val vodArea: String? = null,
    @SerializedName("vod_lang") val vodLang: String? = null,
    @SerializedName("vod_actor") val vodActor: String? = null,
    @SerializedName("vod_director") val vodDirector: String? = null,
    @SerializedName("vod_score") val vodScore: String? = null,
    @SerializedName("type_name") val typeName: String? = null,
    @SerializedName("vod_play_from") val vodPlayFrom: String? = null,
    @SerializedName("vod_play_url") val vodPlayUrl: String? = null,
    val siteVodId: String = "",
    val detailUrl: String = ""
) {
    val displayTitle: String
        get() = vodName.ifBlank { "未命名影片" }

    val subtitle: String
        get() = listOf(vodRemarks, vodSub, typeName, vodYear, vodArea)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" | ")

    val badgeText: String
        get() = firstNotBlank(vodRemarks, vodSub, typeName, vodPubdate)

    val cleanActor: String
        get() = cleanText(vodActor).ifBlank { "暂无" }

    val cleanDirector: String
        get() = cleanText(vodDirector).ifBlank { "暂无" }

    val cleanScore: String
        get() = vodScore.orEmpty().trim().ifBlank { "暂无评分" }

    val metaPairs: List<Pair<String, String>>
        get() = listOf(
            "导演" to cleanDirector,
            "主演" to cleanActor,
            "地区" to firstNotBlank(vodArea, "未知"),
            "年份" to firstNotBlank(vodYear, "未知"),
            "语言" to firstNotBlank(vodLang, "未知"),
            "更新" to firstNotBlank(vodRemarks, vodPubdate, "站内资源")
        )

    val tags: List<String>
        get() = (
            splitTags(vodTag) +
                splitTags(vodClass) +
                listOfNotNull(typeName, vodYear, vodArea)
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

    val description: String
        get() = when {
            !vodBlurb.isNullOrBlank() -> cleanText(vodBlurb)
            !vodContent.isNullOrBlank() -> cleanText(vodContent)
            else -> "暂无简介"
        }

    private fun splitTags(value: String?): List<String> =
        value.orEmpty().split(",", "/", "|", " ")

    private fun cleanText(value: String?): String =
        HtmlCompat.fromHtml(value.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun firstNotBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
}

data class PlaySource(
    val name: String,
    val episodes: List<Episode>
)

data class Episode(
    val name: String,
    val url: String
)

data class AuthResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String = "",
    @SerializedName("url") val url: String? = null,
    @SerializedName("wait") val wait: Int? = null
)

data class AuthSession(
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val userName: String = "",
    val groupName: String = "",
    val portraitUrl: String = ""
)

data class UserProfilePage(
    val fields: List<Pair<String, String>> = emptyList(),
    val editor: UserProfileEditor = UserProfileEditor()
)

data class UserProfileEditor(
    val qq: String = "",
    val email: String = "",
    val phone: String = "",
    val question: String = "",
    val answer: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
)

data class UserCenterItem(
    val recordId: String = "",
    val vodId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val actionLabel: String = "",
    val actionUrl: String = "",
    val playUrl: String = "",
    val sourceIndex: Int = -1,
    val episodeIndex: Int = -1
)

data class UserCenterPage(
    val items: List<UserCenterItem> = emptyList(),
    val nextPageUrl: String? = null
)

data class MembershipInfo(
    val groupName: String = "",
    val points: String = "",
    val expiry: String = ""
)

data class MembershipPlan(
    val groupId: String = "",
    val groupName: String = "",
    val duration: String = "",
    val points: String = ""
)

data class MembershipPage(
    val info: MembershipInfo = MembershipInfo(),
    val plans: List<MembershipPlan> = emptyList()
)
