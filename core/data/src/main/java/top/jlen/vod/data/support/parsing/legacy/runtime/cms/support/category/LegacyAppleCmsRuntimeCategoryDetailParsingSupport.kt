package top.jlen.vod.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal fun extractCategoryPageCount(document: Document, pagination: Element?): Int {
    val mobileCount = pagination?.selectFirst("li.active .num")
        ?.text()
        .orEmpty()
        .substringAfter('/')
        .substringBefore(' ')
        .toIntOrNull()
    if (mobileCount != null && mobileCount > 0) return mobileCount

    val numericLinks = pagination?.select("a[href]")
        .orEmpty()
        .mapNotNull { anchor ->
            anchor.text().trim().toIntOrNull()
        }
    val maxNumericLink = numericLinks.maxOrNull()
    if (maxNumericLink != null && maxNumericLink > 0) return maxNumericLink

    val titleCount = Regex("""第(\d+)页""")
        .find(document.title())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return titleCount ?: 1
}

internal fun extractCategoryTotal(document: Document): Int {
    val scriptTotal = document.select("script")
        .asSequence()
        .mapNotNull { script ->
            Regex("""ewave-total"\)\.text\((\d+)\)""")
                .find(script.html())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        .firstOrNull()
    if (scriptTotal != null && scriptTotal > 0) return scriptTotal

    val headerTotal = document.selectFirst(".vod-list h2 .small")
        ?.text()
        .orEmpty()
        .let { text ->
            Regex("""共\s*(\d+)\s*个视频""")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
    return headerTotal ?: 0
}

internal fun isBrowsableCategory(category: AppleCmsCategory): Boolean {
    val parentId = category.parentId.orEmpty().trim()
    return category.typeId.isNotBlank() &&
        category.typeName.isNotBlank() &&
        (parentId.isBlank() || parentId == "0" || parentId == category.typeId)
}

internal fun parseCategories(homeDocument: Document, mapDocument: Document?): List<AppleCmsCategory> {
    val homeCategories = homeDocument.select(".clist-left-tabs-title[href*=/vodtype/]")
        .mapNotNull { anchor ->
            val href = anchor.attr("href")
            if (href.isBlank()) null else AppleCmsCategory(typeId = href, typeName = anchor.text())
        }
        .distinctBy { it.typeId }

    if (homeCategories.isNotEmpty()) return homeCategories

    return mapDocument?.select(".vod-list h2 a[href*=/vodtype/]")
        .orEmpty()
        .mapNotNull { anchor ->
            val href = anchor.attr("href")
            if (href.isBlank()) null else AppleCmsCategory(typeId = href, typeName = anchor.text())
        }
        .distinctBy { it.typeId }
}

internal fun readLabeledValue(document: Document, label: String): Pair<String, String>? {
    val value = readLabeledText(document, label)
    return value.takeIf { it.isNotBlank() }?.let { label to it }
}

internal fun readLabeledText(document: Document, label: String): String {
    document.select("p, li, div, span").forEach { element ->
        val text = element.text()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val match = Regex("$label[：: ]+(.+)").find(text)
        if (match != null) {
            return match.groupValues.getOrNull(1).orEmpty().trim()
        }
    }
    return ""
}

internal fun extractDetailMeta(span: Element): Pair<String, String>? {
    val normalizedOwnText = span.ownText().replace(Regex("\\s+"), " ").trim()
    val normalizedText = span.text().replace(Regex("\\s+"), " ").trim()
    val label = normalizedOwnText
        .substringBefore('：')
        .substringBefore(':')
        .trim()
        .ifBlank {
            normalizedText.substringBefore('：').substringBefore(':').trim()
        }
    if (label.isBlank()) return null

    val childValues = span.children()
        .mapNotNull { child ->
            child.text()
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.isNotBlank() }
        }
        .distinct()

    if (childValues.isNotEmpty()) {
        return label to childValues.joinToString(" / ")
    }

    val parts = normalizedText.split(Regex("[:\\uFF1A]"), limit = 2)
    if (parts.size != 2) return null
    return label to parts[1].trim()
}
