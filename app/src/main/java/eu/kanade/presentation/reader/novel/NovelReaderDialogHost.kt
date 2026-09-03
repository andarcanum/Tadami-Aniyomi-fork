package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.reader.ReaderChapterListItem
import eu.kanade.presentation.reader.ReaderChapterListSheet
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Host for every reader dialog and sheet.
 *
 * Its callbacks travel in [NovelReaderDialogActions] instead of as individual parameters: a
 * composable with ninety parameters makes the compiler emit a change-tracking prologue so large
 * that ART refuses to JIT-compile the method ("Method exceeds compiler instruction limit"), and
 * an interpreted reader is what made scrolling stutter.
 */
@Composable
internal fun NovelReaderDialogHost(
    showSettings: Boolean,
    showChapterList: Boolean,
    showTtsBehaviorSettings: Boolean,
    showGeminiDialog: Boolean,
    showGoogleDialog: Boolean,
    translationSwitchRequest: TranslationSwitchRequest?,
    state: NovelReaderScreenModel.State.Success,
    showWebView: Boolean,
    usePageReader: Boolean,
    ttsPlacement: NovelReaderTtsSettingsPlacementSnapshot,
    actions: NovelReaderDialogActions,
) {
    val onDismissSettings = actions.onDismissSettings
    val onDismissChapterList = actions.onDismissChapterList
    val onOpenBottomSheet = actions.onOpenBottomSheet
    val onOpenChapter = actions.onOpenChapter
    val onDownloadChapter = actions.onDownloadChapter
    val onDismissTtsBehaviorSettings = actions.onDismissTtsBehaviorSettings
    val onDismissGeminiDialog = actions.onDismissGeminiDialog
    val onDismissGoogleDialog = actions.onDismissGoogleDialog
    val onDismissTranslationSwitchRequest = actions.onDismissTranslationSwitchRequest
    val requestGeminiTranslationStart = actions.requestGeminiTranslationStart
    val requestGoogleTranslationStart = actions.requestGoogleTranslationStart
    val onPrepareWholeBook = actions.onPrepareWholeBook
    val onStopGeminiTranslation = actions.onStopGeminiTranslation
    val onToggleGeminiTranslationVisibility = actions.onToggleGeminiTranslationVisibility
    val onClearGeminiTranslation = actions.onClearGeminiTranslation
    val onClearAllGeminiTranslationCache = actions.onClearAllGeminiTranslationCache
    val onAddAiTranslationLog = actions.onAddAiTranslationLog
    val onClearGeminiLogs = actions.onClearGeminiLogs
    val onSetGeminiApiKey = actions.onSetGeminiApiKey
    val onSetGeminiModel = actions.onSetGeminiModel
    val onSetGeminiBatchSize = actions.onSetGeminiBatchSize
    val onSetGeminiConcurrency = actions.onSetGeminiConcurrency
    val onSetGeminiRelaxedMode = actions.onSetGeminiRelaxedMode
    val onSetGeminiDisableCache = actions.onSetGeminiDisableCache
    val onSetGeminiReasoningEffort = actions.onSetGeminiReasoningEffort
    val onSetGeminiBudgetTokens = actions.onSetGeminiBudgetTokens
    val onSetGeminiTemperature = actions.onSetGeminiTemperature
    val onSetGeminiTopP = actions.onSetGeminiTopP
    val onSetGeminiTopK = actions.onSetGeminiTopK
    val onSetGeminiPromptMode = actions.onSetGeminiPromptMode
    val onSetGeminiSourceLang = actions.onSetGeminiSourceLang
    val onSetGeminiTargetLang = actions.onSetGeminiTargetLang
    val onSetGeminiStylePreset = actions.onSetGeminiStylePreset
    val onSetGeminiEnabledPromptModifiers = actions.onSetGeminiEnabledPromptModifiers
    val onSetGeminiCustomPromptModifier = actions.onSetGeminiCustomPromptModifier
    val onSetGeminiAutoTranslateEnglishSource = actions.onSetGeminiAutoTranslateEnglishSource
    val onSetGeminiPrefetchNextChapterTranslation = actions.onSetGeminiPrefetchNextChapterTranslation
    val onSetGeminiPrivateUnlocked = actions.onSetGeminiPrivateUnlocked
    val onSetGeminiPrivatePythonLikeMode = actions.onSetGeminiPrivatePythonLikeMode
    val onSetTranslationProvider = actions.onSetTranslationProvider
    val onSetOpenRouterBaseUrl = actions.onSetOpenRouterBaseUrl
    val onSetOpenRouterApiKey = actions.onSetOpenRouterApiKey
    val onSetOpenRouterModel = actions.onSetOpenRouterModel
    val onRefreshOpenRouterModels = actions.onRefreshOpenRouterModels
    val onTestOpenRouterConnection = actions.onTestOpenRouterConnection
    val onSetDeepSeekBaseUrl = actions.onSetDeepSeekBaseUrl
    val onSetDeepSeekApiKey = actions.onSetDeepSeekApiKey
    val onSetDeepSeekModel = actions.onSetDeepSeekModel
    val onRefreshDeepSeekModels = actions.onRefreshDeepSeekModels
    val onTestDeepSeekConnection = actions.onTestDeepSeekConnection
    val onSetMistralBaseUrl = actions.onSetMistralBaseUrl
    val onSetMistralApiKey = actions.onSetMistralApiKey
    val onSetMistralModel = actions.onSetMistralModel
    val onRefreshMistralModels = actions.onRefreshMistralModels
    val onTestMistralConnection = actions.onTestMistralConnection
    val onSetNvidiaBaseUrl = actions.onSetNvidiaBaseUrl
    val onSetNvidiaApiKey = actions.onSetNvidiaApiKey
    val onSetNvidiaModel = actions.onSetNvidiaModel
    val onRefreshNvidiaModels = actions.onRefreshNvidiaModels
    val onTestNvidiaConnection = actions.onTestNvidiaConnection
    val onSetOllamaCloudBaseUrl = actions.onSetOllamaCloudBaseUrl
    val onSetOllamaCloudApiKey = actions.onSetOllamaCloudApiKey
    val onSetOllamaCloudModel = actions.onSetOllamaCloudModel
    val onRefreshOllamaCloudModels = actions.onRefreshOllamaCloudModels
    val onTestOllamaCloudConnection = actions.onTestOllamaCloudConnection
    val onStopGoogleTranslation = actions.onStopGoogleTranslation
    val onResumeGoogleTranslation = actions.onResumeGoogleTranslation
    val onToggleGoogleTranslationVisibility = actions.onToggleGoogleTranslationVisibility
    val onClearGoogleTranslation = actions.onClearGoogleTranslation
    val onSetGoogleTranslationAutoStart = actions.onSetGoogleTranslationAutoStart
    val onSetGoogleTranslationSourceLang = actions.onSetGoogleTranslationSourceLang
    val onSetGoogleTranslationTargetLang = actions.onSetGoogleTranslationTargetLang
    val onStartGeminiTranslation = actions.onStartGeminiTranslation
    val onStartGoogleTranslation = actions.onStartGoogleTranslation
    if (showSettings) {
        NovelReaderSettingsDialog(
            sourceId = state.novel.source,
            currentWebViewActive = showWebView,
            currentPageReaderActive = usePageReader,
            onDismissRequest = onDismissSettings,
            onPrepareBook = if (state.bookMode.isEnabled) onPrepareWholeBook else null,
            bookModeActive = state.bookMode.isEnabled,
            prepareBookInProgress = state.bookMode.isPreparingWholeBook,
            preparedChapterCount = state.bookMode.preparedChapterCount,
            totalChapterCount = state.bookMode.totalChapterCount,
        )
    }
    if (showChapterList) {
        LaunchedEffect(Unit) {
            onOpenBottomSheet()
        }
        val currentChapterId = state.chapter.id
        val chapters = if (state.fullChapterOrderList.isNotEmpty()) {
            state.fullChapterOrderList
        } else {
            state.chapterOrderList
        }
        val chapterListItems = chapters.map { chapter ->
            ReaderChapterListItem(
                id = chapter.id,
                title = chapter.name,
                dateText = chapter.dateUpload.takeIf { it > 0 }?.let {
                    relativeDateTimeText(it)
                },
                scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
                isCurrent = chapter.id == currentChapterId,
            )
        }
        ReaderChapterListSheet(
            items = chapterListItems,
            onDismissRequest = onDismissChapterList,
            onChapterClick = { chapterId ->
                onDismissChapterList()
                if (chapterId != state.chapter.id) {
                    onOpenChapter?.invoke(chapterId)
                }
            },
            onDownloadClick = { chapterId ->
                onDownloadChapter?.invoke(chapterId)
            },
        )
    }
    if (showTtsBehaviorSettings && ttsPlacement.showFooterEntry) {
        NovelReaderTtsBehaviorSettingsDialog(
            sourceId = state.novel.source,
            onDismissRequest = onDismissTtsBehaviorSettings,
        )
    }
    if (showGeminiDialog && state.readerSettings.geminiEnabled) {
        GeminiTranslationDialog(
            readerSettings = state.readerSettings,
            isTranslating = state.isGeminiTranslating,
            translationProgress = state.geminiTranslationProgress,
            isVisible = state.isGeminiTranslationVisible,
            hasCache = state.hasGeminiTranslationCache,
            logs = state.geminiLogs,
            onStart = requestGeminiTranslationStart,
            onStop = onStopGeminiTranslation,
            onToggleVisibility = onToggleGeminiTranslationVisibility,
            onClear = onClearGeminiTranslation,
            onClearAllCache = onClearAllGeminiTranslationCache,
            onAddLog = onAddAiTranslationLog,
            onClearLogs = onClearGeminiLogs,
            onSetGeminiApiKey = onSetGeminiApiKey,
            onSetGeminiModel = onSetGeminiModel,
            onSetGeminiBatchSize = onSetGeminiBatchSize,
            onSetGeminiConcurrency = onSetGeminiConcurrency,
            onSetGeminiRelaxedMode = onSetGeminiRelaxedMode,
            onSetGeminiDisableCache = onSetGeminiDisableCache,
            onSetGeminiReasoningEffort = onSetGeminiReasoningEffort,
            onSetGeminiBudgetTokens = onSetGeminiBudgetTokens,
            onSetGeminiTemperature = onSetGeminiTemperature,
            onSetGeminiTopP = onSetGeminiTopP,
            onSetGeminiTopK = onSetGeminiTopK,
            onSetGeminiPromptMode = onSetGeminiPromptMode,
            onSetGeminiSourceLang = onSetGeminiSourceLang,
            onSetGeminiTargetLang = onSetGeminiTargetLang,
            onSetGeminiStylePreset = onSetGeminiStylePreset,
            onSetGeminiEnabledPromptModifiers = onSetGeminiEnabledPromptModifiers,
            onSetGeminiCustomPromptModifier = onSetGeminiCustomPromptModifier,
            onSetGeminiAutoTranslateEnglishSource = onSetGeminiAutoTranslateEnglishSource,
            onSetGeminiPrefetchNextChapterTranslation = onSetGeminiPrefetchNextChapterTranslation,
            onSetGeminiPrivateUnlocked = onSetGeminiPrivateUnlocked,
            onSetGeminiPrivatePythonLikeMode = onSetGeminiPrivatePythonLikeMode,
            onSetTranslationProvider = onSetTranslationProvider,
            onSetOpenRouterBaseUrl = onSetOpenRouterBaseUrl,
            onSetOpenRouterApiKey = onSetOpenRouterApiKey,
            onSetOpenRouterModel = onSetOpenRouterModel,
            onRefreshOpenRouterModels = onRefreshOpenRouterModels,
            onTestOpenRouterConnection = onTestOpenRouterConnection,
            onSetDeepSeekBaseUrl = onSetDeepSeekBaseUrl,
            onSetDeepSeekApiKey = onSetDeepSeekApiKey,
            onSetDeepSeekModel = onSetDeepSeekModel,
            onRefreshDeepSeekModels = onRefreshDeepSeekModels,
            onTestDeepSeekConnection = onTestDeepSeekConnection,
            onSetMistralBaseUrl = onSetMistralBaseUrl,
            onSetMistralApiKey = onSetMistralApiKey,
            onSetMistralModel = onSetMistralModel,
            onRefreshMistralModels = onRefreshMistralModels,
            onTestMistralConnection = onTestMistralConnection,
            onSetNvidiaBaseUrl = onSetNvidiaBaseUrl,
            onSetNvidiaApiKey = onSetNvidiaApiKey,
            onSetNvidiaModel = onSetNvidiaModel,
            onRefreshNvidiaModels = onRefreshNvidiaModels,
            onTestNvidiaConnection = onTestNvidiaConnection,
            onSetOllamaCloudBaseUrl = onSetOllamaCloudBaseUrl,
            onSetOllamaCloudApiKey = onSetOllamaCloudApiKey,
            onSetOllamaCloudModel = onSetOllamaCloudModel,
            onRefreshOllamaCloudModels = onRefreshOllamaCloudModels,
            onTestOllamaCloudConnection = onTestOllamaCloudConnection,
            openRouterModels = state.openRouterModelIds,
            isOpenRouterModelsLoading = state.isOpenRouterModelsLoading,
            isTestingOpenRouterConnection = state.isTestingOpenRouterConnection,
            openRouterApiTestStatus = state.openRouterApiTestStatus,
            openRouterApiTestMessage = state.openRouterApiTestMessage,
            deepSeekModels = state.deepSeekModelIds,
            isDeepSeekModelsLoading = state.isDeepSeekModelsLoading,
            isTestingDeepSeekConnection = state.isTestingDeepSeekConnection,
            deepSeekApiTestStatus = state.deepSeekApiTestStatus,
            deepSeekApiTestMessage = state.deepSeekApiTestMessage,
            mistralModels = state.mistralModelIds,
            isMistralModelsLoading = state.isMistralModelsLoading,
            isTestingMistralConnection = state.isTestingMistralConnection,
            mistralApiTestStatus = state.mistralApiTestStatus,
            mistralApiTestMessage = state.mistralApiTestMessage,
            nvidiaModels = state.nvidiaModelIds,
            isNvidiaModelsLoading = state.isNvidiaModelsLoading,
            isTestingNvidiaConnection = state.isTestingNvidiaConnection,
            nvidiaApiTestStatus = state.nvidiaApiTestStatus,
            nvidiaApiTestMessage = state.nvidiaApiTestMessage,
            ollamaCloudModels = state.ollamaCloudModelIds,
            isOllamaCloudModelsLoading = state.isOllamaCloudModelsLoading,
            isTestingOllamaCloudConnection = state.isTestingOllamaCloudConnection,
            ollamaCloudApiTestStatus = state.ollamaCloudApiTestStatus,
            ollamaCloudApiTestMessage = state.ollamaCloudApiTestMessage,
            onDismiss = onDismissGeminiDialog,
        )
    }
    if (showGoogleDialog && state.readerSettings.googleTranslationEnabled) {
        GoogleTranslationDialog(
            readerSettings = state.readerSettings,
            isTranslating = state.isGoogleTranslating,
            translationProgress = state.googleTranslationProgress,
            translationPhase = state.translationPhase,
            isVisible = state.isGoogleTranslationVisible,
            hasCache = state.hasGoogleTranslationCache,
            isRateLimited = state.isGoogleRateLimited,
            onStart = requestGoogleTranslationStart,
            onStop = onStopGoogleTranslation,
            onResume = onResumeGoogleTranslation,
            onToggleVisibility = onToggleGoogleTranslationVisibility,
            onClear = onClearGoogleTranslation,
            onSetAutoStart = onSetGoogleTranslationAutoStart,
            onSetSourceLang = onSetGoogleTranslationSourceLang,
            onSetTargetLang = onSetGoogleTranslationTargetLang,
            onDismiss = onDismissGoogleDialog,
        )
    }
    translationSwitchRequest?.let { switchRequest ->
        val fromLabel = when (switchRequest.from) {
            TranslationKind.Gemini -> "Gemini"
            TranslationKind.Google -> stringResource(AYMR.strings.novel_reader_google_translate)
        }
        val toLabel = when (switchRequest.to) {
            TranslationKind.Gemini -> "Gemini"
            TranslationKind.Google -> stringResource(AYMR.strings.novel_reader_google_translate)
        }
        AlertDialog(
            onDismissRequest = onDismissTranslationSwitchRequest,
            title = {
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_google_translate_switch_confirm,
                        toLabel,
                        fromLabel,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (switchRequest.from) {
                            TranslationKind.Gemini -> onClearGeminiTranslation()
                            TranslationKind.Google -> onClearGoogleTranslation()
                        }
                        onDismissTranslationSwitchRequest()
                        when (switchRequest.to) {
                            TranslationKind.Gemini -> onStartGeminiTranslation()
                            TranslationKind.Google -> onStartGoogleTranslation()
                        }
                    },
                ) {
                    Text(text = stringResource(AYMR.strings.novel_reader_ai_translator_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissTranslationSwitchRequest) {
                    Text(text = stringResource(AYMR.strings.novel_reader_ai_translator_cancel))
                }
            },
        )
    }
}

/**
 * Every callback the reader dialogs need, grouped into one parameter.
 *
 * Grouping keeps both the host and its caller inside the compiler's per-method instruction
 * budget; passing these one by one is what pushed [NovelReaderScreen] out of it.
 */
@Immutable
internal data class NovelReaderDialogActions(
    val onDismissSettings: () -> Unit,
    val onDismissChapterList: () -> Unit,
    val onOpenBottomSheet: () -> Unit,
    val onOpenChapter: ((Long) -> Unit)?,
    val onDownloadChapter: ((Long) -> Unit)?,
    val onDismissTtsBehaviorSettings: () -> Unit,
    val onDismissGeminiDialog: () -> Unit,
    val onDismissGoogleDialog: () -> Unit,
    val onDismissTranslationSwitchRequest: () -> Unit,
    val requestGeminiTranslationStart: () -> Unit,
    val requestGoogleTranslationStart: () -> Unit,
    val onPrepareWholeBook: () -> Unit,
    val onStopGeminiTranslation: () -> Unit,
    val onToggleGeminiTranslationVisibility: () -> Unit,
    val onClearGeminiTranslation: () -> Unit,
    val onClearAllGeminiTranslationCache: () -> Unit,
    val onAddAiTranslationLog: (String) -> Unit,
    val onClearGeminiLogs: () -> Unit,
    val onSetGeminiApiKey: (String) -> Unit,
    val onSetGeminiModel: (String) -> Unit,
    val onSetGeminiBatchSize: (Int) -> Unit,
    val onSetGeminiConcurrency: (Int) -> Unit,
    val onSetGeminiRelaxedMode: (Boolean) -> Unit,
    val onSetGeminiDisableCache: (Boolean) -> Unit,
    val onSetGeminiReasoningEffort: (String) -> Unit,
    val onSetGeminiBudgetTokens: (Int) -> Unit,
    val onSetGeminiTemperature: (Float) -> Unit,
    val onSetGeminiTopP: (Float) -> Unit,
    val onSetGeminiTopK: (Int) -> Unit,
    val onSetGeminiPromptMode: (GeminiPromptMode) -> Unit,
    val onSetGeminiSourceLang: (String) -> Unit,
    val onSetGeminiTargetLang: (String) -> Unit,
    val onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit,
    val onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit,
    val onSetGeminiCustomPromptModifier: (String) -> Unit,
    val onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit,
    val onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit,
    val onSetGeminiPrivateUnlocked: (Boolean) -> Unit,
    val onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit,
    val onSetTranslationProvider: (NovelTranslationProvider) -> Unit,
    val onSetOpenRouterBaseUrl: (String) -> Unit,
    val onSetOpenRouterApiKey: (String) -> Unit,
    val onSetOpenRouterModel: (String) -> Unit,
    val onRefreshOpenRouterModels: () -> Unit,
    val onTestOpenRouterConnection: () -> Unit,
    val onSetDeepSeekBaseUrl: (String) -> Unit,
    val onSetDeepSeekApiKey: (String) -> Unit,
    val onSetDeepSeekModel: (String) -> Unit,
    val onRefreshDeepSeekModels: () -> Unit,
    val onTestDeepSeekConnection: () -> Unit,
    val onSetMistralBaseUrl: (String) -> Unit,
    val onSetMistralApiKey: (String) -> Unit,
    val onSetMistralModel: (String) -> Unit,
    val onRefreshMistralModels: () -> Unit,
    val onTestMistralConnection: () -> Unit,
    val onSetNvidiaBaseUrl: (String) -> Unit,
    val onSetNvidiaApiKey: (String) -> Unit,
    val onSetNvidiaModel: (String) -> Unit,
    val onRefreshNvidiaModels: () -> Unit,
    val onTestNvidiaConnection: () -> Unit,
    val onSetOllamaCloudBaseUrl: (String) -> Unit,
    val onSetOllamaCloudApiKey: (String) -> Unit,
    val onSetOllamaCloudModel: (String) -> Unit,
    val onRefreshOllamaCloudModels: () -> Unit,
    val onTestOllamaCloudConnection: () -> Unit,
    val onStopGoogleTranslation: () -> Unit,
    val onResumeGoogleTranslation: () -> Unit,
    val onToggleGoogleTranslationVisibility: () -> Unit,
    val onClearGoogleTranslation: () -> Unit,
    val onSetGoogleTranslationAutoStart: (Boolean) -> Unit,
    val onSetGoogleTranslationSourceLang: (String) -> Unit,
    val onSetGoogleTranslationTargetLang: (String) -> Unit,
    val onStartGeminiTranslation: () -> Unit,
    val onStartGoogleTranslation: () -> Unit,
)
