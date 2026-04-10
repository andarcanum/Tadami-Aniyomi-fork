package eu.kanade.domain.entries.manga.model

import android.util.Log

object SourceMangaRatingParser {
    private const val TAG = "SourceMangaRating"

    private val htmlTagPattern = Regex("<[^>]+>")
    private val collapseWhitespacePattern = Regex("\\s+")

    private val patterns = listOf(
        Regex(
            """(?i)\b(?:rating|score|rated|myanimelist|mal|anilist|shikimori)\b[^0-9]{0,24}([0-9]{1,2}(?:[.,][0-9]{1,2})?)\s*(?:/\s*10|out\s*of\s*10)?""",
        ),
        Regex(
            """(?:[Рр]ейтинг|[Оо]ценка)[^0-9]{0,24}([0-9]{1,2}(?:[.,][0-9]{1,2})?)\s*(?:/\s*10|из\s*10)?""",
        ),
        Regex(
            """(?i)(?:avalia[cç][aã]o|评分|評分)[^0-9]{0,24}([0-9]{1,2}(?:[.,][0-9]{1,2})?)\s*(?:/\s*10)?""",
        ),
        Regex(
            """(?i)(?:[*+\-★☆⭐✬✩]{3,10})\s*([0-9]{1,2}(?:[.,][0-9]{1,2})?)\b""",
        ),
        Regex(
            """(?i)([0-9]{1,2}(?:[.,][0-9]{1,2})?)\s*(?:/\s*10|из\s*10|out\s*of\s*10)\b""",
        ),
        Regex(
            """(?i)[★☆⭐✬✩]{3,5}\s*([0-9]{1,2}(?:[.,][0-9]{1,2})?)""",
        ),
    )

    fun parse(text: String?): Float? {
        if (text.isNullOrBlank()) {
            debugLog("parse: blank input")
            return null
        }
        val normalized = normalizeText(text)
        debugLog("parse: input=${text.previewForLog()} normalized=${normalized.previewForLog()}")

        for (pattern in patterns) {
            for (match in pattern.findAll(normalized)) {
                val ratingValue = parseRatingValue(match.groupValues.getOrNull(1) ?: continue)
                if (ratingValue != null) {
                    debugLog("parse: matched=${match.value.previewForLog()} rating=$ratingValue")
                    return ratingValue
                }
            }
        }

        debugLog("parse: no match")
        return null
    }

    private fun parseRatingValue(rawValue: String): Float? {
        val parsedValue = rawValue.replace(',', '.').toFloatOrNull() ?: return null
        if (parsedValue <= 0f || parsedValue > 10f) return null
        return parsedValue
    }

    private fun normalizeText(raw: String): String {
        return raw
            .replace(htmlTagPattern, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(collapseWhitespacePattern, " ")
            .trim()
    }

    private fun String.previewForLog(limit: Int = 120): String {
        return replace(Regex("\\s+"), " ").take(limit)
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }
}
