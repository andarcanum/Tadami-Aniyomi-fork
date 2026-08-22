package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.data.translation.TranslationJob
import eu.kanade.tachiyomi.data.translation.TranslationProgressUpdate
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationStatus
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationSessionCache
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Host the translation controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 */
internal interface NovelTranslationHost {
    val translationScope: CoroutineScope

    fun translationCurrentChapter(): NovelChapter?
    fun translationReaderSettings(): NovelReaderSettings?
    fun translationActiveChapterId(): Long?
    suspend fun translationSourceTextBlocks(chapterId: Long): List<String>
    fun translationCurrentParsedTextBlocks(): List<String>
    fun translationHolderClear(provider: String)
    fun translationHolderPut(provider: String, map: Map<Int, String>)
    fun translationHolderIsEmpty(provider: String): Boolean
    fun translationHolderMap(provider: String): Map<Int, String>
    fun translationUpdateContent(settings: NovelReaderSettings)
    fun translationRefreshBookModeSection(chapterId: Long)

    /** Rebuilds the resident book sections after the visible translation was switched. */
    fun translationRefreshBookModeTranslationVariant()
    fun translationIsGeminiTranslating(): Boolean
    fun translationIsGeminiTranslationVisible(): Boolean
    fun translationIsBookRuntimeActive(): Boolean
    fun translationHasConfiguredProvider(settings: NovelReaderSettings): Boolean
    fun translationApplyTranslationState(
        gemini: NovelReaderScreenModel.State.ReaderGeminiState,
        google: NovelReaderScreenModel.State.ReaderGoogleState,
    )
}

/**
 * Snapshot of the whole-chapter translation UI state, merged into the reader state by the
 * screen model.
 */
data class NovelTranslationState(
    val isGeminiTranslating: Boolean = false,
    val geminiTranslationProgress: Int = 0,
    val isGeminiTranslationVisible: Boolean = false,
    val hasGeminiTranslationCache: Boolean = false,
    val geminiLogs: List<String> = emptyList(),
    /** Queue progress of every chapter of the open book the reader has seen an update for. */
    val chapterProgress: Map<Long, NovelBookChapterTranslationProgress> = emptyMap(),
    val isGoogleTranslating: Boolean = false,
    val googleTranslationProgress: Int = 0,
    val isGoogleTranslationVisible: Boolean = false,
    val hasGoogleTranslationCache: Boolean = false,
    val googleLogs: List<String> = emptyList(),
    val googleRateLimited: Boolean = false,
    val translationPhase: TranslationPhase = TranslationPhase.IDLE,
)

/**
 * Whole-chapter translation subsystem (Gemini/AI queue + Google).
 *
 * Owns the translation jobs, the queue progress subscription, the visibility/cache flags and the
 * per-provider logs. The [NovelReaderScreenModel] keeps the [NovelReaderTranslationHolder] (the
 * rendered content pipeline reads it directly) and delegates the translation actions here.
 */
internal class NovelTranslationController(
    private val host: NovelTranslationHost,
    private val application: Application = Injekt.get(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val googleTranslationService: GoogleTranslationService = Injekt.get(),
    private val translationQueueManager: TranslationQueueManager = Injekt.get(),
) {

    private var geminiTranslationJob: Job? = null
    private var queueProgressJob: Job? = null
    private var subscribedChapterId: Long? = null
    private var googleTranslationJob: Job? = null
    private val googleSessionCache = GoogleTranslationSessionCache()

    private var hasTriggeredGeminiAutoStart: Boolean = false
    private var pendingAutoStartGeminiTranslation: Boolean = false

    private var state: NovelTranslationState = NovelTranslationState()

    /** Snapshot of the translation UI state, merged into the reader state by the screen model. */
    fun snapshot(): NovelTranslationState = state

    /** Copies the current flags back into the controller (used after the screen model builds a state). */
    fun restoreFromReaderState(
        gemini: NovelReaderScreenModel.State.ReaderGeminiState,
        google: NovelReaderScreenModel.State.ReaderGoogleState,
    ) {
        state = state.copy(
            isGeminiTranslating = gemini.isGeminiTranslating,
            geminiTranslationProgress = gemini.geminiTranslationProgress,
            isGeminiTranslationVisible = gemini.isGeminiTranslationVisible,
            hasGeminiTranslationCache = gemini.hasGeminiTranslationCache,
            geminiLogs = gemini.geminiLogs,
            chapterProgress = gemini.chapterProgress,
            isGoogleTranslating = google.isGoogleTranslating,
            googleTranslationProgress = google.googleTranslationProgress,
            isGoogleTranslationVisible = google.isGoogleTranslationVisible,
            hasGoogleTranslationCache = google.hasGoogleTranslationCache,
            googleLogs = google.googleLogs,
            translationPhase = google.translationPhase,
        )
    }

    fun setPendingAutoStart(value: Boolean) {
        pendingAutoStartGeminiTranslation = value
    }

    /** Resets the per-chapter auto-start guard so the next chapter can auto-start again. */
    fun resetAutoStartFlags() {
        hasTriggeredGeminiAutoStart = false
    }

    /** Active Gemini queue job, exposed for the TTS controller to wait on translated text. */
    fun geminiTranslationJob(): Job? = geminiTranslationJob

    /** Restores a cached translation for [chapterId] when one exists and matches the settings. */
    fun restoreGeminiTranslationFromCache(
        chapterId: Long,
        settings: NovelReaderSettings,
    ) {
        val cached = NovelReaderTranslationDiskCacheStore.get(chapterId)
        if (cached == null) {
            updateState { it.copy(hasGeminiTranslationCache = false) }
            return
        }
        val settingsMatch = NovelReaderTranslationCacheResolver.matches(
            cached = cached,
            requirements = settings.toTranslationCacheRequirements(),
        )
        if (!settingsMatch) {
            updateState { it.copy(hasGeminiTranslationCache = false) }
            return
        }
        host.translationHolderPut("gemini", cached.translatedByIndex)
        updateState {
            it.copy(
                hasGeminiTranslationCache = true,
                geminiTranslationProgress = 100,
                isGeminiTranslationVisible = true,
            )
        }
        addAiTranslationLog("?? Restored cached translation")
        // In book mode the document is not rebuilt from the chapter HTML, so the section that holds
        // this chapter has to be re-rendered for the translation to become visible.
        host.translationRefreshBookModeSection(chapterId)
    }

    private fun updateState(transform: (NovelTranslationState) -> NovelTranslationState) {
        state = transform(state)
        host.translationApplyTranslationState(
            gemini = NovelReaderScreenModel.State.ReaderGeminiState(
                isGeminiTranslating = state.isGeminiTranslating,
                geminiTranslationProgress = state.geminiTranslationProgress,
                isGeminiTranslationVisible = state.isGeminiTranslationVisible,
                hasGeminiTranslationCache = state.hasGeminiTranslationCache,
                geminiLogs = state.geminiLogs,
                chapterProgress = state.chapterProgress,
            ),
            google = NovelReaderScreenModel.State.ReaderGoogleState(
                isGoogleTranslating = state.isGoogleTranslating,
                googleTranslationProgress = state.googleTranslationProgress,
                isGoogleTranslationVisible = state.isGoogleTranslationVisible,
                hasGoogleTranslationCache = state.hasGoogleTranslationCache,
                googleLogs = state.googleLogs,
                translationPhase = state.translationPhase,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Queue progress subscription + auto-start
    // ---------------------------------------------------------------------------------------------

    /**
     * Subscribes to the translation queue for the reading session.
     *
     * Over a book the session spans the whole novel, so the subscription cannot be bound to the
     * chapter the reader was opened with: the queue keeps working on chapters the reader has already
     * scrolled past, or on the next ones. With [novelId] set, every update of that novel is recorded
     * per chapter and only the chapter under the reading position drives the overlay indicator.
     */
    fun subscribeToQueueProgress(chapterId: Long, novelId: Long? = null) {
        queueProgressJob?.cancel()
        subscribedChapterId = chapterId
        queueProgressJob = host.translationScope.launch {
            translationQueueManager.progressUpdates
                .filter { it.chapterId == chapterId || (novelId != null && novelId != 0L && it.novelId == novelId) }
                .onEach { update ->
                    if (update.logMessage != null) {
                        if (update.chapterId == overlayChapterId()) addAiTranslationLog(update.logMessage)
                        return@onEach
                    }
                    recordChapterProgress(update)
                    if (update.chapterId != overlayChapterId()) {
                        onBackgroundChapterUpdate(update)
                        return@onEach
                    }
                    when (update.status) {
                        TranslationStatus.IN_PROGRESS -> {
                            updateState {
                                it.copy(
                                    isGeminiTranslating = true,
                                    geminiTranslationProgress = update.progress,
                                )
                            }
                        }
                        TranslationStatus.COMPLETED -> {
                            val settings = host.translationReaderSettings()
                            updateState {
                                it.copy(
                                    isGeminiTranslating = false,
                                    geminiTranslationProgress = 100,
                                )
                            }
                            if (settings != null) {
                                restoreGeminiTranslationFromCache(update.chapterId, settings)
                                host.translationUpdateContent(settings)
                            }
                        }
                        TranslationStatus.FAILED -> {
                            updateState {
                                it.copy(
                                    isGeminiTranslating = false,
                                    geminiTranslationProgress = 0,
                                )
                            }
                            addAiTranslationLog(
                                "Queue translation failed: ${update.errorMessage ?: "Unknown error"}",
                            )
                        }
                        TranslationStatus.CANCELLED -> {
                            updateState {
                                it.copy(
                                    isGeminiTranslating = false,
                                    geminiTranslationProgress = 0,
                                )
                            }
                            addAiTranslationLog("Translation cancelled.")
                        }
                        TranslationStatus.PENDING -> {
                            updateState {
                                it.copy(
                                    isGeminiTranslating = true,
                                    geminiTranslationProgress = 0,
                                )
                            }
                        }
                    }
                }
                .collect { }
        }
    }

    fun maybeAutoStartGeminiTranslation(settings: NovelReaderSettings) {
        if (hasTriggeredGeminiAutoStart) return
        val requestedAutoStart = pendingAutoStartGeminiTranslation
        val englishSourceAutoStart = settings.geminiAutoTranslateEnglishSource &&
            isGeminiSourceLanguageEnglish(settings.geminiSourceLang)
        if (!settings.geminiEnabled || !(requestedAutoStart || englishSourceAutoStart)) return
        if (!host.translationHasConfiguredProvider(settings)) return
        if (host.translationCurrentParsedTextBlocks().isEmpty()) return
        if (state.isGeminiTranslating || state.hasGeminiTranslationCache ||
            !host.translationHolderIsEmpty("gemini")
        ) {
            return
        }
        hasTriggeredGeminiAutoStart = true
        pendingAutoStartGeminiTranslation = false
        addAiTranslationLog("?? Auto-start translation for English source")
        startGeminiTranslation()
    }

    fun cancelQueueProgress() {
        queueProgressJob?.cancel()
        queueProgressJob = null
        subscribedChapterId = null
    }

    /** Chapter the overlay indicator describes: the one under the reading position over a book. */
    private fun overlayChapterId(): Long? = host.translationActiveChapterId() ?: subscribedChapterId

    /** Records the queue state of [update]'s chapter so the overlay can show it per chapter. */
    private fun recordChapterProgress(update: TranslationProgressUpdate) {
        val entry = NovelBookChapterTranslationProgress(
            chapterId = update.chapterId,
            isTranslating = update.status == TranslationStatus.IN_PROGRESS ||
                update.status == TranslationStatus.PENDING,
            progress = if (update.status == TranslationStatus.COMPLETED) 100 else update.progress,
            isDone = update.status == TranslationStatus.COMPLETED,
        )
        updateState { it.copy(chapterProgress = it.chapterProgress + (update.chapterId to entry)) }
    }

    /**
     * Handles a queue update of a chapter the reader is not standing in.
     *
     * Its text can still be on screen (the neighbouring resident sections of the book), so a
     * finished translation has to reach the document; the overlay flags stay on the chapter under
     * the reading position.
     */
    private fun onBackgroundChapterUpdate(update: TranslationProgressUpdate) {
        if (update.status != TranslationStatus.COMPLETED) return
        host.translationRefreshBookModeSection(update.chapterId)
    }

    /**
     * Moves the overlay indicator onto [chapterId] when the reading position crosses into it.
     *
     * Nothing is restarted or cancelled: the queue keeps running, only what the indicator describes
     * changes, which is what makes the progress per chapter instead of per session.
     */
    fun onActiveChapterChanged(chapterId: Long) {
        val entry = state.chapterProgress.overlayIndicatorFor(chapterId)
        updateState {
            it.copy(
                isGeminiTranslating = entry?.isTranslating == true,
                geminiTranslationProgress = entry?.percent ?: 0,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Gemini / AI translation
    // ---------------------------------------------------------------------------------------------

    fun startGeminiTranslation() {
        if (state.isGeminiTranslating) return
        val currentState = host.translationReaderSettings() ?: return
        // Over a book this is the chapter under the reading position; the old `currentChapter` was
        // the chapter the book was entered from, so the queued job translated the wrong chapter.
        val translationChapterId = host.translationActiveChapterId() ?: return
        // The content model belongs to the entry chapter and is empty over a book, so it cannot gate
        // the queue there: the worker resolves the chapter payload itself.
        if (!host.translationIsBookRuntimeActive() && host.translationCurrentParsedTextBlocks().isEmpty()) return
        val settings = currentState
        if (!settings.geminiEnabled) {
            addAiTranslationLog("AI translation is disabled.")
            return
        }
        if (!host.translationHasConfiguredProvider(settings)) {
            addAiTranslationLog("? Translation provider is not configured")
            return
        }
        host.translationHolderClear("gemini")
        updateState {
            it.copy(
                isGeminiTranslationVisible = false,
                hasGeminiTranslationCache = false,
                isGeminiTranslating = true,
                geminiTranslationProgress = 0,
            )
        }
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        addAiTranslationLog("AI translation queued in background.")
        geminiTranslationJob = host.translationScope.launch(Dispatchers.IO) {
            try {
                translationQueueManager.addToQueue(listOf(translationChapterId), novelIdForQueue())
                if (!isActive) return@launch
                val appContext = Injekt.get<Application>()
                TranslationJob.runImmediately(appContext)
                addAiTranslationLog("AI translation queued.")
            } catch (_: CancellationException) {
                // Job cancelled intentionally by the user or screen teardown.
            } catch (error: Exception) {
                logcat(LogPriority.WARN, error) { "Failed to queue AI translation for chapter=$translationChapterId" }
                addAiTranslationLog(
                    "Failed to start background translation: ${error.message ?: error::class.java.simpleName}",
                )
                updateState {
                    it.copy(
                        isGeminiTranslating = false,
                        geminiTranslationProgress = 0,
                    )
                }
            }
        }
    }

    private fun targetTranslationChapterId(): Long? =
        host.translationActiveChapterId() ?: host.translationCurrentChapter()?.id

    private fun novelIdForQueue(): Long = host.translationCurrentChapter()?.novelId ?: 0L

    fun stopGeminiTranslation() {
        val chapterId = targetTranslationChapterId() ?: return
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        updateState {
            it.copy(
                isGeminiTranslating = false,
                isGeminiTranslationVisible = false,
                geminiTranslationProgress = 0,
            )
        }
        addAiTranslationLog("⏹️ Stop requested")
        host.translationScope.launch(Dispatchers.IO) {
            val wasActive = translationQueueManager.cancelChapter(chapterId)
            val appContext = Injekt.get<Application>()
            if (wasActive) {
                TranslationJob.stop(appContext)
                if (translationQueueManager.hasNext()) {
                    TranslationJob.runImmediately(appContext)
                }
            }
        }
        val settings = host.translationReaderSettings() ?: return
        host.translationUpdateContent(settings)
    }

    fun toggleGeminiTranslationVisibility() {
        if (host.translationHolderIsEmpty("gemini")) return
        updateState { it.copy(isGeminiTranslationVisible = !it.isGeminiTranslationVisible) }
        addAiTranslationLog(
            "👁️ Visibility: ${if (state.isGeminiTranslationVisible) "ON" else "OFF"}",
        )
        // Book mode shows its own document, so re-rendering the chapter reader alone left the
        // translated markup on screen and the button looked dead.
        host.translationRefreshBookModeTranslationVariant()
        val settings = host.translationReaderSettings() ?: return
        host.translationUpdateContent(settings)
    }

    fun clearGeminiTranslation() {
        val chapterId = targetTranslationChapterId() ?: return
        if (state.isGeminiTranslating) {
            stopGeminiTranslation()
        }
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        host.translationHolderClear("gemini")
        updateState {
            it.copy(
                isGeminiTranslating = false,
                isGeminiTranslationVisible = false,
                geminiTranslationProgress = 0,
                hasGeminiTranslationCache = false,
            )
        }
        NovelReaderTranslationDiskCacheStore.remove(chapterId)
        addAiTranslationLog("🗑️ Cleared chapter cache")
        host.translationRefreshBookModeTranslationVariant()
        val settings = host.translationReaderSettings() ?: return
        host.translationUpdateContent(settings)
    }

    // ---------------------------------------------------------------------------------------------
    // Google translation
    // ---------------------------------------------------------------------------------------------

    fun startGoogleTranslation() {
        if (state.isGoogleTranslating) return
        val settings = host.translationReaderSettings() ?: return
        if (!settings.googleTranslationEnabled) {
            addGoogleLog("Google Translate is disabled.")
            host.translationUpdateContent(settings)
            return
        }

        if (state.isGeminiTranslating) {
            addGoogleLog("Cannot start: AI translation is active.")
            host.translationUpdateContent(settings)
            return
        }

        // Over a book the text on screen belongs to the chapter under the reading position, not to
        // the chapter the reader was opened with. The source blocks are resolved inside the job
        // below because loading another chapter's payload suspends.
        val translationChapterId = host.translationActiveChapterId() ?: return

        val params = GoogleTranslationParams(
            sourceLang = settings.googleTranslationSourceLang,
            targetLang = settings.googleTranslationTargetLang,
        )

        host.translationHolderClear("google")
        updateState {
            it.copy(
                isGoogleTranslationVisible = false,
                hasGoogleTranslationCache = false,
                isGoogleTranslating = true,
                googleTranslationProgress = 0,
                googleLogs = emptyList(),
                googleRateLimited = false,
                translationPhase = TranslationPhase.IDLE,
            )
        }
        host.translationUpdateContent(settings)

        googleTranslationJob = host.translationScope.launch {
            try {
                val baseTextBlocks = host.translationSourceTextBlocks(translationChapterId)
                if (baseTextBlocks.isEmpty()) {
                    addGoogleLog("Nothing to translate: chapter=$translationChapterId has no text.")
                    updateState {
                        it.copy(
                            isGoogleTranslating = false,
                            translationPhase = TranslationPhase.IDLE,
                        )
                    }
                    host.translationUpdateContent(settings)
                    return@launch
                }
                addGoogleLog(
                    "Start: chapter=$translationChapterId, textBlocks=${baseTextBlocks.size}, " +
                        "source=${settings.googleTranslationSourceLang}, " +
                        "target=${settings.googleTranslationTargetLang}, backend=simple, " +
                        "autoStart=${settings.googleTranslationAutoStart}",
                )
                val firstText = baseTextBlocks.firstOrNull()
                val firstTextPreview = firstText?.take(80)?.replace('\n', ' ') ?: ""
                addGoogleLog(
                    "Sample: firstTextLen=${firstText?.length ?: 0}, firstTextPreview=$firstTextPreview",
                )
                val response = googleTranslationService.translateBatch(
                    texts = baseTextBlocks,
                    params = params,
                    onLog = { log ->
                        addGoogleLog(log)
                        // Rebuilding the whole reader state for every diagnostic line floods the
                        // main thread with recompositions, so only refresh when the progress the
                        // user can actually see moved.
                        val progressBefore = state.googleTranslationProgress
                        updateGoogleProgressFromLog(log)
                        if (state.googleTranslationProgress != progressBefore) {
                            host.translationUpdateContent(settings)
                        }
                    },
                    onProgress = onProgress@{ phase, percent ->
                        updateState {
                            it.copy(
                                translationPhase = phase,
                                googleTranslationProgress = percent,
                            )
                        }
                        host.translationUpdateContent(settings)
                    },
                )
                val results = response.translatedByIndex
                    .filterKeys { index -> index in baseTextBlocks.indices }
                    .filterValues { translated -> translated.isNotBlank() }
                addGoogleLog(
                    "Finished: translatedSegments=${results.values.count { it.isNotBlank() }}/" +
                        "${baseTextBlocks.size}, rateLimited=false",
                )
                host.translationHolderPut("google", results)
                googleSessionCache.put(
                    chapterId = translationChapterId,
                    sourceLang = params.sourceLang,
                    targetLang = params.targetLang,
                    translatedByIndex = results,
                )
                updateState {
                    it.copy(
                        hasGoogleTranslationCache = results.isNotEmpty(),
                        isGoogleTranslating = false,
                        googleTranslationProgress = 100,
                        translationPhase = TranslationPhase.IDLE,
                        isGoogleTranslationVisible = if (results.isNotEmpty()) {
                            true
                        } else {
                            it.isGoogleTranslationVisible
                        },
                    )
                }
                host.translationUpdateContent(settings)
                // `updateContent` only rebuilds the chapter HTML, which book mode does not render.
                if (results.isNotEmpty()) host.translationRefreshBookModeSection(translationChapterId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                addGoogleLog("Google translation failed: ${error.message ?: error::class.java.simpleName}")
                updateState {
                    it.copy(
                        googleRateLimited = false,
                        isGoogleTranslating = false,
                        googleTranslationProgress = 0,
                        translationPhase = TranslationPhase.IDLE,
                    )
                }
                host.translationUpdateContent(settings)
            }
        }
    }

    fun stopGoogleTranslation() {
        googleTranslationJob?.cancel()
        googleTranslationJob = null
        updateState { it.copy(isGoogleTranslating = false) }
        val settings = host.translationReaderSettings() ?: return
        host.translationUpdateContent(settings)
    }

    fun resumeGoogleTranslation() {
        if (!state.googleRateLimited) return
        updateState { it.copy(googleRateLimited = false) }
        addGoogleLog("Resume requested: restarting Google translation")
        startGoogleTranslation()
    }

    fun toggleGoogleTranslationVisibility() {
        if (host.translationHolderIsEmpty("google")) return
        updateState { it.copy(isGoogleTranslationVisible = !it.isGoogleTranslationVisible) }
        host.translationRefreshBookModeTranslationVariant()
        val settings = host.translationReaderSettings() ?: return
        host.translationUpdateContent(settings)
    }

    fun clearGoogleTranslation() {
        val chapterId = targetTranslationChapterId() ?: return
        googleTranslationJob?.cancel()
        googleTranslationJob = null
        host.translationHolderClear("google")
        updateState {
            it.copy(
                isGoogleTranslating = false,
                isGoogleTranslationVisible = false,
                googleTranslationProgress = 0,
                hasGoogleTranslationCache = false,
                googleLogs = emptyList(),
                googleRateLimited = false,
                translationPhase = TranslationPhase.IDLE,
            )
        }
        googleSessionCache.remove(
            chapterId = chapterId,
            sourceLang = host.translationReaderSettings()?.googleTranslationSourceLang ?: "auto",
            targetLang = host.translationReaderSettings()?.googleTranslationTargetLang ?: "Russian",
        )
        host.translationRefreshBookModeTranslationVariant()
        val settings = host.translationReaderSettings() ?: return
        host.translationUpdateContent(settings)
    }

    private fun restoreGoogleTranslationFromSessionCache(settings: NovelReaderSettings) {
        val chapterId = targetTranslationChapterId() ?: return
        val cached = googleSessionCache.get(
            chapterId = chapterId,
            sourceLang = settings.googleTranslationSourceLang,
            targetLang = settings.googleTranslationTargetLang,
        )
        if (cached != null && cached.isNotEmpty()) {
            host.translationHolderPut("google", cached)
            updateState {
                it.copy(
                    hasGoogleTranslationCache = true,
                    isGoogleTranslationVisible = true,
                )
            }
            addGoogleLog(
                "Restored session cache: segments=${cached.size}, " +
                    "source=${settings.googleTranslationSourceLang}, " +
                    "target=${settings.googleTranslationTargetLang}",
            )
            host.translationUpdateContent(settings)
        }
    }

    fun maybeAutoStartGoogleTranslation() {
        val settings = host.translationReaderSettings() ?: return
        if (!settings.googleTranslationEnabled || !settings.googleTranslationAutoStart) return
        if (state.isGeminiTranslating || state.isGeminiTranslationVisible) return
        restoreGoogleTranslationFromSessionCache(settings)
        if (!state.hasGoogleTranslationCache) {
            startGoogleTranslation()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Logs + cache helpers
    // ---------------------------------------------------------------------------------------------

    fun addAiTranslationLog(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        updateState { it.copy(geminiLogs = (listOf(text) + it.geminiLogs).take(100)) }
    }

    fun clearGeminiLogs() {
        updateState { it.copy(geminiLogs = emptyList()) }
    }

    fun clearAllGeminiTranslationCache() {
        NovelReaderTranslationDiskCacheStore.clear()
        addAiTranslationLog("🗑️ Clear ALL cache")
        val chapterId = targetTranslationChapterId() ?: return
        if (NovelReaderTranslationDiskCacheStore.get(chapterId) == null) {
            updateState { it.copy(hasGeminiTranslationCache = false) }
        }
    }

    private fun addGoogleLog(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        updateState { it.copy(googleLogs = (listOf(text) + it.googleLogs).take(100)) }
        logcat(LogPriority.DEBUG) { "[GoogleTranslate] $text" }
    }

    /** Public wrapper used by the screen model's render helpers. */
    fun addGoogleLogPublic(message: String) = addGoogleLog(message)

    private fun updateGoogleProgressFromLog(message: String) {
        val match = Regex("""Simple chunk (\d+)/(\d+)""").find(message) ?: return
        val current = match.groupValues[1].toIntOrNull() ?: return
        val total = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return
        val progress = (current * 100) / total
        updateState {
            it.copy(googleTranslationProgress = maxOf(it.googleTranslationProgress, progress.coerceIn(0, 99)))
        }
    }

    /** Public wrapper used by the screen model's render helpers. */
    fun updateGoogleProgressFromLogPublic(message: String) = updateGoogleProgressFromLog(message)

    /** Resets translation state that must not survive a chapter switch. */
    fun resetTransientState() {
        updateState {
            it.copy(
                isGeminiTranslating = false,
                isGoogleTranslating = false,
                geminiTranslationProgress = 0,
                googleTranslationProgress = 0,
                isGeminiTranslationVisible = false,
                isGoogleTranslationVisible = false,
                hasGeminiTranslationCache = false,
                hasGoogleTranslationCache = false,
                geminiLogs = emptyList(),
                googleLogs = emptyList(),
                googleRateLimited = false,
            )
        }
        geminiTranslationJob?.cancel()
        geminiTranslationJob = null
        googleTranslationJob?.cancel()
        googleTranslationJob = null
        queueProgressJob?.cancel()
        queueProgressJob = null
        hasTriggeredGeminiAutoStart = false
    }
}
