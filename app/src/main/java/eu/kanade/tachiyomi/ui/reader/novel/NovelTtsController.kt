package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import android.os.SystemClock
import eu.kanade.presentation.reader.novel.NovelReaderTtsChapterHandoffPolicy
import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToHtml
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderOverride
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidNovelTtsAudioFocusBridge
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidNovelTtsEngineInfoSource
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidNovelTtsPlatformFactory
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsAudioFocusManager
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModelBuildOptions
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModelBuilder
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngine
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineRegistry
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsHighlightEstimator
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackProgressListener
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackServiceRuntime
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsResolvedChapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSession
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSessionController
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSessionUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsTextSource
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordTokenizer
import eu.kanade.tachiyomi.ui.reader.novel.tts.SharedNovelTtsSessionStore
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolveNovelTtsVoiceSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

/**
 * Host the TTS controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 *
 * TTS needs the reader settings, the current chapter/novel, the translation holder (translated
 * text playback), the content pipeline and the reader state updates. The screen model implements
 * this surface; the controller stays decoupled from the per-chapter reader internals.
 */
internal interface NovelTtsHost {
    val ttsScope: CoroutineScope

    fun ttsCurrentNovel(): Novel?
    fun ttsCurrentChapter(): NovelChapter?
    fun ttsReaderSettings(): NovelReaderSettings?
    fun ttsUpdateSuccessState(transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success)
    fun ttsRefreshTtsUiState(uiState: NovelReaderTtsUiState)
    fun ttsSetTtsUiState(uiState: NovelReaderTtsUiState)

    fun ttsBookChapterAtReadingPosition(): NovelChapter?
    fun ttsActiveTranslationChapterId(): Long?
    fun ttsIsGeminiTranslating(): Boolean
    fun ttsGeminiTranslationJob(): Job?
    fun ttsIsGeminiTranslationVisible(): Boolean
    fun ttsIsGoogleTranslationVisible(): Boolean
    fun ttsTranslationHolderEmpty(provider: String): Boolean
    fun ttsApplyGeminiTranslationToContentBlocks(
        blocks: List<NovelReaderScreenModel.ContentBlock>,
        forceTranslation: Boolean,
    ): List<NovelReaderScreenModel.ContentBlock>
    fun ttsApplyGoogleTranslationToContentBlocks(
        blocks: List<NovelReaderScreenModel.ContentBlock>,
    ): List<NovelReaderScreenModel.ContentBlock>
    fun ttsUpdateGeminiSetting(setGlobal: () -> Unit, setOverride: (NovelReaderOverride) -> NovelReaderOverride)
    fun ttsUpdateContent(settings: NovelReaderSettings)
}

/**
 * TTS subsystem of the novel reader.
 *
 * Owns the TTS engine, session controller, highlight estimator and every playback/settings
 * interaction. [NovelReaderScreenModel] delegates `tts*` calls here and hosts the shared state
 * through [NovelTtsHost].
 */
internal class NovelTtsController(
    private val host: NovelTtsHost,
    private val application: Application = Injekt.get(),
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val ttsChapterRepository: NovelTtsChapterRepository,
    private val sourceManager: tachiyomi.domain.source.novel.service.NovelSourceManager,
) {

    private val ttsChapterModelBuilder = NovelTtsChapterModelBuilder(NovelTtsWordTokenizer)
    private val ttsHighlightEstimator = NovelTtsHighlightEstimator()
    private val ttsEngineRegistry = NovelTtsEngineRegistry(AndroidNovelTtsEngineInfoSource(application))
    private val ttsEngine = NovelTtsEngine(AndroidNovelTtsPlatformFactory(application))
    private val ttsAudioFocusManager = NovelTtsAudioFocusManager(
        bridge = AndroidNovelTtsAudioFocusBridge(application),
        onPauseRequested = {
            host.ttsScope.launch {
                ttsSessionController.pause()
            }
        },
    )
    private val ttsSessionStore = SharedNovelTtsSessionStore
    private val ttsSessionController = NovelTtsSessionController(
        chapterSource = object : eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterSource {
            override suspend fun loadChapter(chapterId: Long): NovelTtsResolvedChapter? {
                return resolveTtsChapter(chapterId)
            }
        },
        speaker = object : eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackSpeaker {
            override suspend fun speak(
                utterance: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance,
                flushQueue: Boolean,
                startWordIndex: Int,
            ) {
                val startChar = utterance.wordRanges
                    .getOrNull(startWordIndex.coerceAtLeast(0))
                    ?.startChar
                    ?.coerceIn(0, utterance.text.length)
                    ?: 0
                ttsSpokenTextStart = utterance.id to startChar
                val resumedText = if (startChar > 0) utterance.text.substring(startChar) else utterance.text
                ttsEngine.speak(utterance.id, resumedText, flushQueue)
            }

            override fun stop() {
                ttsEngine.stop()
            }
        },
        sessionStore = ttsSessionStore,
    )
    private var ttsWordProgressJob: Job? = null

    /** True once the current utterance received exact word offsets from the engine. */
    private var ttsExactWordProgressActive = false

    /**
     * Utterance id and the character offset (inside the full utterance text) where the text
     * handed to the engine begins. A mid-utterance resume speaks only a suffix of the utterance,
     * so engine `onRangeStart` offsets arrive suffix-relative and must be shifted back before
     * they are mapped onto the full-text word ranges.
     */
    private var ttsSpokenTextStart: Pair<String, Int>? = null

    private val ttsRuntimeMutex = Mutex()
    private var ttsRuntimeGeneration: Long = 0L

    private var initializedTtsEnginePackage: String? = null
    private var pendingTtsStartRequest: NovelTtsPlaybackStartRequest? = null

    private var ttsUiState: NovelReaderTtsUiState = NovelReaderTtsUiState()

    /** Snapshot of the TTS UI state, merged into the reader state by the screen model. */
    fun snapshot(): NovelReaderTtsUiState = ttsUiState

    fun setTtsUiStateFromReader(uiState: NovelReaderTtsUiState) {
        ttsUiState = uiState
    }

    /**
     * Wires the engine progress listener and the session state collector. Called once from the
     * screen model's init.
     */
    fun attach() {
        ttsEngine.setProgressListener(
            object : NovelTtsPlaybackProgressListener {
                override fun onUtteranceStart(utteranceId: String) {
                    host.ttsScope.launch {
                        handleTtsUtteranceStarted(utteranceId)
                    }
                }

                override fun onUtteranceRangeStart(
                    utteranceId: String,
                    startChar: Int,
                    endCharExclusive: Int,
                ) {
                    host.ttsScope.launch {
                        handleTtsUtteranceRangeStart(utteranceId, startChar)
                    }
                }

                override fun onUtteranceDone(utteranceId: String) {
                    host.ttsScope.launch {
                        if (utteranceId == TTS_PREVIEW_UTTERANCE_ID) {
                            clearTtsVoicePreview(restoreSelectedVoice = true)
                            return@launch
                        }
                        ttsWordProgressJob?.cancel()
                        // A pause/stop landing between the engine finishing the utterance and this
                        // queued callback must win: advancing now would silently revert it and keep
                        // speaking. skipNext() drives onUtteranceCompleted directly and stays
                        // unaffected (skipping is an explicit request to continue).
                        if (ttsSessionController.state.value.playbackState != NovelTtsPlaybackState.PLAYING) {
                            return@launch
                        }
                        ttsSessionController.onUtteranceCompleted(utteranceId)
                    }
                }

                override fun onUtteranceError(utteranceId: String) {
                    host.ttsScope.launch {
                        if (utteranceId == TTS_PREVIEW_UTTERANCE_ID) {
                            clearTtsVoicePreview(restoreSelectedVoice = true)
                            return@launch
                        }
                        ttsWordProgressJob?.cancel()
                        ttsWordProgressJob = null
                        ttsUiState = ttsUiState.copy(
                            errorMessage = application.stringResource(MR.strings.novel_tts_error_speak),
                        )
                        refreshTtsUiState()
                        // A failed utterance leaves silence behind while every surface still claims
                        // PLAYING; park the session in PAUSED so UI/notification/service reflect
                        // reality and an explicit play retries from the checkpointed position.
                        if (ttsSessionController.state.value.playbackState == NovelTtsPlaybackState.PLAYING) {
                            ttsSessionController.pause()
                        }
                    }
                }
            },
        )
        host.ttsScope.launch {
            refreshTtsEngines()
        }
        host.ttsScope.launch {
            ttsSessionController.state.collect { sessionState ->
                onTtsSessionStateChanged(sessionState)
            }
        }
    }

    private suspend fun refreshTtsEngines() {
        val engines = runCatching { ttsEngineRegistry.listEngines() }
            .getOrElse { emptyList() }
        ttsUiState = ttsUiState.copy(availableEngines = engines)
        refreshTtsUiState()
        initializeTtsRuntime()
    }

    private fun readRecentTtsLanguageTags(): List<String> {
        return novelReaderPreferences.ttsRecentLanguageTags().get()
            .split('|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun rememberRecentTtsLanguage(localeTag: String) {
        if (localeTag.isBlank()) return
        val updatedTags = buildList {
            add(localeTag)
            addAll(readRecentTtsLanguageTags().filterNot { it.equals(localeTag, ignoreCase = true) })
        }.take(5)
        novelReaderPreferences.ttsRecentLanguageTags().set(updatedTags.joinToString("|"))
        ttsUiState = ttsUiState.copy(recentLanguageTags = updatedTags)
        refreshTtsUiState()
    }

    private suspend fun initializeTtsRuntime() = ttsRuntimeMutex.withLock {
        val generation = ++ttsRuntimeGeneration
        val settings = host.ttsReaderSettings() ?: host.ttsCurrentNovel()?.source
            ?.let(novelReaderPreferences::resolveSettings)
            ?: return@withLock
        val recentLanguageTags = readRecentTtsLanguageTags()
        val preferredEngine = ttsEngineRegistry.resolvePreferredEngine(
            settings.ttsEnginePackage.takeIf { it.isNotBlank() },
        )
        if (!settings.ttsEnabled) {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            pendingTtsStartRequest = null
            ttsAudioFocusManager.abandonPlaybackFocus()
            ttsSessionController.stop()
            ttsEngine.shutdown()
            initializedTtsEnginePackage = null
            ttsUiState = ttsUiState.copy(
                enabled = false,
                playbackState = NovelTtsPlaybackState.IDLE,
                activeSession = null,
                activeHighlightMode = NovelTtsHighlightMode.OFF,
                activeWordRange = null,
                activeUtteranceText = null,
                activeSourceBlockIndex = null,
                availableVoices = emptyList(),
                availableLocales = emptyList(),
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = false,
                selectedEnginePackage = "",
                selectedVoiceId = "",
                selectedLocaleTag = "",
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                errorMessage = null,
            )
            refreshTtsUiState()
            return@withLock
        }

        val targetEnginePackage = preferredEngine?.packageName
        val enginePackageChanged = initializedTtsEnginePackage != targetEnginePackage
        if (enginePackageChanged || ttsUiState.availableVoices.isEmpty()) {
            ttsUiState = ttsUiState.copy(
                enabled = true,
                availableVoices = emptyList(),
                availableLocales = emptyList(),
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = true,
                selectedEnginePackage = targetEnginePackage.orEmpty(),
                selectedVoiceId = "",
                selectedLocaleTag = settings.ttsLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                errorMessage = null,
            )
            refreshTtsUiState()
        }

        runCatching {
            ttsEngine.initialize(targetEnginePackage)
            ttsEngine.setSpeechRate(settings.ttsSpeechRate)
            ttsEngine.setPitch(settings.ttsPitch)
            val capabilities = ttsEngine.capabilities()
            val availableVoices = ttsEngine.availableVoices()
            val availableLocales = ttsEngine.availableLocales()
            val selection = resolveNovelTtsVoiceSelection(
                availableVoices = availableVoices,
                availableLocales = availableLocales,
                capabilities = capabilities,
                preferredVoiceId = settings.ttsVoiceId,
                preferredLocaleTag = settings.ttsLocaleTag,
            )
            ttsEngine.setLocale(selection.selectedLocaleTag.takeIf { it.isNotBlank() })
            ttsEngine.setVoice(selection.selectedVoiceId.takeIf { it.isNotBlank() })
            if (generation != ttsRuntimeGeneration) return@withLock
            initializedTtsEnginePackage = targetEnginePackage
            ttsUiState = ttsUiState.copy(
                enabled = true,
                availableVoices = availableVoices,
                availableLocales = availableLocales,
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = false,
                selectedEnginePackage = targetEnginePackage.orEmpty(),
                selectedVoiceId = selection.selectedVoiceId,
                selectedLocaleTag = selection.selectedLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                capabilities = capabilities,
                activeHighlightMode = capabilities.resolveHighlightMode(settings.ttsHighlightMode),
                errorMessage = null,
            )
        }.onFailure { error ->
            logcat(LogPriority.WARN, error) { "Failed to initialize novel reader TTS" }
            initializedTtsEnginePackage = null
            ttsUiState = ttsUiState.copy(
                enabled = settings.ttsEnabled,
                availableVoices = emptyList(),
                availableLocales = emptyList(),
                recentLanguageTags = recentLanguageTags,
                isLoadingVoices = false,
                selectedEnginePackage = targetEnginePackage.orEmpty(),
                selectedVoiceId = settings.ttsVoiceId,
                selectedLocaleTag = settings.ttsLocaleTag,
                speechRate = settings.ttsSpeechRate,
                pitch = settings.ttsPitch,
                errorMessage = error.message,
            )
        }
        refreshTtsUiState()
    }

    private suspend fun maybeRestoreTtsAfterChapterHandoff(
        chapterId: Long,
        settings: NovelReaderSettings,
    ) {
        if (!settings.ttsEnabled) return
        // Peek before requesting focus: grabbing focus for a restore that is not pending would
        // duck other apps for nothing, and a denied focus leaves the fresh mark for a retry
        // within its TTL instead of losing the restore permanently.
        if (!NovelReaderTtsChapterHandoffPolicy.hasPendingRestore(chapterId)) return
        // Only a controller without a live session may be resurrected from the checkpoint: the
        // seamless-switch path already speaks the new chapter itself (restoring here would flush
        // and re-speak it), and a pause issued during the handoff must survive the switch.
        if (ttsSessionController.state.value.session != null) return
        if (!ttsAudioFocusManager.requestPlaybackFocus()) return
        if (!NovelReaderTtsChapterHandoffPolicy.consumePendingRestore(chapterId)) return
        ttsSessionController.restoreFromCheckpoint()
    }

    private suspend fun resolveTtsChapter(targetChapterId: Long): NovelTtsResolvedChapter? {
        val snapshot = ttsChapterRepository.loadChapterSnapshot(targetChapterId)
        val source = sourceManager.getOrStub(snapshot.novel.source)
        val normalizedHtml = withContext(Dispatchers.Default) {
            val withHeading = prependChapterHeadingIfMissing(
                rawHtml = snapshot.rawHtml.normalizeStructuredChapterPayload(),
                chapterName = snapshot.chapter.name,
            )
            val sanitized = sanitizeChapterHtmlForReader(withHeading)
            if (sanitized.isBlank()) {
                withHeading
            } else {
                applyReplaceRulesToHtml(
                    rawHtml = sanitized,
                    rules = novelReaderPreferences.enabledReplaceRules(),
                )
            }
        }
        val chapterWebUrl = resolveNovelChapterWebUrlForSource(
            source = source,
            chapterUrl = snapshot.chapter.url,
            novelUrl = snapshot.novel.url,
            pluginSite = snapshot.pluginSite,
        )
        val parsedBlocks = withContext(Dispatchers.Default) {
            extractContentBlocks(
                rawHtml = normalizedHtml,
                chapterWebUrl = chapterWebUrl,
                novelUrl = snapshot.novel.url,
                pluginSite = snapshot.pluginSite,
            ).ifEmpty {
                extractTextBlocks(normalizedHtml).map(NovelReaderScreenModel.ContentBlock::Text)
            }
        }
        val normalizedContent = normalizeHtml(
            rawHtml = normalizedHtml,
            settings = novelReaderPreferences.resolveSettings(snapshot.novel.source),
            customCss = snapshot.customCss,
            customJs = snapshot.customJs,
        )
        val richBlocks = parseNovelRichContent(normalizedContent)
            .let { parsed ->
                resolveRichContentBlocks(
                    blocks = parsed.blocks,
                    chapterWebUrl = chapterWebUrl,
                    novelUrl = snapshot.novel.url,
                    pluginSite = snapshot.pluginSite,
                )
            }
        val currentSettings = novelReaderPreferences.resolveSettings(snapshot.novel.source)
        val originalModel = ttsChapterModelBuilder.build(
            chapterId = snapshot.chapter.id,
            chapterTitle = snapshot.chapter.name,
            contentBlocks = parsedBlocks,
            richContentBlocks = richBlocks,
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = currentSettings.ttsReadChapterTitle,
            ),
        )
        val translatedModel = resolveTranslatedTtsChapterModel(
            chapterId = targetChapterId,
            chapterTitle = snapshot.chapter.name,
            originalContentBlocks = parsedBlocks,
            richContentBlocks = richBlocks,
            settings = currentSettings,
        )
        val nextChapterId = snapshot.chapterOrderList
            .indexOfFirst { it.id == snapshot.chapter.id }
            .takeIf { it >= 0 }
            ?.let { snapshot.chapterOrderList.getOrNull(it + 1)?.id }
        return NovelTtsResolvedChapter(
            chapterId = snapshot.chapter.id,
            nextChapterId = nextChapterId,
            originalModel = originalModel,
            translatedModel = translatedModel,
        )
    }

    internal fun resolveTranslatedTtsChapterModel(
        chapterId: Long,
        chapterTitle: String,
        originalContentBlocks: List<NovelReaderScreenModel.ContentBlock>,
        richContentBlocks: List<NovelRichContentBlock>,
        settings: NovelReaderSettings,
    ): eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModel? {
        if (!shouldPreferTranslatedTts(settings)) return null
        val translatedBlocks = if (chapterId == host.ttsCurrentChapter()?.id) {
            // Current chapter: use in-memory translation holder (fast path)
            when {
                settings.geminiEnabled && !host.ttsTranslationHolderEmpty("gemini") -> {
                    host.ttsApplyGeminiTranslationToContentBlocks(originalContentBlocks, forceTranslation = true)
                }
                settings.googleTranslationEnabled && !host.ttsTranslationHolderEmpty("google") -> {
                    host.ttsApplyGoogleTranslationToContentBlocks(originalContentBlocks)
                }
                else -> return null
            }
        } else {
            // Non-current chapter (e.g. next chapter during TTS auto-advance):
            // The in-memory holder belongs to the current chapter only.
            // Check the disk cache — the prefetch job may have already written a translation here.
            if (!settings.geminiEnabled) return null
            val cached = NovelReaderTranslationDiskCacheStore.get(chapterId) ?: return null
            val settingsMatch = NovelReaderTranslationCacheResolver.matches(
                cached = cached,
                requirements = settings.toTranslationCacheRequirements(),
            )
            if (!settingsMatch) return null
            applyTranslationMapToContentBlocks(originalContentBlocks, cached.translatedByIndex)
        }
        return ttsChapterModelBuilder.build(
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            contentBlocks = translatedBlocks,
            richContentBlocks = emptyList(),
            options = NovelTtsChapterModelBuildOptions(
                includeChapterTitle = settings.ttsReadChapterTitle,
            ),
        )
    }

    /**
     * Applies a pre-built translation map (block index -> translated text) directly to a list of
     * content blocks without going through the in-memory translation holder.
     */
    private fun applyTranslationMapToContentBlocks(
        blocks: List<NovelReaderScreenModel.ContentBlock>,
        translationMap: Map<Int, String>,
    ): List<NovelReaderScreenModel.ContentBlock> {
        var textIndex = 0
        return blocks.map { block ->
            when (block) {
                is NovelReaderScreenModel.ContentBlock.Image -> block
                is NovelReaderScreenModel.ContentBlock.Text -> {
                    val translated = translationMap[textIndex]
                    textIndex += 1
                    if (translated.isNullOrBlank()) {
                        block
                    } else {
                        NovelReaderScreenModel.ContentBlock.Text(translated)
                    }
                }
            }
        }
    }

    private suspend fun onTtsSessionStateChanged(sessionState: NovelTtsSessionUiState) {
        val session = sessionState.session
        val activeUtterance = session?.utterance
        ttsUiState = ttsUiState.copy(
            playbackState = sessionState.playbackState,
            activeSession = session,
            pendingChapterHandoffId = sessionState.pendingChapterHandoffId,
            activeUtteranceText = activeUtterance?.text,
            activeSourceBlockIndex = activeUtterance?.sourceBlockIndex,
            activeWordRange = activeUtterance?.wordRanges?.getOrNull(session.wordIndex),
        )
        refreshTtsUiState()
    }

    private suspend fun handleTtsUtteranceStarted(utteranceId: String) {
        val session = ttsSessionController.state.value.session ?: return
        if (session.utterance.id != utteranceId) return
        ttsWordProgressJob?.cancel()
        ttsExactWordProgressActive = false
        startEstimatedTtsWordProgress(session)
    }

    /**
     * Exact word progress reported by the platform engine (onRangeStart).
     * The first event for an utterance permanently switches that utterance from
     * estimated to exact highlighting by cancelling the estimator ticker.
     */
    private suspend fun handleTtsUtteranceRangeStart(utteranceId: String, startChar: Int) {
        if (ttsUiState.activeHighlightMode == NovelTtsHighlightMode.OFF) return
        val sessionState = ttsSessionController.state.value
        val utterance = sessionState.session?.utterance ?: return
        if (utterance.id != utteranceId) return
        val spokenTextStartChar = ttsSpokenTextStart?.takeIf { it.first == utteranceId }?.second ?: 0
        val wordIndex = mapTtsEngineRangeStartToWordIndex(utterance, startChar, spokenTextStartChar) ?: return
        if (!ttsExactWordProgressActive) {
            ttsExactWordProgressActive = true
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
        }
        if (sessionState.playbackState != NovelTtsPlaybackState.PLAYING) return
        ttsSessionController.updateWordProgress(wordIndex)
        ttsUiState = ttsUiState.copy(activeWordRange = utterance.wordRanges.getOrNull(wordIndex))
        refreshTtsUiState()
    }

    private fun startEstimatedTtsWordProgress(session: NovelTtsSession) {
        val highlightMode = ttsUiState.activeHighlightMode
        if (highlightMode == NovelTtsHighlightMode.OFF) return
        if (ttsExactWordProgressActive) return
        ttsWordProgressJob = host.ttsScope.launch {
            val utterance = session.utterance
            val estimatedDurationMs = estimateTtsUtteranceDurationMs(
                utterance = utterance,
                speechRate = ttsUiState.speechRate,
            )
            val startTimeMs = SystemClock.elapsedRealtime()
            while (isActive) {
                val elapsedMs = (SystemClock.elapsedRealtime() - startTimeMs).coerceAtLeast(0L)
                val selection = ttsHighlightEstimator.estimateWordRange(
                    utterance = utterance,
                    elapsedMs = elapsedMs,
                    durationMs = estimatedDurationMs,
                    mode = highlightMode,
                    startWordIndex = session.wordIndex,
                )
                if (selection != null) {
                    val currentSession = ttsSessionController.state.value.session
                    if (currentSession?.utterance?.id != utterance.id) break
                    if (ttsSessionController.state.value.playbackState != NovelTtsPlaybackState.PLAYING) break
                    ttsSessionController.updateWordProgress(selection.wordIndex)
                    ttsUiState = ttsUiState.copy(activeWordRange = selection.wordRange)
                    refreshTtsUiState()
                }
                if (elapsedMs >= estimatedDurationMs) break
                delay(TTS_WORD_PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun estimateTtsUtteranceDurationMs(
        utterance: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance,
        speechRate: Float,
    ): Long {
        val words = utterance.wordRanges.size.coerceAtLeast(1)
        val effectiveRate = speechRate.coerceAtLeast(0.5f)
        val millisPerWord = (TTS_BASE_MILLIS_PER_WORD / effectiveRate).roundToInt()
        return (words * millisPerWord)
            .coerceAtLeast(TTS_MIN_UTTERANCE_DURATION_MS.toInt())
            .toLong()
    }

    private fun refreshTtsUiState() {
        host.ttsRefreshTtsUiState(ttsUiState)
    }

    fun toggleTtsPlayback(
        startRequest: NovelTtsPlaybackStartRequest = NovelTtsPlaybackStartRequest(),
    ) {
        val settings = host.ttsReaderSettings() ?: return
        if (!settings.ttsEnabled) return
        host.ttsScope.launch {
            initializeTtsRuntime()
            val playbackState = ttsSessionController.state.value.playbackState
            when (playbackState) {
                NovelTtsPlaybackState.PLAYING -> {
                    pendingTtsStartRequest = null
                    ttsWordProgressJob?.cancel()
                    ttsWordProgressJob = null
                    ttsSessionController.pause()
                }
                NovelTtsPlaybackState.PAUSED -> {
                    val pendingRequest = pendingTtsStartRequest
                    if (!ttsAudioFocusManager.requestPlaybackFocus()) return@launch
                    if (pendingRequest != null) {
                        startTtsFromRequest(pendingRequest, settings)
                    } else {
                        ttsSessionController.resume()
                    }
                    pendingTtsStartRequest = null
                }
                else -> {
                    if (!ttsAudioFocusManager.requestPlaybackFocus()) return@launch
                    startTtsFromRequest(startRequest, settings)
                    pendingTtsStartRequest = null
                }
            }
        }
    }

    fun stopTtsPlayback() {
        host.ttsScope.launch {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            ttsAudioFocusManager.abandonPlaybackFocus()
            ttsSessionController.stop()
        }
    }

    fun skipToNextTtsSegment() {
        host.ttsScope.launch {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            // Skipping from a paused state (including an audio-focus-loss pause) restarts speech,
            // so it must hold focus like every other path that starts speaking.
            if (!ttsAudioFocusManager.requestPlaybackFocus()) return@launch
            ttsSessionController.skipNext()
        }
    }

    fun skipToPreviousTtsSegment() {
        host.ttsScope.launch {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            if (!ttsAudioFocusManager.requestPlaybackFocus()) return@launch
            ttsSessionController.skipPrevious()
        }
    }

    fun pauseTtsForManualNavigation(
        startRequest: NovelTtsPlaybackStartRequest,
    ) {
        val settings = host.ttsReaderSettings() ?: return
        if (!settings.ttsPauseOnManualNavigation) return
        if (ttsSessionController.state.value.playbackState != NovelTtsPlaybackState.PLAYING) {
            pendingTtsStartRequest = startRequest
            return
        }
        host.ttsScope.launch {
            pendingTtsStartRequest = startRequest
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            ttsSessionController.pause()
        }
    }

    fun setTtsEnginePackage(value: String) = updateTtsSetting(
        setGlobal = { novelReaderPreferences.ttsEnginePackage().set(value) },
        setOverride = { it.copy(ttsEnginePackage = value) },
        restartPlayback = true,
    )

    fun setTtsVoiceId(value: String) {
        val localeTag = ttsUiState.availableVoices
            .firstOrNull { it.id == value }
            ?.localeTag
            ?: ttsUiState.selectedLocaleTag
        updateTtsSetting(
            setGlobal = {
                novelReaderPreferences.ttsVoiceId().set(value)
                if (localeTag.isNotBlank()) {
                    novelReaderPreferences.ttsLocaleTag().set(localeTag)
                }
            },
            setOverride = {
                it.copy(
                    ttsVoiceId = value,
                    ttsLocaleTag = localeTag.takeIf(String::isNotBlank) ?: it.ttsLocaleTag,
                )
            },
            restartPlayback = true,
        )
        rememberRecentTtsLanguage(localeTag)
    }

    fun setTtsLocaleTag(value: String) {
        // Switching the language must also drop a voice that belongs to another
        // language, otherwise the stored voice wins during the next runtime
        // initialization and the language silently snaps back.
        val selectedVoiceLocale = ttsUiState.availableVoices
            .firstOrNull { it.id == ttsUiState.selectedVoiceId }
            ?.localeTag
        val clearVoice = selectedVoiceLocale != null && !selectedVoiceLocale.equals(value, ignoreCase = true)
        updateTtsSetting(
            setGlobal = {
                novelReaderPreferences.ttsLocaleTag().set(value)
                if (clearVoice) {
                    novelReaderPreferences.ttsVoiceId().set("")
                }
            },
            setOverride = {
                it.copy(
                    ttsLocaleTag = value,
                    ttsVoiceId = if (clearVoice) "" else it.ttsVoiceId,
                )
            },
            restartPlayback = true,
        )
        rememberRecentTtsLanguage(value)
    }

    fun setTtsSpeechRate(value: Float) = updateTtsSetting(
        setGlobal = { novelReaderPreferences.ttsSpeechRate().set(value) },
        setOverride = { it.copy(ttsSpeechRate = value) },
    )

    fun setTtsPitch(value: Float) = updateTtsSetting(
        setGlobal = { novelReaderPreferences.ttsPitch().set(value) },
        setOverride = { it.copy(ttsPitch = value) },
    )

    /**
     * Speaks a short locale-aware sample with [voiceId] without advancing chapter TTS.
     * Empty [voiceId] previews the engine default voice for the selected language.
     */
    fun previewTtsVoice(voiceId: String) {
        host.ttsScope.launch {
            try {
                if (ttsSessionController.state.value.playbackState == NovelTtsPlaybackState.PLAYING) {
                    ttsSessionController.pause()
                }
                ttsEngine.stop()
                ttsEngine.setSpeechRate(ttsUiState.speechRate)
                ttsEngine.setPitch(ttsUiState.pitch)
                ttsEngine.setLocale(ttsUiState.selectedLocaleTag.takeIf { it.isNotBlank() })
                ttsEngine.setVoice(voiceId.takeIf { it.isNotBlank() })
                ttsUiState = ttsUiState.copy(previewingVoiceId = voiceId)
                refreshTtsUiState()
                ttsEngine.speak(
                    utteranceId = TTS_PREVIEW_UTTERANCE_ID,
                    text = ttsPreviewSampleText(ttsUiState.selectedLocaleTag),
                    flushQueue = true,
                )
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "TTS voice preview failed" }
                clearTtsVoicePreview(restoreSelectedVoice = true)
            }
        }
    }

    fun stopTtsVoicePreview() {
        host.ttsScope.launch {
            if (ttsUiState.previewingVoiceId == null) return@launch
            ttsEngine.stop()
            clearTtsVoicePreview(restoreSelectedVoice = true)
        }
    }

    private suspend fun clearTtsVoicePreview(restoreSelectedVoice: Boolean) {
        if (ttsUiState.previewingVoiceId == null) return
        ttsUiState = ttsUiState.copy(previewingVoiceId = null)
        if (restoreSelectedVoice) {
            runCatching {
                ttsEngine.setLocale(ttsUiState.selectedLocaleTag.takeIf { it.isNotBlank() })
                ttsEngine.setVoice(ttsUiState.selectedVoiceId.takeIf { it.isNotBlank() })
            }
        }
        refreshTtsUiState()
    }

    private fun ttsPreviewSampleText(localeTag: String): String {
        val lang = localeTag.substringBefore('-').substringBefore('_').lowercase()
        return if (lang == "ru") {
            application.stringResource(AYMR.strings.novel_reader_tts_preview_sample_ru)
        } else {
            application.stringResource(AYMR.strings.novel_reader_tts_preview_sample)
        }
    }

    fun disableTts() {
        val currentState = host.ttsReaderSettings() ?: return
        if (!currentState.ttsEnabled) return
        val sourceId = host.ttsCurrentNovel()?.source ?: return
        host.ttsScope.launch {
            ttsWordProgressJob?.cancel()
            ttsWordProgressJob = null
            pendingTtsStartRequest = null
            ttsAudioFocusManager.abandonPlaybackFocus()
            ttsSessionController.stop()
            if (novelReaderPreferences.getSourceOverride(sourceId) != null) {
                novelReaderPreferences.updateSourceOverride(sourceId) {
                    it.copy(ttsEnabled = false)
                }
            } else {
                novelReaderPreferences.ttsEnabled().set(false)
            }
            host.ttsUpdateContent(currentState.copy(ttsEnabled = false))
        }
    }

    fun createTtsPlaybackServiceRuntime(): NovelTtsPlaybackServiceRuntime {
        return NovelTtsPlaybackServiceRuntime(
            controller = ttsSessionController,
            audioFocusManager = ttsAudioFocusManager,
        )
    }

    private suspend fun startTtsFromRequest(
        startRequest: NovelTtsPlaybackStartRequest,
        settings: NovelReaderSettings,
    ) {
        // Fix: if translation of the current chapter is still in progress and TTS prefers
        // translated text, wait briefly for it to finish before building the utterance list.
        if (shouldPreferTranslatedTts(settings) && host.ttsIsGeminiTranslating()) {
            withTimeoutOrNull(5_000) {
                host.ttsGeminiTranslationJob()?.join()
            }
        }
        // Over a compiled book `currentChapter` is only the session entry point, so playback has to
        // start from the chapter under the reading position instead of the one the reader opened.
        val ttsStartChapterId = host.ttsBookChapterAtReadingPosition()?.id ?: host.ttsCurrentChapter()?.id ?: return
        val resolvedChapter = resolveTtsChapter(targetChapterId = ttsStartChapterId) ?: return
        val useTranslatedText = shouldPreferTranslatedTts(settings) &&
            resolvedChapter.translatedModel != null
        val sessionModel = if (useTranslatedText) {
            resolvedChapter.translatedModel
        } else {
            resolvedChapter.originalModel
        }
        ttsSessionController.setPreferredTranslatedText(useTranslatedText)
        val utteranceId = startRequest.pageReaderPosition?.let { pageReaderPosition ->
            val utteranceAnchors = eu.kanade.tachiyomi.ui.reader.novel.tts.resolvePlainPageReaderTtsAnchors(
                textBlocks = pageReaderPosition.blockTexts,
                pages = pageReaderPosition.pages,
                chapterModel = sessionModel,
            )
            eu.kanade.tachiyomi.ui.reader.novel.tts.resolvePageReaderTtsStartUtteranceId(
                pageIndex = pageReaderPosition.pageIndex,
                fallbackBlockIndex = startRequest.fallbackBlockIndex,
                chapterModel = sessionModel,
                utteranceAnchors = utteranceAnchors,
            )
        } ?: sessionModel.utterances
            .firstOrNull { it.sourceBlockIndex >= startRequest.fallbackBlockIndex }
            ?.id
            ?: sessionModel.utterances.firstOrNull()?.id
        ttsSessionController.startFromCurrentPosition(
            chapterId = resolvedChapter.chapterId,
            utteranceId = utteranceId,
            preferTranslatedText = useTranslatedText,
            autoAdvanceChapter = settings.ttsAutoAdvanceChapter,
        )
    }

    private fun shouldPreferTranslatedTts(settings: NovelReaderSettings): Boolean {
        return settings.ttsPreferTranslatedText ||
            host.ttsIsGeminiTranslationVisible() ||
            host.ttsIsGoogleTranslationVisible()
    }

    private fun hasCurrentTranslatedTtsContent(settings: NovelReaderSettings): Boolean {
        return (settings.geminiEnabled && !host.ttsTranslationHolderEmpty("gemini")) ||
            (settings.googleTranslationEnabled && !host.ttsTranslationHolderEmpty("google"))
    }

    private fun maybeSyncActiveTtsSessionOptions(settings: NovelReaderSettings) {
        val session = ttsSessionController.state.value.session ?: return
        if (session.autoAdvanceChapter == settings.ttsAutoAdvanceChapter) return
        host.ttsScope.launch {
            ttsSessionController.setAutoAdvanceChapter(settings.ttsAutoAdvanceChapter)
        }
    }

    private fun maybeSwitchActiveTtsTextSource(settings: NovelReaderSettings) {
        val session = ttsSessionController.state.value.session ?: return
        // The TTS session is started for the chapter under the reading position (book mode included),
        // so comparing it against `currentChapter` silently skipped every original/translated switch
        // while reading a book.
        val activeChapterId = host.ttsActiveTranslationChapterId() ?: return
        if (session.chapterId != activeChapterId) return
        val preferTranslated = shouldPreferTranslatedTts(settings) && hasCurrentTranslatedTtsContent(settings)
        val targetTextSource = if (preferTranslated) {
            NovelTtsTextSource.TRANSLATED
        } else {
            NovelTtsTextSource.ORIGINAL
        }
        if (session.textSource == targetTextSource) return
        host.ttsScope.launch {
            ttsSessionController.switchToPreferredTextSource(preferTranslated)
        }
    }

    private fun updateTtsSetting(
        setGlobal: () -> Unit,
        setOverride: (NovelReaderOverride) -> NovelReaderOverride,
        restartPlayback: Boolean = false,
    ) {
        host.ttsUpdateGeminiSetting(setGlobal, setOverride)
        ttsWordProgressJob?.cancel()
        ttsWordProgressJob = null
        val wasPlaying = ttsSessionController.state.value.playbackState == NovelTtsPlaybackState.PLAYING
        host.ttsScope.launch {
            if (restartPlayback && wasPlaying) {
                ttsSessionController.pause()
            }
            initializeTtsRuntime()
            if (restartPlayback && wasPlaying && ttsAudioFocusManager.requestPlaybackFocus()) {
                ttsSessionController.resume()
            }
        }
    }

    /** Exposed to the screen model for settings-change handling. */
    fun maybeSyncActiveTtsSessionOptionsPublic(settings: NovelReaderSettings) =
        maybeSyncActiveTtsSessionOptions(settings)

    fun maybeSwitchActiveTtsTextSourcePublic(settings: NovelReaderSettings) =
        maybeSwitchActiveTtsTextSource(settings)

    /** Called by the screen model when a new TTS session state arrives. */
    suspend fun handleTtsSessionStateChanged(sessionState: NovelTtsSessionUiState) =
        onTtsSessionStateChanged(sessionState)

    suspend fun handleTtsUtteranceStartedPublic(utteranceId: String) = handleTtsUtteranceStarted(utteranceId)

    suspend fun handleTtsUtteranceRangeStartPublic(utteranceId: String, startChar: Int) =
        handleTtsUtteranceRangeStart(utteranceId, startChar)

    suspend fun restoreTtsAfterChapterHandoff(chapterId: Long, settings: NovelReaderSettings) =
        maybeRestoreTtsAfterChapterHandoff(chapterId, settings)

    suspend fun refreshTtsEnginesPublic() = refreshTtsEngines()

    /** Re-initializes the TTS runtime from the current reader settings. */
    suspend fun initializeTtsRuntimePublic() = initializeTtsRuntime()

    /** Applies TTS session options / text source after a settings change. */
    fun syncActiveTtsSessionOptions(settings: NovelReaderSettings) =
        maybeSyncActiveTtsSessionOptions(settings)

    fun switchActiveTtsTextSource(settings: NovelReaderSettings) =
        maybeSwitchActiveTtsTextSource(settings)

    /** Marks the active TTS session as speaking translated text when the settings prefer it. */
    fun setPreferredTranslatedText(settings: NovelReaderSettings) {
        ttsSessionController.setPreferredTranslatedText(shouldPreferTranslatedTts(settings))
    }

    /** Tears the TTS runtime down on reader disposal. */
    fun shutdown() {
        ttsWordProgressJob?.cancel()
        ttsWordProgressJob = null
        pendingTtsStartRequest = null
        ttsAudioFocusManager.abandonPlaybackFocus()
        ttsEngine.shutdown()
        initializedTtsEnginePackage = null
    }

    /** Resets TTS state that must not survive a chapter switch. */
    fun resetTransientState() {
        ttsWordProgressJob?.cancel()
        ttsWordProgressJob = null
        pendingTtsStartRequest = null
    }

    companion object {
        private const val TTS_BASE_MILLIS_PER_WORD = 360f
        private const val TTS_MIN_UTTERANCE_DURATION_MS = 700L
        private const val TTS_WORD_PROGRESS_UPDATE_INTERVAL_MS = 60L
        private const val TTS_PREVIEW_UTTERANCE_ID = "tts-preview"
    }
}

/**
 * Maps an engine `onRangeStart` offset onto a full-utterance word index. Engine offsets are
 * relative to the text passed to `speak`, which after a mid-utterance resume is only a suffix
 * of [utterance]'s text starting at [spokenTextStartChar]; the shift restores full-text
 * coordinates before the lookup.
 */
internal fun mapTtsEngineRangeStartToWordIndex(
    utterance: eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsUtterance,
    engineStartChar: Int,
    spokenTextStartChar: Int,
): Int? = utterance.wordIndexForCharOffset(spokenTextStartChar + engineStartChar)
