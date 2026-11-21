package top.jlen.vod.data

import com.google.gson.JsonParser

internal fun parseTencentHotSearch(json: String, limit: Int): HotSearchGroup {
    val root = JsonParser.parseString(json).safeObject()
    val channels = (root?.firstObject("data") ?: root)?.get("Results").safeArray()
    val overall = channels?.mapNotNull { it.safeObject() }?.firstOrNull { it.firstString("ChannelID") == "0" }
    val titles = overall?.firstObject("Result")?.get("ListInfo").safeArray()?.toList().orEmpty()
        .map { it.safeObject()?.firstString("Query").orEmpty() }
    return rankedKeywords("腾讯视频", titles, limit, "https://v.qq.com/ranks/pages/index.html?t=hotsearch")
}

internal fun parseMgtvHotSearch(json: String, limit: Int): HotSearchGroup {
    val data = JsonParser.parseString(json).safeObject()?.firstObject("data")
    val sections = data?.firstObject("toplist")?.get("list").safeArray()?.toList().orEmpty()
    val overall = sections.mapNotNull { it.safeObject() }.firstOrNull { it.firstString("label") == "热门内容" }
    val titles = listOf("listWithPic", "listOnlyText").flatMap { overall?.get(it).safeArray()?.toList().orEmpty() }
        .mapNotNull { it.safeObject() }.sortedBy { it.firstInt("index") ?: Int.MAX_VALUE }
        .map { it.firstString("clipName") }
    return rankedKeywords("芒果TV", titles, limit, "https://m.mgtv.com/so/")
}

private fun rankedKeywords(platform: String, titles: List<String>, limit: Int, url: String): HotSearchGroup {
    val items = titles.map { sanitizeHotSearchKeyword(platform, it) }.filter(String::isNotBlank)
        .distinct().take(limit).mapIndexed { index, title -> HotSearchItem(index + 1, title, platform, url) }
    return HotSearchGroup(platform, items)
}

internal fun sanitizeHotSearchKeyword(platform: String, raw: String): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
        .removePrefix("#")
        .trim()
    if (normalized.isBlank()) return ""

    val withoutEmojiSuffix = normalized
        .replace(Regex("[\\p{So}\\p{Cn}]+.*$"), "")
        .trim()

    val cleaned = when (platform) {
        "优酷" -> withoutEmojiSuffix
        "芒果TV" -> withoutEmojiSuffix
            .replace(Regex("\\s+加更版$"), "")
            .replace(Regex("·[^·]*vlog$", RegexOption.IGNORE_CASE), "")
            .trim()
        else -> withoutEmojiSuffix
    }

    return cleaned
        .replace(Regex("[\\p{Punct}·・：:：、，。！!？?]+$"), "")
        .trim()
}
