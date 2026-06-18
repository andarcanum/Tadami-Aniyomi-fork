package eu.kanade.tachiyomi.ui.player.subtitle.translation

interface AiSubtitleTranslationClient {
    val fingerprint: String

    suspend fun translateIndexedSegments(
        prompt: String,
        segments: List<IndexedSubtitleSegment>,
        sourceLanguage: String,
        targetLanguage: String,
    ): String
}

data class IndexedSubtitleSegment(
    val index: Int,
    val text: String,
)

class AiSubtitleTranslationProvider(
    private val client: AiSubtitleTranslationClient?,
    private val maxCueChars: Int = 24_000,
) : SubtitleTranslationProvider {
    override val id = SubtitleTranslationProviderId.Ai
    override val fingerprint = "ai-subtitle-${client?.fingerprint ?: "unconfigured"}-v1"
    override val supportsBatch = true

    override suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (SubtitleTranslationProgress) -> Unit,
    ): SubtitleTranslationProviderResult {
        val aiClient = client ?: return SubtitleTranslationProviderResult.Failure(
            message = "AI subtitle translation provider is not configured",
            retryable = false,
        )
        val cleanSegments = cues
            .filter { it.text.isNotBlank() }
            .map { IndexedSubtitleSegment(it.index, protectSubtitleText(it.text)) }
        if (cleanSegments.isEmpty()) {
            return SubtitleTranslationProviderResult.Success(emptyMap())
        }

        val chunks = cleanSegments.chunkByCharacterBudget(maxCueChars)
        val translatedByIndex = linkedMapOf<Int, String>()
        var translatedCount = 0
        onProgress(SubtitleTranslationProgress(0, cleanSegments.size, SubtitleTranslationStage.Translating))

        chunks.forEach { chunk ->
            val raw = aiClient.translateIndexedSegments(
                prompt = buildSubtitleAiPrompt(sourceLanguage, targetLanguage),
                segments = chunk,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
            )
            val parsed = parseIndexedSubtitleTranslationResponse(raw)
            chunk.forEach { segment ->
                parsed[segment.index]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { translatedByIndex[segment.index] = restoreSubtitleText(it) }
            }
            translatedCount += chunk.size
            onProgress(
                SubtitleTranslationProgress(
                    translated = translatedCount.coerceAtMost(cleanSegments.size),
                    total = cleanSegments.size,
                    stage = SubtitleTranslationStage.Translating,
                ),
            )
        }

        onProgress(
            SubtitleTranslationProgress(translatedByIndex.size, cleanSegments.size, SubtitleTranslationStage.Writing),
        )
        return SubtitleTranslationProviderResult.Success(translatedByIndex)
    }
}

private fun List<IndexedSubtitleSegment>.chunkByCharacterBudget(maxChars: Int): List<List<IndexedSubtitleSegment>> {
    val budget = maxChars.coerceAtLeast(1_000)
    val chunks = mutableListOf<List<IndexedSubtitleSegment>>()
    val current = mutableListOf<IndexedSubtitleSegment>()
    var currentChars = 0
    for (segment in this) {
        val segmentChars = segment.text.length.coerceAtLeast(1)
        if (current.isNotEmpty() && currentChars + segmentChars > budget) {
            chunks += current.toList()
            current.clear()
            currentChars = 0
        }
        if (segmentChars > budget) {
            chunks += listOf(segment)
        } else {
            current += segment
            currentChars += segmentChars
        }
    }
    if (current.isNotEmpty()) chunks += current.toList()
    return chunks
}

fun buildSubtitleAiPrompt(sourceLanguage: String, targetLanguage: String): String {
    return """
        Translate subtitle cues from ${sourceLanguage.ifBlank { "auto" }} to $targetLanguage.
        Preserve cue order, meaning, tone and reasonable line breaks.
        Return only indexed XML-like segments in this exact format:
        <s i=\"0\">translated text</s>
        <s i=\"1\">translated text</s>
        Do not add explanations. Do not merge, remove, reorder or add segments.
        Preserve placeholders and protected subtitle styling markers.
    """.trimIndent()
}

fun parseIndexedSubtitleTranslationResponse(raw: String): Map<Int, String> {
    val regex = Regex(
        pattern = """<s\s+i=["'](\d+)["']\s*>(.*?)</s>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    return regex.findAll(raw)
        .mapNotNull { match ->
            val index = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val text = match.groupValues.getOrNull(2)?.trim().orEmpty()
            index to unescapeXmlText(text)
        }
        .toMap()
}

private fun protectSubtitleText(text: String): String {
    return text
        .replace(Regex("\\{[^}]+}")) { match -> "<keep>${match.value}</keep>" }
}

private fun restoreSubtitleText(text: String): String {
    return text
        .replace(Regex("(?i)<keep>(.*?)</keep>")) { match -> match.groupValues[1] }
}

private fun unescapeXmlText(value: String): String {
    return value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
