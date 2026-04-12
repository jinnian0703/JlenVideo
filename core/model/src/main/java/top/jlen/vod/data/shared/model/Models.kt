package top.jlen.vod.data

import androidx.core.text.HtmlCompat
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlin.LazyThreadSafetyMode

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

data class VideoApiEnvelope<T>(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val message: String = "",
    @SerializedName("data") val data: T? = null
)

data class VideoApiPagedRows<T>(
    @SerializedName("engine") val engine: String = "",
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("total_pages") val totalPages: Int = 1,
    @SerializedName("has_more") val hasMore: Int? = null,
    @SerializedName("next_cursor") val nextCursor: String? = null,
    @SerializedName("rows") val rows: List<T> = emptyList()
)

data class CursorPagedVodItems(
    val items: List<VodItem> = emptyList(),
    val limit: Int = 0,
    val nextCursor: String = "",
    val hasMore: Boolean = false
)

data class AppleCmsCategory(
    @SerializedName("type_id") val typeId: String = "",
    @SerializedName("type_name") val typeName: String = "",
    @SerializedName("type_pid") val parentId: String? = null,
    @SerializedName("type_extend") val typeExtend: String? = null,
    @SerializedName("child") val children: List<AppleCmsCategory> = emptyList()
) {
    val filterGroups: List<CategoryFilterGroup> by lazy(LazyThreadSafetyMode.NONE) {
        val parsed = runCatching { JsonParser.parseString(typeExtend.orEmpty()).asJsonObject }.getOrNull()
            ?: return@lazy emptyList()
        CATEGORY_FILTER_LABELS.mapNotNull { (key, label) ->
            val options = parsed.get(key)
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                .orEmpty()
                .split(",")
                .map(::sanitizeUserFacingToken)
                .distinct()
                .filter(String::isNotBlank)
            if (options.isEmpty()) null else CategoryFilterGroup(key = key, label = label, options = options)
        }
    }
}

data class CategoryFilterGroup(
    val key: String,
    val label: String,
    val options: List<String>
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
            .map(::sanitizeDisplayValue)
            .filterNot(::isDuplicateTitleValue)
            .filter { it.isNotBlank() }
            .joinToString(" | ")

    val badgeText: String
        get() = firstNotBlank(vodRemarks, vodSub, typeName, vodPubdate)

    val resolvedLatestEpisodeNumber: Int?
        get() = null

    val resolvedUpdateLabel: String
        get() = selectUpdateValue()

    val resolvedSubtitle: String
        get() = subtitle

    val resolvedBadgeText: String
        get() = badgeText

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
            "更新" to selectUpdateValue().ifBlank { "站内资源" }
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

    private fun sanitizeDisplayValue(value: String?): String =
        value.orEmpty()
            .trim()
            .takeUnless {
                it.isBlank() ||
                    it.equals("null", ignoreCase = true) ||
                    it.equals("undefined", ignoreCase = true) ||
                    it.equals("none", ignoreCase = true)
            }
            .orEmpty()

    private fun cleanText(value: String?): String =
        HtmlCompat.fromHtml(value.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun firstNotBlank(vararg values: String?): String =
        values
            .map(::sanitizeDisplayValue)
            .filterNot(::isDuplicateTitleValue)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun selectUpdateValue(): String {
        val remarks = sanitizeDisplayValue(vodRemarks)
        if (remarks.isNotBlank()) return remarks

        val publishMeta = sanitizeDisplayValue(vodPubdate)
        return publishMeta.takeIf(::looksLikeUpdateLabel).orEmpty()
    }

    private fun isDuplicateTitleValue(value: String): Boolean {
        val normalizedValue = value.trim()
        val normalizedTitle = displayTitle.trim()
        return normalizedValue.isNotBlank() &&
            normalizedTitle.isNotBlank() &&
            normalizedValue.equals(normalizedTitle, ignoreCase = true)
    }

    private fun looksLikeUpdateLabel(value: String): Boolean {
        if (value.isBlank()) return false
        val normalized = value.replace(Regex("\\s+"), "")
        val patterns = listOf(
            Regex("""^更新至第?\d{1,4}集?$"""),
            Regex("""^第\d{1,4}集$"""),
            Regex("""^\d{1,4}集$"""),
            Regex("""^第\d{1,4}期$"""),
            Regex("""^\d{1,8}期$"""),
            Regex("""^全\d{1,4}集$"""),
            Regex("""^共\d{1,4}集$"""),
            Regex("""^\d{1,4}集全$"""),
            Regex("""^(完结|已完结|完結|全集|正片|抢先版?|抢先看|预告|HD|TC|SP|OVA|PV)$""")
        )
        return patterns.any { it.matches(normalized) }
    }
}

fun sanitizeUserFacingToken(value: String?): String =
    value.orEmpty()
        .trim()
        .replace("绫诲瀷", "类型")
        .replace("鍦板尯", "地区")
        .replace("骞翠唤", "年份")
        .replace("璇█", "语言")
        .replace("鐘舵€", "状态")
        .replace("鐗堟湰", "版本")
        .takeUnless {
            it.isBlank() ||
                it.equals("null", ignoreCase = true) ||
                it.equals("undefined", ignoreCase = true) ||
                it.equals("none", ignoreCase = true)
        }
        .orEmpty()

fun sanitizeUserFacingComposite(value: String?): String =
    value.orEmpty()
        .split("|")
        .map(::sanitizeUserFacingToken)
        .filter(String::isNotBlank)
        .joinToString(" | ")

private val CATEGORY_FILTER_LABELS = linkedMapOf(
    "class" to "类型",
    "area" to "地区",
    "year" to "年份",
    "lang" to "语言",
    "state" to "状态",
    "version" to "版本"
)

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
    val editor: UserProfileEditor = UserProfileEditor(),
    val session: AuthSession = AuthSession()
)

data class UserProfileEditor(
    val qq: String = "",
    val email: String = "",
    val pendingEmail: String = "",
    val emailCode: String = "",
    val phone: String = "",
    val question: String = "",
    val answer: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
)

data class RegisterPage(
    val channel: String = "email",
    val contactLabel: String = "邮箱",
    val codeLabel: String = "邮箱验证码",
    val requiresCode: Boolean = true,
    val requiresVerify: Boolean = true,
    val captchaUrl: String = "",
    val captchaBytes: ByteArray? = null
)

data class RegisterEditor(
    val userName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val channel: String = "email",
    val contact: String = "",
    val code: String = "",
    val verify: String = ""
)

data class FindPasswordPage(
    val requiresVerify: Boolean = true,
    val captchaUrl: String = "",
    val captchaBytes: ByteArray? = null
)

data class FindPasswordEditor(
    val userName: String = "",
    val question: String = "",
    val answer: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val verify: String = ""
)

data class UserCenterItem(
    val recordId: String = "",
    val vodId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val actionLabel: String = "",
    val actionUrl: String = "",
    val playUrl: String = "",
    val sourceName: String = "",
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

data class MembershipSignInInfo(
    val enabled: Boolean = false,
    val signedToday: Boolean = false,
    val rewardPoints: String = "",
    val rewardMinPoints: String = "",
    val rewardMaxPoints: String = "",
    val signedAt: String = ""
)

data class PointLogItem(
    val logId: String = "",
    val type: String = "",
    val typeText: String = "",
    val points: String = "",
    val pointsText: String = "",
    val isIncome: Boolean = false,
    val remarks: String = "",
    val time: String = "",
    val timeText: String = ""
)

data class PointTrendPoint(
    val date: String = "",
    val delta: Int = 0,
    val label: String = ""
)

data class MembershipPlan(
    val groupId: String = "",
    val groupName: String = "",
    val duration: String = "",
    val points: String = ""
)

data class MembershipPage(
    val info: MembershipInfo = MembershipInfo(),
    val plans: List<MembershipPlan> = emptyList(),
    val signInInfo: MembershipSignInInfo = MembershipSignInInfo(),
    val pointLogs: List<PointLogItem> = emptyList()
)

data class SearchSuggestionItem(
    val vodId: String = "",
    val title: String = "",
    val subTitle: String = "",
    val poster: String = "",
    val remarks: String = "",
    val typeId: String = "",
    val typeName: String = "",
    val typeParentName: String = "",
    val highlight: String = ""
)

data class SearchSuggestionPage(
    val engine: String = "",
    val keyword: String = "",
    val limit: Int = 0,
    val items: List<SearchSuggestionItem> = emptyList()
)

data class PlaybackResumeRecord(
    val vodId: String = "",
    val sourceIndex: Int = 0,
    val episodeIndex: Int = 0,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val updatedAt: Long = 0L
)

data class HotSearchCacheSnapshot(
    val groups: List<HotSearchGroup> = emptyList(),
    val cachedAt: Long = 0L
)

data class AppUpdateInfo(
    val currentVersion: String = "",
    val latestVersion: String = "",
    val releasePageUrl: String = "",
    val downloadUrl: String = "",
    val notes: String = "",
    val hasUpdate: Boolean = false
)

data class AppNotice(
    val id: String = "",
    val title: String = "",
    val htmlContent: String = "",
    val content: String = "",
    val summary: String = "",
    val isPinned: Boolean = false,
    val isActive: Boolean = true,
    val alwaysShowDialog: Boolean = false,
    val startAt: String = "",
    val endAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val previewText: String
        get() = summary.ifBlank {
            content
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }

    val displayContent: String
        get() = content.ifBlank { summary }.ifBlank { "暂无公告内容" }
}

data class HotSearchItem(
    val rank: Int = 0,
    val keyword: String = "",
    val platform: String = "",
    val sourceUrl: String = ""
)

data class HotSearchGroup(
    val platform: String = "",
    val items: List<HotSearchItem> = emptyList()
)
