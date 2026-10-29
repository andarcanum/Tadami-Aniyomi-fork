package eu.kanade.tachiyomi.ui.reader.novel

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

/**
 * Restores the spaces that source plugins delete when they trim every text node separately.
 *
 * Multisrc novel plugins stream chapter markup tag by tag and call `trim()` on each text chunk, so
 * `<p><strong>Victoria,</strong>nineteen ...` reaches the app with the word boundary already gone.
 * The HTML pipeline hides the damage, but the reader renders the markup as plain text, where the
 * words end up glued together.
 *
 * A space is inserted only where typography requires one: both edges are visible, non-CJK
 * characters, at least one edge is a letter or digit, and neither edge is punctuation that picks
 * its neighbour (`word.`, `(word)`, `1990–2000`, `well-known`). Tags whose spacing is significant
 * (`sub`, `sup`, `code`, ruby annotations) are never touched.
 */
internal fun restoreInlineTextBoundaries(document: Document) {
    document.select(INLINE_BOUNDARY_SELECTOR).forEach { element ->
        if (element.parents().any { parent -> parent.tagName().lowercase(Locale.US) in VERBATIM_ANCESTORS }) {
            return@forEach
        }
        val previous = element.previousSibling()
        if (previous is TextNode && needsSpaceBetween(previous, element)) {
            previous.after(TextNode(" "))
        }
        when (val next = element.nextSibling()) {
            is TextNode -> if (needsSpaceBetween(element, next)) {
                next.before(TextNode(" "))
            }
            // An element-to-element gap is handled once, by the element on the left.
            is Element -> if (
                next.tagName().lowercase(Locale.US) in INLINE_BOUNDARY_TAGS &&
                needsSpaceBetween(element, next)
            ) {
                next.before(TextNode(" "))
            }
            else -> Unit
        }
    }
}

private fun needsSpaceBetween(left: Node, right: Node): Boolean {
    val leftEdge = edgeChar(left, fromStart = false) ?: return false
    val rightEdge = edgeChar(right, fromStart = true) ?: return false
    if (leftEdge.isBoundaryWhitespace() || rightEdge.isBoundaryWhitespace()) return false
    if (leftEdge.isCjk() || rightEdge.isCjk()) return false
    if (!leftEdge.isLetterOrDigit() && !rightEdge.isLetterOrDigit()) return false
    if (rightEdge in ATTACHES_TO_PRECEDING) return false
    if (leftEdge in ATTACHES_TO_FOLLOWING) return false
    return true
}

private fun edgeChar(node: Node, fromStart: Boolean): Char? {
    val text = when (node) {
        is TextNode -> node.wholeText
        is Element -> node.wholeText()
        else -> return null
    }
    return if (fromStart) text.firstOrNull() else text.lastOrNull()
}

private fun Char.isBoundaryWhitespace(): Boolean {
    return isWhitespace() ||
        this == '\u00A0' ||
        this == '\u2007' ||
        this == '\u202F' ||
        this == '\u200B' ||
        this == '\u200C' ||
        this == '\u200D' ||
        this == '\uFEFF'
}

private fun Char.isCjk(): Boolean {
    return this in '\u1100'..'\u11FF' ||
        this in '\u2E80'..'\u2FFF' ||
        this in '\u3000'..'\u303F' ||
        this in '\u3040'..'\u30FF' ||
        this in '\u3130'..'\u318F' ||
        this in '\u3400'..'\u4DBF' ||
        this in '\u4E00'..'\u9FFF' ||
        this in '\uAC00'..'\uD7AF' ||
        this in '\uF900'..'\uFAFF' ||
        this in '\uFF00'..'\uFFEF'
}

private val INLINE_BOUNDARY_TAGS = setOf(
    "a",
    "abbr",
    "b",
    "bdi",
    "bdo",
    "big",
    "cite",
    "data",
    "del",
    "dfn",
    "em",
    "font",
    "i",
    "ins",
    "label",
    "mark",
    "nobr",
    "q",
    "s",
    "small",
    "span",
    "strike",
    "strong",
    "time",
    "tt",
    "u",
)

private val INLINE_BOUNDARY_SELECTOR = INLINE_BOUNDARY_TAGS.joinToString(", ")

private val VERBATIM_ANCESTORS = setOf("pre", "code", "kbd", "samp", "var", "textarea", "ruby")

/** Characters that belong to the text before them, so a gap before them is not a word boundary. */
private val ATTACHES_TO_PRECEDING = setOf(
    '.',
    ',',
    ';',
    ':',
    '!',
    '?',
    ')',
    ']',
    '}',
    '›',
    '»',
    '…',
    '–',
    '—',
    '-',
    '/',
    '\\',
    '\'',
    '"',
    '’',
    '”',
    '%',
    '‰',
    '°',
    '*',
    '†',
    '‡',
    '©',
    '®',
    '™',
)

/** Characters that belong to the text after them, so a gap after them is not a word boundary. */
private val ATTACHES_TO_FOLLOWING = setOf(
    '(',
    '[',
    '{',
    '‹',
    '«',
    '„',
    '“',
    '‘',
    '\'',
    '"',
    '–',
    '—',
    '-',
    '/',
    '\\',
    '$',
    '€',
    '£',
    '¥',
    '₽',
    '#',
    '@',
)
