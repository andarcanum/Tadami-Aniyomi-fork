package eu.kanade.domain.entries.anime.model

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

internal object AnimeRatingHtmlParser {
    private const val TAG = "AnimeRatingHtmlParser"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val ratingKeywordTerms = listOf(
        "rating",
        "score",
        "рейтинг",
        "оценка",
        "оценки",
        "stars",
        "star",
        "звезда",
        "звезды",
        "звёзды",
        "звезд",
        "imdb",
        "shikimori",
        "анимего",
        "animego",
    )

    private val htmlTagPattern = Regex("<[^>]+>")
    private val collapseWhitespacePattern = Regex("\\s+")
    private val explicitScalePattern = Regex(
        """([0-9]{1,2}(?:[.,][0-9]{1,6})?)\s*(?:/|из|of)\s*(5|10)""",
        RegexOption.IGNORE_CASE,
    )
    private val bareRatingPattern = Regex("""(?<!\d)([0-9]{1,2}(?:[.,][0-9]{1,6})?)(?!\d)""")
    private val jsonRatingValuePattern = Regex(
        """(?i)"?ratingValue"?\s*[:=]\s*"?([0-9]{1,2}(?:[.,][0-9]{1,6})?)"?""",
    )
    private val jsonAverageRatingPattern = Regex(
        """(?i)"?averageRating"?\s*[:=]\s*(?:\[\s*0\s*,\s*)?"?([0-9]{1,2}(?:[.,][0-9]{1,6})?)"?""",
    )
    private val jsonScorePattern = Regex(
        """(?i)"?score"?\s*[:=]\s*"?([0-9]{1,2}(?:[.,][0-9]{1,6})?)"?""",
    )
    private val jsonBestRatingPattern = Regex(
        """(?i)"?bestRating"?\s*[:=]\s*"?([0-9]{1,2}(?:[.,][0-9]{1,6})?)"?""",
    )

    private enum class CandidateSource(val rank: Int) {
        STRUCTURED_JSON_LD(500),
        STRUCTURED_META(470),
        EMBEDDED_STATE(430),
        SEMANTIC_WIDGET(390),
        TEXT_SCALE(300),
        TEXT_KEYWORD(260),
        TEXT_BARE(180),
    }

    private data class TextMatch(
        val value: Float,
        val scale: Int?,
        val evidence: String,
    )

    private data class RatingCandidate(
        val value: Float,
        val source: CandidateSource,
        val evidence: String,
        val order: Int,
        val scale: Int? = null,
    ) {
        fun score(frequency: Int, totalCandidates: Int): Int {
            var score = source.rank
            if (scale != null) {
                score += 20
            }
            if (frequency > 1) {
                score += (frequency - 1) * 6
            }
            if (isBoundaryValue() && totalCandidates > 1) {
                score -= 10
            }
            if (!value.isWholeNumber()) {
                score += 2
            }
            return score
        }

        private fun isBoundaryValue(): Boolean {
            return value == 0f || value == 5f || value == 10f
        }

        private fun Float.isWholeNumber(): Boolean {
            return this % 1f == 0f
        }

        fun valueKey(): String = String.format(Locale.ROOT, "%.6f", value)
    }

    fun parse(html: String?): Float? {
        if (html.isNullOrBlank()) {
            debugLog("parse: blank input")
            return null
        }

        val document = Jsoup.parse(html)
        val candidates = buildList {
            addAll(extractStructuredCandidates(document))
            addAll(extractSemanticCandidates(document))
            addAll(extractTextCandidates(document))
        }

        val bestCandidate = selectBestCandidate(candidates)
        if (bestCandidate != null) {
            debugLog(
                buildString {
                    append("parse: selected source=")
                    append(bestCandidate.source)
                    append(" value=")
                    append(bestCandidate.value.previewFloat())
                    append(" evidence=")
                    append(bestCandidate.evidence.previewForLog())
                },
            )
            return bestCandidate.value
        }

        debugLog("parse: no match")
        return null
    }

    private fun extractStructuredCandidates(document: Document): List<RatingCandidate> {
        val candidates = mutableListOf<RatingCandidate>()

        document.select("[itemprop=ratingValue], meta[property=rating:average], meta[name=rating]")
            .forEachIndexed { index, element ->
                val rawValue = element.attr("content").ifBlank { element.text() }.trim()
                if (rawValue.isBlank()) {
                    return@forEachIndexed
                }

                val scaleHint = detectScaleHint(element)
                extractTextMatch(rawValue, allowBareValue = true)
                    ?.let { match ->
                        normalizeRating(match.value, scaleHint ?: match.scale)
                            ?.let { normalized ->
                                candidates += RatingCandidate(
                                    value = normalized,
                                    source = CandidateSource.STRUCTURED_META,
                                    evidence = buildString {
                                        append(element.tagName())
                                        append("=")
                                        append(rawValue.previewForLog())
                                        append(" scale=")
                                        append((scaleHint ?: match.scale)?.toString().orEmpty())
                                    },
                                    order = index,
                                    scale = scaleHint ?: match.scale,
                                )
                            }
                    }
            }

        document.select("script[type=application/ld+json]").forEachIndexed { index, script ->
            val scriptData = script.data()
            val scaleHint = extractBestRatingScale(scriptData)
            addScriptCandidates(
                data = scriptData,
                source = CandidateSource.STRUCTURED_JSON_LD,
                orderOffset = index * 100,
                scaleHint = scaleHint,
                candidates = candidates,
            )
        }

        document.select("script:not([type=application/ld+json])").forEachIndexed { index, script ->
            val scriptData = script.data()
            addScriptCandidates(
                data = scriptData,
                source = CandidateSource.EMBEDDED_STATE,
                orderOffset = index * 100,
                scaleHint = extractBestRatingScale(scriptData),
                candidates = candidates,
            )
        }

        return candidates
    }

    private fun addScriptCandidates(
        data: String,
        source: CandidateSource,
        orderOffset: Int,
        scaleHint: Int?,
        candidates: MutableList<RatingCandidate>,
    ) {
        buildList {
            jsonRatingValuePattern.findAll(data).forEach { add(it) }
            jsonAverageRatingPattern.findAll(data).forEach { add(it) }
            jsonScorePattern.findAll(data).forEach { add(it) }
        }.forEachIndexed { index, match ->
            val rawValue = match.groupValues.getOrNull(1).orEmpty()
            if (rawValue.isBlank()) {
                return@forEachIndexed
            }

            extractTextMatch(rawValue, allowBareValue = true)
                ?.let { parsed ->
                    normalizeRating(parsed.value, scaleHint ?: parsed.scale)
                        ?.let { normalized ->
                            candidates += RatingCandidate(
                                value = normalized,
                                source = source,
                                evidence = "${source.name.lowercase(Locale.ROOT)}=${match.value.previewForLog()}",
                                order = orderOffset + index,
                                scale = scaleHint ?: parsed.scale,
                            )
                        }
                }
        }
    }

    private fun extractSemanticCandidates(document: Document): List<RatingCandidate> {
        val candidates = mutableListOf<RatingCandidate>()

        document.select("[data-tippy-content], [title], [aria-label]").forEachIndexed { index, element ->
            val label = element.attr("data-tippy-content")
                .ifBlank { element.attr("title") }
                .ifBlank { element.attr("aria-label") }
            if (label.isBlank()) {
                return@forEachIndexed
            }

            val signal = normalizeText(
                listOf(
                    label,
                    element.className(),
                    element.id(),
                    element.attr("title"),
                    element.attr("aria-label"),
                ).joinToString(" "),
            )
            if (!looksLikeRatingSignal(signal)) {
                return@forEachIndexed
            }

            val candidateText = element.text().ifBlank { label }
            extractTextMatch(candidateText, allowBareValue = true)
                ?.let { match ->
                    normalizeRating(match.value, match.scale)
                        ?.let { normalized ->
                            candidates += RatingCandidate(
                                value = normalized,
                                source = CandidateSource.SEMANTIC_WIDGET,
                                evidence = "widget=${candidateText.previewForLog()} label=${label.previewForLog()}",
                                order = index,
                                scale = match.scale,
                            )
                        }
                }
        }

        val ratingSelectors = listOf(
            "[class*=rating]",
            "[id*=rating]",
            "[class*=score]",
            "[id*=score]",
            "[class*=star]",
            "[id*=star]",
            "[aria-label*=rating]",
            "[title*=rating]",
            "[aria-label*=score]",
            "[title*=score]",
        ).joinToString(", ")
        document.select(ratingSelectors).forEachIndexed { index, element ->
            val candidateText = element.text()
                .ifBlank { element.attr("title") }
                .ifBlank { element.attr("aria-label") }
            if (candidateText.isBlank()) {
                return@forEachIndexed
            }

            extractTextMatch(candidateText, allowBareValue = true)
                ?.let { match ->
                    normalizeRating(match.value, match.scale)
                        ?.let { normalized ->
                            candidates += RatingCandidate(
                                value = normalized,
                                source = CandidateSource.SEMANTIC_WIDGET,
                                evidence = buildString {
                                    append("class=")
                                    append(element.className().previewForLog())
                                    append(" text=")
                                    append(candidateText.previewForLog())
                                },
                                order = 1000 + index,
                                scale = match.scale,
                            )
                        }
                }
        }

        return candidates
    }

    private fun extractTextCandidates(document: Document): List<RatingCandidate> {
        val bodyText = document.body().text()
        val match = extractTextMatch(bodyText, allowBareValue = false) ?: return emptyList()
        val normalized = normalizeRating(match.value, match.scale) ?: return emptyList()
        val source = if (match.scale != null) CandidateSource.TEXT_SCALE else CandidateSource.TEXT_KEYWORD
        return listOf(
            RatingCandidate(
                value = normalized,
                source = source,
                evidence = match.evidence,
                order = 0,
                scale = match.scale,
            ),
        )
    }

    private fun extractTextMatch(text: String?, allowBareValue: Boolean): TextMatch? {
        if (text.isNullOrBlank()) {
            return null
        }

        val normalized = normalizeText(text)
        if (normalized.isBlank() || hasNoRatingSentinel(normalized)) {
            return null
        }

        explicitScalePattern.find(normalized)?.let { match ->
            val rawValue = match.groupValues.getOrNull(1).orEmpty()
            val rawScale = match.groupValues.getOrNull(2).orEmpty().toIntOrNull()
            rawValue.toFloatOrNull()?.let { value ->
                return TextMatch(value = value, scale = rawScale, evidence = match.value)
            }
            rawValue.replace(',', '.').toFloatOrNull()?.let { value ->
                return TextMatch(value = value, scale = rawScale, evidence = match.value)
            }
        }

        findRatingKeywordRanges(normalized).forEach { range ->
            val start = (range.first - 40).coerceAtLeast(0)
            val end = (range.last + 60).coerceAtMost(normalized.lastIndex)
            val window = normalized.substring(start, end + 1)

            explicitScalePattern.find(window)?.let { match ->
                val rawValue = match.groupValues.getOrNull(1).orEmpty()
                val rawScale = match.groupValues.getOrNull(2).orEmpty().toIntOrNull()
                rawValue.toFloatOrNull()?.let { value ->
                    return TextMatch(value = value, scale = rawScale, evidence = window)
                }
                rawValue.replace(',', '.').toFloatOrNull()?.let { value ->
                    return TextMatch(value = value, scale = rawScale, evidence = window)
                }
            }

            bareRatingPattern.find(window)?.let { match ->
                val rawValue = match.groupValues.getOrNull(1).orEmpty()
                val value = rawValue.replace(',', '.').toFloatOrNull() ?: return@let
                return TextMatch(value = value, scale = null, evidence = window)
            }
        }

        if (allowBareValue && looksLikeStandaloneRating(normalized)) {
            bareRatingPattern.find(normalized)?.let { match ->
                val rawValue = match.groupValues.getOrNull(1).orEmpty()
                val value = rawValue.replace(',', '.').toFloatOrNull() ?: return@let
                return TextMatch(value = value, scale = null, evidence = normalized)
            }
        }

        return null
    }

    private fun looksLikeStandaloneRating(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("vote") ||
            lower.contains("голос") ||
            lower.contains("chapter") ||
            lower.contains("глава") ||
            lower.contains("progress") ||
            lower.contains("прогресс") ||
            lower.contains("просмотр") ||
            lower.contains("view")
        ) {
            return false
        }
        return text.length <= 72
    }

    private fun normalizeRating(rawValue: Float, rawScale: Int?): Float? {
        val normalized = when (rawScale) {
            5 -> rawValue * 2f
            else -> rawValue
        }

        if (normalized <= 0f || normalized > 10f) {
            return null
        }
        return normalized
    }

    private fun detectScaleHint(element: Element): Int? {
        var current: Element? = element.parent()
        while (current != null) {
            current.selectFirst(
                listOf(
                    "meta[itemprop=bestRating]",
                    "meta[property=rating:best]",
                    "meta[name=bestRating]",
                ).joinToString(", "),
            )?.attr("content")
                ?.replace(',', '.')
                ?.toFloatOrNull()
                ?.toInt()
                ?.takeIf { it in 1..10 }
                ?.let { return it }
            current = current.parent()
        }
        return null
    }

    private fun extractBestRatingScale(data: String): Int? {
        jsonBestRatingPattern.find(data)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.toInt()
            ?.takeIf { it in 1..10 }
            ?.let { return it }
        return null
    }

    private fun findRatingKeywordRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase(Locale.ROOT)
        val ranges = mutableListOf<IntRange>()

        for (keyword in ratingKeywordTerms) {
            var index = lower.indexOf(keyword)
            while (index >= 0) {
                val endIndex = index + keyword.length
                val startsAtBoundary = index == 0 || !isWordChar(lower[index - 1])
                val endsAtBoundary = endIndex >= lower.length || !isWordChar(lower[endIndex])
                if (startsAtBoundary && endsAtBoundary) {
                    ranges += index until endIndex
                }
                index = lower.indexOf(keyword, index + keyword.length)
            }
        }

        return ranges.distinctBy { it.first to it.last }.sortedBy { it.first }
    }

    private fun looksLikeRatingSignal(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return ratingKeywordTerms.any { keyword ->
            lower.contains(keyword)
        }
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_'
    }

    private fun selectBestCandidate(candidates: List<RatingCandidate>): RatingCandidate? {
        if (candidates.isEmpty()) {
            return null
        }

        val frequencyByValue = candidates.groupingBy { it.valueKey() }.eachCount()
        return candidates.maxWithOrNull(
            compareBy<RatingCandidate> {
                it.score(
                    frequency = frequencyByValue[it.valueKey()] ?: 1,
                    totalCandidates = candidates.size,
                )
            }.thenByDescending { it.order },
        )
    }

    private fun hasNoRatingSentinel(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("нет оценок") ||
            lower.contains("нет рейтинга") ||
            lower.contains("no ratings") ||
            lower.contains("not rated") ||
            lower.contains("n/d") ||
            lower.contains("н/д")
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

    private fun parseJsonLdAggregateRating(payload: String): Float? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return null
        val ratingObject = root.findAggregateRatingObject() ?: return null
        val ratingValue =
            ratingObject.jsonPrimitiveFloat("ratingValue")
                ?: ratingObject.jsonPrimitiveFloat("score")
                ?: return null
        val bestRating = ratingObject.jsonPrimitiveFloat("bestRating")
        return normalizeRating(ratingValue, bestRating?.toInt())
    }

    private fun JsonObject.jsonPrimitiveFloat(key: String): Float? {
        return (this[key] as? JsonPrimitive)
            ?.content
            ?.replace(',', '.')
            ?.toFloatOrNull()
    }

    private fun String.previewForLog(limit: Int = 120): String {
        return replace(Regex("\\s+"), " ").take(limit)
    }

    private fun Float?.previewFloat(): String {
        return this?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }
}
