package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import android.speech.tts.TextToSpeech
import eu.kanade.tachiyomi.ui.reader.novel.dictionary.NovelDictionaryHistory
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelDictionaryRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.buildNovelSelectedTextTranslationRequestKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

/**
 * Host the selected-text translation controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 */
internal interface NovelSelectionTranslationHost {
    val selectionScope: CoroutineScope

    fun selectionReaderSettings(): NovelReaderSettings?
    fun selectionNovel(): Novel?
    fun selectionChapter(): NovelChapter?
    fun selectionSourceLanguage(): String?
    fun selectionUpdateContent(settings: NovelReaderSettings)
    fun saveHighlight(selection: NovelSelectedTextSelection)
}

/**
 * Snapshot of the selected-text translation + dictionary UI state, merged into the reader state by
 * the screen model.
 */
data class NovelSelectionTranslationSnapshot(
    val selection: NovelSelectedTextSelection? = null,
    val translationUiState: NovelSelectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Idle,
    val dictionaryUiState: NovelDictionaryUiState = NovelDictionaryUiState.Idle,
)

/**
 * Selected-text translation + dictionary subsystem.
 *
 * Owns the selection, its translation/dictionary jobs, the session caches and the pronunciation
 * TTS engine. The [NovelReaderScreenModel] hosts the shared reader state through
 * [NovelSelectionTranslationHost] and forwards the reader's calls here.
 */
internal class NovelSelectionTranslationController(
    private val host: NovelSelectionTranslationHost,
    private val application: Application,
    private val novelReaderPreferences: NovelReaderPreferences,
    private val selectedTextTranslationProvider: NovelSelectedTextTranslationProvider,
    private val novelDictionaryProvider: NovelDictionaryProvider,
) {

    private var selectedTextTranslationSelection: NovelSelectedTextSelection? = null
    private var selectedTextTranslationUiState: NovelSelectedTextTranslationUiState =
        NovelSelectedTextTranslationUiState.Idle
    private var selectedTextTranslationJob: Job? = null
    private val selectedTextTranslationSessionCache = NovelSelectedTextTranslationSessionCache()
    private var novelDictionaryUiState: NovelDictionaryUiState = NovelDictionaryUiState.Idle
    private var novelDictionaryJob: Job? = null
    private val novelDictionarySessionCache = NovelDictionarySessionCache()

    private var dictionaryTts: TextToSpeech? = null

    /** Snapshot of the selection/dictionary UI state, merged into the reader state by the screen model. */
    fun snapshot(): NovelSelectionTranslationSnapshot = NovelSelectionTranslationSnapshot(
        selection = selectedTextTranslationSelection,
        translationUiState = selectedTextTranslationUiState,
        dictionaryUiState = novelDictionaryUiState,
    )

    fun updateSelectedTextSelection(selection: NovelSelectedTextSelection?) {
        val currentSettings = host.selectionReaderSettings()
        val translationEnabled = currentSettings?.selectedTextTranslationEnabled == true
        val dictionaryEnabled = novelReaderPreferences.novelDictionaryEnabled().get()
        // Highlighting works independently of the translation/dictionary toggles.
        val highlightOnly = selection?.triggerAction == SelectedTextAction.HIGHLIGHT
        if (!translationEnabled && !dictionaryEnabled && !highlightOnly) {
            clearSelection(refreshUi = false)
            return
        }
        selectedTextTranslationJob?.cancel()
        selectedTextTranslationJob = null
        novelDictionaryJob?.cancel()
        novelDictionaryJob = null
        selectedTextTranslationSelection = selection
        // Selecting text no longer shows an intermediate card: the selection toolbar carries an
        // explicit trigger action, so the state stays Idle until that action starts its own work.
        selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Idle
        novelDictionaryUiState = NovelDictionaryUiState.Idle
        refreshSelectedTextTranslationUi()

        if (selection != null) {
            when (selection.triggerAction) {
                SelectedTextAction.DICTIONARY -> lookupSelectedTextDefinition()
                SelectedTextAction.TRANSLATION -> translateSelectedText()
                SelectedTextAction.HIGHLIGHT -> {
                    host.saveHighlight(selection)
                    // A saved highlight owns no card: drop the selection so the translation/
                    // dictionary overlay never appears for it.
                    clearSelection(refreshUi = true)
                }
                null -> {}
            }
        }
    }

    fun translateSelectedText() {
        val selection = selectedTextTranslationSelection ?: return
        val settings = host.selectionReaderSettings() ?: return
        if (!settings.selectedTextTranslationEnabled) return
        if (selectedTextTranslationJob?.isActive == true) return

        // Respect the language of the selected text: detect it (using the source's declared
        // language as a tiebreaker for Han characters) and pass it as a hint so the provider can
        // send an explicit `sl` instead of always relying on auto-detection.
        val sourceLanguage = host.selectionSourceLanguage()
        val request = NovelSelectedTextTranslationRequest(
            selectedText = selection.text,
            targetLanguage = settings.selectedTextTranslationTargetLanguage,
            sourceLanguageHint = detectNovelTextLanguage(selection.text, sourceLanguage),
        )
        val cacheKey = buildNovelSelectedTextTranslationRequestKey(
            providerFingerprint = selectedTextTranslationProvider.fingerprint,
            request = request,
        )
        selectedTextTranslationSessionCache.get(cacheKey)?.let { cached ->
            selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Result(
                selection = selection,
                translationResult = cached,
            )
            refreshSelectedTextTranslationUi()
            return
        }

        selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Translating(selection)
        refreshSelectedTextTranslationUi()
        selectedTextTranslationJob?.cancel()
        selectedTextTranslationJob = host.selectionScope.launch {
            val outcome = selectedTextTranslationProvider.translate(request)
            if (isNovelSelectedTextTranslationResponseStale(selectedTextTranslationSelection, selection.sessionId)) {
                return@launch
            }
            when (outcome) {
                is NovelSelectedTextTranslationProviderOutcome.Success -> {
                    selectedTextTranslationSessionCache.put(cacheKey, outcome.result)
                    selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Result(
                        selection = selection,
                        translationResult = outcome.result,
                    )
                }
                is NovelSelectedTextTranslationProviderOutcome.Unavailable -> {
                    selectedTextTranslationUiState = when (outcome.reason) {
                        is NovelSelectedTextTranslationErrorReason.Cooldown,
                        NovelSelectedTextTranslationErrorReason.EmptySelection,
                        NovelSelectedTextTranslationErrorReason.TooLongSelection,
                        NovelSelectedTextTranslationErrorReason.WebViewUnavailable,
                        is NovelSelectedTextTranslationErrorReason.BackendUnavailable,
                        -> {
                            NovelSelectedTextTranslationUiState.Unavailable(outcome.reason)
                        }
                        is NovelSelectedTextTranslationErrorReason.NetworkFailure,
                        NovelSelectedTextTranslationErrorReason.ParserFailure,
                        -> {
                            NovelSelectedTextTranslationUiState.Error(
                                selection = selection,
                                reason = outcome.reason,
                            )
                        }
                    }
                }
            }
            refreshSelectedTextTranslationUi()
        }
    }

    fun retrySelectedTextTranslation() {
        translateSelectedText()
    }

    fun dismissSelectedTextTranslation() {
        clearSelection()
    }

    fun resetSelectedTextTranslationForChapter() {
        clearSelection()
        selectedTextTranslationSessionCache.clear()
    }

    fun lookupSelectedTextDefinition() {
        val enabled = novelReaderPreferences.novelDictionaryEnabled().get()
        if (!enabled) return
        val selection = selectedTextTranslationSelection ?: return
        if (novelDictionaryJob?.isActive == true) return

        val term = selection.text
        val sourceLanguage = host.selectionSourceLanguage()
        val wordLang = detectNovelTextLanguage(term, sourceLanguage)
        val targetLangCode = novelReaderPreferences.novelDictionaryTargetLanguage().get()

        val cacheKey = buildNovelDictionaryCacheKey(
            backendFingerprint = novelDictionaryProvider.fingerprint,
            sourceLanguage = wordLang ?: "",
            term = term,
        )
        novelDictionarySessionCache.get(cacheKey)?.let { cached ->
            novelDictionaryUiState = NovelDictionaryUiState.Result(selection, cached)
            recordDictionaryHistory(term, wordLang, targetLangCode, cached)
            refreshSelectedTextTranslationUi()
            return
        }

        novelDictionaryUiState = NovelDictionaryUiState.Looking(selection)
        refreshSelectedTextTranslationUi()
        novelDictionaryJob?.cancel()
        novelDictionaryJob = host.selectionScope.launch {
            val outcome = novelDictionaryProvider.lookup(
                NovelDictionaryRequest(
                    term = term,
                    sourceLanguageHint = wordLang,
                    targetLanguageCode = targetLangCode,
                ),
            )
            if (isNovelSelectedTextTranslationResponseStale(selectedTextTranslationSelection, selection.sessionId)) {
                return@launch
            }
            when (outcome) {
                is NovelDictionaryProviderOutcome.Success -> {
                    novelDictionarySessionCache.put(cacheKey, outcome.result)
                    recordDictionaryHistory(term, wordLang, targetLangCode, outcome.result)
                    novelDictionaryUiState = NovelDictionaryUiState.Result(selection, outcome.result)
                }
                is NovelDictionaryProviderOutcome.Unavailable -> {
                    novelDictionaryUiState = when (outcome.reason) {
                        is NovelSelectedTextTranslationErrorReason.Cooldown,
                        NovelSelectedTextTranslationErrorReason.EmptySelection,
                        NovelSelectedTextTranslationErrorReason.TooLongSelection,
                        NovelSelectedTextTranslationErrorReason.WebViewUnavailable,
                        is NovelSelectedTextTranslationErrorReason.BackendUnavailable,
                        -> NovelDictionaryUiState.Unavailable(outcome.reason)
                        is NovelSelectedTextTranslationErrorReason.NetworkFailure,
                        NovelSelectedTextTranslationErrorReason.ParserFailure,
                        -> NovelDictionaryUiState.Error(selection, outcome.reason)
                    }
                }
            }
            refreshSelectedTextTranslationUi()
        }
    }

    fun retryNovelDictionary() {
        lookupSelectedTextDefinition()
    }

    fun dismissNovelDictionary() {
        novelDictionaryJob?.cancel()
        novelDictionaryJob = null
        novelDictionaryUiState = NovelDictionaryUiState.Idle
        refreshSelectedTextTranslationUi()
    }

    fun resetNovelDictionaryForChapter() {
        novelDictionaryJob?.cancel()
        novelDictionaryJob = null
        novelDictionarySessionCache.clear()
        novelDictionaryUiState = NovelDictionaryUiState.Idle
    }

    private fun recordDictionaryHistory(
        term: String,
        language: String?,
        targetLanguage: String?,
        result: NovelDictionaryResult,
    ) {
        val novelTitle = host.selectionNovel()?.title
        val chapterName = host.selectionChapter()?.name
        host.selectionScope.launch(Dispatchers.IO) {
            runCatching {
                NovelDictionaryHistory.record(
                    context = application,
                    term = term,
                    language = language,
                    targetLanguage = targetLanguage,
                    preview = NovelDictionaryHistory.previewOf(result),
                    novelTitle = novelTitle,
                    chapterName = chapterName,
                    provider = result.attribution,
                )
            }
        }
    }

    fun playSelectedTextPronunciation(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val sourceLanguage = host.selectionSourceLanguage()
        val lang = detectNovelTextLanguage(cleanText, sourceLanguage) ?: "en"
        val locale = when (lang) {
            "ja" -> java.util.Locale.JAPANESE
            "zh" -> java.util.Locale.CHINESE
            "ko" -> java.util.Locale.KOREAN
            "ru" -> java.util.Locale.forLanguageTag("ru")
            else -> java.util.Locale.ENGLISH
        }

        val ttsListener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                dictionaryTts?.language = locale
                dictionaryTts?.speak(
                    cleanText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "dictionary_pronunciation",
                )
            }
        }

        if (dictionaryTts == null) {
            dictionaryTts = TextToSpeech(application, ttsListener)
        } else {
            dictionaryTts?.language = locale
            dictionaryTts?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "dictionary_pronunciation",
            )
        }
    }

    fun clearSelection(refreshUi: Boolean = true) {
        selectedTextTranslationJob?.cancel()
        selectedTextTranslationJob = null
        selectedTextTranslationSelection = null
        selectedTextTranslationUiState = NovelSelectedTextTranslationUiState.Idle
        novelDictionaryJob?.cancel()
        novelDictionaryJob = null
        novelDictionaryUiState = NovelDictionaryUiState.Idle
        if (refreshUi) {
            refreshSelectedTextTranslationUi()
        }
    }

    /** Resets selection state that must not survive a chapter switch. */
    fun clearChapterScopedState() {
        clearSelection(refreshUi = false)
        selectedTextTranslationSessionCache.clear()
    }

    /** Stops the pronunciation TTS engine and cancels in-flight jobs (screen model disposal). */
    fun dispose() {
        selectedTextTranslationJob?.cancel()
        novelDictionaryJob?.cancel()
        novelDictionaryJob = null
        dictionaryTts?.stop()
        dictionaryTts?.shutdown()
        dictionaryTts = null
    }

    private fun refreshSelectedTextTranslationUi() {
        val settings = host.selectionReaderSettings() ?: return
        host.selectionUpdateContent(settings)
    }
}
