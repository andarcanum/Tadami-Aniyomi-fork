package eu.kanade.tachiyomi.ui.player.subtitle.translation

import java.security.MessageDigest

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val settings: String? = null,
)

data class SubtitleDocument(
    val format: SubtitleFormat,
    val cues: List<SubtitleCue>,
    val headerLines: List<String> = emptyList(),
)

enum class SubtitleFormat(val extension: String) {
    Srt("srt"),
    Vtt("vtt"),
    Ass("ass"),
    Unknown("vtt"),
}

enum class SubtitleTranslationProviderId {
    Google,
    Ai,
}

data class SubtitleTranslationRequest(
    val document: SubtitleDocument,
    val sourceLanguage: String,
    val targetLanguage: String,
    val providerId: SubtitleTranslationProviderId,
    val sourceIdentity: String,
    val useCache: Boolean = true,
)

data class SubtitleTranslationProgress(
    val translated: Int,
    val total: Int,
    val stage: SubtitleTranslationStage,
) {
    val percent: Int = if (total <= 0) 0 else ((translated * 100) / total).coerceIn(0, 100)
}

enum class SubtitleTranslationStage {
    Parsing,
    CacheLookup,
    Translating,
    Writing,
    Complete,
}

data class SubtitleTranslationResult(
    val document: SubtitleDocument,
    val translatedCueCount: Int,
    val cacheKey: String,
    val fromCache: Boolean,
)

sealed interface SubtitleTranslationProviderResult {
    data class Success(
        val translatedByIndex: Map<Int, String>,
        val detectedSourceLanguage: String? = null,
    ) : SubtitleTranslationProviderResult

    data class Failure(
        val message: String,
        val retryable: Boolean = true,
    ) : SubtitleTranslationProviderResult
}

interface SubtitleTranslationProvider {
    val id: SubtitleTranslationProviderId
    val fingerprint: String
    val supportsBatch: Boolean

    suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): SubtitleTranslationProviderResult
}

fun buildSubtitleTranslationCacheKey(
    providerFingerprint: String,
    sourceIdentity: String,
    sourceLanguage: String,
    targetLanguage: String,
    document: SubtitleDocument,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    update(providerFingerprint)
    update(sourceIdentity)
    update(sourceLanguage.trim().lowercase())
    update(targetLanguage.trim().lowercase())
    update(document.format.name)
    document.cues.forEach { cue ->
        update(cue.startMs.toString())
        update(cue.endMs.toString())
        update(cue.text)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
