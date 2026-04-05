package top.jlen.vod.data

import org.jsoup.nodes.Document

internal fun buildProfileFields(
    userId: String = "",
    userName: String = "",
    groupName: String = "",
    points: String = "",
    expiry: String = "",
    email: String = "",
    phone: String = "",
    qq: String = ""
): List<Pair<String, String>> = buildList {
    userId.takeIf(String::isNotBlank)?.let { add("用户 ID" to it) }
    userName.takeIf(String::isNotBlank)?.let { add("用户名" to it) }
    groupName.takeIf(String::isNotBlank)?.let { add("会员组" to it) }
    points.takeIf(String::isNotBlank)?.let { add("积分" to it) }
    expiry.takeIf(String::isNotBlank)?.let { add("到期时间" to it) }
    email.takeIf(String::isNotBlank)?.let { add("邮箱" to it) }
    phone.takeIf(String::isNotBlank)?.let { add("手机号" to it) }
    qq.takeIf(String::isNotBlank)?.let { add("QQ号" to it) }
}

internal fun isUserLoginResponse(response: okhttp3.Response, body: String): Boolean {
    val resolvedPath = response.request.url.encodedPath
    return resolvedPath.contains("/index.php/user/login") ||
        body.contains("/index.php/user/login", ignoreCase = true) ||
        body.contains("login required", ignoreCase = true) ||
        isLoginMessage(body)
}

internal fun isLoginMessage(message: String): Boolean {
    val normalized = message.trim()
    return normalized.contains("请先登录") ||
        normalized.contains("未登录") ||
        normalized.contains("登录失效") ||
        normalized.contains("login required", ignoreCase = true)
}

internal fun extractLabeledValues(
    document: Document,
    labels: List<String>,
    stopPhrases: List<String> = emptyList()
): Map<String, String> {
    val bodyText = decodeSiteText(document.body().text())
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    if (bodyText.isBlank()) return emptyMap()

    data class LabelHit(val label: String, val start: Int, val valueStart: Int)

    val hits = labels.mapNotNull { label ->
        Regex("${Regex.escape(label)}(?:[:： ]+)?")
            .find(bodyText)
            ?.let { match -> LabelHit(label, match.range.first, match.range.last + 1) }
    }.sortedBy { it.start }

    if (hits.isEmpty()) return emptyMap()

    return buildMap {
        hits.forEachIndexed { index, hit ->
            val nextStart = hits.getOrNull(index + 1)?.start ?: bodyText.length
            val rawValue = bodyText.substring(hit.valueStart, nextStart)
            val cleaned = stopPhrases.fold(rawValue) { acc, stop -> acc.substringBefore(stop) }
                .replace(Regex("^[:：\\s]+"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isNotBlank()) {
                put(hit.label, cleaned)
            }
        }
    }
}
