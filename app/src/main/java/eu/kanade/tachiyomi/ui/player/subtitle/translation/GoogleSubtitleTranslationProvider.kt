package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase

class GoogleSubtitleTranslationProvider(
    private val service: GoogleTranslationService,
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
        return runCatching {
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
        }.getOrElse { error ->
            SubtitleTranslationProviderResult.Failure(
                message = error.message ?: "Subtitle translation failed",
                retryable = true,
            )
        }
    }
}
