package eu.kanade.tachiyomi.ui.reader.novel

import org.jsoup.Jsoup

internal fun prependChapterHeadingIfMissing(
    rawHtml: String,
    chapterName: String?,
): String {
    val headingText = chapterName.orEmpty()
        .replace('\u00A0', ' ')
        .replace("\r", "")
        .trim()
    if (headingText.isBlank()) return rawHtml

    return runCatching {
        val document = Jsoup.parseBodyFragment(rawHtml)
        val body = document.body() ?: return rawHtml
        if (body.select("h1, h2, h3, h4, h5, h6").isNotEmpty()) {
            return body.html()
        }

        body.prependElement("h1")
            .addClass("an-reader-chapter-title")
            .text(headingText)
        body.html()
    }.getOrDefault(rawHtml)
}
