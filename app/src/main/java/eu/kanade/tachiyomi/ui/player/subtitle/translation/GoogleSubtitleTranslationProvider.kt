package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import kotlinx.coroutines.delay

class GoogleSubtitleTranslationProvider(
    private val service: GoogleTranslationService,
    private val maxAttempts: Int = 3,
) : SubtitleTranslationProvider {
    override val id = SubtitleTranslationProviderId.Google
    override val fingerprint = "google-unofficial-subtitle-v1"
    override val supportsBatch = true

    override suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (SubtitleTranslationProgress) -> Unit,
    ): SubtitleTranslationProviderResult {
        if (cues.isEmpty()) return SubtitleTranslationProviderResult.Success(emptyMap())

        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts.coerceAtLeast(1)) {
            val result = runCatching {
                val response = service.translateBatch(
                    texts = cues.map { it.text },
                    params = GoogleTranslationParams(
                        sourceLang = sourceLanguage.ifBlank { "auto" },
                        targetLang = targetLanguage,
                    ),
                    onProgress = { phase: TranslationPhase, percent: Int ->
                        val stage = when (phase) {
                            TranslationPhase.IDLE -> SubtitleTranslationStage.CacheLookup
                            TranslationPhase.TRANSLATING -> SubtitleTranslationStage.Translating
                        }
                        onProgress(
                            SubtitleTranslationProgress(
                                translated = ((percent.coerceIn(0, 100) * cues.size) / 100),
                                total = cues.size,
                                stage = stage,
                            ),
                        )
                    },
                )
                SubtitleTranslationProviderResult.Success(
                    translatedByIndex = response.translatedByIndex.mapKeys { (index, _) -> cues[index].index },
                    detectedSourceLanguage = response.detectedSourceLanguage,
                )
            }

            result.onSuccess { return it }
            val error = result.exceptionOrNull() ?: return SubtitleTranslationProviderResult.Failure(
                message = "Subtitle translation failed",
                retryable = true,
            )
            if (error is kotlin.coroutines.cancellation.CancellationException) throw error
            lastError = error
            if (!error.isRetryableNetworkError()) {
                return SubtitleTranslationProviderResult.Failure(
                    message = error.message ?: "Subtitle translation failed",
                    retryable = false,
                )
            }
            attempt++
            if (attempt < maxAttempts) {
                onProgress(SubtitleTranslationProgress(0, cues.size, SubtitleTranslationStage.Retrying))
                delay(googleBackoffMs(attempt))
            }
        }

        return SubtitleTranslationProviderResult.Failure(
            message = lastError?.message ?: "Subtitle translation failed after retries",
            retryable = true,
        )
    }
}

private fun googleBackoffMs(attempt: Int): Long {
    val base = 1_000L * (1L shl (attempt - 1).coerceIn(0, 4))
    val jitter = (0..400).random().toLong()
    return (base + jitter).coerceAtMost(10_000L)
}

private fun Throwable.isRetryableNetworkError(): Boolean {
    val message = (message ?: "").lowercase()
    // Rate limiting / transient server / network conditions -> retry.
    val retryable = listOf(
        "429", "rate", "too many", "500", "502", "503", "504",
        "timeout", "timed out", "connection", "reset", "unavailable",
    )
    if (retryable.any { message.contains(it) }) return true
    // Hard client errors -> do not retry.
    val fatal = listOf("400", "401", "403", "404", "unsupported", "invalid")
    if (fatal.any { message.contains(it) }) return false
    // Unknown IO errors: retry once is safer than failing hard.
    return this is java.io.IOException
}
