package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.util.Locale
import top.jlen.vod.data.AppNotice

private val NoAnnouncementLinkHandler: (String) -> Unit = {}

@Composable
fun AnnouncementRichContent(
    notice: AppNotice,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = NoAnnouncementLinkHandler
) {
    AnnouncementRichHtmlContent(
        htmlContent = notice.htmlContent,
        fallbackContent = notice.displayContent,
        modifier = modifier,
        onOpenLink = onOpenLink
    )
}

@Composable
fun AnnouncementRichHtmlContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
    fallbackContent: String = htmlContent,
    onOpenLink: (String) -> Unit = NoAnnouncementLinkHandler
) {
    val richBlocks = remember(htmlContent) { parseAnnouncementHtmlBlocks(htmlContent) }

    if (richBlocks.isEmpty()) {
        AnnouncementRichText(content = fallbackContent.stripAnnouncementCodeFenceForDisplay())
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            richBlocks.forEach { block ->
                AnnouncementRichBlockText(
                    block = block,
                    onOpenLink = onOpenLink
                )
            }
        }
    }
}

@Composable
fun AnnouncementRichInlineText(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = UiPalette.Ink,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    onOpenLink: (String) -> Unit = NoAnnouncementLinkHandler
) {
    val annotated = remember(content) { parseAnnouncementInlineHtml(content) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations(AnnouncementUrlTag, 0, annotated.length).isNotEmpty()
    }
    val textModifier = if (hasLinks && onOpenLink !== NoAnnouncementLinkHandler) {
        modifier.pointerInput(annotated, onOpenLink) {
            detectTapGestures { offset ->
                val layout = layoutResult ?: return@detectTapGestures
                val textOffset = layout.getOffsetForPosition(offset)
                annotated.getStringAnnotations(AnnouncementUrlTag, textOffset, textOffset)
                    .firstOrNull()
                    ?.item
                    ?.let(onOpenLink)
            }
        }
    } else {
        modifier
    }
    Text(
        text = annotated,
        modifier = textModifier,
        onTextLayout = { layoutResult = it },
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign
    )
}

@Composable
private fun AnnouncementRichText(content: String) {
    val blocks = remember(content) { content.toAnnouncementPlainBlocks() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            val text = block.text
            val lines = block
                .text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

            when {
                lines.isEmpty() -> Unit
                block.kind == AnnouncementPlainBlockKind.List -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        lines.forEach { line ->
                            val label = line.removeAnnouncementListPrefix()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(6.dp)
                                        .background(UiPalette.Accent, CircleShape)
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = UiPalette.Ink,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                )
                            }
                        }
                    }
                }

                block.kind == AnnouncementPlainBlockKind.Heading ||
                    block.kind == AnnouncementPlainBlockKind.Title ||
                    (lines.size == 1 && text.length <= 20) -> {
                    Text(
                        text = text.removeAnnouncementMarkdownHeadingPrefix(),
                        style = if (block.kind == AnnouncementPlainBlockKind.Title) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = if (block.kind == AnnouncementPlainBlockKind.Title) {
                            FontWeight.ExtraBold
                        } else {
                            FontWeight.Bold
                        },
                        color = UiPalette.Ink
                    )
                }

                else -> {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = UiPalette.Ink,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementRichBlockText(
    block: AnnouncementRichBlock,
    onOpenLink: (String) -> Unit
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = block.text,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(block.text, onOpenLink) {
                detectTapGestures { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val textOffset = layout.getOffsetForPosition(offset)
                    block.text.getStringAnnotations(AnnouncementUrlTag, textOffset, textOffset)
                        .firstOrNull()
                        ?.item
                        ?.let(onOpenLink)
                }
            },
        onTextLayout = { layoutResult = it },
        style = when (block.kind) {
            AnnouncementBlockKind.Title -> MaterialTheme.typography.titleLarge
            AnnouncementBlockKind.Heading -> MaterialTheme.typography.titleMedium
            AnnouncementBlockKind.Paragraph -> MaterialTheme.typography.bodyMedium
        },
        fontWeight = when (block.kind) {
            AnnouncementBlockKind.Title -> FontWeight.ExtraBold
            AnnouncementBlockKind.Heading -> FontWeight.Bold
            AnnouncementBlockKind.Paragraph -> FontWeight.Normal
        },
        color = block.textColor ?: UiPalette.Ink,
        textAlign = block.alignment,
        lineHeight = when (block.kind) {
            AnnouncementBlockKind.Title -> MaterialTheme.typography.titleLarge.lineHeight
            AnnouncementBlockKind.Heading -> MaterialTheme.typography.titleMedium.lineHeight
            AnnouncementBlockKind.Paragraph -> MaterialTheme.typography.bodyMedium.lineHeight
        }
    )
}

internal enum class AnnouncementBlockKind {
    Title,
    Heading,
    Paragraph
}

internal data class AnnouncementRichBlock(
    val text: AnnotatedString,
    val alignment: TextAlign = TextAlign.Start,
    val kind: AnnouncementBlockKind = AnnouncementBlockKind.Paragraph,
    val textColor: Color? = null
)

internal const val AnnouncementUrlTag = "announcement_url"
private val bareAnnouncementUrlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")

internal fun isAnnouncementListLine(text: String): Boolean =
    text.startsWith("- ") ||
        text.startsWith("* ") ||
        text.startsWith("•") ||
        Regex("^\\d+[.、]\\s*.+").matches(text)

internal fun String.removeAnnouncementListPrefix(): String =
    replaceFirst(Regex("^(-|\\*|•)\\s*"), "")
        .replaceFirst(Regex("^\\d+[.、]\\s*"), "")
        .trim()

internal enum class AnnouncementPlainBlockKind {
    Title,
    Heading,
    Paragraph,
    List
}

internal data class AnnouncementPlainBlock(
    val text: String,
    val kind: AnnouncementPlainBlockKind = AnnouncementPlainBlockKind.Paragraph
)

internal fun String.toAnnouncementPlainBlocks(): List<AnnouncementPlainBlock> {
    val normalized = stripAnnouncementCodeFenceForDisplay()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map { it.trimEnd() }
        .toList()
    val blocks = mutableListOf<AnnouncementPlainBlock>()
    val pending = mutableListOf<String>()

    fun flushParagraph() {
        val text = pending.joinToString("\n").trim()
        if (text.isNotBlank()) {
            blocks += AnnouncementPlainBlock(text)
        }
        pending.clear()
    }

    normalized.forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> flushParagraph()
            line.startsWith("### ") || line.startsWith("## ") -> {
                flushParagraph()
                blocks += AnnouncementPlainBlock(
                    text = line.removeAnnouncementMarkdownHeadingPrefix(),
                    kind = if (line.startsWith("## ")) AnnouncementPlainBlockKind.Title else AnnouncementPlainBlockKind.Heading
                )
            }
            isAnnouncementListLine(line) -> {
                flushParagraph()
                blocks += AnnouncementPlainBlock(
                    text = line,
                    kind = AnnouncementPlainBlockKind.List
                )
            }
            else -> pending += line
        }
    }
    flushParagraph()
    return blocks
}

private fun String.removeAnnouncementMarkdownHeadingPrefix(): String =
    replaceFirst(Regex("^#{1,6}\\s*"), "").trim()

internal fun parseAnnouncementHtmlBlocks(html: String): List<AnnouncementRichBlock> {
    val normalized = Parser.unescapeEntities(html.stripAnnouncementCodeFence(), false).trim()
    if (normalized.isBlank() || !normalized.contains('<')) return emptyList()

    val body = Jsoup.parseBodyFragment(normalized).body()
    val blocks = mutableListOf<AnnouncementRichBlock>()
    body.childNodes().forEach { node ->
        blocks += node.toAnnouncementBlocks()
    }
    return blocks.filter { it.text.text.isNotBlank() }
}

internal fun parseAnnouncementInlineHtml(html: String): AnnotatedString {
    val normalized = Parser.unescapeEntities(html.stripAnnouncementCodeFence(), false).trim()
    if (normalized.isBlank()) return AnnotatedString("")

    val blocks = parseAnnouncementHtmlBlocks(normalized)
    if (blocks.isNotEmpty()) {
        return buildAnnotatedString {
            blocks.forEachIndexed { index, block ->
                if (index > 0 && !endsWithWhitespace()) {
                    append(' ')
                }
                append(block.text.trimAnnouncementInlineText())
            }
        }
    }

    return AnnotatedString(normalized.replace(Regex("<[^>]+>"), "").trim())
}

private fun Node.toAnnouncementBlocks(): List<AnnouncementRichBlock> {
    return when (this) {
        is TextNode -> text()
            .trim()
            .takeIf(String::isNotBlank)
            ?.let {
                listOf(
                    AnnouncementRichBlock(
                        text = AnnotatedString(it),
                        kind = AnnouncementBlockKind.Paragraph
                    )
                )
            }
            .orEmpty()

        is Element -> when (tagName().lowercase(Locale.ROOT)) {
            in announcementContainerTags ->
                if (children().any { it.isAnnouncementBlockElement() }) {
                    childNodes().flatMap { it.toAnnouncementBlocks() }
                } else {
                    val text = buildAnnouncementAnnotatedString(this)
                    if (text.text.isBlank()) emptyList() else {
                        listOf(
                            AnnouncementRichBlock(
                                text = text,
                                alignment = resolveAnnouncementAlignment(),
                                kind = resolveAnnouncementBlockKind(),
                                textColor = resolveAnnouncementTextColor()
                            )
                        )
                    }
                }

            "ul", "ol" -> children().flatMapIndexed { index, child ->
                val childText = buildAnnouncementAnnotatedString(child).text.trim()
                if (childText.isBlank()) {
                    emptyList()
                } else {
                    val prefix = if (tagName().equals("ol", ignoreCase = true)) "${index + 1}. " else "* "
                    listOf(
                        AnnouncementRichBlock(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = UiPalette.Accent, fontWeight = FontWeight.Bold)) {
                                    append(prefix)
                                }
                                append(buildAnnouncementAnnotatedString(child))
                            },
                            alignment = child.resolveAnnouncementAlignment(),
                            kind = AnnouncementBlockKind.Paragraph,
                            textColor = child.resolveAnnouncementTextColor()
                        )
                    )
                }
            }

            "br" -> emptyList()
            else -> {
                val text = buildAnnouncementAnnotatedString(this)
                if (text.text.isBlank()) {
                    children().flatMap { it.toAnnouncementBlocks() }
                } else {
                    listOf(
                        AnnouncementRichBlock(
                            text = text,
                            alignment = resolveAnnouncementAlignment(),
                            kind = resolveAnnouncementBlockKind(),
                            textColor = resolveAnnouncementTextColor()
                        )
                    )
                }
            }
        }

        else -> emptyList()
    }
}

private fun buildAnnouncementAnnotatedString(node: Node): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendAnnouncementNode(builder, node)
    return builder.toAnnotatedString()
}

private fun appendAnnouncementNode(builder: AnnotatedString.Builder, node: Node) {
    when (node) {
        is TextNode -> {
            val normalized = node.text()
                .replace(Regex("\\s+"), " ")
            builder.appendAnnouncementTextWithBareLinks(normalized)
        }
        is Element -> {
            if (node.tagName().equals("br", ignoreCase = true)) {
                builder.append('\n')
                return
            }

            val style = node.resolveAnnouncementSpanStyle()
            val href = node.takeIf { it.tagName().equals("a", ignoreCase = true) }
                ?.attr("href")
                ?.normalizeAnnouncementUrl()
                ?.takeIf(String::isNotBlank)
            val handledBlock = node.tagName().lowercase(Locale.ROOT) in setOf("p", "div", "section", "article")
            if (href != null) {
                builder.pushStringAnnotation(AnnouncementUrlTag, href)
                builder.pushStyle(announcementLinkStyle())
            }
            if (style != null) {
                builder.pushStyle(style)
            }
            node.childNodes().forEach { child ->
                appendAnnouncementNode(builder, child)
            }
            if (style != null) {
                builder.pop()
            }
            if (href != null) {
                builder.pop()
                builder.pop()
            }
            if (handledBlock && !builder.endsWithLineBreak()) {
                builder.append('\n')
            }
        }
    }
}

private fun Element.resolveAnnouncementBlockKind(): AnnouncementBlockKind {
    val tag = tagName().lowercase(Locale.ROOT)
    return when {
        tag in setOf("h1", "h2") -> AnnouncementBlockKind.Title
        tag in setOf("h3", "h4", "h5", "h6") -> AnnouncementBlockKind.Heading
        tag == "p" && text().trim().length <= 20 && containsBoldContent() -> AnnouncementBlockKind.Heading
        else -> AnnouncementBlockKind.Paragraph
    }
}

private fun Element.resolveAnnouncementAlignment(): TextAlign {
    val style = attr("style").compactAnnouncementStyle()
    val align = attr("align").trim().lowercase(Locale.ROOT)
    return when {
        style.contains("text-align:center") ||
            align == "center" ||
            tagName().equals("center", ignoreCase = true) -> TextAlign.Center
        style.contains("text-align:right") || align == "right" -> TextAlign.End
        else -> TextAlign.Start
    }
}

private fun Element.resolveAnnouncementTextColor(): Color? {
    return styleValue("color")
        .takeIf(String::isNotBlank)
        ?.parseAnnouncementColor()
        ?: attr("color")
            .takeIf(String::isNotBlank)
            ?.parseAnnouncementColor()
}

private fun Element.resolveAnnouncementSpanStyle(): SpanStyle? {
    var hasStyle = false
    var color: Color? = null
    var background: Color? = null
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textDecoration: TextDecoration? = null
    var fontSize = androidx.compose.ui.unit.TextUnit.Unspecified

    when (tagName().lowercase(Locale.ROOT)) {
        "b", "strong" -> {
            fontWeight = FontWeight.Bold
            hasStyle = true
        }
        "i", "em" -> {
            fontStyle = FontStyle.Italic
            hasStyle = true
        }
        "u", "ins" -> {
            textDecoration = TextDecoration.Underline
            hasStyle = true
        }
        "s", "strike", "del" -> {
            textDecoration = TextDecoration.LineThrough
            hasStyle = true
        }
        "font" -> {
            attr("size").parseAnnouncementFontTagSize()?.let {
                fontSize = it
                hasStyle = true
            }
        }
    }

    resolveAnnouncementTextColor()?.let {
        color = it
        hasStyle = true
    }
    resolveAnnouncementBackgroundColor()?.let {
        background = it
        hasStyle = true
    }
    resolveAnnouncementFontWeight()?.let {
        fontWeight = it
        hasStyle = true
    }
    resolveAnnouncementFontSize()?.let {
        fontSize = it
        hasStyle = true
    }
    if (hasAnnouncementFontStyle("italic", "oblique")) {
        fontStyle = FontStyle.Italic
        hasStyle = true
    }
    resolveAnnouncementTextDecoration()?.let {
        textDecoration = it
        hasStyle = true
    }

    if (!hasStyle) return null
    return SpanStyle(
        color = color ?: Color.Unspecified,
        background = background ?: Color.Unspecified,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSize = fontSize,
        textDecoration = textDecoration
    )
}

private fun Element.resolveAnnouncementBackgroundColor(): Color? =
    (styleValue("background-color").ifBlank { styleValue("background") })
        .takeIf(String::isNotBlank)
        ?.parseAnnouncementColor()

private fun Element.resolveAnnouncementFontWeight(): FontWeight? {
    val raw = styleValue("font-weight").lowercase(Locale.ROOT)
    return when {
        raw in setOf("bold", "bolder", "600", "700", "800", "900") -> FontWeight.Bold
        raw == "500" -> FontWeight.Medium
        else -> null
    }
}

private fun Element.resolveAnnouncementFontSize() =
    styleValue("font-size").parseAnnouncementFontSize()

private fun Element.hasAnnouncementFontStyle(vararg values: String): Boolean {
    val raw = styleValue("font-style").lowercase(Locale.ROOT)
    return values.any { raw.contains(it) }
}

private fun Element.resolveAnnouncementTextDecoration(): TextDecoration? {
    val raw = listOf(
        styleValue("text-decoration"),
        styleValue("text-decoration-line")
    ).joinToString(" ").lowercase(Locale.ROOT)
    return when {
        raw.contains("line-through") -> TextDecoration.LineThrough
        raw.contains("underline") -> TextDecoration.Underline
        else -> null
    }
}

private fun Element.styleValue(name: String): String {
    val escaped = Regex.escape(name)
    return Regex("""(?:^|;)\s*$escaped\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
        .find(attr("style"))
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
}

private fun String.compactAnnouncementStyle(): String =
    lowercase(Locale.ROOT).replace(Regex("\\s+"), "")

private fun String.parseAnnouncementFontSize() =
    trim()
        .lowercase(Locale.ROOT)
        .let { raw ->
            when {
                raw.endsWith("px") -> raw.removeSuffix("px").toFloatOrNull()?.sp
                raw.endsWith("sp") -> raw.removeSuffix("sp").toFloatOrNull()?.sp
                raw.endsWith("pt") -> raw.removeSuffix("pt").toFloatOrNull()?.let { (it * 1.333f).sp }
                raw.endsWith("em") -> raw.removeSuffix("em").toFloatOrNull()?.let { (it * 16f).sp }
                raw in setOf("xx-small", "x-small") -> 11.sp
                raw == "small" -> 13.sp
                raw == "medium" -> 16.sp
                raw == "large" -> 18.sp
                raw == "x-large" -> 22.sp
                raw == "xx-large" -> 26.sp
                else -> null
            }
        }

private fun String.parseAnnouncementFontTagSize() =
    trim().toIntOrNull()?.let { size ->
        when (size.coerceIn(1, 7)) {
            1 -> 11.sp
            2 -> 13.sp
            3 -> 16.sp
            4 -> 18.sp
            5 -> 22.sp
            6 -> 26.sp
            else -> 30.sp
        }
    }

private fun String.parseAnnouncementColor(): Color? {
    val raw = trim()
        .trim('"', '\'')
        .replace(Regex("""\s*!important$""", RegexOption.IGNORE_CASE), "")
        .trim()
    if (raw.isBlank()) return null
    val androidColor = raw.toAndroidColorString()
    return runCatching { Color(android.graphics.Color.parseColor(androidColor)) }
        .getOrNull()
        ?: parseAnnouncementRgbColor(raw)
}

private fun String.toAndroidColorString(): String =
    when (trim().lowercase(Locale.ROOT)) {
        "red", "crimson" -> "#d32f2f"
        "orange" -> "#f57c00"
        "yellow" -> "#f9a825"
        "green" -> "#2e7d32"
        "blue" -> "#1976d2"
        "purple" -> "#7b1fa2"
        "pink" -> "#c2185b"
        "gray", "grey" -> "#757575"
        "black" -> "#000000"
        "white" -> "#ffffff"
        "transparent" -> "#00000000"
        else -> trim()
    }

private fun parseAnnouncementRgbColor(raw: String): Color? {
    val match = Regex("""rgba?\(([^)]+)\)""", RegexOption.IGNORE_CASE).find(raw) ?: return null
    val parts = match.groupValues[1]
        .split(',')
        .map { it.trim() }
    if (parts.size < 3) return null
    fun String.channel(): Int? =
        if (endsWith("%")) {
            removeSuffix("%").toFloatOrNull()?.let { (it * 2.55f).toInt().coerceIn(0, 255) }
        } else {
            toFloatOrNull()?.toInt()?.coerceIn(0, 255)
        }
    val red = parts[0].channel() ?: return null
    val green = parts[1].channel() ?: return null
    val blue = parts[2].channel() ?: return null
    val alpha = parts.getOrNull(3)
        ?.toFloatOrNull()
        ?.coerceIn(0f, 1f)
        ?: 1f
    return Color(red, green, blue, (alpha * 255).toInt().coerceIn(0, 255))
}

private fun Element.containsBoldContent(): Boolean =
    select("strong, b").isNotEmpty() ||
        styleValue("font-weight").lowercase(Locale.ROOT) in setOf("bold", "bolder", "600", "700", "800", "900")

private fun AnnotatedString.Builder.endsWithLineBreak(): Boolean =
    length > 0 && toAnnotatedString().text.last() == '\n'

private fun AnnotatedString.Builder.endsWithWhitespace(): Boolean =
    length > 0 && toAnnotatedString().text.last().isWhitespace()

private fun AnnotatedString.trimAnnouncementInlineText(): AnnotatedString {
    val raw = text
    val start = raw.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val endExclusive = raw.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, endExclusive)
}

private fun Element.isAnnouncementBlockElement(): Boolean =
    tagName().lowercase(Locale.ROOT) in announcementBlockTags

private val announcementContainerTags = setOf("div", "section", "article", "main")

private val announcementBlockTags = setOf(
    "div",
    "section",
    "article",
    "p",
    "ul",
    "ol",
    "li",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "center"
)

private fun String.stripAnnouncementCodeFence(): String =
    trim()
        .replace(Regex("""(?is)^```\s*(?:html)?\s*"""), "")
        .replace(Regex("""(?is)\s*```\s*$"""), "")
        .trim()

private fun String.stripAnnouncementCodeFenceForDisplay(): String =
    trim()
        .replace(Regex("""(?is)^```\s*(?:html)?\s*"""), "")
        .replace(Regex("""(?is)\s*```\s*$"""), "")
        .trim()

private fun AnnotatedString.Builder.appendAnnouncementTextWithBareLinks(text: String) {
    if (text.isBlank()) return
    var index = 0
    bareAnnouncementUrlRegex.findAll(text).forEach { match ->
        if (match.range.first > index) {
            append(text.substring(index, match.range.first))
        }
        val rawUrl = match.value.trimEnd('.', ',', '，', '。', '、', ')', '）')
        val trailing = match.value.substring(rawUrl.length)
        val url = rawUrl.normalizeAnnouncementUrl()
        if (url.isBlank()) {
            append(match.value)
        } else {
            pushStringAnnotation(AnnouncementUrlTag, url)
            withStyle(announcementLinkStyle()) {
                append(rawUrl)
            }
            pop()
            append(trailing)
        }
        index = match.range.last + 1
    }
    if (index < text.length) {
        append(text.substring(index))
    }
}

fun String.normalizeAnnouncementUrl(): String {
    val normalized = trim()
    if (normalized.isBlank()) return ""
    return when {
        normalized.contains("://") -> normalized
        normalized.startsWith("www.", ignoreCase = true) -> "https://$normalized"
        normalized.contains('.') && !normalized.contains(' ') -> "https://$normalized"
        else -> normalized
    }
}

private fun announcementLinkStyle(): SpanStyle =
    SpanStyle(
        color = UiPalette.Accent,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline
    )
