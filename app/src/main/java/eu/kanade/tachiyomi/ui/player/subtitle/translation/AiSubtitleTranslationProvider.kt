package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

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
    private val maxConcurrency: Int = 3,
    private val maxAttemptsPerChunk: Int = 3,
) : SubtitleTranslationProvider {
    override val id = SubtitleTranslationProviderId.Ai
    override val fingerprint = "ai-subtitle-${client?.fingerprint ?: "unconfigured"}-v2"
    override val supportsBatch = true

    override suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (SubtitleTranslationProgress) -> Unit,
    ): SubtitleTranslationProviderResult =
        translate(cues, sourceLanguage, targetLanguage, SubtitleTranslationContext(), onProgress)

    override suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        context: SubtitleTranslationContext,
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
        val total = cleanSegments.size
        val done = AtomicInteger(0)
        onProgress(SubtitleTranslationProgress(0, total, SubtitleTranslationStage.Translating))

        val prompt = buildSubtitleAiPrompt(sourceLanguage, targetLanguage, context)
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))

        val chunkResults = try {
            coroutineScope {
                chunks.map { chunk ->
                    async {
                        semaphore.withPermit {
                            val parsed = translateChunkWithRetry(
                                aiClient = aiClient,
                                prompt = prompt,
                                chunk = chunk,
                                sourceLanguage = sourceLanguage,
                                targetLanguage = targetLanguage,
                            )
                            val mapped = LinkedHashMap<Int, String>()
                            chunk.forEach { segment ->
                                parsed[segment.index]
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { mapped[segment.index] = restoreSubtitleText(it) }
                            }
                            val progress = done.addAndGet(chunk.size).coerceAtMost(total)
                            onProgress(
                                SubtitleTranslationProgress(progress, total, SubtitleTranslationStage.Translating),
                            )
                            mapped
                        }
                    }
                }.awaitAll()
            }
        } catch (error: Throwable) {
            return SubtitleTranslationProviderResult.Failure(
                message = error.message ?: "AI subtitle translation failed",
                retryable = error.isRetryable(),
            )
        }

        val translatedByIndex = linkedMapOf<Int, String>()
        chunkResults.forEach { translatedByIndex.putAll(it) }

        onProgress(SubtitleTranslationProgress(translatedByIndex.size, total, SubtitleTranslationStage.Writing))
        return SubtitleTranslationProviderResult.Success(translatedByIndex)
    }

    private suspend fun translateChunkWithRetry(
        aiClient: AiSubtitleTranslationClient,
        prompt: String,
        chunk: List<IndexedSubtitleSegment>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<Int, String> {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttemptsPerChunk.coerceAtLeast(1)) {
            try {
                val raw = aiClient.translateIndexedSegments(
                    prompt = prompt,
                    segments = chunk,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                )
                return parseIndexedSubtitleTranslationResponse(raw)
            } catch (error: Throwable) {
                lastError = error
                if (!error.isRetryable()) throw error
                attempt++
                if (attempt >= maxAttemptsPerChunk) break
                delay(backoffDelayMs(attempt))
            }
        }
        throw lastError ?: IllegalStateException("AI subtitle translation failed")
    }
}

private fun backoffDelayMs(attempt: Int): Long {
    val base = 500L * (1L shl (attempt - 1).coerceIn(0, 5))
    val jitter = (0..250).random().toLong()
    return (base + jitter).coerceAtMost(8_000L)
}

private fun Throwable.isRetryable(): Boolean {
    val message = (message ?: "").lowercase()
    val fatal = listOf("unauthor", "forbidden", "invalid api", "api key", "400", "401", "403")
    if (fatal.any { message.contains(it) }) return false
    return true
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

fun buildSubtitleAiPrompt(
    sourceLanguage: String,
    targetLanguage: String,
    context: SubtitleTranslationContext = SubtitleTranslationContext(),
): String {
    val domain = buildString {
        context.title?.takeIf { it.isNotBlank() }?.let { append("Title: ").append(it).append('\n') }
        context.genreHint?.takeIf { it.isNotBlank() }?.let { append("Genre: ").append(it).append('\n') }
        if (context.glossaryTerms.isNotEmpty()) {
            append("Keep these names/terms transliterated consistently across the whole file: ")
            append(context.glossaryTerms.joinToString(", "))
            append('\n')
        }
    }
    return """
        Translate subtitle cues from ${sourceLanguage.ifBlank { "auto" }} to $targetLanguage.
        $domain
        Preserve cue order, meaning, tone and reasonable line breaks.
        Use natural, fluent dialogue consistent with previous lines.
        Return only indexed XML-like segments in this exact format:
        <s i="0">translated text</s>
        <s i="1">translated text</s>
        Do not add explanations. Do not merge, remove, reorder or add segments.
        Preserve placeholders and protected subtitle styling markers exactly as given.
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

// Android's ICU regex engine rejects a literal '}' that is not escaped, unlike the desktop JVM.
internal val subtitleAssBlockRegex = Regex("""\{[^}]+\}""")

private fun protectSubtitleText(text: String): String {
    return text
        .replace(subtitleAssBlockRegex) { match -> "<keep>${match.value}</keep>" }
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
