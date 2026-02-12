package eu.kanade.domain.entries.novel.model

import org.jsoup.Jsoup

fun normalizeNovelDescription(rawDescription: String?): String? {
    if (rawDescription.isNullOrBlank()) return null

    val sanitized = Jsoup.parse(rawDescription)
        .text()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    return sanitized.ifBlank { null }
}
