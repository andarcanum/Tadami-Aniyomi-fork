package tachiyomi.source.local.entries.novel

import org.jsoup.nodes.Entities
import tachiyomi.source.local.io.novel.markdownToHtml

private val RENDERER_HTML_EXTENSIONS = setOf("html", "htm", "xhtml")
private val RENDERER_MARKDOWN_EXTENSIONS = setOf("md", "markdown")

/**
 * Single dispatch point for plain-text chapter sources: directories-as-chapters and
 * archive entries both render through this so .md/.markdown get real Markdown→HTML
 * conversion while .txt keeps the preformatted-escape fallback and html passes through.
 */
internal fun renderTextFileBody(fileName: String, rawText: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        in RENDERER_HTML_EXTENSIONS -> rawText
        in RENDERER_MARKDOWN_EXTENSIONS -> markdownToHtml(rawText)
        else -> escapePlainTextToHtml(rawText)
    }
}

internal fun escapePlainTextToHtml(text: String): String {
    return "<html><body><pre style=\"white-space: pre-wrap; font-family: inherit;\">" +
        Entities.escape(text) +
        "</pre></body></html>"
}

/**
 * Renders a single archive entry body. fb2 entries go through the full Fb2Book parser,
 * everything else dispatches on the entry extension like loose files do.
 */
internal fun renderArchiveEntryText(entryName: String, rawText: String): String {
    return if (entryName.substringAfterLast('.', "").equals("fb2", ignoreCase = true)) {
        rawText.byteInputStream().use { stream -> Fb2Book.parse(stream).bookHtml() }
    } else {
        renderTextFileBody(entryName, rawText)
    }
}
