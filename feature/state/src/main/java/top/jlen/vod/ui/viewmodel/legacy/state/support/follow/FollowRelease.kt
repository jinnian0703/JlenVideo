package top.jlen.vod.ui

import top.jlen.vod.data.VodItem

internal data class FollowRelease(val index: Int, val label: String)

private val updated = Regex("""更新至\s*第?\s*(\d{1,4})(?!\d|\s*期)\s*集?""")
private val complete = Regex("""([全共])\s*(\d{1,4})\s*集""")
private val allEpisodes = Regex("""(?<!\d)(\d{1,4})\s*集全""")
private val episodeName = Regex("""(?:第\s*)?(\d{1,4})\s*集|(?:第\s*)?(\d{1,3})""")

private fun numberedRelease(number: Int) = FollowRelease(number - 1, "更新至第${number}集")

private fun explicitRelease(text: String): FollowRelease? {
    updated.find(text)?.groupValues?.get(1)?.toInt()?.takeIf { it > 0 }?.let { return numberedRelease(it) }
    complete.find(text)?.let { match ->
        val number = match.groupValues[2].toInt()
        if (number > 0) return FollowRelease(number - 1, "${match.groupValues[1]}${number}集")
    }
    allEpisodes.find(text)?.groupValues?.get(1)?.toInt()?.takeIf { it > 0 }?.let { return FollowRelease(it - 1, "${it}集全") }
    return null
}

private fun episodeNumber(name: String): Int? = episodeName.matchEntire(name.trim())?.let { match ->
    match.groupValues[1].ifEmpty { match.groupValues[2] }.toInt().takeIf { it > 0 }
}

internal fun resolveFollowRelease(detail: VodItem?, favoriteSubtitle: String, cached: FollowUpItem?): FollowRelease? {
    val metadata = listOfNotNull(detail?.episodeRemark, detail?.vodRemarks, detail?.compatBadgeText, detail?.resolvedSubtitle)
    metadata.firstNotNullOfOrNull(::explicitRelease)?.let { return it }

    val previous = cached?.let { card ->
        card.latestEpisodeIndex?.let { index -> FollowRelease(index, card.latestEpisodeLabel.ifBlank { numberedRelease(index + 1).label }) }
            ?: explicitRelease(card.latestEpisodeLabel)
    }
    // A refresh may only return part of a playlist. Compare numbered episodes across all lines.
    val playlist = detail?.playGroups.orEmpty().asSequence().flatMap { group -> group.episodes.asSequence().map { it.name } } +
        detail?.vodPlayUrl.orEmpty().splitToSequence("$$$", "#").map { it.substringBefore('$') }
    val inferred = (playlist + metadata.asSequence()).mapNotNull(::episodeNumber).maxOrNull()?.let(::numberedRelease)
    return listOfNotNull(previous, explicitRelease(favoriteSubtitle), inferred).maxByOrNull { it.index }
}
