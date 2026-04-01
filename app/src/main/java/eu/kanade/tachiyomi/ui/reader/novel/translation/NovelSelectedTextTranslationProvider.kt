package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationCacheKey
import eu.kanade.tachiyomi.ui.reader.novel.buildNovelSelectedTextTranslationCacheKey
import eu.kanade.tachiyomi.ui.reader.novel.normalizeNovelSelectedText

data class NovelSelectedTextTranslationRequest(
    val selectedText: String,
    val targetLanguage: String,
    val sourceLanguageHint: String? = null,
)

sealed interface NovelSelectedTextTranslationProviderOutcome {
    data class Success(
        val result: NovelSelectedTextTranslationResult,
    ) : NovelSelectedTextTranslationProviderOutcome

    data class Unavailable(
        val reason: NovelSelectedTextTranslationErrorReason,
    ) : NovelSelectedTextTranslationProviderOutcome
}

interface NovelSelectedTextTranslationProvider {
    val fingerprint: String

    suspend fun translate(
        request: NovelSelectedTextTranslationRequest,
    ): NovelSelectedTextTranslationProviderOutcome
}

fun buildNovelSelectedTextTranslationRequestKey(
    providerFingerprint: String,
    request: NovelSelectedTextTranslationRequest,
): NovelSelectedTextTranslationCacheKey {
    return buildNovelSelectedTextTranslationCacheKey(
        backendFingerprint = providerFingerprint,
        targetLanguage = request.targetLanguage,
        text = normalizeNovelSelectedText(request.selectedText),
    )
}
