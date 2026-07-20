package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryEntry
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryResult
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline dictionary provider backed by user-imported StarDict dictionaries.
 *
 * No dictionary data ships with the app; when nothing is imported (or everything is
 * disabled) the provider reports itself as unavailable so callers can fall back to
 * the online provider.
 */
class OfflineStarDictDictionaryProvider(
    private val context: Context,
    private val disabledIdsProvider: () -> Set<String> = { emptySet() },
) : NovelDictionaryProvider {

    override val fingerprint: String
        get() {
            val disabled = disabledIdsProvider().sorted().joinToString(",")
            return "stardict-offline:r${StarDictManager.revision}:d${disabled.hashCode()}"
        }

    override suspend fun lookup(request: NovelDictionaryRequest): NovelDictionaryProviderOutcome {
        val term = request.term.trim()
        if (term.isEmpty()) {
            return NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.EmptySelection,
            )
        }
        if (term.length > MAX_TERM_LENGTH) {
            return NovelDictionaryProviderOutcome.Unavailable(
                NovelSelectedTextTranslationErrorReason.TooLongSelection,
            )
        }
        return withContext(Dispatchers.IO) {
            val dictionaries = runCatching {
                StarDictManager.loadEnabled(context, disabledIdsProvider())
            }.getOrElse { emptyList() }
            if (dictionaries.isEmpty()) {
                return@withContext NovelDictionaryProviderOutcome.Unavailable(
                    NovelSelectedTextTranslationErrorReason.BackendUnavailable(
                        "No offline dictionaries are imported and enabled",
                    ),
                )
            }

            val entries = mutableListOf<NovelDictionaryEntry>()
            val usedDictionaries = mutableListOf<String>()
            for (dictionary in dictionaries) {
                val articles = runCatching { dictionary.lookup(term) }.getOrElse { emptyList() }
                if (articles.isEmpty()) continue
                usedDictionaries += dictionary.info.bookname
                articles.forEach { article ->
                    entries += NovelDictionaryEntry(
                        headword = article.headword,
                        definitionsHtml = article.definitionsHtml,
                        sourceLanguage = request.sourceLanguageHint,
                    )
                }
                if (entries.size >= MAX_TOTAL_ENTRIES) break
            }

            if (entries.isEmpty()) {
                NovelDictionaryProviderOutcome.Unavailable(
                    NovelSelectedTextTranslationErrorReason.ParserFailure,
                )
            } else {
                NovelDictionaryProviderOutcome.Success(
                    NovelDictionaryResult(
                        entries = entries.take(MAX_TOTAL_ENTRIES),
                        providerFingerprint = fingerprint,
                        attribution = usedDictionaries.joinToString(" \u00b7 "),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MAX_TERM_LENGTH = 100
        const val MAX_TOTAL_ENTRIES = 8
    }
}
