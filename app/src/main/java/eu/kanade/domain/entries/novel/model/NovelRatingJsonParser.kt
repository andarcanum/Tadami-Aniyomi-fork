package eu.kanade.domain.entries.novel.model

import android.util.Log
import kotlin.math.round

internal object NovelRatingJsonParser {
    private const val TAG = "NovelRatingJsonParser"
    private val directAveragePattern = Regex(
        """(?is)"(?:average|averageFormated|ratingValue)"\s*:\s*"?([0-9]{1,2}(?:[.,][0-9]{1,2})?)"?""",
    )
    private val statsItemPattern = Regex(
        """(?is)\{\s*"label"\s*:\s*([0-9]{1,2}(?:[.,][0-9]{1,2})?)\s*,\s*"value"\s*:\s*([0-9]+)""",
    )

    fun parseRanobeLibStats(json: String?): Float? {
        if (json.isNullOrBlank()) {
            debugLog("parse: blank input")
            return null
        }

        parseDirectAverage(json)?.let { parsed ->
            debugLog("parse: direct average=$parsed")
            return parsed
        }

        parseStatsDistribution(json)?.let { parsed ->
            debugLog("parse: stats average=$parsed")
            return parsed
        }

        debugLog("parse: no match")
        return null
    }

    private fun parseDirectAverage(json: String): Float? {
        return directAveragePattern.find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseNumericValue)
    }

    private fun parseStatsDistribution(json: String): Float? {
        val matches = statsItemPattern.findAll(json).toList()
        if (matches.isEmpty()) return null

        var weightedSum = 0f
        var totalVotes = 0f

        for (match in matches) {
            val label = parseNumericValue(match.groupValues.getOrNull(1)) ?: continue
            val value = parseCount(match.groupValues.getOrNull(2)) ?: continue
            weightedSum += label * value
            totalVotes += value
        }

        if (totalVotes <= 0f) return null
        return normalizeRating(weightedSum / totalVotes)
    }

    private fun parseNumericValue(raw: String?): Float? {
        return raw
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.takeIf { it in 0f..10f }
            ?.let(::normalizeRating)
    }

    private fun parseCount(raw: String?): Float? {
        return raw
            ?.replace(",", "")
            ?.toFloatOrNull()
            ?.takeIf { it >= 0f }
    }

    private fun normalizeRating(value: Float): Float? {
        if (value <= 0f || value > 10f) return null
        return round(value * 100f) / 100f
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }
}
