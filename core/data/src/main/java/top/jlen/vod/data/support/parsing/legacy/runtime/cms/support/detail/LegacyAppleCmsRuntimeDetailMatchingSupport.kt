package top.jlen.vod.data

import java.util.Locale

internal fun detailMatchesPreview(detail: VodItem, preview: VodItem): Boolean {
    val detailTitle = canonicalTitle(detail.vodName)
    val previewTitle = canonicalTitle(preview.vodName)
    if (detailTitle.isBlank() || previewTitle.isBlank()) return false
    if (detailTitle != previewTitle) return false
    val previewYear = preview.vodYear.orEmpty().trim()
    val detailYear = detail.vodYear.orEmpty().trim()
    return previewYear.isBlank() || detailYear.isBlank() || previewYear == detailYear
}

internal fun searchCandidateScore(candidate: VodItem, preview: VodItem): Int {
    var score = 0
    if (canonicalTitle(candidate.vodName) == canonicalTitle(preview.vodName)) score += 100
    if (candidate.vodPic.orEmpty() == preview.vodPic.orEmpty()) score += 25
    if (candidate.vodYear.orEmpty() == preview.vodYear.orEmpty() && preview.vodYear.orEmpty().isNotBlank()) {
        score += 10
    }
    return score
}

internal fun mergePreviewIntoDetail(preview: VodItem, detail: VodItem): VodItem =
    detail.copy(
        vodId = preview.vodId.ifBlank { detail.vodId },
        vodName = preview.vodName.ifBlank { detail.vodName },
        vodSub = preview.vodSub ?: detail.vodSub,
        compatSubtitle = detail.compatSubtitle ?: preview.compatSubtitle,
        vodPic = preview.vodPic ?: detail.vodPic,
        vodRemarks = detail.vodRemarks?.takeIf { it.isNotBlank() } ?: preview.vodRemarks,
        compatBadgeText = detail.compatBadgeText ?: preview.compatBadgeText,
        episodeRemark = detail.episodeRemark ?: preview.episodeRemark,
        vodBlurb = preview.vodBlurb ?: detail.vodBlurb,
        vodContent = preview.vodContent ?: detail.vodContent,
        vodYear = preview.vodYear ?: detail.vodYear,
        vodArea = preview.vodArea ?: detail.vodArea,
        vodLang = preview.vodLang ?: detail.vodLang,
        typeName = preview.typeName ?: detail.typeName,
        siteVodId = preview.siteVodId.ifBlank { detail.siteVodId },
        detailUrl = preview.detailUrl.ifBlank { detail.detailUrl }
    )

internal fun canonicalTitle(raw: String): String =
    raw.lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{Punct}·：:～~]+"), "")
        .trim()
