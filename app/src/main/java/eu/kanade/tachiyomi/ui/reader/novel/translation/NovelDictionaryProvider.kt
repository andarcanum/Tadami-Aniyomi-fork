package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryResult
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason

data class NovelDictionaryRequest(
    val term: String,
    val sourceLanguageHint: String? = null,
    val targetLanguageCode: String = "en",
)

sealed interface NovelDictionaryProviderOutcome {
    data class Success(val result: NovelDictionaryResult) : NovelDictionaryProviderOutcome
    data class Unavailable(val reason: NovelSelectedTextTranslationErrorReason) : NovelDictionaryProviderOutcome
}

interface NovelDictionaryProvider {
    /** Stable identity used as part of the cache key. */
    val fingerprint: String

    suspend fun lookup(request: NovelDictionaryRequest): NovelDictionaryProviderOutcome
}
