package top.jlen.vod.ui

private val whitespaceRegex = Regex("\\s+")
private val rankPrefixRegex = Regex("^NO\\s*\\d+[\\d\\s]*")
private val punctuationOnlyRegex = Regex("^[.、·•-]+$")
private val compactUpdateWithPrefixRegex = Regex("^更新至第(\\d{1,4})集?$")
private val compactUpdateRegex = Regex("^更新至(\\d{1,4})集?$")
private val episodeNumberWithoutUnitRegex = Regex("^第\\d{1,4}$")
private val pureNumberRegex = Regex("^\\d{1,4}$")

private const val localizedStatusSuffix = "(国语|粤语|英语|日语|韩语|法语|德语|俄语|泰语|中字|双字|中字版|双语版|国语版|粤语版)?"
private val meaningfulPosterBadgePatterns = listOf(
    Regex("""^更新至第?\d{1,4}集?$"""),
    Regex("""^更新至第?\d{1,4}$"""),
    Regex("""^第\d{1,4}集$"""),
    Regex("""^第\d{1,4}$"""),
    Regex("""^\d{1,4}集$"""),
    Regex("""^\d{1,4}$"""),
    Regex("""^更新至第?\d{1,8}期$"""),
    Regex("""^第\d{1,4}期$"""),
    Regex("""^\d{1,8}期$"""),
    Regex("""^全\d{1,4}集$"""),
    Regex("""^共\d{1,4}集$"""),
    Regex("""^\d{1,4}集全$"""),
    Regex("""^(完结|已完结|完結|全集)$"""),
    Regex("""^(正片|抢先版?|抢先看|预告)$localizedStatusSuffix$"""),
    Regex("""^(更新)?(HD|BD|TC|TS|CAM|DVD|4K|720P|1080P|2160P|蓝光|超清|高清|标清|SP|OVA|PV)$localizedStatusSuffix$""")
)

fun formatPosterBadge(raw: String, compact: Boolean = true): String {
    val normalized = raw
        .replace(whitespaceRegex, " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(rankPrefixRegex, "")
        .trim()

    if (!isMeaningfulPosterBadge(trimmedRankPrefix)) return ""

    val compactEpisodeBadge = when {
        compact && compactUpdateWithPrefixRegex.matches(trimmedRankPrefix) ->
            trimmedRankPrefix.replace(compactUpdateWithPrefixRegex, "第$1集")
        compact && compactUpdateRegex.matches(trimmedRankPrefix) ->
            trimmedRankPrefix.replace(compactUpdateRegex, "第$1集")
        episodeNumberWithoutUnitRegex.matches(trimmedRankPrefix) -> "${trimmedRankPrefix}集"
        compact && pureNumberRegex.matches(trimmedRankPrefix) -> "第${trimmedRankPrefix}集"
        else -> trimmedRankPrefix
    }

    return when {
        punctuationOnlyRegex.matches(compactEpisodeBadge) -> ""
        compactEpisodeBadge.isBlank() -> ""
        else -> compactEpisodeBadge
    }
}

private fun isMeaningfulPosterBadge(text: String): Boolean {
    if (text.isBlank()) return false
    val normalized = text.replace(whitespaceRegex, "")
    return meaningfulPosterBadgePatterns.any { it.matches(normalized) }
}
