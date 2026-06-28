package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.detectNovelTextLanguage
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Routes a dictionary lookup to a [NovelDictionaryProvider].
 *
 * Definitions are returned in the user's chosen [targetLanguageCode]:
 *  - "en" (default) → en.wiktionary.org REST API (always available, definitions in English)
 *  - other (e.g. "ru") → {lang}.wiktionary.org MediaWiki Action API (`/w/api.php?action=query
 *    &prop=extracts&explaintext`) so definitions arrive in the user's language.
 */
class OnlineDictionaryProvider(
    private val client: OkHttpClient,
    private val json: Json,
    private val fallbackLanguage: String = "en",
    private val extraProviders: Map<String, NovelDictionaryProvider> = emptyMap(),
) : NovelDictionaryProvider {

    override val fingerprint: String = "online-dictionary"

    private val wiktionaryCache = mutableMapOf<String, WiktionaryDictionaryProvider>()

    override suspend fun lookup(request: NovelDictionaryRequest): NovelDictionaryProviderOutcome {
        val wordLang = resolveWordLanguage(request)

        // Choose Wiktionary edition based on the user's desired definition language:
        //  - "en"  → en.wiktionary REST API (always available, definitions in English)
        //  - other → {lang}.wiktionary MediaWiki Action API (definitions in user's language)
        val defLang = normalizeLangCode(
            request.targetLanguageCode.trim().ifEmpty { DEFINITION_EDITION },
        )
        val definitionEdition = if (defLang == "en") DEFINITION_EDITION else defLang

        val provider = wiktionaryFor(definitionEdition)
        val outcome = provider.lookup(request.copy(sourceLanguageHint = wordLang))

        if (outcome is NovelDictionaryProviderOutcome.Unavailable && definitionEdition != "en") {
            // Fall back to the English ("en") Wiktionary provider
            val fallbackProvider = wiktionaryFor("en")
            return fallbackProvider.lookup(request.copy(sourceLanguageHint = wordLang))
        }

        return outcome
    }

    private fun resolveWordLanguage(request: NovelDictionaryRequest): String {
        val hint = request.sourceLanguageHint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (hint != null) return hint
        return detectNovelTextLanguage(request.term)?.lowercase() ?: fallbackLanguage
    }

    private fun wiktionaryFor(lang: String): WiktionaryDictionaryProvider {
        val cleanLang = lang.lowercase().trim().ifEmpty { "en" }
        return synchronized(wiktionaryCache) {
            wiktionaryCache.getOrPut(cleanLang) {
                WiktionaryDictionaryProvider(client = client, json = json, languageCode = cleanLang)
            }
        }
    }

    private companion object {
        // Wiktionary's definition REST endpoint is English-only; see lookup() above.
        const val DEFINITION_EDITION = "en"

        /**
         * Normalize whatever the user stored in preferences to a 2-letter ISO 639-1 code.
         * Legacy stored values like "Russian" / "English" are mapped here so old preferences
         * don't accidentally produce "russian.wiktionary.org".
         */
        fun normalizeLangCode(raw: String): String = when (raw.trim().lowercase()) {
            "russian", "русский" -> "ru"
            "english", "английский" -> "en"
            "japanese", "японский" -> "ja"
            "chinese", "китайский" -> "zh"
            "korean", "корейский" -> "ko"
            "german", "немецкий", "deutsch" -> "de"
            "french", "французский", "français" -> "fr"
            "spanish", "испанский", "español" -> "es"
            "italian", "итальянский" -> "it"
            "portuguese", "португальский" -> "pt"
            "ukrainian", "украинский" -> "uk"
            "polish", "польский" -> "pl"
            "arabic", "арабский" -> "ar"
            "turkish", "турецкий" -> "tr"
            "hindi", "хинди" -> "hi"
            else -> raw.trim().lowercase()
        }
    }
}
