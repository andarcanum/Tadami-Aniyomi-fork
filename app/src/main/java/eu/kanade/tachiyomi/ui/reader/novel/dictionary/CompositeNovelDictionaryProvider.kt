package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryRequest

/**
 * Routes dictionary lookups between the online (Wiktionary) and offline (StarDict)
 * providers based on the `novel_reader_dictionary_source` preference:
 *
 *  - `ONLINE`        — online only (legacy default)
 *  - `OFFLINE`       — imported StarDict dictionaries only
 *  - `OFFLINE_FIRST` — offline lookup first, online fallback
 *  - `ONLINE_FIRST`  — online lookup first, offline fallback
 *
 * The mode is re-read on every lookup, so preference changes apply immediately.
 */
class CompositeNovelDictionaryProvider(
    private val modeProvider: () -> String,
    private val online: NovelDictionaryProvider,
    private val offline: NovelDictionaryProvider,
) : NovelDictionaryProvider {

    enum class Mode { ONLINE, OFFLINE, OFFLINE_FIRST, ONLINE_FIRST }

    override val fingerprint: String
        get() = when (mode()) {
            Mode.ONLINE -> online.fingerprint
            Mode.OFFLINE -> offline.fingerprint
            Mode.OFFLINE_FIRST -> "offline-first|${offline.fingerprint}|${online.fingerprint}"
            Mode.ONLINE_FIRST -> "online-first|${online.fingerprint}|${offline.fingerprint}"
        }

    override suspend fun lookup(request: NovelDictionaryRequest): NovelDictionaryProviderOutcome {
        return when (mode()) {
            Mode.ONLINE -> online.lookup(request)
            Mode.OFFLINE -> offline.lookup(request)
            Mode.OFFLINE_FIRST -> lookupWithFallback(primary = offline, secondary = online, request = request)
            Mode.ONLINE_FIRST -> lookupWithFallback(primary = online, secondary = offline, request = request)
        }
    }

    private fun mode(): Mode = when (modeProvider().trim().uppercase()) {
        "OFFLINE" -> Mode.OFFLINE
        "OFFLINE_FIRST" -> Mode.OFFLINE_FIRST
        "ONLINE_FIRST" -> Mode.ONLINE_FIRST
        else -> Mode.ONLINE
    }

    private suspend fun lookupWithFallback(
        primary: NovelDictionaryProvider,
        secondary: NovelDictionaryProvider,
        request: NovelDictionaryRequest,
    ): NovelDictionaryProviderOutcome {
        val first = primary.lookup(request)
        if (first is NovelDictionaryProviderOutcome.Success) return first
        val second = secondary.lookup(request)
        if (second is NovelDictionaryProviderOutcome.Success) return second
        // Both failed: prefer the more meaningful error over a plain "backend missing".
        val firstReason = (first as NovelDictionaryProviderOutcome.Unavailable).reason
        return if (firstReason is NovelSelectedTextTranslationErrorReason.BackendUnavailable) second else first
    }
}
