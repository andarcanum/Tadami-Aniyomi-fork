package tachiyomi.source.local.entries.novel

import android.util.Base64
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.InputStream

/**
 * Lightweight FB2 (FictionBook 2) parser built on top of Jsoup's XML parser.
 *
 * - Encoding is auto-detected from the BOM / XML declaration (UTF-8, windows-1251, etc.).
 * - Chapters are the leaf `<section>` elements of all non-note bodies, with titles inherited
 *   from ancestor sections ("Volume 1 — Chapter 2").
 * - Content is converted to simple HTML compatible with the novel reader pipeline.
 * - Inline images and covers are resolved from `<binary>` payloads.
 * - Footnote links are rendered inline and referenced notes are appended after the chapter text.
 */
internal class Fb2Book private constructor(private val root: Element) {

    data class ChapterRef(val index: Int, val title: String)

    private data class Leaf(val element: Element, val title: String)

    private val binaries: Map<String, Element> by lazy {
        root.select("binary").associateBy { it.attr("id") }
    }

    private val bodies: List<Element> by lazy {
        root.children().filter { it.tagName().equals("body", ignoreCase = true) }
    }

    private val mainBodies: List<Element> by lazy {
        bodies.filter { !isNotesBodyName(it.attr("name")) }
    }

    private val notesById: Map<String, Element> by lazy {
        bodies.filter { isNotesBodyName(it.attr("name")) }
            .flatMap { it.select("section[id]") }
            .associateBy { it.attr("id") }
    }

    private val leaves: List<Leaf> by lazy {
        val result = mutableListOf<Leaf>()
        mainBodies.forEach { body -> collectLeaves(body, "", result) }
        if (result.isEmpty()) {
            mainBodies.forEach { body -> result.add(Leaf(body, titleOf(body))) }
        }
        result
    }

    // Metadata ------------------------------------------------------------

    private val titleInfo: Element? by lazy { root.selectFirst("description > title-info") }

    val bookTitle: String?
        get() = titleInfo?.selectFirst("book-title")?.text()?.trim()?.takeIf { it.isNotBlank() }

    val authors: List<String>
        get() = titleInfo?.select("> author").orEmpty().mapNotNull { author ->
            val parts = listOf("first-name", "middle-name", "last-name")
                .mapNotNull { tag -> author.selectFirst(tag)?.text()?.trim()?.takeIf { it.isNotBlank() } }
            when {
                parts.isNotEmpty() -> parts.joinToString(" ")
                else -> author.selectFirst("nickname")?.text()?.trim()?.takeIf { it.isNotBlank() }
            }
        }.distinct()

    val annotation: String?
        get() = titleInfo?.selectFirst("annotation")?.text()?.trim()?.takeIf { it.isNotBlank() }

    val genres: List<String>
        get() = titleInfo?.select("> genre").orEmpty()
            .map { it.text().trim().replace('_', ' ') }
            .filter { it.isNotBlank() }
            .distinct()

    /** Decoded cover image bytes, if the book declares a cover page. */
    fun coverImageBytes(): ByteArray? {
        val image = titleInfo?.selectFirst("coverpage image")
            ?: root.selectFirst("coverpage image")
            ?: return null
        val id = imageHref(image).removePrefix("#")
        if (id.isBlank()) return null
        val binary = binaries[id] ?: return null
        return runCatching {
            Base64.decode(binary.text().replace(WHITESPACE_REGEX, ""), Base64.DEFAULT)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    // Chapters ------------------------------------------------------------

    val chapters: List<ChapterRef>
        get() = leaves.mapIndexed { index, leaf -> ChapterRef(index, leaf.title) }

    /** HTML for a single chapter (leaf section) by index. */
    fun chapterHtml(index: Int): String {
        val leaf = leaves.getOrNull(index) ?: return EMPTY_HTML
        return buildHtml(listOf(leaf))
    }

    /** HTML for the whole book, used when it is displayed as a single chapter. */
    fun bookHtml(): String = buildHtml(leaves)

    // Rendering -----------------------------------------------------------

    private fun buildHtml(items: List<Leaf>): String {
        if (items.isEmpty()) return EMPTY_HTML
        val sb = StringBuilder("<html><body>")
        val referencedNotes = linkedSetOf<String>()
        items.forEach { leaf ->
            leaf.element.children().forEach { child -> renderBlock(child, sb, referencedNotes) }
        }
        appendNotes(sb, referencedNotes)
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun appendNotes(sb: StringBuilder, noteIds: Set<String>) {
        val notes = noteIds.mapNotNull { id -> notesById[id]?.let { id to it } }
        if (notes.isEmpty()) return
        sb.append("<hr/>")
        notes.forEach { (_, note) ->
            sb.append("<div>")
            val label = titleOf(note)
            if (label.isNotBlank()) {
                sb.append("<sup>").append(Entities.escape(label)).append("</sup> ")
            }
            note.children()
                .filter { !it.tagName().equals("title", ignoreCase = true) }
                .forEach { renderBlock(it, sb, mutableSetOf()) }
            sb.append("</div>")
        }
    }

    private fun renderBlock(el: Element, sb: StringBuilder, notes: MutableSet<String>) {
        when (el.tagName().lowercase()) {
            "title" -> {
                sb.append("<h3>")
                val parts = el.children().filter { it.tagName().equals("p", ignoreCase = true) }
                if (parts.isEmpty()) {
                    sb.append(Entities.escape(el.text().trim()))
                } else {
                    parts.forEachIndexed { i, p ->
                        if (i > 0) sb.append("<br/>")
                        renderInlineChildren(p, sb, notes)
                    }
                }
                sb.append("</h3>")
            }
            "subtitle" -> {
                sb.append("<h4>")
                renderInlineChildren(el, sb, notes)
                sb.append("</h4>")
            }
            "p" -> {
                sb.append("<p>")
                renderInlineChildren(el, sb, notes)
                sb.append("</p>")
            }
            "empty-line" -> sb.append("<br/>")
            "image" -> renderImage(el, sb)
            "epigraph", "cite", "annotation" -> {
                sb.append("<blockquote>")
                el.children().forEach { renderBlock(it, sb, notes) }
                sb.append("</blockquote>")
            }
            "poem" -> {
                sb.append("<div>")
                el.children().forEach { renderBlock(it, sb, notes) }
                sb.append("</div>")
            }
            "stanza" -> {
                sb.append("<div style=\"margin: 0.75em 0;\">")
                el.children().forEach { renderBlock(it, sb, notes) }
                sb.append("</div>")
            }
            "v" -> {
                sb.append("<p style=\"margin: 0;\">")
                renderInlineChildren(el, sb, notes)
                sb.append("</p>")
            }
            "text-author", "date" -> {
                sb.append("<p><em>")
                renderInlineChildren(el, sb, notes)
                sb.append("</em></p>")
            }
            "table" -> sb.append(el.outerHtml())
            "section" -> el.children().forEach { renderBlock(it, sb, notes) }
            else -> {
                // Unknown block: keep the text so nothing is lost.
                val text = el.text().trim()
                if (text.isNotBlank()) {
                    sb.append("<p>")
                    renderInlineChildren(el, sb, notes)
                    sb.append("</p>")
                }
            }
        }
    }

    private fun renderInlineChildren(el: Element, sb: StringBuilder, notes: MutableSet<String>) {
        el.childNodes().forEach { renderInlineNode(it, sb, notes) }
    }

    private fun renderInlineNode(node: Node, sb: StringBuilder, notes: MutableSet<String>) {
        when (node) {
            is TextNode -> sb.append(Entities.escape(node.text()))
            is Element -> when (node.tagName().lowercase()) {
                "emphasis" -> wrapInline(node, sb, notes, "em")
                "strong" -> wrapInline(node, sb, notes, "strong")
                "strikethrough" -> wrapInline(node, sb, notes, "del")
                "sub" -> wrapInline(node, sb, notes, "sub")
                "sup" -> wrapInline(node, sb, notes, "sup")
                "code" -> wrapInline(node, sb, notes, "code")
                "style" -> renderInlineChildren(node, sb, notes)
                "image" -> renderImage(node, sb)
                "a" -> renderLink(node, sb, notes)
                else -> renderInlineChildren(node, sb, notes)
            }
            else -> Unit
        }
    }

    private fun wrapInline(el: Element, sb: StringBuilder, notes: MutableSet<String>, tag: String) {
        sb.append("<").append(tag).append(">")
        renderInlineChildren(el, sb, notes)
        sb.append("</").append(tag).append(">")
    }

    private fun renderLink(el: Element, sb: StringBuilder, notes: MutableSet<String>) {
        val href = imageHref(el)
        val isInternal = href.startsWith("#")
        if (isInternal) {
            val id = href.removePrefix("#")
            if (notesById.containsKey(id)) {
                notes.add(id)
            }
            sb.append("<sup>")
            renderInlineChildren(el, sb, notes)
            sb.append("</sup>")
        } else if (href.isNotBlank()) {
            sb.append("<a href=\"").append(Entities.escape(href)).append("\">")
            renderInlineChildren(el, sb, notes)
            sb.append("</a>")
        } else {
            renderInlineChildren(el, sb, notes)
        }
    }

    private fun renderImage(el: Element, sb: StringBuilder) {
        val href = imageHref(el)
        if (!href.startsWith("#")) return
        val binary = binaries[href.removePrefix("#")] ?: return
        val contentType = binary.attr("content-type").ifBlank { "image/jpeg" }
        val base64Data = binary.text().replace(WHITESPACE_REGEX, "")
        if (base64Data.isBlank()) return
        sb.append("<img src=\"data:")
            .append(contentType)
            .append(";base64,")
            .append(base64Data)
            .append("\"/>")
    }

    // Helpers ---------------------------------------------------------------

    private fun collectLeaves(el: Element, prefix: String, out: MutableList<Leaf>) {
        val childSections = el.children().filter { it.tagName().equals("section", ignoreCase = true) }
        val isSection = el.tagName().equals("section", ignoreCase = true)
        if (isSection && childSections.isEmpty()) {
            out.add(Leaf(el, joinTitles(prefix, titleOf(el))))
            return
        }
        val newPrefix = if (isSection) joinTitles(prefix, titleOf(el)) else prefix
        childSections.forEach { collectLeaves(it, newPrefix, out) }
    }

    private fun joinTitles(prefix: String, own: String): String {
        return listOf(prefix, own).filter { it.isNotBlank() }.joinToString(" — ")
    }

    private fun titleOf(el: Element): String {
        val title = el.children().firstOrNull { it.tagName().equals("title", ignoreCase = true) }
            ?: return ""
        val parts = title.select("p")
        val text = if (parts.isEmpty()) title.text() else parts.joinToString(". ") { it.text().trim() }
        return text.trim()
    }

    private fun imageHref(el: Element): String {
        return el.attr("l:href")
            .ifBlank { el.attr("xlink:href") }
            .ifBlank { el.attr("href") }
            .trim()
    }

    private fun isNotesBodyName(name: String): Boolean {
        return name.equals("notes", ignoreCase = true) || name.equals("comments", ignoreCase = true)
    }

    companion object {
        private const val EMPTY_HTML = "<html><body></body></html>"
        private val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * Parses an FB2 document from [stream]. The charset is detected automatically
         * from the BOM or the XML declaration (`<?xml ... encoding="windows-1251"?>`).
         */
        fun parse(stream: InputStream): Fb2Book {
            val doc = Jsoup.parse(stream, null, "", Parser.xmlParser())
            val root = doc.children().firstOrNull { it.tagName().equals("FictionBook", ignoreCase = true) }
                ?: doc
            return Fb2Book(root)
        }
    }
}
