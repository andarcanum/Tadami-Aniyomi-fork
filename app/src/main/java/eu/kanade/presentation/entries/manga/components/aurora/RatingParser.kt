package eu.kanade.presentation.entries.manga.components.aurora

import android.util.Log

/**
 * Data class to hold parsed rating information from manga description.
 */
data class ParsedRating(
    val rating: Float,
    val votes: Int?,
)

/**
 * Utility object for parsing rating information from manga descriptions.
 */
object RatingParser {
    private const val TAG = "RatingParser"

    private val ratingPatterns = listOf(
        // Russian patterns with colon - MOST COMMON, check first
        Regex("""(\d+\.?\d*)\s*\(голосов?:\s*(\d+(?:\s*\d+)*)\)""", RegexOption.IGNORE_CASE),
        Regex("""Рейтинг:\s*(\d+\.?\d*)\s*(?:\(голосов?:\s*(\d+(?:\s*\d+)*)\))?""", RegexOption.IGNORE_CASE),
        Regex("""рейтинг\s*(\d+\.?\d*)\s*(?:\(голосов?:\s*(\d+(?:\s*\d+)*)\))?""", RegexOption.IGNORE_CASE),
        Regex("""Оценка:\s*(\d+\.?\d*)\s*(?:\(голосов?:\s*(\d+(?:\s*\d+)*)\))?""", RegexOption.IGNORE_CASE),

        // English patterns with various formats
        Regex("""Rating:\s*(\d+\.?\d*)\s*(?:\((\d+(?:,\d+)*)\s*votes?\))?""", RegexOption.IGNORE_CASE),
        Regex("""Score:\s*(\d+\.?\d*)\s*(?:\((\d+(?:,\d+)*)\s*votes?\))?""", RegexOption.IGNORE_CASE),
        Regex("""(\d+\.?\d*)\s*/\s*10\s*(?:\((\d+(?:,\d+)*)\s*votes?\))?""", RegexOption.IGNORE_CASE),
        Regex("""rated:\s*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),

        // MangaUpdates/MAL formats
        Regex("""MAL:\s*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""MyAnimeList:\s*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),

        // GroupLe multisrc star format: ***** 9.8[9.4] (votes: 123)
        Regex(
            """[\*\+\-]{5}\s*(\d+[.,]?\d*)\s*(?:\[[^\]]*])?\s*(?:\(votes:\s*(\d+(?:,\s*\d+)*)\))?""",
            RegexOption.IGNORE_CASE,
        ),

        // Star ratings - 5 star pattern (filled/empty/half mix) with rating
        Regex("""[★☆⭐✬✩]{5}\s*(\d+\.?\d*)\s*(?:\[ⓘ[^\]]*\])?\s*(?:\((\d+(?:,\s*\d+)*)\s*(?:голосов?|votes?)?\))?"""),
        // Single star patterns
        Regex("""★\s*(\d+\.?\d*)\s*(?:\((\d+(?:,\d+)*)\))?"""),
        Regex("""⭐\s*(\d+\.?\d*)\s*(?:\((\d+(?:,\d+)*)\))?"""),

        // Generic patterns - must be last (less specific)
        Regex("""(\d+\.?\d*)\s*из\s*10""", RegexOption.IGNORE_CASE),
        Regex("""(\d+\.?\d*)\s*out of\s*10""", RegexOption.IGNORE_CASE),
    )

    /**
     * Attempts to parse rating from manga description.
     * Returns null if no rating pattern is found or rating is invalid.
     */
    fun parseRating(description: String?): ParsedRating? {
        if (description.isNullOrBlank()) {
            debugLog("parseRating: description is null or blank")
            return null
        }

        debugLog("parseRating: description = \"$description\"")

        for (pattern in ratingPatterns) {
            val match = pattern.find(description) ?: continue

            val ratingString = match.groupValues.getOrNull(1) ?: continue
            val rating = ratingString.replace(',', '.').toFloatOrNull() ?: continue

            // Validate rating is in reasonable range (0-10)
            if (rating < 0 || rating > 10) continue

            // Parse votes count if present
            val votesString = match.groupValues.getOrNull(2)
            val votes = votesString?.replace(Regex("""[,\s]"""), "")?.toIntOrNull()

            debugLog("parseRating: FOUND rating=$rating, votes=$votes, matched pattern: ${match.value}")
            return ParsedRating(rating, votes)
        }

        debugLog("parseRating: NO MATCH FOUND")
        return null
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    /**
     * Formats rating as a display string (e.g., "8.5" or "8.0")
     * Always shows one decimal place.
     */
    fun formatRating(rating: Float): String {
        return String.format("%.1f", rating)
    }

    /**
     * Formats votes count with thousands separator (e.g., "3,725")
     */
    fun formatVotes(votes: Int): String {
        return String.format("%,d", votes)
    }
}
