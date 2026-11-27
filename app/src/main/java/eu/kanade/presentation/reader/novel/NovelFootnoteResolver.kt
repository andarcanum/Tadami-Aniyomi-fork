package eu.kanade.presentation.reader.novel

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val footnoteContainerTags = setOf("p", "li", "dd", "aside", "div", "blockquote", "section")
private val backlinkOnlyTextRegex =
    Regex("""^[\[\]()\s]*(?:↩|↑|▲|\^|←|back|return|назад)[\[\]()\s]*$""", RegexOption.IGNORE_CASE)
private val noteDigitsRegex = Regex("""(\d+)""")

/**
 * Resolves a tapped link against local chapter HTML when the target points to an in-page
 * footnote/endnote anchor (`#fn1`, `#note-2`, `chapter.xhtml#fn1`, etc.).
 *
 * Returns `null` when [rawUrl] is an external/inter-document link that should continue routing
 * through `WebViewActivity`.
 */
internal fun resolveNovelFootnoteFromHtml(
    rawUrl: String,
    rawHtml: String,
    chapterWebUrl: String? = null,
    novelUrl: String? = null,
): NovelFootnoteDialogData? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val hashIndex = trimmed.indexOf('#')
    if (hashIndex < 0) return null

    val rawFragment = trimmed.substring(hashIndex + 1).trim()
    if (rawFragment.isBlank()) return null
    val anchorId = decodeFootnoteFragment(rawFragment)
    val basePart = trimmed.substring(0, hashIndex).trim()
    val fallbackUrl = resolveNovelReaderLinkUrl(
        rawUrl = trimmed,
        chapterWebUrl = chapterWebUrl,
        novelUrl = novelUrl,
    )

    val isPureFragment = basePart.isEmpty()
    val isSameDocumentHttp = isSameDocumentLink(
        rawUrl = trimmed,
        chapterWebUrl = chapterWebUrl,
    )
    val isRelativeAssetFragment = !trimmed.contains("://") && !trimmed.startsWith("mailto:", ignoreCase = true)

    val matchedText = if (rawHtml.isNotBlank()) {
        extractFootnoteBodyByAnchorId(rawHtml = rawHtml, anchorId = anchorId)
    } else {
        null
    }

    if (matchedText != null) {
        if (isPureFragment || isSameDocumentHttp || isRelativeAssetFragment) {
            return NovelFootnoteDialogData(
                anchorId = anchorId,
                titleNumber = extractFootnoteDisplayLabel(anchorId),
                bodyText = matchedText,
                resolvedExternalUrl = fallbackUrl,
                found = true,
            )
        }
    }

    if (isPureFragment || isSameDocumentHttp) {
        return NovelFootnoteDialogData(
            anchorId = anchorId,
            titleNumber = extractFootnoteDisplayLabel(anchorId),
            bodyText = "",
            resolvedExternalUrl = if (isPureFragment) null else fallbackUrl,
            found = false,
        )
    }

    return null
}

private fun decodeFootnoteFragment(rawFragment: String): String {
    return runCatching {
        URLDecoder.decode(rawFragment, StandardCharsets.UTF_8.name())
    }.getOrDefault(rawFragment).trim()
}

private fun isSameDocumentLink(
    rawUrl: String,
    chapterWebUrl: String?,
): Boolean {
    val chapterBase = chapterWebUrl?.toHttpUrlOrNull() ?: return false
    val resolved = rawUrl.toHttpUrlOrNull() ?: chapterBase.resolve(rawUrl) ?: return false
    return resolved.scheme == chapterBase.scheme &&
        resolved.host == chapterBase.host &&
        resolved.port == chapterBase.port &&
        resolved.encodedPath == chapterBase.encodedPath &&
        resolved.encodedQuery == chapterBase.encodedQuery
}

private fun extractFootnoteDisplayLabel(anchorId: String): String? {
    val match = noteDigitsRegex.findAll(anchorId).lastOrNull()?.value
    return match?.let { "[$it]" }
}

private fun extractFootnoteBodyByAnchorId(
    rawHtml: String,
    anchorId: String,
): String? {
    val document = Jsoup.parseBodyFragment(rawHtml)
    val target = findAnchorTargetElement(document.body(), anchorId) ?: return null

    val directText = extractCleanedElementText(target)
    if (directText.isNotEmpty()) {
        return directText
    }

    // Legacy EPUB / WordPress notes often place `<a id="fn1"></a>Note text` inside a `<p>` or `<li>`.
    var ancestor = target.parent()
    while (ancestor != null && ancestor != document.body()) {
        val tag = ancestor.tagName().lowercase(Locale.US)
        if (tag in footnoteContainerTags) {
            val containerText = extractCleanedElementText(ancestor)
            if (containerText.isNotEmpty()) {
                return containerText
            }
        }
        ancestor = ancestor.parent()
    }
    return null
}

private fun findAnchorTargetElement(
    root: Element,
    anchorId: String,
): Element? {
    root.getElementById(anchorId)?.let { return it }
    for (element in root.allElements) {
        if (element.attr("id") == anchorId) return element
        if (element.tagName().equals("a", ignoreCase = true) && element.attr("name") == anchorId) {
            return element
        }
    }
    return null
}

private fun extractCleanedElementText(root: Element): String {
    val clone = root.clone()
    clone.select("a").forEach { anchor ->
        val href = anchor.attr("href").trim()
        val text = anchor.text().trim()
        val cls = anchor.className().lowercase(Locale.US)
        val isBacklink = backlinkOnlyTextRegex.matches(text) ||
            cls.contains("backref") ||
            cls.contains("reversefootnote") ||
            (href.startsWith("#") && (href.contains("ref", ignoreCase = true) || backlinkOnlyTextRegex.matches(text)))
        if (isBacklink) {
            anchor.remove()
        }
    }

    val builder = StringBuilder()
    appendNormalizedFootnoteText(clone, builder)
    return builder.toString()
        .replace(Regex("""[ \t\u00A0]+"""), " ")
        .replace(Regex(""" *\n *"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun appendNormalizedFootnoteText(
    node: Node,
    out: StringBuilder,
) {
    when (node) {
        is TextNode -> {
            out.append(node.wholeText)
        }
        is Element -> {
            val tag = node.tagName().lowercase(Locale.US)
            if (tag == "script" || tag == "style") return
            if (tag == "br") {
                out.append('\n')
                return
            }
            val isBlock =
                tag == "p" || tag == "div" || tag == "li" || tag == "blockquote" || tag == "aside" || tag == "dd"
            if (isBlock && out.isNotEmpty() && !out.endsWith("\n")) {
                out.append('\n')
            }
            for (child in node.childNodes()) {
                appendNormalizedFootnoteText(child, out)
            }
            if (isBlock && out.isNotEmpty() && !out.endsWith("\n")) {
                out.append('\n')
            }
        }
    }
}
