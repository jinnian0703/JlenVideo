package top.jlen.vod.data

import androidx.core.text.HtmlCompat
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
