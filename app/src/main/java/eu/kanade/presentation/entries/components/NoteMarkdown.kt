package eu.kanade.presentation.entries.components

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

sealed interface NoteSpan {
    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
    ) : NoteSpan

    data class Link(val text: String, val url: String) : NoteSpan
}

sealed interface NoteBlock {
    data class Paragraph(val spans: List<NoteSpan>) : NoteBlock
    data class Bullet(val spans: List<NoteSpan>, val depth: Int) : NoteBlock
    data class Ordered(val spans: List<NoteSpan>, val number: Int, val depth: Int) : NoteBlock
    data class Quote(val spans: List<NoteSpan>) : NoteBlock
    data object Divider : NoteBlock
}

/**
 * Markdown → [NoteBlock]s for user-authored notes. Parsing only: the text is never rewritten,
 * reordered or trimmed beyond the markdown syntax itself — unknown constructs and unclosed markers
 * stay literal. Deliberately no heuristics from `DescriptionEngine` (label detection, sentence
 * splitting, wall-of-text recovery): those belong to scraped descriptions, not to what a user wrote.
 */
object NoteMarkdown {

    private val atxHeadingTypes = setOf(
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
    )

    private val headingTypes = atxHeadingTypes + setOf(
        MarkdownElementTypes.SETEXT_1,
        MarkdownElementTypes.SETEXT_2,
    )

    private val listTypes = setOf(
        MarkdownElementTypes.UNORDERED_LIST,
        MarkdownElementTypes.ORDERED_LIST,
    )

    private val bulletMarker = Regex("^\\s*[-*+]\\s+")
    private val orderedMarker = Regex("^\\s*(\\d{1,9})[.)]\\s+")
    private val atxPrefix = Regex("^\\s*#{1,6}[ \t]*")
    private val quotePrefix = Regex("^\\s*>+[ \t]?")
    private val inlineLink = Regex("""^\[([^\]]*)\]\(\s*<?([^\s>)]*)>?(?:\s+"[^"]*")?\s*\)""")
    private val angleAutolink = Regex("""^<([^<>\s]+)>""")
    private val bareUrl = Regex("""^(?:https?://|ftp://|file://|www\.)[^\s<>\[\]]+""")
    private val urlScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun parse(note: String): List<NoteBlock> {
        val text = note.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return emptyList()

        val blocks = mutableListOf<NoteBlock>()
        val tree = MarkdownParser(
            flavour = GFMFlavourDescriptor(),
            cancellationToken = CancellationToken.NonCancellable,
        ).buildMarkdownTreeFromString(text as CharSequence)
        for (node in tree.children) {
            appendBlock(node, text, blocks)
        }
        return blocks
    }

    private fun appendBlock(node: ASTNode, text: String, out: MutableList<NoteBlock>) {
        when (node.type) {
            MarkdownTokenTypes.HORIZONTAL_RULE -> out += NoteBlock.Divider
            in headingTypes -> heading(node, text)?.let { out += it }
            MarkdownElementTypes.BLOCK_QUOTE -> quote(node, text)?.let { out += it }
            MarkdownElementTypes.UNORDERED_LIST,
            MarkdownElementTypes.ORDERED_LIST,
            -> appendList(node, text, depth = 0, out = out)
            else -> paragraph(node, text)?.let { out += it }
        }
    }

    private fun paragraph(node: ASTNode, text: String): NoteBlock.Paragraph? {
        val content = text.substring(node.startOffset, node.endOffset).trim()
        if (content.isEmpty()) return null
        return NoteBlock.Paragraph(inline(content))
    }

    private fun heading(node: ASTNode, text: String): NoteBlock.Paragraph? {
        val slice = text.substring(node.startOffset, node.endOffset)
        val content = if (node.type in atxHeadingTypes) {
            slice.replaceFirst(atxPrefix, "")
        } else {
            slice.lines().dropLast(1).joinToString("\n")
        }.trim()
        if (content.isEmpty()) return null
        return NoteBlock.Paragraph(inline(content).map { it.bold() })
    }

    private fun quote(node: ASTNode, text: String): NoteBlock.Quote? {
        val content = text.substring(node.startOffset, node.endOffset)
            .lines()
            .joinToString("\n") { it.replaceFirst(quotePrefix, "") }
            .trim()
        if (content.isEmpty()) return null
        return NoteBlock.Quote(inline(content))
    }

    private fun appendList(node: ASTNode, text: String, depth: Int, out: MutableList<NoteBlock>) {
        val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        if (items.isEmpty()) return

        val numbers = items.map { orderedMarker.find(text.substring(it.startOffset, it.endOffset)) }
        val renumber = items.size > 1 && numbers.all { it?.groupValues?.get(1) == "1" }

        items.forEachIndexed { index, item ->
            val slice = text.substring(item.startOffset, item.endOffset)
            val bullet = bulletMarker.find(slice)
            val ordered = numbers[index]
            val markerLength = bullet?.value?.length ?: ordered?.value?.length ?: 0
            val nested = item.children.filter { it.type in listTypes }
            val contentEnd = nested.firstOrNull()?.startOffset ?: item.endOffset
            val content = text.substring(item.startOffset + markerLength, contentEnd).trim()
            val spans = inline(content)

            if (ordered != null) {
                val number = if (renumber) index + 1 else ordered.groupValues[1].toIntOrNull() ?: 1
                out += NoteBlock.Ordered(spans = spans, number = number, depth = depth)
            } else {
                out += NoteBlock.Bullet(spans = spans, depth = depth)
            }

            nested.forEach { appendList(it, text, depth + 1, out) }
        }
    }

    private fun inline(text: String): List<NoteSpan> = buildList { scan(text, InlineFlags(), this) }

    private fun NoteSpan.bold(): NoteSpan = when (this) {
        is NoteSpan.Text -> copy(bold = true)
        is NoteSpan.Link -> this
    }

    private data class InlineFlags(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
    )

    private fun scan(text: String, flags: InlineFlags, out: MutableList<NoteSpan>) {
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                out += NoteSpan.Text(buffer.toString(), flags.bold, flags.italic, code = false, flags.strike)
                buffer.clear()
            }
        }

        fun styled(inner: String, next: InlineFlags, end: Int): Int {
            flush()
            scan(inner, next, out)
            return end
        }

        var i = 0
        while (i < text.length) {
            val two = text.substring(i, minOf(i + 2, text.length))
            val strongClose = if (two == "**" || two == "__") text.indexOf(two, i + 2) else -1
            val strikeClose = if (two == "~~") text.indexOf(two, i + 2) else -1
            when {
                strongClose > i + 2 ->
                    i = styled(text.substring(i + 2, strongClose), flags.copy(bold = true), strongClose + 2)

                strikeClose > i + 2 ->
                    i = styled(text.substring(i + 2, strikeClose), flags.copy(strike = true), strikeClose + 2)

                text[i] == '*' || text[i] == '_' -> {
                    // Внутри слова `_` не является разметкой (CommonMark): snake_case остаётся как есть.
                    val intraWord = text[i] == '_' && i > 0 && text[i - 1].isLetterOrDigit()
                    val close = text.indexOf(text[i], i + 1)
                    if (intraWord || close <= i + 1) {
                        buffer.append(text[i])
                        i++
                    } else {
                        i = styled(text.substring(i + 1, close), flags.copy(italic = true), close + 1)
                    }
                }

                text[i] == '`' -> {
                    val runLength = text.substring(i).takeWhile { it == '`' }.length
                    val marker = "`".repeat(runLength)
                    val close = text.indexOf(marker, i + runLength)
                    if (close > i + runLength) {
                        flush()
                        out += NoteSpan.Text(
                            text.substring(i + runLength, close),
                            flags.bold,
                            flags.italic,
                            code = true,
                        )
                        i = close + runLength
                    } else {
                        buffer.append(text[i])
                        i++
                    }
                }

                text[i] == '[' -> {
                    val link = inlineLink.find(text.substring(i))
                    if (link != null && link.groupValues[2].isNotEmpty()) {
                        flush()
                        out += NoteSpan.Link(text = link.groupValues[1], url = link.groupValues[2])
                        i += link.value.length
                    } else {
                        buffer.append(text[i])
                        i++
                    }
                }

                text[i] == '<' -> {
                    val auto = angleAutolink.find(text.substring(i))
                    val url = auto?.groupValues?.get(1)
                    if (url != null && (urlScheme.containsMatchIn(url) || url.contains('@'))) {
                        flush()
                        out += NoteSpan.Link(text = url, url = url)
                        i += auto.value.length
                    } else {
                        buffer.append(text[i])
                        i++
                    }
                }

                text[i] in "hfw" && urlAtBoundary(text, i) -> {
                    val match = bareUrl.find(text.substring(i))
                    if (match != null) {
                        val url = trimUrlTail(match.value)
                        flush()
                        out += NoteSpan.Link(text = url, url = if (url.startsWith("www.")) "https://$url" else url)
                        i += url.length
                    } else {
                        buffer.append(text[i])
                        i++
                    }
                }

                else -> {
                    buffer.append(text[i])
                    i++
                }
            }
        }
        flush()
    }

    private fun urlAtBoundary(text: String, index: Int): Boolean {
        if (index == 0) return true
        val previous = text[index - 1]
        return !previous.isLetterOrDigit() && previous != '/' && previous != '.' && previous != '@'
    }

    private fun trimUrlTail(url: String): String {
        var result = url.trimEnd('.', ',', ';', ':', '!', '?', '"', '\'')
        // Закрывающая скобка принадлежит URL только если ей соответствует открывающая (GFM).
        while (result.endsWith(")") && result.count { it == '(' } < result.count { it == ')' }) {
            result = result.dropLast(1)
        }
        return result
    }
}
