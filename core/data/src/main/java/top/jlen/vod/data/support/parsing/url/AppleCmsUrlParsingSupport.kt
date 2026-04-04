package top.jlen.vod.data

import android.net.Uri
import android.util.Base64
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.Cookie

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

internal fun buildVodDetailUrl(item: VodItem, baseUrl: String): String =
    item.detailUrl.ifBlank {
        item.vodId
            .takeIf(String::isNotBlank)
            ?.let { normalizeUrl(baseUrl, "/voddetail/$it/") }
            .orEmpty()
    }

internal fun extractVodIdFromUserUrl(pathOrUrl: String, baseUrl: String): String {
    val normalized = resolveUrl(baseUrl, pathOrUrl)
    return when {
        normalized.contains("/voddetail/") -> extractVodId(normalized)
        normalized.contains("/vodplay/") -> normalized
            .substringAfter("/vodplay/")
            .substringBefore('/')
            .substringBefore('-')
        else -> ""
    }
}

internal fun resolveUrl(baseUrl: String, pathOrUrl: String): String {
    val value = pathOrUrl.trim()
    if (value.startsWith("http://") || value.startsWith("https://")) {
        return value
    }
    return normalizeUrl(baseUrl, value)
}

internal fun normalizeAgainst(raw: String, base: String, baseUrl: String): String {
    val value = raw.trim().replace("\\/", "/")
    if (value.isBlank()) return ""
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("//")) return "https:$value"
    return runCatching { URI(base).resolve(value).toString() }
        .getOrElse { resolveUrl(baseUrl, value) }
}

internal fun normalizeUrl(baseUrl: String, raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("//")) return "https:$value"
    return if (value.startsWith("/")) {
        baseUrl + value
    } else {
        Uri.parse("$baseUrl/").buildUpon().appendEncodedPath(value).build().toString()
    }
}

internal fun appendTimestamp(url: String): String {
    val separator = if (url.contains("?")) "&" else "?"
    return "$url${separator}t=${System.currentTimeMillis()}"
}

internal fun isDirectMediaUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith(".m3u8") ||
        lower.endsWith(".mp4") ||
        lower.contains(".m3u8?") ||
        lower.contains("/index.m3u8")
}

internal fun List<Cookie>.firstCookieValue(name: String): String =
    firstOrNull { it.name == name }
        ?.value
        .orEmpty()
        .takeUnless { it == "deleted" }
        .orEmpty()

internal fun normalizePortraitUrl(baseUrl: String, raw: String): String {
    val value = raw.trim()
    if (value.isBlank() || value == "deleted") return ""
    return appendTimestamp(normalizeUrl(baseUrl, value))
}

internal fun decodePlayerUrl(rawUrl: String, encrypt: Int): String {
    val cleaned = rawUrl.trim().replace("\\/", "/")
    if (cleaned.isBlank()) return ""

    return when (encrypt) {
        1 -> Uri.decode(cleaned)
        2 -> runCatching {
            val decodedBase64 = String(Base64.decode(cleaned, Base64.DEFAULT))
            Uri.decode(decodedBase64)
        }.getOrElse { cleaned }
        else -> cleaned
    }
}

internal fun extractPlayerConfig(html: String): Pair<String, Int>? {
    val block = Regex("""player_aaaa\s*=\s*(\{.*?\})</script>""", setOf(RegexOption.DOT_MATCHES_ALL))
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    if (block.isBlank()) return null

    val rawUrl = Regex(""""url"\s*:\s*"([^"]*)"""")
        .find(block)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val encrypt = Regex(""""encrypt"\s*:\s*(\d+)""")
        .find(block)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0

    return rawUrl to encrypt
}

internal fun extractEmbeddedMediaUrls(html: String): List<String> {
    val patterns = listOf(
        Regex("""const\s+url\s*=\s*"([^"]+)""""),
        Regex("""var\s+url\s*=\s*"([^"]+)""""),
        Regex(""""url"\s*:\s*"([^"]+)""""),
        Regex("""src\s*:\s*"([^"]+\.m3u8[^"]*)""""),
        Regex("""loadSource\(\s*"([^"]+)"\s*\)"""),
        Regex("""video\.src\s*=\s*"([^"]+)""""),
        Regex("""<iframe[^>]+src=["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE)),
        Regex("""["']((?:https?:)?//[^"']+\.m3u8[^"']*)["']"""),
        Regex("""["']((?:https?:)?//[^"']+\.mp4[^"']*)["']"""),
        Regex("""["']([^"']+\.m3u8[^"']*)["']"""),
        Regex("""["']([^"']+\.mp4[^"']*)["']"""),
        Regex("""<source[^>]+src=["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
    )
    return buildList {
        patterns.forEach { regex ->
            regex.findAll(html).forEach { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

internal fun extractAssignedJsonObject(html: String, variableName: String): String {
    val markerIndex = html.indexOf("$variableName=")
    if (markerIndex < 0) return ""

    val startIndex = html.indexOf('{', markerIndex)
    if (startIndex < 0) return ""

    var depth = 0
    var inString = false
    var escaped = false
    for (index in startIndex until html.length) {
        val char = html[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }

        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return html.substring(startIndex, index + 1)
                }
            }
        }
    }

    return ""
}
