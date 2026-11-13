package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.more.settings.translation.AiTranslationAction
import eu.kanade.presentation.more.settings.translation.AiTranslationEditorHost
import eu.kanade.presentation.more.settings.translation.AiTranslationSettingsEffect
import eu.kanade.presentation.more.settings.translation.AiTranslationSettingsMessage
import eu.kanade.presentation.more.settings.translation.AiTranslationSettingsScreen
import eu.kanade.presentation.more.settings.translation.AiTranslationSettingsScreenModel
import eu.kanade.presentation.more.settings.translation.AiTranslationTab
import eu.kanade.presentation.reader.ReaderChapterListItem
import eu.kanade.presentation.reader.ReaderChapterListSheet
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.util.system.toast
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
    val requestGoogleTranslationStart = actions.requestGoogleTranslationStart
    val onPrepareWholeBook = actions.onPrepareWholeBook
    val onToggleGeminiTranslationVisibility = actions.onToggleGeminiTranslationVisibility
    val onClearGeminiTranslation = actions.onClearGeminiTranslation
    val onClearGeminiTranslationForSwitch = actions.onClearGeminiTranslationForSwitch
    val onClearAllGeminiTranslationCache = actions.onClearAllGeminiTranslationCache
    val onClearGeminiLogs = actions.onClearGeminiLogs
    val onSetGeminiAutoTranslateEnglishSource = actions.onSetGeminiAutoTranslateEnglishSource
    val onSetGeminiPrefetchNextChapterTranslation = actions.onSetGeminiPrefetchNextChapterTranslation
    val onSetGeminiDisableCache = actions.onSetGeminiDisableCache
    val onSetGeminiPrivatePythonLikeMode = actions.onSetGeminiPrivatePythonLikeMode
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
    // Always open the unified Aurora quick window when AI translation is enabled so the profile
    // grid and quick setup editors stay accessible even before the first profile is created.
    val quickModel = if (showGeminiDialog && state.readerSettings.geminiEnabled) {
        val quickScope = rememberCoroutineScope()
        remember(state.novel.id, state.chapter.id, quickScope) {
            AiTranslationSettingsScreenModel(
                scope = quickScope,
                novelId = state.novel.id,
                chapterId = state.chapter.id,
                sourceId = state.novel.source,
                initialCatalog = TranslationProfileServices.cachedCatalog,
            )
        }
    } else {
        null
    }
    if (quickModel != null) {
        val context = LocalContext.current
        DisposableEffect(quickModel) { onDispose { quickModel.onDispose() } }
        LaunchedEffect(quickModel) {
            quickModel.effects.collect { effect ->
                when (effect) {
                    is AiTranslationSettingsEffect.Message -> context.toast(
                        when (effect.kind) {
                            AiTranslationSettingsMessage.VALIDATION -> AYMR.strings.ai_translation_error_field
                            AiTranslationSettingsMessage.STORAGE -> AYMR.strings.ai_translation_protected_save_failed
                            AiTranslationSettingsMessage.UNAVAILABLE -> AYMR.strings.ai_translation_action_unavailable
                            AiTranslationSettingsMessage.REFERENCED -> AYMR.strings.ai_translation_item_in_use
                        },
                    )
                    is AiTranslationSettingsEffect.OpenChapter -> Unit
                }
            }
        }
    }
    val quick = quickModel?.quickState?.collectAsStateWithLifecycle()?.value
    if (quickModel != null && quick != null) {
        val navigator = LocalNavigator.currentOrThrow
        val centre by quickModel.state.collectAsStateWithLifecycle()
        AiTranslationQuickDialog(
            state = quick.copy(sourceLabel = state.novel.title, chapterLabel = state.chapter.name),
            onAction = { action ->
                when (action) {
                    AiTranslationAction.OpenAllJobs -> {
                        onDismissGeminiDialog()
                        navigator.push(AiTranslationSettingsScreen())
                    }
                    AiTranslationAction.OpenFullSettings -> {
                        onDismissGeminiDialog()
                        navigator.push(AiTranslationSettingsScreen(initialTab = AiTranslationTab.PROFILES))
                    }
                    else -> quickModel.onAction(action)
                }
            },
            onDismiss = onDismissGeminiDialog,
            isTranslationVisible = state.isGeminiTranslationVisible,
            hasTranslationCache = state.hasGeminiTranslationCache,
            onToggleTranslationVisibility = onToggleGeminiTranslationVisibility,
            onClearChapterCache = onClearGeminiTranslation,
            onClearAllCache = onClearAllGeminiTranslationCache,
            autoTranslateEnglishSource = state.readerSettings.geminiAutoTranslateEnglishSource,
            onSetAutoTranslateEnglishSource = onSetGeminiAutoTranslateEnglishSource,
            prefetchNextChapterTranslation = state.readerSettings.geminiPrefetchNextChapterTranslation,
            onSetPrefetchNextChapterTranslation = onSetGeminiPrefetchNextChapterTranslation,
            disableCache = state.readerSettings.geminiDisableCache,
            onSetDisableCache = onSetGeminiDisableCache,
            privatePythonLikeMode = state.readerSettings.geminiPrivatePythonLikeMode,
            onSetPrivatePythonLikeMode = onSetGeminiPrivatePythonLikeMode,
            logs = state.geminiLogs,
            onClearLogs = onClearGeminiLogs,
        )
        AiTranslationEditorHost(
            state = centre,
            onAction = quickModel::onAction,
            onSaveCredential = quickModel::saveCredential,
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
                            // The switch must not delete the chapter's disk cache: switching back
                            // should restore the already-paid translation instead of re-running
                            // the paid API.
                            TranslationKind.Gemini -> onClearGeminiTranslationForSwitch()
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
    val onClearGeminiTranslationForSwitch: () -> Unit,
    val onClearAllGeminiTranslationCache: () -> Unit,
    val onAddAiTranslationLog: (String) -> Unit,
    val onClearGeminiLogs: () -> Unit,
    val onSetGeminiApiKey: (String) -> Unit,
    val onSetGeminiModel: (String) -> Unit,
    val onRefreshGeminiModels: () -> Unit,
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
