package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSection
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest

/**
 * Every callback [NovelReaderScreen] needs, grouped into a single parameter.
 *
 * Compose emits a change-tracking prologue for each parameter, and with more than a hundred of
 * them the generated method grew past the runtime's per-method instruction budget: ART logged
 * "Method exceeds compiler instruction limit" and ran the whole reader interpreted, which is what
 * made scrolling stutter and reloads visible.
 */
@Immutable
data class NovelReaderScreenActions(
    val onBack: () -> Unit,
    val onReadingProgress: (
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long?,
        isInitialPositionRestored: Boolean,
    ) -> Unit,
    val onSeekBookModeProgress: (Float) -> Unit = {},
    val onToggleBookmark: () -> Unit = {},
    val onOpenDictionaryHistory: (() -> Unit)? = null,
    val onStartGeminiTranslation: () -> Unit = {},
    val onStopGeminiTranslation: () -> Unit = {},
    val onToggleGeminiTranslationVisibility: () -> Unit = {},
    val onClearGeminiTranslation: () -> Unit = {},
    val onClearAllGeminiTranslationCache: () -> Unit = {},
    val onAddAiTranslationLog: (String) -> Unit = {},
    val onClearGeminiLogs: () -> Unit = {},
    val onSetGeminiApiKey: (String) -> Unit = {},
    val onSetGeminiModel: (String) -> Unit = {},
    val onSetGeminiBatchSize: (Int) -> Unit = {},
    val onSetGeminiConcurrency: (Int) -> Unit = {},
    val onSetGeminiRelaxedMode: (Boolean) -> Unit = {},
    val onSetGeminiDisableCache: (Boolean) -> Unit = {},
    val onSetGeminiReasoningEffort: (String) -> Unit = {},
    val onSetGeminiBudgetTokens: (Int) -> Unit = {},
    val onSetGeminiTemperature: (Float) -> Unit = {},
    val onSetGeminiTopP: (Float) -> Unit = {},
    val onSetGeminiTopK: (Int) -> Unit = {},
    val onSetGeminiPromptMode: (GeminiPromptMode) -> Unit = {},
    val onSetGeminiSourceLang: (String) -> Unit = {},
    val onSetGeminiTargetLang: (String) -> Unit = {},
    val onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit = {},
    val onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit = {},
    val onSetGeminiCustomPromptModifier: (String) -> Unit = {},
    val onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit = {},
    val onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit = {},
    val onSetGeminiPrivateUnlocked: (Boolean) -> Unit = {},
    val onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit = {},
    val onSetTranslationProvider: (NovelTranslationProvider) -> Unit = {},
    val onSetOpenRouterBaseUrl: (String) -> Unit = {},
    val onSetOpenRouterApiKey: (String) -> Unit = {},
    val onSetOpenRouterModel: (String) -> Unit = {},
    val onRefreshOpenRouterModels: () -> Unit = {},
    val onTestOpenRouterConnection: () -> Unit = {},
    val onSetDeepSeekBaseUrl: (String) -> Unit = {},
    val onSetDeepSeekApiKey: (String) -> Unit = {},
    val onSetDeepSeekModel: (String) -> Unit = {},
    val onRefreshDeepSeekModels: () -> Unit = {},
    val onTestDeepSeekConnection: () -> Unit = {},
    val onSetMistralBaseUrl: (String) -> Unit = {},
    val onSetMistralApiKey: (String) -> Unit = {},
    val onSetMistralModel: (String) -> Unit = {},
    val onRefreshMistralModels: () -> Unit = {},
    val onTestMistralConnection: () -> Unit = {},
    val onSetNvidiaBaseUrl: (String) -> Unit = {},
    val onSetNvidiaApiKey: (String) -> Unit = {},
    val onSetNvidiaModel: (String) -> Unit = {},
    val onRefreshNvidiaModels: () -> Unit = {},
    val onTestNvidiaConnection: () -> Unit = {},
    val onSetOllamaCloudBaseUrl: (String) -> Unit = {},
    val onSetOllamaCloudApiKey: (String) -> Unit = {},
    val onSetOllamaCloudModel: (String) -> Unit = {},
    val onRefreshOllamaCloudModels: () -> Unit = {},
    val onTestOllamaCloudConnection: () -> Unit = {},
    val onStartGoogleTranslation: () -> Unit = {},
    val onStopGoogleTranslation: () -> Unit = {},
    val onResumeGoogleTranslation: () -> Unit = {},
    val onToggleGoogleTranslationVisibility: () -> Unit = {},
    val onClearGoogleTranslation: () -> Unit = {},
    val onSetGoogleTranslationEnabled: (Boolean) -> Unit = {},
    val onSetGoogleTranslationAutoStart: (Boolean) -> Unit = {},
    val onSetGoogleTranslationSourceLang: (String) -> Unit = {},
    val onSetGoogleTranslationTargetLang: (String) -> Unit = {},
    val onToggleTtsPlayback: (NovelTtsPlaybackStartRequest) -> Unit = {},
    val onStopTtsPlayback: () -> Unit = {},
    val onSkipPreviousTts: () -> Unit = {},
    val onSkipNextTts: () -> Unit = {},
    val onPauseTtsForManualNavigation: (NovelTtsPlaybackStartRequest) -> Unit = {},
    val onSetTtsEnginePackage: (String) -> Unit = {},
    val onSetTtsVoiceId: (String) -> Unit = {},
    val onSetTtsLocaleTag: (String) -> Unit = {},
    val onSetTtsSpeechRate: (Float) -> Unit = {},
    val onSetTtsPitch: (Float) -> Unit = {},
    val onDisableTts: () -> Unit = {},
    val onPreviewTtsVoice: (String) -> Unit = {},
    val onStopTtsVoicePreview: () -> Unit = {},
    val onOpenPreviousChapter: ((Long) -> Unit)? = null,
    val onOpenNextChapter: ((Long) -> Unit)? = null,
    val onPrepareAutoScrollHandoff: (targetChapterId: Long, speed: Int) -> Unit = { _, _ -> },
    val onConsumeAutoScrollHandoff: (chapterId: Long) -> NovelAutoScrollHandoffState? = { null },
    val onCancelAutoScrollHandoff: () -> Unit = {},
    val onRequestAutoScrollNextChapterPrefetch: () -> Unit = {},
    val onOpenChapter: ((Long) -> Unit)? = null,
    val onDownloadChapter: ((Long) -> Unit)? = null,
    val onSetShowReaderUi: (Boolean) -> Unit,
    val onOpenBottomSheet: () -> Unit = {},
    val onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    val onTranslateSelectedText: () -> Unit = {},
    val onRetrySelectedTextTranslation: () -> Unit = onTranslateSelectedText,
    val onDismissSelectedTextTranslation: () -> Unit = {},
    val onLookupSelectedTextDefinition: () -> Unit = {},
    val onRetryNovelDictionary: () -> Unit = onLookupSelectedTextDefinition,
    val onDismissNovelDictionary: () -> Unit = {},
    val onPlaySelectedTextPronunciation: (String) -> Unit = {},
    val onUpdateHighlight: (highlightId: Long, note: String, colorArgb: Long) -> Unit = { _, _, _ -> },
    val onDeleteHighlight: (highlightId: Long) -> Unit = {},
    val onDefaultHighlightColorChanged: (colorArgb: Long) -> Unit = {},
    val loadBookEngineDocument: (suspend (NovelBookSection) -> NovelBookDocument)? = null,
    val onBookEngineLocationChanged: (NovelBookLocation) -> Unit = {},
    /** Acknowledges a [BookSeekRequest] the renderer applied, identified by its id. */
    val onBookSeekApplied: (Long) -> Unit = {},
    val loadBookSectionHtml: suspend (Int) -> String? = { null },
    val onBookModeScroll: (sectionIndex: Int, sectionFraction: Float) -> Unit = { _, _ -> },
    val onBookModeRetrySection: (sectionIndex: Int) -> Unit = {},
    val onPrepareWholeBook: () -> Unit = {},
    /**
     * Pre-compiled blocks of a book section, or null when the compiled book has none.
     *
     * Supplied by the screen model from the book artifact. When present, the native renderer
     * skips parsing the section HTML entirely, which is what makes opening and scrolling a
     * 50-100 chapter book instant instead of running Jsoup over a 200k character window on
     * every append.
     */
    val nativeBookBlocksForSection: (sectionIndex: Int) -> List<NovelRichContentBlock>? = { null },
)
