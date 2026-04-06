package eu.kanade.domain.entries.manga.model

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

internal object SourceMangaHtmlRatingParser {
    private const val TAG = "SourceMangaHtmlRating"
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parse(
        sourceName: String?,
        sourceBaseUrl: String?,
        sourceClassName: String? = null,
        html: String?,
    ): Float? {
        val family = SourceMangaRatingSourceMatcher.resolveFamily(sourceName, sourceBaseUrl, sourceClassName)
        if (family == null || html.isNullOrBlank()) {
            debugLog("parse: skip source=$sourceName family=$family htmlBlank=${html.isNullOrBlank()}")
            return null
        }

        val document = Jsoup.parse(html)
        val rating = when (family) {
            SourceMangaRatingFamily.GROUP_LE -> parseGroupLe(document)
            SourceMangaRatingFamily.INK_STORY -> parseInkStory(document)
            SourceMangaRatingFamily.MADARA -> parseMadara(document)
        }
        debugLog("parse: source=$sourceName family=$family rating=${rating.previewFloat()} htmlPreview=${html.previewForLog()}")
        return rating
    }

    private fun parseGroupLe(document: Document): Float? {
        document.selectFirst(".rating-block[data-score]")?.attr("data-score")
            ?.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { return (it * 2f).coerceIn(0f, 10f) }

        document.selectFirst(".cr-hero-rating__main .cr-hero-rating__value")
            ?.text()
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { return it.coerceIn(0f, 10f) }

        document.selectFirst("meta[itemprop=ratingValue]")
            ?.attr("content")
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { rating ->
                return if (rating <= 5f) (rating * 2f).coerceIn(0f, 10f) else rating.coerceIn(0f, 10f)
            }

        return null
    }

    private fun parseInkStory(document: Document): Float? {
        parseAggregateRating(document)?.let { return it }
        return parseInkStoryText(document.body().text())
    }

    private fun parseMadara(document: Document): Float? {
        parseAggregateRating(document)?.let { return it }

        document.body().text()
            .let(::parseMadaraText)
            ?.let { return it }

        return null
    }

    private fun parseAggregateRating(document: Document): Float? {
        return document.select("script[type=application/ld+json]")
            .asSequence()
            .mapNotNull { parseJsonLdAggregateRating(it.data()) }
            .firstOrNull()
    }

    private fun parseJsonLdAggregateRating(payload: String): Float? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return null
        val ratingObject = root.findAggregateRatingObject() ?: return null
        val ratingValue = ratingObject.jsonPrimitiveFloat("ratingValue") ?: return null
        val bestRating = ratingObject.jsonPrimitiveFloat("bestRating")
        return normalizeRating(ratingValue, bestRating)
    }

    private fun JsonElement.findAggregateRatingObject(): JsonObject? {
        return when (this) {
            is JsonObject -> {
                (this["aggregateRating"] as? JsonObject)?.let { return it }
                values.forEach { child ->
                    child.findAggregateRatingObject()?.let { return it }
                }
                null
            }
            is JsonArray -> {
                forEach { child ->
                    child.findAggregateRatingObject()?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun JsonObject.jsonPrimitiveFloat(key: String): Float? {
        return (this[key] as? JsonPrimitive)
            ?.content
            ?.replace(',', '.')
            ?.toFloatOrNull()
    }

    private fun parseMadaraText(text: String): Float? {
        val ratingBeforeYourRating = Regex("""\b([0-9]+(?:\.[0-9]+)?)\s+Your Rating\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?.let { normalizeRating(it, 5f) }
        if (ratingBeforeYourRating != null) return ratingBeforeYourRating

        val averageRating = Regex("""Average\s+([0-9]+(?:\.[0-9]+)?)\s*/\s*5\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?.let { normalizeRating(it, 5f) }
        if (averageRating != null) return averageRating

        return null
    }

    private fun parseInkStoryText(text: String): Float? {
        val ratingWithVotes = Regex("""(?i)\bРейтинг\b\s+([0-9]+(?:[.,][0-9]+)?)\s+\d+\s+оценок\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
        if (ratingWithVotes != null) return ratingWithVotes

        val standaloneRating = Regex("""\b([0-9]+(?:[.,][0-9]+)?)\s+\d+\s+оценок\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
        if (standaloneRating != null) return standaloneRating

        return null
    }

    private fun normalizeRating(value: Float, bestRating: Float? = null): Float {
        val normalized = when {
            bestRating != null && bestRating > 0f && bestRating <= 5f -> value * (10f / bestRating)
            value <= 5f -> value * 2f
            else -> value
        }
        return normalized.coerceIn(0f, 10f)
    }

    private fun String.previewForLog(limit: Int = 120): String {
        return replace(Regex("\\s+"), " ").take(limit)
    }

    private fun Float?.previewFloat(): String {
        return this?.let { String.format(Locale.US, "%.3f", it) }.orEmpty()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }
}
