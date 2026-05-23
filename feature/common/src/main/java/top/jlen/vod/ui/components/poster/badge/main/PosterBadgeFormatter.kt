package top.jlen.vod.ui

fun formatPosterBadge(raw: String, compact: Boolean = true): String {
    val normalized = raw
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return ""

    val trimmedRankPrefix = normalized
        .replace(Regex("^NO\\s*\\d+[\\d\\s]*"), "")
        .trim()

    if (!isMeaningfulPosterBadge(trimmedRankPrefix)) return ""

    val compactEpisodeBadge = when {
        compact && trimmedRankPrefix.matches(Regex("^更新至第\\d{1,4}集?$")) ->
            trimmedRankPrefix.replace(Regex("^更新至第(\\d{1,4})集?$"), "第$1集")
        compact && trimmedRankPrefix.matches(Regex("^更新至\\d{1,4}集?$")) ->
            trimmedRankPrefix.replace(Regex("^更新至(\\d{1,4})集?$"), "第$1集")
        compact && trimmedRankPrefix.matches(Regex("^更新至第\\d{1,4}$")) ->
            trimmedRankPrefix.replace(Regex("^更新至第(\\d{1,4})$"), "第$1集")
        compact && trimmedRankPrefix.matches(Regex("^更新至\\d{1,4}$")) ->
            trimmedRankPrefix.replace(Regex("^更新至(\\d{1,4})$"), "第$1集")
        trimmedRankPrefix.matches(Regex("^第\\d{1,4}$")) -> "${trimmedRankPrefix}集"
        compact && trimmedRankPrefix.matches(Regex("^\\d{1,4}$")) -> "第${trimmedRankPrefix}集"
        else -> trimmedRankPrefix
    }

    return when {
        compactEpisodeBadge.matches(Regex("^[.、·•-]+$")) -> ""
        compactEpisodeBadge.isBlank() -> ""
        else -> compactEpisodeBadge
    }
}

private fun isMeaningfulPosterBadge(text: String): Boolean {
    if (text.isBlank()) return false
    val normalized = text.replace(Regex("\\s+"), "")
    val localizedStatusSuffix = "(国语|粤语|英语|日语|韩语|法语|德语|俄语|泰语|中字|双字|中字版|双语版|国语版|粤语版)?"
    val patterns = listOf(
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
    return patterns.any { it.matches(normalized) }
}
