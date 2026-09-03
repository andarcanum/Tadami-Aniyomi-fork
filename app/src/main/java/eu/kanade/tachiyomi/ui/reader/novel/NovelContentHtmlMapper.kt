package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel.ContentBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stateless HTML/content mapping helpers for the novel reader.
 *
 * Builds display HTML from translated content, projects translated text onto rich segments and
 * applies the translation to a chapter template while preserving inline markup. Extracted from
 * [NovelReaderScreenModel] so the screen model stays focused on reader orchestration.
 */
internal object NovelContentHtmlMapper {

    private const val TRANSLATED_TEXT_STRONG_BOUNDARY_CHARS = ".,!?;:…)]}»”’"
    private const val TRANSLATED_TEXT_OPENING_BOUNDARY_CHARS = "([{«“‘"
    private const val TRANSLATED_TEXT_SOFT_BOUNDARY_CHARS = "—–-"

    fun buildTranslatedRawHtmlForDisplay(
        templateHtml: String,
        fallbackBlocks: List<ContentBlock>,
        translatedByIndex: Map<Int, String>,
    ): String {
        if (translatedByIndex.isEmpty()) return buildRawHtmlFromContentBlocks(fallbackBlocks)
        return buildTranslatedHtmlFromTemplate(
            templateHtml = templateHtml,
            translatedByIndex = translatedByIndex,
        ) ?: buildRawHtmlFromContentBlocks(fallbackBlocks)
    }

    fun buildTranslatedHtmlFromTemplate(
        templateHtml: String,
        translatedByIndex: Map<Int, String>,
    ): String? {
        if (templateHtml.isBlank() || translatedByIndex.isEmpty()) return null
        return runCatching {
            val document = Jsoup.parse(templateHtml)
            document.outputSettings().prettyPrint(false)
            // Canonical collect-space walk (shared with extractTextBlocks and the reader): the
            // translatedByIndex keys address text blocks in THIS order. The old select-based walk
            // double-counted paragraph-like tags nested inside blockquotes and never saw loose
            // text nodes, shifting every following translation onto the wrong paragraph.
            val pairs = mutableListOf<Pair<Node, ContentBlock>>()
            collectContentNodes(
                node = document.body(),
                out = pairs,
                chapterWebUrl = null,
                novelUrl = "",
                pluginSite = null,
            )
            val textPairs = pairs.mapNotNull { (node, block) ->
                if (block is ContentBlock.Text) node to block else null
            }
            if (textPairs.isEmpty()) return@runCatching null
            // An element that emitted several text blocks (structured fragment) cannot be replaced
            // 1:1; those indices keep the original markup.
            val textBlocksPerNode = textPairs.groupingBy { it.first }.eachCount()

            var textIndex = 0
            var replacedCount = 0
            textPairs.forEach { (node, _) ->
                val translated = translatedByIndex[textIndex]
                textIndex += 1
                if (translated.isNullOrBlank()) return@forEach
                if (textBlocksPerNode.getValue(node) != 1) return@forEach
                when (node) {
                    is TextNode -> node.text(translated.sanitizeTranslatedDisplayText())
                    is Element -> replaceElementTextPreservingInlineMarkup(
                        element = node,
                        translatedText = translated.normalizedForHtmlElement(node),
                    )
                    else -> Unit
                }
                replacedCount += 1
            }
            if (replacedCount <= 0) return@runCatching null
            if (templateHtml.contains("<html", ignoreCase = true)) {
                document.outerHtml()
            } else {
                document.body().html()
            }
        }.getOrNull()
    }

    private fun buildRawHtmlFromContentBlocks(blocks: List<ContentBlock>): String {
        return buildString {
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Image -> {
                        append("<img src=\"")
                        append(block.url.escapeHtmlAttribute())
                        append("\" alt=\"")
                        append((block.alt ?: "").escapeHtmlAttribute())
                        append("\" />")
                    }
                    is ContentBlock.Text -> {
                        append("<p>")
                        append(block.text.escapeHtml())
                        append("</p>")
                    }
                }
            }
        }
    }

    private fun replaceElementTextPreservingInlineMarkup(
        element: Element,
        translatedText: String,
    ) {
        val cleanedText = translatedText.sanitizeTranslatedDisplayText()
        if (cleanedText.isBlank()) return

        val textNodes = mutableListOf<TextNode>()
        collectInlineTextNodes(element, textNodes)
        if (textNodes.isEmpty()) {
            element.text(cleanedText)
            return
        }

        val pieces = splitTranslatedTextBySourceWeights(
            sourceParts = textNodes.map { it.text() },
            translatedText = cleanedText,
        )
        textNodes.forEachIndexed { index, textNode ->
            textNode.text(pieces.getOrNull(index).orEmpty())
        }
    }

    private fun collectInlineTextNodes(
        node: Node,
        out: MutableList<TextNode>,
    ) {
        when (node) {
            is TextNode -> {
                if (node.text().isNotBlank()) {
                    out += node
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag == "script" || tag == "style" || tag == "noscript") return
                node.childNodes().forEach { child ->
                    collectInlineTextNodes(child, out)
                }
            }
        }
    }

    fun projectTranslatedTextOntoRichSegments(
        originalSegments: List<NovelRichTextSegment>,
        translatedText: String,
    ): List<NovelRichTextSegment> {
        val cleanedText = translatedText.sanitizeTranslatedDisplayText()
        if (cleanedText.isBlank()) return originalSegments
        if (originalSegments.isEmpty()) return listOf(NovelRichTextSegment(cleanedText))
        if (originalSegments.size == 1) return listOf(originalSegments.first().copy(text = cleanedText))

        val pieces = splitTranslatedTextBySourceWeights(
            sourceParts = originalSegments.map { it.text },
            translatedText = cleanedText,
        )
        return originalSegments.mapIndexedNotNull { index, segment ->
            val piece = pieces.getOrNull(index).orEmpty()
            when {
                piece.isEmpty() && segment.text.isNotEmpty() -> null
                else -> segment.copy(text = piece)
            }
        }.ifEmpty {
            listOf(originalSegments.first().copy(text = cleanedText))
        }
    }

    private fun splitTranslatedTextBySourceWeights(
        sourceParts: List<String>,
        translatedText: String,
    ): List<String> {
        if (sourceParts.isEmpty()) return emptyList()
        val cleanedText = translatedText.sanitizeTranslatedDisplayText()
        if (sourceParts.size == 1) return listOf(cleanedText)
        if (cleanedText.isEmpty()) return List(sourceParts.size) { "" }

        val weights = sourceParts.map { part ->
            part.count { char -> !char.isWhitespace() }.coerceAtLeast(0)
        }
        val totalWeight = weights.sum()
        if (totalWeight <= 0) {
            return List(sourceParts.size) { index -> if (index == 0) cleanedText else "" }
        }

        val boundaries = mutableListOf<Int>()
        var cumulativeWeight = 0
        var previousBoundary = 0
        weights.dropLast(1).forEach { weight ->
            cumulativeWeight += weight
            val preferred = ((cleanedText.length.toFloat() * cumulativeWeight.toFloat()) / totalWeight.toFloat())
                .roundToInt()
                .coerceIn(previousBoundary, cleanedText.length)
            val boundary = findNearestTranslatedTextBoundary(
                text = cleanedText,
                preferred = preferred,
                min = previousBoundary,
            )
            boundaries += boundary
            previousBoundary = boundary
        }

        val pieces = mutableListOf<String>()
        var start = 0
        boundaries.forEach { boundary ->
            pieces += cleanedText.substring(start, boundary)
            start = boundary
        }
        pieces += cleanedText.substring(start)
        return pieces
    }

    private fun findNearestTranslatedTextBoundary(
        text: String,
        preferred: Int,
        min: Int,
    ): Int {
        if (preferred <= min) return min
        if (preferred >= text.length) return text.length
        val radius = maxOf(8, text.length / 32)
        var best = preferred
        var bestScore = boundaryScore(text, preferred) * 100
        val start = maxOf(min, preferred - radius)
        val end = minOf(text.length, preferred + radius)
        for (candidate in start..end) {
            val score = boundaryScore(text, candidate) * 100 + abs(candidate - preferred)
            if (score < bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best.coerceIn(min, text.length)
    }

    private fun boundaryScore(text: String, index: Int): Int {
        if (index <= 0 || index >= text.length) return 0
        val before = text[index - 1]
        val after = text[index]
        return when {
            before.isWhitespace() || after.isWhitespace() -> 0
            before in TRANSLATED_TEXT_STRONG_BOUNDARY_CHARS -> 1
            after in TRANSLATED_TEXT_OPENING_BOUNDARY_CHARS -> 1
            before in TRANSLATED_TEXT_SOFT_BOUNDARY_CHARS -> 2
            else -> 8
        }
    }

    private fun String.normalizedForHtmlElement(element: Element): String {
        val cleaned = sanitizeTranslatedDisplayText()
        if (!element.tagName().equals("li", ignoreCase = true)) return cleaned
        return cleaned
            .removePrefix("•")
            .removePrefix("-")
            .removePrefix("*")
            .trimStart()
    }

    private fun String.sanitizeTranslatedDisplayText(): String {
        return replace('\u00A0', ' ')
            .replace("\r", "")
            .trim()
    }
}
