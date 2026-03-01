package eu.kanade.tachiyomi.ui.reader.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

internal fun parseNovelRichContent(rawHtml: String): NovelRichContentParseResult {
    val doc = Jsoup.parse(rawHtml)
    val body = doc.body()
    val unsupported = detectUnsupportedRichFeatures(doc)
    val context = parseBlockStyleContext(doc)
    val blocks = mutableListOf<NovelRichContentBlock>()

    val blockRoots = body.children().ifEmpty {
        doc.children().filterIsInstance<Element>()
    }

    blockRoots.forEach { element ->
        blocks += parseBlockElement(element, context)
    }

    if (blocks.isEmpty()) {
        val segments = parseInlineSegments(body)
        if (segments.isNotEmpty()) {
            blocks += NovelRichContentBlock.Paragraph(segments)
        }
    }

    return NovelRichContentParseResult(
        blocks = blocks,
        unsupportedFeaturesDetected = unsupported,
    )
}

private fun parseBlockElement(
    element: Element,
    context: NovelRichParseContext,
): List<NovelRichContentBlock> {
    val tag = element.tagName().lowercase(Locale.US)
    return when (tag) {
        "p", "div", "article", "section", "main" -> {
            parseParagraphLikeOrContainerBlocks(element, tag, context)
        }
        "script", "style", "head", "meta", "link", "noscript" -> emptyList()
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            val segments = parseInlineSegments(element)
            if (segments.isEmpty()) {
                emptyList()
            } else {
                val level = tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
                listOf(
                    NovelRichContentBlock.Heading(
                        level = level,
                        segments = segments,
                        textAlign = parseBlockTextAlign(element.attr("style")),
                    ),
                )
            }
        }
        "blockquote" -> {
            val segments = parseInlineSegments(element)
            if (segments.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    NovelRichContentBlock.BlockQuote(
                        segments = segments,
                        textAlign = parseBlockTextAlign(element.attr("style")),
                    ),
                )
            }
        }
        "hr" -> listOf(NovelRichContentBlock.HorizontalRule)
        "img" -> {
            val imageUrl = parseImageUrlFromElement(element)
            if (imageUrl.isNullOrBlank()) {
                emptyList()
            } else {
                listOf(NovelRichContentBlock.Image(url = imageUrl, alt = element.attr("alt").ifBlank { null }))
            }
        }
        else -> {
            // Try block-like descendants first (e.g. body/html wrappers), else flatten text.
            val childBlocks = element.children().flatMap { parseBlockElement(it, context) }
            if (childBlocks.isNotEmpty()) {
                childBlocks
            } else {
                val segments = parseInlineSegments(element)
                if (segments.isEmpty()) emptyList() else listOf(NovelRichContentBlock.Paragraph(segments))
            }
        }
    }
}

private fun parseParagraphLikeOrContainerBlocks(
    element: Element,
    tag: String,
    context: NovelRichParseContext,
): List<NovelRichContentBlock> {
    val blocks = mutableListOf<NovelRichContentBlock>()
    val pendingSegments = mutableListOf<NovelRichTextSegment>()
    val blockStyleFromContext = resolveParagraphLikeStyleFromContext(
        element = element,
        context = context,
        tag = tag,
    )
    val blockTextAlign = parseBlockTextAlign(element.attr("style")) ?: blockStyleFromContext.textAlign
    val blockFirstLineIndentEm =
        parseBlockFirstLineIndentEm(element.attr("style")) ?: blockStyleFromContext.firstLineIndentEm

    fun flushParagraph() {
        val merged = mergeAdjacentRichSegments(pendingSegments)
        if (merged.isNotEmpty()) {
            val inferredLeadingIndent = if (blockFirstLineIndentEm == null) {
                inferParagraphLeadingIndentFromSegments(merged)
            } else {
                null
            }
            val normalizedSegments = trimTrailingFormattingWhitespaceFromSegments(
                inferredLeadingIndent?.segments ?: merged,
            )
            if (!hasRenderableParagraphContent(normalizedSegments)) {
                pendingSegments.clear()
                return
            }
            blocks += NovelRichContentBlock.Paragraph(
                segments = normalizedSegments,
                textAlign = blockTextAlign,
                firstLineIndentEm = blockFirstLineIndentEm ?: inferredLeadingIndent?.indentEm,
            )
        }
        pendingSegments.clear()
    }

    element.childNodes().forEach { node ->
        when (node) {
            is Element -> {
                val childTag = node.tagName().lowercase(Locale.US)
                if (childTag == "img") {
                    flushParagraph()
                    val imageUrl = parseImageUrlFromElement(node)
                    if (!imageUrl.isNullOrBlank()) {
                        blocks += NovelRichContentBlock.Image(
                            url = imageUrl,
                            alt = node.attr("alt").ifBlank { null },
                        )
                    }
                    return@forEach
                }

                // Container-like nodes should preserve nested block ordering instead of flattening.
                if (tag != "p" && childTag in richContainerBlockTags) {
                    flushParagraph()
                    blocks += parseBlockElement(node, context)
                    return@forEach
                }
            }
        }

        when (node) {
            is TextNode -> parseInlineNode(node, NovelRichTextStyle(), null, pendingSegments)
            is Element -> parseInlineNode(node, NovelRichTextStyle(), null, pendingSegments)
        }
    }

    flushParagraph()
    return blocks
}

private fun parseInlineSegments(root: Element): List<NovelRichTextSegment> {
    val out = mutableListOf<NovelRichTextSegment>()
    root.childNodes().forEach { node ->
        parseInlineNode(
            node = node,
            inheritedStyle = NovelRichTextStyle(),
            inheritedLink = null,
            out = out,
        )
    }
    return mergeAdjacentRichSegments(out)
}

private fun parseBlockTextAlign(inlineStyle: String): NovelRichBlockTextAlign? {
    if (inlineStyle.isBlank()) return null
    return parseCssTextAlign(parseInlineCssMap(inlineStyle)["text-align"])
}

private fun parseCssTextAlign(raw: String?): NovelRichBlockTextAlign? {
    return when (raw?.trim()?.lowercase(Locale.US)) {
        "left", "start" -> NovelRichBlockTextAlign.LEFT
        "center" -> NovelRichBlockTextAlign.CENTER
        "justify" -> NovelRichBlockTextAlign.JUSTIFY
        "right", "end" -> NovelRichBlockTextAlign.RIGHT
        else -> null
    }
}

private fun parseBlockFirstLineIndentEm(inlineStyle: String): Float? {
    if (inlineStyle.isBlank()) return null
    val raw = parseInlineCssMap(inlineStyle)["text-indent"] ?: return null
    return parseCssLengthAsEm(raw)
}

private fun parseCssLengthAsEm(raw: String): Float? {
    val normalized = raw.trim().lowercase(Locale.US).replace("!important", "").trim()
    if (normalized.isBlank() || normalized == "0" || normalized == "0.0") return 0f
    return when {
        normalized.endsWith("em") -> normalized.removeSuffix("em").trim().toFloatOrNull()
        normalized.endsWith("rem") -> normalized.removeSuffix("rem").trim().toFloatOrNull()
        normalized.endsWith("px") -> normalized.removeSuffix("px").trim().toFloatOrNull()?.div(16f)
        normalized.endsWith("pt") -> normalized.removeSuffix("pt").trim().toFloatOrNull()?.div(12f)
        normalized.endsWith("%") -> normalized.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
        else -> null
    }
}

private fun parseBlockStyleContext(doc: Element): NovelRichParseContext {
    val cssText = doc.select("style").joinToString(separator = "\n") { it.data() + "\n" + it.wholeText() }
    if (cssText.isBlank()) {
        return NovelRichParseContext(selectors = emptyList())
    }
    val selectors = mutableListOf<NovelRichSelectorStyle>()
    val cssRuleRegex = Regex("""(?is)([^{}]+)\{([^}]*)\}""")
    val textIndentRegex = Regex("""(?is)\btext-indent\s*:\s*([^;}]*)""")
    val textAlignRegex = Regex("""(?is)\btext-align\s*:\s*([^;}]*)""")
    cssRuleRegex.findAll(cssText).forEach { rule ->
        val selectorsRaw = rule.groupValues.getOrNull(1).orEmpty()
        val declarations = rule.groupValues.getOrNull(2).orEmpty()
        val rawIndent = textIndentRegex.find(declarations)?.groupValues?.getOrNull(1)?.trim()
        val rawAlign = textAlignRegex.find(declarations)?.groupValues?.getOrNull(1)?.trim()
        val style = NovelRichBlockStyle(
            textAlign = parseCssTextAlign(rawAlign),
            firstLineIndentEm = rawIndent?.let(::parseCssLengthAsEm),
        )
        if (style.textAlign == null && style.firstLineIndentEm == null) return@forEach

        selectorsRaw.split(',')
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .forEach { selector ->
                parseTrailingBlockSelector(selector)?.let { parsed ->
                    selectors += NovelRichSelectorStyle(
                        tag = parsed.tag,
                        className = parsed.className,
                        style = style,
                    )
                }
            }
    }

    return NovelRichParseContext(selectors = selectors)
}

private fun resolveParagraphLikeStyleFromContext(
    element: Element,
    context: NovelRichParseContext,
    tag: String,
): NovelRichBlockStyle {
    if (tag !in paragraphLikeStyleTags) return NovelRichBlockStyle()

    val elementClasses = element.classNames().map { it.lowercase(Locale.US) }.toSet()
    var resolved = NovelRichBlockStyle()
    context.selectors.forEach { selector ->
        if (selector.tag != null && selector.tag != tag) return@forEach
        if (selector.className != null && selector.className !in elementClasses) return@forEach
        resolved = resolved.merge(selector.style)
    }
    return resolved
}

private fun parseTrailingBlockSelector(selector: String): ParsedRichBlockSelector? {
    val trailing = selector
        .split(Regex("""\s*[>+~]\s*|\s+"""))
        .lastOrNull()
        ?.trim()
        ?.lowercase(Locale.US)
        ?: return null
    if (trailing.isBlank()) return null

    val cleaned = trailing
        .replace(Regex("""\[[^\]]*]"""), "")
        .substringBefore(':')
        .trim()
    if (cleaned.isBlank()) return null

    val classOnlyMatch = Regex("""^\.([a-z0-9_-]+)$""").matchEntire(cleaned)
    if (classOnlyMatch != null) {
        return ParsedRichBlockSelector(
            tag = null,
            className = classOnlyMatch.groupValues[1],
        )
    }

    val tagAndClassMatch = Regex("""^([a-z0-9_-]+)(?:\.([a-z0-9_-]+))?$""").matchEntire(cleaned)
    if (tagAndClassMatch != null) {
        val tag = tagAndClassMatch.groupValues[1]
        if (tag !in paragraphLikeStyleTags) return null
        val className = tagAndClassMatch.groupValues.getOrNull(2)?.ifBlank { null }
        return ParsedRichBlockSelector(
            tag = tag,
            className = className,
        )
    }

    return null
}

private fun NovelRichBlockStyle.merge(other: NovelRichBlockStyle): NovelRichBlockStyle {
    return copy(
        textAlign = other.textAlign ?: textAlign,
        firstLineIndentEm = other.firstLineIndentEm ?: firstLineIndentEm,
    )
}

private fun parseInlineNode(
    node: Node,
    inheritedStyle: NovelRichTextStyle,
    inheritedLink: String?,
    out: MutableList<NovelRichTextSegment>,
) {
    when (node) {
        is TextNode -> {
            val text = resolveTextNodeText(node)
            if (text.isEmpty() || text.isBlank()) return
            out += NovelRichTextSegment(
                text = text,
                style = inheritedStyle,
                linkUrl = inheritedLink,
            )
        }
        is Element -> {
            val tag = node.tagName().lowercase(Locale.US)
            if (tag == "br") {
                out += NovelRichTextSegment(text = "\n", style = inheritedStyle, linkUrl = inheritedLink)
                return
            }
            if (tag == "img") return

            val styled = applyRichStyleTagAndInlineCss(
                base = inheritedStyle,
                tag = tag,
                inlineStyle = node.attr("style"),
            )
            val link = node.attr("href").trim().takeIf { tag == "a" && it.isNotBlank() } ?: inheritedLink
            node.childNodes().forEach { child ->
                parseInlineNode(child, styled, link, out)
            }
        }
    }
}

private fun resolveTextNodeText(node: TextNode): String {
    val whole = node.wholeText
    if (whole.any(::isRichIndentSpaceChar)) {
        return whole
    }
    return node.text()
}

private fun parseImageUrlFromElement(element: Element): String? {
    val candidates = listOf(
        element.attr("src"),
        element.attr("data-src"),
        element.attr("data-original"),
        element.attr("data-lazy-src"),
        element.attr("data-url"),
    )
    candidates
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.let { return it }

    val srcSet = element.attr("srcset")
        .ifBlank { element.attr("data-srcset") }
        .trim()
    if (srcSet.isBlank()) return null
    return srcSet
        .split(',')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.substringBefore(' ')
        ?.trim()
}

private fun applyRichStyleTagAndInlineCss(
    base: NovelRichTextStyle,
    tag: String,
    inlineStyle: String,
): NovelRichTextStyle {
    var style = when (tag) {
        "b", "strong" -> base.copy(bold = true)
        "i", "em" -> base.copy(italic = true)
        "u" -> base.copy(underline = true)
        "s", "strike", "del" -> base.copy(strikeThrough = true)
        else -> base
    }

    if (inlineStyle.isBlank()) return style
    parseInlineCssMap(inlineStyle).forEach { (key, value) ->
        when (key) {
            "color" -> style = style.copy(colorCss = value)
            "background", "background-color" -> style = style.copy(backgroundColorCss = value)
            "font-weight" -> if (value == "bold" || value.toIntOrNull()?.let { it >= 600 } == true) {
                style = style.copy(bold = true)
            }
            "font-style" -> if (value == "italic" || value == "oblique") {
                style = style.copy(italic = true)
            }
            "text-decoration" -> {
                val decorations = value.split(' ').map { it.trim() }
                if ("underline" in decorations) style = style.copy(underline = true)
                if ("line-through" in decorations) style = style.copy(strikeThrough = true)
            }
        }
    }

    return style
}

private fun parseInlineCssMap(raw: String): Map<String, String> {
    return raw.split(';')
        .mapNotNull { entry ->
            val idx = entry.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = entry.substring(0, idx).trim().lowercase(Locale.US)
            val value = entry.substring(idx + 1).trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
}

private fun mergeAdjacentRichSegments(segments: List<NovelRichTextSegment>): List<NovelRichTextSegment> {
    if (segments.isEmpty()) return emptyList()
    val merged = ArrayList<NovelRichTextSegment>(segments.size)
    segments.forEach { segment ->
        val last = merged.lastOrNull()
        if (last != null && last.style == segment.style && last.linkUrl == segment.linkUrl) {
            merged[merged.lastIndex] = last.copy(text = last.text + segment.text)
        } else {
            merged += segment
        }
    }
    return merged
}

private fun detectUnsupportedRichFeatures(doc: Element): Boolean {
    if (doc.select("table, iframe, svg").isNotEmpty()) return true

    return doc.select("[style]").any { element ->
        val inlineStyle = element.attr("style").lowercase(Locale.US)
        inlineStyle.contains("position:") ||
            inlineStyle.contains("display:flex") ||
            inlineStyle.contains("display: grid") ||
            inlineStyle.contains("display:grid") ||
            inlineStyle.contains("float:")
    }
}

private val richContainerBlockTags = setOf(
    "p",
    "div",
    "article",
    "section",
    "main",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "blockquote",
    "hr",
)

private val paragraphLikeStyleTags = setOf(
    "p",
    "div",
    "article",
    "section",
    "main",
)

private fun inferParagraphLeadingIndentFromSegments(
    segments: List<NovelRichTextSegment>,
): InferredParagraphIndent? {
    if (segments.isEmpty()) return null
    val first = segments.first()
    val (indentEm, consumedChars) = parseLeadingWhitespaceIndentEm(first.text) ?: return null
    val strippedFirstText = first.text.drop(consumedChars)
    val updatedSegments = segments.toMutableList()
    if (strippedFirstText.isEmpty()) {
        updatedSegments.removeAt(0)
    } else {
        updatedSegments[0] = first.copy(text = strippedFirstText)
    }
    if (updatedSegments.isEmpty()) return null
    return InferredParagraphIndent(
        indentEm = indentEm,
        segments = updatedSegments,
    )
}

private fun parseLeadingWhitespaceIndentEm(text: String): Pair<Float, Int>? {
    if (text.isEmpty()) return null
    var index = 0
    while (index < text.length && isHtmlFormattingWhitespace(text[index])) {
        index++
    }
    val markerStart = index
    var indentEm = 0f
    while (index < text.length) {
        val char = text[index]
        val charIndent = richIndentSpaceEm(char) ?: break
        indentEm += charIndent
        index++
    }
    if (index == markerStart || indentEm < 0.5f) return null
    return indentEm to index
}

private fun trimTrailingFormattingWhitespaceFromSegments(
    segments: List<NovelRichTextSegment>,
): List<NovelRichTextSegment> {
    if (segments.isEmpty()) return segments
    val mutable = segments.toMutableList()
    var index = mutable.lastIndex
    while (index >= 0) {
        val segment = mutable[index]
        val trimmedText = segment.text.trimEnd(::isHtmlFormattingWhitespace)
        if (trimmedText.isNotEmpty()) {
            mutable[index] = segment.copy(text = trimmedText)
            break
        }
        mutable.removeAt(index)
        index--
    }
    return mutable
}

private fun hasRenderableParagraphContent(
    segments: List<NovelRichTextSegment>,
): Boolean {
    return segments.any { segment ->
        segment.text.any(::isRenderableParagraphChar)
    }
}

private fun isRenderableParagraphChar(char: Char): Boolean {
    return !isHtmlFormattingWhitespace(char) && !isRichIndentSpaceChar(char)
}

private fun isHtmlFormattingWhitespace(char: Char): Boolean {
    return char == ' ' || char == '\t' || char == '\n' || char == '\r'
}

private fun isRichIndentSpaceChar(char: Char): Boolean {
    return richIndentSpaceEm(char) != null
}

private fun richIndentSpaceEm(char: Char): Float? {
    return when (char) {
        '\u3000' -> 1f // Ideographic space.
        '\u2001', '\u2003' -> 1f // Em quad / em space.
        '\u2000', '\u2002' -> 0.5f // En quad / en space.
        '\u2004' -> 0.333f // Three-per-em space.
        '\u2005', '\u2008', '\u00A0' -> 0.25f // Four-per-em / punctuation / nbsp.
        '\u2006' -> 0.167f // Six-per-em space.
        '\u2007' -> 0.5f // Figure space (digit width; approximate as half-em).
        '\u2009' -> 0.2f // Thin space.
        '\u200A' -> 0.1f // Hair space.
        '\u205F' -> 0.222f // Medium mathematical space.
        else -> null
    }
}

private data class NovelRichParseContext(
    val selectors: List<NovelRichSelectorStyle>,
)

private data class NovelRichSelectorStyle(
    val tag: String?,
    val className: String?,
    val style: NovelRichBlockStyle,
)

private data class NovelRichBlockStyle(
    val textAlign: NovelRichBlockTextAlign? = null,
    val firstLineIndentEm: Float? = null,
)

private data class ParsedRichBlockSelector(
    val tag: String?,
    val className: String?,
)

private data class InferredParagraphIndent(
    val indentEm: Float,
    val segments: List<NovelRichTextSegment>,
)
