package eu.kanade.domain.description

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Turns any raw description blob (flat wall of text, CRLF line-per-sentence, HTML-ish, or already
 * structured markdown) into a uniform list of [DescriptionBlock]s.
 *
 * Design constraints:
 * - Never loses text (except a consciously skipped `---` divider).
 * - Never breaks text that is already well structured (markdown from sources is preserved).
 * - Never guesses structure on short prose (a "honest wall" stays one paragraph).
 */
object DescriptionEngine {

    const val VERSION = 1

    /** Min length of a wall-of-text paragraph before sentence splitting kicks in. */
    private const val SPLIT_THRESHOLD = 600

    /** Sentences per generated paragraph when splitting a wall of text. */
    private const val SENTENCES_PER_PARAGRAPH = 2

    /** Max length of a leading label inside a paragraph before a list of links. */
    private const val LABEL_MAX_LENGTH = 40

    private val sectionLabels = listOf(
        "альтернативные названия",
        "альтернативное название",
        "альт. названия",
        "альт. название",
        "альт.названия",
        "альт.название",
        "alternative titles",
        "alternative title",
        "alt. titles",
        "alt. title",
        "alt titles",
        "alt title",
        "другие названия",
        "другое название",
        "original webcomic",
        "official translations",
        "оригинальный вебкомикс",
        "официальные переводы",
    )
    private val rowLabels = listOf(
        "rank", "rating", "рейтинг",
        "author", "автор", "авторы", "artist", "художник", "художники",
        "status", "статус", "notes", "примечания", "source", "источник", "written by",
        "год выпуска", "год релиза", "год", "year", "release year", "published",
        "перевод", "переводчик", "переводчики", "сценарист", "иллюстратор", "translator",
        "тип", "формат", "type", "глав", "томов", "главы", "тома", "chapters", "volumes",
        "возрастной рейтинг", "возрастное ограничение", "age rating",
        "издатель", "publisher", "лицензировано", "licensed",
    )

    private val abbreviation = Regex(
        "(?i)\\b(mr|mrs|ms|dr|prof|sr|jr|st|vs|etc|vol|ch|no|№|рис|стр|см|им|напр|т\\.д|т\\.п|e\\.g|i\\.e|u\\.s)\\.",
    )

    /** Sentence boundary: ". " / "! " / "? " followed by an uppercase letter. */
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+(?=[\\p{Lu}\\p{Lt}])")

    /** Scraper defect: missing space after sentence-ending punctuation ("…бойцов.Сражение…"). */
    private val missingSpaceAfterSentenceEnd = Regex("(?<=[.!?…])(?=[\\p{Lu}\\p{Lt}])")

    fun beautify(raw: String): List<DescriptionBlock> {
        val text = normalizeRaw(raw)
        if (text.isBlank()) return emptyList()

        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(text)
        val blocks = mutableListOf<DescriptionBlock>()
        var sawStructure = false

        for (node in tree.children) {
            when (node.type) {
                MarkdownElementTypes.PARAGRAPH -> {
                    paragraph(node, text)?.let {
                        sawStructure = true
                        blocks += it
                    }
                }
                in headingTypes -> {
                    val heading = collectText(node, text).trim()
                    if (heading.isNotEmpty()) {
                        sawStructure = true
                        blocks += DescriptionBlock.SectionHeading(heading)
                    }
                }
                MarkdownElementTypes.UNORDERED_LIST,
                MarkdownElementTypes.ORDERED_LIST,
                -> {
                    sawStructure = true
                    blocks += listItems(node, text)
                }
                MarkdownElementTypes.HTML_BLOCK,
                MarkdownElementTypes.CODE_BLOCK,
                MarkdownElementTypes.CODE_FENCE,
                -> blocks += DescriptionBlock.Fallback(text.substring(node.startOffset, node.endOffset).trim())
                MarkdownTokenTypes.HORIZONTAL_RULE -> Unit // divider, intentionally skipped
                else -> Unit
            }
        }

        // A flat wall (single paragraph, no headings/lists/links/labels) needs recovery.
        return if (!sawStructure) flatWall(text) else blocks
    }

    /** Concatenates all block text; used by tests to verify no text is lost. */
    fun blocksToText(blocks: List<DescriptionBlock>): String = buildString {
        blocks.forEach { block ->
            when (block) {
                is DescriptionBlock.Paragraph -> append(block.text)
                is DescriptionBlock.SectionHeading -> append(block.text)
                is DescriptionBlock.LabelRow -> {
                    append(block.label)
                    if (block.value.isNotEmpty()) append(": ", block.value)
                }
                is DescriptionBlock.ListItem -> {
                    append(block.text)
                    if (!block.url.isNullOrEmpty()) append(" ", block.url)
                }
                is DescriptionBlock.LinksRow ->
                    append(block.links.joinToString("\n") { "${it.text} ${it.url}" })
                is DescriptionBlock.Fallback -> append(block.text)
            }
            append('\n')
        }
    }

    internal fun normalizeRaw(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u00A0', ' ')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    // ------------------------------------------------------------------ structured path

    private val headingTypes = setOf(
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
        MarkdownElementTypes.SETEXT_1,
        MarkdownElementTypes.SETEXT_2,
    )

    private fun paragraph(node: ASTNode, content: String): List<DescriptionBlock>? {
        val rawSnippet = content.substring(node.startOffset, node.endOffset).trim()
        if (rawSnippet.isEmpty()) return null

        val rawLines = rawSnippet.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.size > 1 && rawLines.any { isKnownLabel(it) }) {
            val result = mutableListOf<DescriptionBlock>()
            val proseBuffer = mutableListOf<String>()

            fun flushProse() {
                if (proseBuffer.isNotEmpty()) {
                    val combined = proseBuffer.joinToString(" ")
                    if (combined.length > SPLIT_THRESHOLD) {
                        result += splitParagraph(combined)
                    } else {
                        result += DescriptionBlock.Paragraph(combined)
                    }
                    proseBuffer.clear()
                }
            }

            for (line in rawLines) {
                val parsed = parseLineLabel(line)
                if (parsed != null) {
                    flushProse()
                    result += parsed
                } else {
                    proseBuffer += line
                }
            }
            flushProse()
            if (result.isNotEmpty()) return result
        }

        val plain = collectText(node, content).trim()
        if (plain.isEmpty()) return null

        // A known section label inside the paragraph ("Alternative Titles: a / b / c").
        splitKnownLabel(plain)?.let { return splitLongParagraphs(it) }

        // A paragraph that consists only of links → LinksRow.
        val links = collectLinks(node, content)
        if (links.isNotEmpty()) {
            // "External links: https://a" → heading + LinksRow (bare autolink next to a label).
            val remainder = stripLinkTexts(plain, links).trim()
            if (remainder.isNotEmpty() && remainder.length <= LABEL_MAX_LENGTH &&
                (remainder.endsWith(":") || remainder.endsWith("："))
            ) {
                return listOf(
                    DescriptionBlock.SectionHeading(remainder.trimEnd(':', '：').trim()),
                    DescriptionBlock.LinksRow(links),
                )
            }
            if (isLinksOnly(plain, links)) {
                return listOf(DescriptionBlock.LinksRow(links))
            }
        }

        return if (plain.length > SPLIT_THRESHOLD) splitParagraph(plain) else listOf(DescriptionBlock.Paragraph(plain))
    }

    private fun listItems(node: ASTNode, content: String): List<DescriptionBlock.ListItem> {
        val items = mutableListOf<DescriptionBlock.ListItem>()
        for (child in node.children) {
            if (child.type != MarkdownElementTypes.LIST_ITEM) continue
            val text = collectText(child, content).trim()
            if (text.isEmpty()) continue
            val links = collectLinks(child, content)
            val url = if (links.size == 1) links.first().url else null
            items += DescriptionBlock.ListItem(text, url)
        }
        return items
    }

    // ------------------------------------------------------------------ flat path

    private fun flatWall(text: String): List<DescriptionBlock> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1 && lines.any { isKnownLabel(it) }) {
            val result = mutableListOf<DescriptionBlock>()
            val proseBuffer = mutableListOf<String>()

            fun flushProse() {
                if (proseBuffer.isNotEmpty()) {
                    val combined = proseBuffer.joinToString(" ")
                    if (combined.length > SPLIT_THRESHOLD) {
                        result += splitParagraph(combined)
                    } else {
                        result += DescriptionBlock.Paragraph(combined)
                    }
                    proseBuffer.clear()
                }
            }

            for (line in lines) {
                val parsed = parseLineLabel(line)
                if (parsed != null) {
                    flushProse()
                    result += parsed
                } else {
                    proseBuffer += line
                }
            }
            flushProse()
            if (result.isNotEmpty()) return result
        }

        splitKnownLabel(text)?.let { return splitLongParagraphs(it) }
        return splitParagraph(text)
    }

    private fun splitLongParagraphs(blocks: List<DescriptionBlock>): List<DescriptionBlock> =
        blocks.flatMap { block ->
            if (block is DescriptionBlock.Paragraph && block.text.length > SPLIT_THRESHOLD) {
                splitParagraph(block.text)
            } else {
                listOf(block)
            }
        }

    private fun splitParagraph(text: String): List<DescriptionBlock> {
        if (text.length <= SPLIT_THRESHOLD) return listOf(DescriptionBlock.Paragraph(text))

        // Repair the missing-space scraper defect, guarding abbreviations and initials.
        val repaired = text.replace(missingSpaceAfterSentenceEnd) { m ->
            if (isAbbreviationBoundary(text, m.range.first)) m.value else " "
        }

        val rawSentences = repaired.split(sentenceEnd)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Merge back splits that landed right after an abbreviation ("Dr." / "U.S." / "Mr.").
        val sentences = mutableListOf<String>()
        for (sentence in rawSentences) {
            val prev = sentences.lastOrNull()
            if (prev != null && abbreviation.containsMatchIn(prev.takeLast(12))) {
                sentences[sentences.lastIndex] = "$prev $sentence"
            } else {
                sentences += sentence
            }
        }
        if (sentences.size < 2) return listOf(DescriptionBlock.Paragraph(repaired))

        return sentences.chunked(SENTENCES_PER_PARAGRAPH)
            .map { DescriptionBlock.Paragraph(it.joinToString(" ")) }
    }

    private fun isAbbreviationBoundary(text: String, index: Int): Boolean {
        val before = text.substring(maxOf(0, index - 8), index).trim()
        if (abbreviation.containsMatchIn(before)) return true
        // Single-letter initial ("J. K. Rowling", "U.S.A.") — don't insert a space.
        return text.getOrNull(index - 2)?.isUpperCase() == true
    }

    // ------------------------------------------------------------------ label detection

    private fun isKnownLabel(line: String): Boolean {
        for (label in sectionLabels) {
            if (Regex("(?iu)^(${Regex.escape(label)})\\s*[:：]").containsMatchIn(line)) return true
        }
        for (label in rowLabels) {
            if (Regex("(?iu)^(${Regex.escape(label)})\\s*[:：]").containsMatchIn(line)) return true
        }
        return false
    }

    private fun parseLineLabel(line: String): List<DescriptionBlock>? {
        for (label in sectionLabels) {
            val regex = Regex("(?iu)^(${Regex.escape(label)})\\s*[:：]")
            val m = regex.find(line) ?: continue
            val rawValue = line.substring(m.range.last + 1).trim()
            val labelText = m.groupValues[1].trim()

            val cleanValue = rawValue.trim().trimStart('|').trimEnd('|').trim()
            val items = cleanValue
                .split(Regex("\\s*/\\s*|\\s*\\|\\s*|\n\\s*[-•]\\s*|\n"))
                .map { it.trim().trimStart('|').trimEnd('|').trim() }
                .filter { it.isNotEmpty() }

            return when {
                items.isEmpty() -> listOf(DescriptionBlock.SectionHeading(labelText))
                items.size == 1 && !cleanValue.contains(
                    '/',
                ) && !cleanValue.contains('|') && !cleanValue.contains('\n') ->
                    listOf(DescriptionBlock.LabelRow(labelText, cleanValue))
                else -> {
                    val out = mutableListOf<DescriptionBlock>()
                    out += DescriptionBlock.SectionHeading(labelText)
                    out += items.map { DescriptionBlock.ListItem(it) }
                    out
                }
            }
        }

        for (label in rowLabels) {
            val m = Regex("(?iu)^(${Regex.escape(label)})\\s*[:：]\\s*(.+)$").find(line) ?: continue
            val value = m.groupValues[2].trim().trimStart('|').trimEnd('|').trim()
            if (value.isNotEmpty() && value.length <= 120) {
                return listOf(DescriptionBlock.LabelRow(m.groupValues[1].trim(), value))
            }
        }
        return null
    }

    private fun splitKnownLabel(text: String): List<DescriptionBlock>? {
        for (label in sectionLabels) {
            val regex = Regex("(?iu)(${Regex.escape(label)})\\s*[:：]")
            val m = regex.find(text) ?: continue
            val before = text.substring(0, m.range.first).trim()
            val rawValue = text.substring(m.range.last + 1).trim()
            val labelText = m.groupValues[1].trim()

            val cleanValue = rawValue.trim().trimStart('|').trimEnd('|').trim()
            val items = cleanValue
                .split(Regex("\\s*/\\s*|\\s*\\|\\s*|\n\\s*[-•]\\s*|\n"))
                .map { it.trim().trimStart('|').trimEnd('|').trim() }
                .filter { it.isNotEmpty() }

            val out = mutableListOf<DescriptionBlock>()
            if (before.isNotEmpty()) out += DescriptionBlock.Paragraph(before)
            when {
                items.isEmpty() -> out += DescriptionBlock.SectionHeading(labelText)
                items.size == 1 && !cleanValue.contains(
                    '/',
                ) && !cleanValue.contains('|') && !cleanValue.contains('\n') ->
                    out += DescriptionBlock.LabelRow(labelText, cleanValue)
                else -> {
                    out += DescriptionBlock.SectionHeading(labelText)
                    out += items.map { DescriptionBlock.ListItem(it) }
                }
            }
            return out
        }

        for (label in rowLabels) {
            val m = Regex("(?iu)^(${Regex.escape(label)})\\s*[:：]\\s*(.+)$").find(text) ?: continue
            val value = m.groupValues[2].trim().trimStart('|').trimEnd('|').trim()
            if (value.isNotEmpty() && value.length <= 120) {
                return listOf(DescriptionBlock.LabelRow(m.groupValues[1].trim(), value))
            }
        }
        return null
    }

    // ------------------------------------------------------------------ text extraction

    private fun collectText(node: ASTNode, content: String): String {
        val sb = StringBuilder()
        appendText(node, content, sb)
        return sb.toString()
    }

    private fun appendText(node: ASTNode, content: String, sb: StringBuilder) {
        when (node.type) {
            // Plain text leaves.
            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.CODE_LINE,
            MarkdownTokenTypes.LINK_ID,
            MarkdownTokenTypes.ATX_CONTENT,
            MarkdownTokenTypes.SETEXT_CONTENT,
            -> sb.append(content, node.startOffset, node.endOffset)

            // Autolinks: the text is the URL itself.
            MarkdownTokenTypes.AUTOLINK,
            MarkdownTokenTypes.EMAIL_AUTOLINK,
            GFMTokenTypes.GFM_AUTOLINK,
            -> sb.append(content, node.startOffset, node.endOffset)

            // Inline punctuation tokens that are part of prose (must not be dropped).
            MarkdownTokenTypes.COLON,
            MarkdownTokenTypes.EXCLAMATION_MARK,
            MarkdownTokenTypes.SINGLE_QUOTE,
            MarkdownTokenTypes.DOUBLE_QUOTE,
            -> sb.append(content, node.startOffset, node.endOffset)

            // Inline links: only the link text belongs in prose.
            MarkdownElementTypes.INLINE_LINK -> {
                val linkText = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                if (linkText != null) {
                    appendText(linkText, content, sb)
                } else {
                    node.children.forEach { appendText(it, content, sb) }
                }
            }

            // Whitespace (incl. soft line breaks inside a paragraph).
            MarkdownTokenTypes.EOL,
            MarkdownTokenTypes.HARD_LINE_BREAK,
            MarkdownTokenTypes.WHITE_SPACE,
            -> sb.append(' ')

            // Everything else: markers (tokens without children) append nothing;
            // unknown elements recurse so no text is ever lost.
            else -> node.children.forEach { appendText(it, content, sb) }
        }
    }

    private fun collectLinks(node: ASTNode, content: String): List<DescriptionBlock.Link> {
        val links = mutableListOf<DescriptionBlock.Link>()
        appendLinks(node, content, links)
        return links
    }

    private fun appendLinks(node: ASTNode, content: String, out: MutableList<DescriptionBlock.Link>) {
        when (node.type) {
            MarkdownElementTypes.INLINE_LINK -> {
                val dest = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.let { content.substring(it.startOffset, it.endOffset).trim() }
                    .orEmpty()
                    .trim(' ', '<', '>', '(', ')')
                if (dest.isNotEmpty()) {
                    val text = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                        ?.let { collectText(it, content) }
                        ?.trim()
                        .orEmpty()
                    out += DescriptionBlock.Link(text.ifEmpty { dest }, dest)
                }
            }
            MarkdownTokenTypes.AUTOLINK,
            MarkdownTokenTypes.EMAIL_AUTOLINK,
            GFMTokenTypes.GFM_AUTOLINK,
            -> {
                val url = content.substring(node.startOffset, node.endOffset).trim('<', '>')
                if (url.isNotEmpty()) out += DescriptionBlock.Link(url, url)
            }
            else -> node.children.forEach { appendLinks(it, content, out) }
        }
    }

    private fun stripLinkTexts(plain: String, links: List<DescriptionBlock.Link>): String {
        var result = plain
        for (link in links.sortedByDescending { it.url.length }) {
            result = result.replace(link.url, "")
            link.text.takeIf { it.isNotEmpty() && it != link.url }?.let { text ->
                result = result.replace(text, "")
            }
        }
        return result
    }

    private fun isLinksOnly(plain: String, links: List<DescriptionBlock.Link>): Boolean {
        val stripped = links.fold(plain) { acc, link ->
            acc.replace(link.text, "").replace(link.url, "")
        }.trim()
        return stripped.isEmpty() || stripped.all { it.isWhitespace() || it in "·•|/-" }
    }
}
