package eu.kanade.tachiyomi.ui.reader.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.reader.novel.NovelReaderScreen
import eu.kanade.presentation.reader.novel.NovelReaderSystemUiSession
import eu.kanade.presentation.reader.novel.SystemUIController
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class NovelReaderScreen(
    private val chapterId: Long,
) : eu.kanade.presentation.util.Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelReaderScreenModel(chapterId) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val currentState = state
        var showReaderUi by remember { mutableStateOf(false) }

        val activeReaderSettings = (currentState as? NovelReaderScreenModel.State.Success)?.readerSettings

        SystemUIController(
            fullScreenMode = activeReaderSettings?.fullScreenMode ?: false,
            keepScreenOn = activeReaderSettings?.keepScreenOn ?: false,
            showReaderUi = showReaderUi,
        )

        when (currentState) {
            is NovelReaderScreenModel.State.Loading -> LoadingScreen()
            is NovelReaderScreenModel.State.Error -> {
                val message = currentState.message ?: stringResource(MR.strings.unknown_error)
                EmptyScreen(message = message)
            }
            is NovelReaderScreenModel.State.Success -> NovelReaderScreen(
                state = currentState,
                showReaderUi = showReaderUi,
                onSetShowReaderUi = { showReaderUi = it },
                onBack = navigator::pop,
                onReadingProgress = screenModel::updateReadingProgress,
                onToggleBookmark = screenModel::toggleChapterBookmark,
                onStartGeminiTranslation = screenModel::startGeminiTranslation,
                onStopGeminiTranslation = screenModel::stopGeminiTranslation,
                onToggleGeminiTranslationVisibility = screenModel::toggleGeminiTranslationVisibility,
                onClearGeminiTranslation = screenModel::clearGeminiTranslation,
                onClearAllGeminiTranslationCache = screenModel::clearAllGeminiTranslationCache,
                onAddGeminiLog = screenModel::addGeminiLog,
                onClearGeminiLogs = screenModel::clearGeminiLogs,
                onSetGeminiApiKey = screenModel::setGeminiApiKey,
                onSetGeminiModel = screenModel::setGeminiModel,
                onSetGeminiBatchSize = screenModel::setGeminiBatchSize,
                onSetGeminiConcurrency = screenModel::setGeminiConcurrency,
                onSetGeminiRelaxedMode = screenModel::setGeminiRelaxedMode,
                onSetGeminiDisableCache = screenModel::setGeminiDisableCache,
                onSetGeminiReasoningEffort = screenModel::setGeminiReasoningEffort,
                onSetGeminiBudgetTokens = screenModel::setGeminiBudgetTokens,
                onSetGeminiTemperature = screenModel::setGeminiTemperature,
                onSetGeminiTopP = screenModel::setGeminiTopP,
                onSetGeminiTopK = screenModel::setGeminiTopK,
                onSetGeminiPromptMode = screenModel::setGeminiPromptMode,
                onSetGeminiStylePreset = screenModel::setGeminiStylePreset,
                onSetGeminiEnabledPromptModifiers = screenModel::setGeminiEnabledPromptModifiers,
                onSetGeminiCustomPromptModifier = screenModel::setGeminiCustomPromptModifier,
                onSetGeminiAutoTranslateEnglishSource = screenModel::setGeminiAutoTranslateEnglishSource,
                onSetGeminiPrefetchNextChapterTranslation = screenModel::setGeminiPrefetchNextChapterTranslation,
                onSetGeminiPrivateUnlocked = screenModel::setGeminiPrivateUnlocked,
                onSetGeminiPrivatePythonLikeMode = screenModel::setGeminiPrivatePythonLikeMode,
                onSetTranslationProvider = screenModel::setTranslationProvider,
                onSetAirforceBaseUrl = screenModel::setAirforceBaseUrl,
                onSetAirforceApiKey = screenModel::setAirforceApiKey,
                onSetAirforceModel = screenModel::setAirforceModel,
                onRefreshAirforceModels = screenModel::refreshAirforceModels,
                onTestAirforceConnection = screenModel::testAirforceConnection,
                onSetOpenRouterBaseUrl = screenModel::setOpenRouterBaseUrl,
                onSetOpenRouterApiKey = screenModel::setOpenRouterApiKey,
                onSetOpenRouterModel = screenModel::setOpenRouterModel,
                onRefreshOpenRouterModels = screenModel::refreshOpenRouterModels,
                onTestOpenRouterConnection = screenModel::testOpenRouterConnection,
                onSetDeepSeekBaseUrl = screenModel::setDeepSeekBaseUrl,
                onSetDeepSeekApiKey = screenModel::setDeepSeekApiKey,
                onSetDeepSeekModel = screenModel::setDeepSeekModel,
                onRefreshDeepSeekModels = screenModel::refreshDeepSeekModels,
                onTestDeepSeekConnection = screenModel::testDeepSeekConnection,
                onOpenPreviousChapter = { previousChapterId ->
                    NovelReaderSystemUiSession.markInternalChapterReplace()
                    navigator.replace(NovelReaderScreen(previousChapterId))
                },
                onOpenNextChapter = { nextChapterId ->
                    NovelReaderSystemUiSession.markInternalChapterReplace()
                    navigator.replace(NovelReaderScreen(nextChapterId))
                },
            )
        }
    }
}
