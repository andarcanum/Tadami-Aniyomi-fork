package eu.kanade.tachiyomi.ui.reader.novel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.reader.novel.NovelAtmosphereBackground
import eu.kanade.presentation.reader.novel.NovelReaderBackdropSession
import eu.kanade.presentation.reader.novel.NovelReaderChapterHandoffPolicy
import eu.kanade.presentation.reader.novel.NovelReaderPageReaderHandoffTarget
import eu.kanade.presentation.reader.novel.NovelReaderScreen
import eu.kanade.presentation.reader.novel.NovelReaderSystemUiSession
import eu.kanade.presentation.reader.novel.SystemUIController
import eu.kanade.presentation.reader.novel.readNovelReaderCustomBackgroundItems
import eu.kanade.presentation.reader.novel.resolveNovelReaderBackdropColor
import eu.kanade.presentation.reader.novel.resolveReaderBackgroundBackdropColor
import eu.kanade.presentation.reader.novel.resolveReaderBackgroundImageModel
import eu.kanade.presentation.reader.novel.resolveReaderBackgroundSelection
import eu.kanade.presentation.reader.novel.resolveReaderSystemUiFlag
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackService
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackState
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class NovelReaderScreen(
    private val chapterId: Long,
    private val sourceId: Long? = null,
) : eu.kanade.presentation.util.Screen() {
    fun resolveInitialBackdropColor(): Color? {
        val initialReaderSettings = sourceId?.let { id ->
            Injekt.get<NovelReaderPreferences>().resolveSettings(id)
        } ?: return null
        return resolveNovelReaderBackdropColor(
            settings = initialReaderSettings,
            isSystemDark = Injekt.get<android.app.Application>().isNightMode(),
        )
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelReaderScreenModel(chapterId) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val currentState = state
        val coroutineScope = rememberCoroutineScope()
        var showReaderUi by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var ttsPlaybackService by remember { mutableStateOf<NovelTtsPlaybackService?>(null) }
        val initialReaderSettings = remember(sourceId) {
            sourceId?.let { id ->
                Injekt.get<NovelReaderPreferences>().resolveSettings(id)
            }
        }
        val initialBackdropColor = remember(initialReaderSettings) {
            resolveInitialBackdropColor()
        }

        val activeReaderSettings = (currentState as? NovelReaderScreenModel.State.Success)?.readerSettings
        val loadingReaderSettings = (currentState as? NovelReaderScreenModel.State.Loading)
            ?.readerSettings
            ?: initialReaderSettings
        val fullScreenMode = resolveReaderSystemUiFlag(
            activeValue = activeReaderSettings?.fullScreenMode,
            loadingValue = loadingReaderSettings?.fullScreenMode,
            initialValue = initialReaderSettings?.fullScreenMode,
        )
        val keepScreenOn = resolveReaderSystemUiFlag(
            activeValue = activeReaderSettings?.keepScreenOn,
            loadingValue = loadingReaderSettings?.keepScreenOn,
            initialValue = initialReaderSettings?.keepScreenOn,
        )

        SystemUIController(
            fullScreenMode = fullScreenMode,
            keepScreenOn = keepScreenOn,
            showReaderUi = showReaderUi,
        )

        val loadingBackdropColor = loadingReaderSettings
            ?.let {
                resolveNovelReaderBackdropColor(
                    it,
                    isSystemDark =
                    MaterialTheme.colorScheme.background.luminance() < 0.5f,
                )
            }
            ?: initialBackdropColor
            ?: MaterialTheme.colorScheme.background
        val activeBackdropColor = when (currentState) {
            is NovelReaderScreenModel.State.Loading -> loadingBackdropColor
            is NovelReaderScreenModel.State.Success -> initialBackdropColor ?: loadingBackdropColor
            is NovelReaderScreenModel.State.Error -> null
        }

        SideEffect {
            NovelReaderBackdropSession.update(activeBackdropColor)
        }

        when (currentState) {
            is NovelReaderScreenModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(loadingBackdropColor),
            ) {
                loadingReaderSettings?.let { settings ->
                    NovelReaderLoadingBackdrop(
                        settings = settings,
                    )
                }
                LoadingScreen()
            }
            is NovelReaderScreenModel.State.Error -> {
                val message = currentState.message ?: stringResource(MR.strings.unknown_error)
                EmptyScreen(message = message)
            }
            is NovelReaderScreenModel.State.Success -> {
                val successState = currentState
                DisposableEffect(successState.chapter.id, successState.readerSettings.ttsEnabled) {
                    var isBound = false
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            val binder = service as? NovelTtsPlaybackService.LocalBinder ?: return
                            ttsPlaybackService = binder.service().also { playbackService ->
                                playbackService.bindRuntime(screenModel.createTtsPlaybackServiceRuntime())
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            ttsPlaybackService = null
                        }
                    }

                    if (successState.readerSettings.ttsEnabled) {
                        val serviceIntent = Intent(context, NovelTtsPlaybackService::class.java)
                        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
                        isBound = true
                    }

                    onDispose {
                        if (isBound) {
                            runCatching { context.unbindService(connection) }
                            ttsPlaybackService = null
                        }
                    }
                }
                DisposableEffect(successState.ttsUiState.playbackState, successState.readerSettings.ttsEnabled) {
                    val shouldRunService =
                        successState.readerSettings.ttsEnabled &&
                            (
                                successState.ttsUiState.playbackState == NovelTtsPlaybackState.PLAYING ||
                                    successState.ttsUiState.playbackState == NovelTtsPlaybackState.PAUSED
                                )
                    val serviceIntent = Intent(context, NovelTtsPlaybackService::class.java)
                    if (shouldRunService) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.stopService(serviceIntent)
                    }
                    onDispose { }
                }
                DisposableEffect(successState.readerSettings.ttsEnabled) {
                    onDispose {
                        if (!successState.readerSettings.ttsEnabled) {
                            context.stopService(Intent(context, NovelTtsPlaybackService::class.java))
                        }
                    }
                }
                NovelReaderScreen(
                    state = successState,
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
                    onStartGoogleTranslation = screenModel::startGoogleTranslation,
                    onStopGoogleTranslation = screenModel::stopGoogleTranslation,
                    onResumeGoogleTranslation = screenModel::resumeGoogleTranslation,
                    onToggleGoogleTranslationVisibility = screenModel::toggleGoogleTranslationVisibility,
                    onClearGoogleTranslation = screenModel::clearGoogleTranslation,
                    onSetGoogleTranslationEnabled = screenModel::setGoogleTranslationEnabled,
                    onSetGoogleTranslationAutoStart = screenModel::setGoogleTranslationAutoStart,
                    onSetGoogleTranslationSourceLang = screenModel::setGoogleTranslationSourceLang,
                    onSetGoogleTranslationTargetLang = screenModel::setGoogleTranslationTargetLang,
                    onToggleTtsPlayback = screenModel::toggleTtsPlayback,
                    onStopTtsPlayback = screenModel::stopTtsPlayback,
                    onSkipPreviousTts = screenModel::skipToPreviousTtsSegment,
                    onSkipNextTts = screenModel::skipToNextTtsSegment,
                    onPauseTtsForManualNavigation = screenModel::pauseTtsForManualNavigation,
                    onSetTtsEnginePackage = screenModel::setTtsEnginePackage,
                    onSetTtsVoiceId = screenModel::setTtsVoiceId,
                    onSetTtsLocaleTag = screenModel::setTtsLocaleTag,
                    onSetTtsSpeechRate = screenModel::setTtsSpeechRate,
                    onSetTtsPitch = screenModel::setTtsPitch,
                    onDisableTts = screenModel::disableTts,
                    onSelectedTextSelectionChanged = screenModel::updateSelectedTextSelection,
                    onTranslateSelectedText = screenModel::translateSelectedText,
                    onRetrySelectedTextTranslation = screenModel::retrySelectedTextTranslation,
                    onDismissSelectedTextTranslation = screenModel::dismissSelectedTextTranslation,
                    onOpenPreviousChapter = { previousChapterId ->
                        coroutineScope.launch {
                            screenModel.awaitPendingProgressPersistence()
                            NovelReaderSystemUiSession.markInternalChapterReplace()
                            NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
                                NovelReaderPageReaderHandoffTarget.END,
                            )
                            navigator.replace(NovelReaderScreen(previousChapterId, successState.novel.source))
                        }
                    },
                    onOpenNextChapter = { nextChapterId ->
                        coroutineScope.launch {
                            screenModel.awaitPendingProgressPersistence()
                            NovelReaderSystemUiSession.markInternalChapterReplace()
                            NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
                                NovelReaderPageReaderHandoffTarget.START,
                            )
                            navigator.replace(NovelReaderScreen(nextChapterId, successState.novel.source))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NovelReaderLoadingBackdrop(
    settings: eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings,
) {
    if (settings.appearanceMode != NovelReaderAppearanceMode.BACKGROUND) return

    val context = LocalContext.current
    val customBackgroundItems = remember(context) {
        readNovelReaderCustomBackgroundItems(context)
    }

    val customBackgroundExists = remember(
        settings.customBackgroundId,
        settings.customBackgroundPath,
        customBackgroundItems,
    ) {
        val selectedPathFromCatalog = customBackgroundItems
            .firstOrNull { it.id == settings.customBackgroundId }
            ?.absolutePath
        val candidatePath = selectedPathFromCatalog ?: settings.customBackgroundPath
        candidatePath.isNotBlank() && File(candidatePath).exists()
    }
    val backgroundSelection = remember(
        settings.backgroundSource,
        settings.backgroundPresetId,
        settings.customBackgroundId,
        settings.customBackgroundPath,
        customBackgroundItems,
        customBackgroundExists,
    ) {
        resolveReaderBackgroundSelection(
            backgroundSource = settings.backgroundSource,
            backgroundPresetId = settings.backgroundPresetId,
            customBackgroundId = settings.customBackgroundId,
            customBackgroundItems = customBackgroundItems,
            customBackgroundPath = settings.customBackgroundPath,
            customBackgroundExists = customBackgroundExists,
        )
    }
    val backgroundColor = remember(backgroundSelection) {
        resolveReaderBackgroundBackdropColor(backgroundSelection)
    }
    val backgroundImageModel = remember(backgroundSelection) {
        resolveReaderBackgroundImageModel(backgroundSelection)
    }

    NovelAtmosphereBackground(
        backgroundColor = backgroundColor,
        backgroundTexture = NovelReaderBackgroundTexture.NONE,
        nativeTextureStrengthPercent = settings.nativeTextureStrengthPercent,
        oledEdgeGradient = false,
        isDarkTheme = backgroundColor.luminance() < 0.5f,
        pageEdgeShadow = false,
        pageEdgeShadowAlpha = 0f,
        backgroundImageModel = backgroundImageModel,
    )
}
