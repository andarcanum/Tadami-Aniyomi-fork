package eu.kanade.presentation.reader.novel

import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import eu.kanade.domain.easteregg.lattice.LatticeCarrier
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.easteregg.lattice.LatticeCarrierSlot
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.components.AutoScrollActionFab
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelBookFlipAnimationSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnActivationZone
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.kanade.tachiyomi.ui.reader.novel.setting.parseNovelReaderTapZoneActions
import eu.kanade.tachiyomi.ui.reader.novel.setting.resolveConfiguredNovelReaderTapAction
import eu.kanade.tachiyomi.ui.reader.novel.tts.NativeScrollTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.NativeScrollTtsNavigator
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPageReaderPosition
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPageSlice
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest
import eu.kanade.tachiyomi.ui.reader.novel.tts.PageReaderTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.PageReaderTtsNavigator
import eu.kanade.tachiyomi.ui.reader.novel.tts.WebViewTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.WebViewTtsNavigator
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolvePlainPageReaderTtsAnchors
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

@Suppress("ktlint:standard:max-line-length", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelReaderContentHost(
    rawState: NovelReaderScreenModel.State.Success,
    showReaderUi: Boolean,
    bookEngineSpine: NovelBookSpine = NovelBookSpine.EMPTY,
    // Where the book opens. The renderer owns the position afterwards: it is never pushed back down.
    bookInitialLocation: NovelBookLocation = NovelBookLocation.START,
    // Explicit move requested by the core (resume, seek bar, chapter picker, TTS, search).
    bookSeekRequest: BookSeekRequest? = null,
    bookWindow: NovelBookWindowState = NovelBookWindowState.EMPTY,
    actions: NovelReaderScreenActions,
) {
    val onBack = actions.onBack
    val onReadingProgress = actions.onReadingProgress
    val onSeekBookModeProgress = actions.onSeekBookModeProgress
    val onToggleBookmark = actions.onToggleBookmark
    val onOpenDictionaryHistory = actions.onOpenDictionaryHistory
    val onStartGeminiTranslation = actions.onStartGeminiTranslation
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
    val onStartGoogleTranslation = actions.onStartGoogleTranslation
    val onStopGoogleTranslation = actions.onStopGoogleTranslation
    val onResumeGoogleTranslation = actions.onResumeGoogleTranslation
    val onToggleGoogleTranslationVisibility = actions.onToggleGoogleTranslationVisibility
    val onClearGoogleTranslation = actions.onClearGoogleTranslation
    val onSetGoogleTranslationEnabled = actions.onSetGoogleTranslationEnabled
    val onSetGoogleTranslationAutoStart = actions.onSetGoogleTranslationAutoStart
    val onSetGoogleTranslationSourceLang = actions.onSetGoogleTranslationSourceLang
    val onSetGoogleTranslationTargetLang = actions.onSetGoogleTranslationTargetLang
    val onToggleTtsPlayback = actions.onToggleTtsPlayback
    val onStopTtsPlayback = actions.onStopTtsPlayback
    val onSkipPreviousTts = actions.onSkipPreviousTts
    val onSkipNextTts = actions.onSkipNextTts
    val onPauseTtsForManualNavigation = actions.onPauseTtsForManualNavigation
    val onSetTtsEnginePackage = actions.onSetTtsEnginePackage
    val onSetTtsVoiceId = actions.onSetTtsVoiceId
    val onSetTtsLocaleTag = actions.onSetTtsLocaleTag
    val onSetTtsSpeechRate = actions.onSetTtsSpeechRate
    val onSetTtsPitch = actions.onSetTtsPitch
    val onDisableTts = actions.onDisableTts
    val onPreviewTtsVoice = actions.onPreviewTtsVoice
    val onStopTtsVoicePreview = actions.onStopTtsVoicePreview
    val onOpenPreviousChapter = actions.onOpenPreviousChapter
    val onOpenNextChapter = actions.onOpenNextChapter
    val onPrepareAutoScrollHandoff = actions.onPrepareAutoScrollHandoff
    val onConsumeAutoScrollHandoff = actions.onConsumeAutoScrollHandoff
    val onCancelAutoScrollHandoff = actions.onCancelAutoScrollHandoff
    val onRequestAutoScrollNextChapterPrefetch = actions.onRequestAutoScrollNextChapterPrefetch
    val onOpenChapter = actions.onOpenChapter
    val onDownloadChapter = actions.onDownloadChapter
    val onSetShowReaderUi = actions.onSetShowReaderUi
    val onOpenBottomSheet = actions.onOpenBottomSheet
    val onSelectedTextSelectionChanged = actions.onSelectedTextSelectionChanged
    val onTranslateSelectedText = actions.onTranslateSelectedText
    val onRetrySelectedTextTranslation = actions.onRetrySelectedTextTranslation
    val onDismissSelectedTextTranslation = actions.onDismissSelectedTextTranslation
    val onLookupSelectedTextDefinition = actions.onLookupSelectedTextDefinition
    val onRetryNovelDictionary = actions.onRetryNovelDictionary
    val onDismissNovelDictionary = actions.onDismissNovelDictionary
    val onPlaySelectedTextPronunciation = actions.onPlaySelectedTextPronunciation
    val loadBookEngineDocument = actions.loadBookEngineDocument
    val onBookEngineLocationChanged = actions.onBookEngineLocationChanged
    val onBookSeekApplied = actions.onBookSeekApplied
    val loadBookSectionHtml = actions.loadBookSectionHtml
    val onBookModeScroll = actions.onBookModeScroll
    val onBookModeRetrySection = actions.onBookModeRetrySection
    val onPrepareWholeBook = actions.onPrepareWholeBook
    val nativeBookBlocksForSection = actions.nativeBookBlocksForSection
    val sanitizedSettings = remember(rawState.readerSettings) {
        rawState.readerSettings.copy(
            theme = safeEnum(rawState.readerSettings.theme, NovelReaderTheme.SYSTEM),
            appearanceMode = safeEnum(rawState.readerSettings.appearanceMode, NovelReaderAppearanceMode.THEME),
            backgroundSource = safeEnum(rawState.readerSettings.backgroundSource, NovelReaderBackgroundSource.PRESET),
            backgroundTexture = safeEnum(rawState.readerSettings.backgroundTexture, NovelReaderBackgroundTexture.NONE),
            textAlign = safeEnum(rawState.readerSettings.textAlign, TextAlign.SOURCE),
            pageTransitionStyle = safeEnum(rawState.readerSettings.pageTransitionStyle, NovelPageTransitionStyle.SLIDE),
            bookFlipAnimationSpeed = safeEnum(
                rawState.readerSettings.bookFlipAnimationSpeed,
                NovelBookFlipAnimationSpeed.SLOW,
            ),
            pageTurnSpeed = safeEnum(rawState.readerSettings.pageTurnSpeed, NovelPageTurnSpeed.NORMAL),
            pageTurnIntensity = safeEnum(rawState.readerSettings.pageTurnIntensity, NovelPageTurnIntensity.MEDIUM),
            pageTurnShadowIntensity = safeEnum(
                rawState.readerSettings.pageTurnShadowIntensity,
                NovelPageTurnShadowIntensity.MEDIUM,
            ),
            pageTurnActivationZone = safeEnum(
                rawState.readerSettings.pageTurnActivationZone,
                NovelPageTurnActivationZone.WIDE,
            ),
            translationProvider = safeEnum(
                rawState.readerSettings.translationProvider,
                NovelTranslationProvider.GEMINI,
            ),
            geminiPromptMode = safeEnum(rawState.readerSettings.geminiPromptMode, GeminiPromptMode.ADULT_18),
            geminiStylePreset = safeEnum(
                rawState.readerSettings.geminiStylePreset,
                NovelTranslationStylePreset.PROFESSIONAL,
            ),
            ttsHighlightMode = safeEnum(rawState.readerSettings.ttsHighlightMode, NovelTtsHighlightMode.AUTO),
        )
    }
    val state = remember(rawState, sanitizedSettings) {
        rawState.copy(readerSettings = sanitizedSettings)
    }
    val isBookMode = state.bookMode.isEnabled
    // Sub-object selectors: derivedStateOf prevents recomposition of translation/gemini
    // panels when unrelated state (scroll progress) changes.
    val readerSettings by remember(state) { derivedStateOf { state.readerSettings } }
    val geminiTranslation by remember(state) { derivedStateOf { state.geminiTranslation } }
    val googleTranslation by remember(state) { derivedStateOf { state.googleTranslation } }
    val aiProviders by remember(state) { derivedStateOf { state.aiProviders } }
    val progress by remember(state) { derivedStateOf { state.progress } }

    var showSettings by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showTtsBehaviorSettings by remember { mutableStateOf(false) }
    var selectedTextSelectionSessionId by remember(state.chapter.id) {
        mutableIntStateOf(0)
    }
    val appHaptics = LocalAppHaptics.current
    val ttsPlacement = remember(state.readerSettings.ttsEnabled) {
        resolveNovelReaderTtsSettingsPlacementSnapshot(state.readerSettings.ttsEnabled)
    }
    LaunchedEffect(ttsPlacement.showFooterEntry) {
        if (!ttsPlacement.showFooterEntry) {
            showTtsBehaviorSettings = false
        }
    }
    // A seamless in-place chapter switch reuses this composition, so the renderer that is already
    // mounted must not change while the chapter content is swapped: detaching the reader WebView in
    // the same pass that mounts the native lazy list makes Android restart a focus search from the
    // window root, and Compose then disposes lazy subcompositions in the middle of that layout pass
    // ("Cannot start a writer when another writer is pending"). Keep the mounted renderer until the
    // reader screen itself is recreated or the reader settings change.
    val mountedReaderRenderer = remember { arrayOfNulls<Boolean>(1) }
    val mountedRendererSwitchToken = remember { longArrayOf(state.seamlessSwitchToken) }
    val seamlessRendererSwap = mountedReaderRenderer[0] != null &&
        state.seamlessSwitchToken != mountedRendererSwitchToken[0]
    var showWebView by remember(
        state.chapter.id,
        state.readerSettings.preferWebViewRenderer,
        state.contentBlocks.size,
        isBookMode,
    ) {
        val resolvedShowWebView = shouldStartInWebView(
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            pageReaderEnabled = state.readerSettings.pageReader,
            contentBlocksCount = state.contentBlocks.size,
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
            bookModeEnabled = isBookMode,
        )
        mutableStateOf(
            if (seamlessRendererSwap) {
                mountedReaderRenderer[0] ?: resolvedShowWebView
            } else {
                resolvedShowWebView
            },
        )
    }
    mountedReaderRenderer[0] = showWebView
    mountedRendererSwitchToken[0] = state.seamlessSwitchToken
    val nextSelectedTextSelectionSessionId = remember(state.chapter.id) {
        {
            selectedTextSelectionSessionId += 1
            selectedTextSelectionSessionId.toLong()
        }
    }
    LaunchedEffect(
        state.chapter.id,
        state.readerSettings.preferWebViewRenderer,
        state.readerSettings.richNativeRendererExperimental,
        state.readerSettings.pageReader,
        state.contentBlocks.size,
        state.richContentUnsupportedFeaturesDetected,
        isBookMode,
    ) {
        // Never flip the mounted renderer as part of a seamless chapter swap (see comment above).
        if (seamlessRendererSwap) return@LaunchedEffect
        showWebView = syncShowWebViewWithReaderSettings(
            currentShowWebView = showWebView,
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            pageReaderEnabled = state.readerSettings.pageReader,
            contentBlocksCount = state.contentBlocks.size,
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
            bookModeEnabled = isBookMode,
        )
    }
    val readerPreferences = remember { Injekt.get<NovelReaderPreferences>() }
    // Opt-in experimental chapter handoff: in-place chapter switch for scrolled reading and no
    // intermediate chapter page in the paged reader. Disabled by default.
    val seamlessChapterTransitionEnabled by readerPreferences.seamlessChapterTransition().collectAsState()
    val displayRefreshPreferences = remember { Injekt.get<ReaderPreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val flashOnPageChange by displayRefreshPreferences.flashOnPageChange().collectAsState()
    val eInkProfile by uiPreferences.eInkProfile().collectAsState()
    val displayRefreshHost = remember { DisplayRefreshHost() }
    val sourceId = state.novel.source
    val hasSourceOverride = remember(sourceId) { readerPreferences.getSourceOverride(sourceId) != null }
    var pageViewportSize by remember(state.chapter.id) { mutableStateOf(IntSize.Zero) }
    var hasCompletedInitialReaderLayout by remember(state.chapter.id) { mutableStateOf(false) }
    val autoScrollHandoff = remember(state.chapter.id) {
        onConsumeAutoScrollHandoff(state.chapter.id)
    }
    var autoScrollEnabled by remember(state.chapter.id, autoScrollHandoff) {
        mutableStateOf(
            resolveInitialAutoScrollEnabled(
                savedPreferenceEnabled = state.readerSettings.autoScroll,
                handoff = autoScrollHandoff,
            ),
        )
    }
    var autoScrollSpeed by remember(state.chapter.id, state.readerSettings.autoScrollInterval, autoScrollHandoff) {
        mutableIntStateOf(
            autoScrollHandoff?.speed ?: intervalToAutoScrollSpeed(state.readerSettings.autoScrollInterval),
        )
    }
    var autoScrollExpanded by remember(state.chapter.id) { mutableStateOf(false) }
    var autoScrollWasUsed by remember(state.chapter.id) { mutableStateOf(false) }
    var touchCooldownUntilNanos by remember(state.chapter.id) { mutableLongStateOf(0L) }
    var speedFactor by remember(state.chapter.id) { mutableFloatStateOf(1f) }
    var autoScrollEndStableFrames by remember(state.chapter.id) { mutableIntStateOf(0) }
    var autoScrollEndDwellActive by remember(state.chapter.id) { mutableStateOf(false) }
    var autoScrollEndDwellRemainingSeconds by remember(state.chapter.id) { mutableIntStateOf(0) }
    var showGeminiDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var showGoogleDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var activeImageActionsUrl by remember(state.chapter.id) { mutableStateOf<String?>(null) }
    var translationSwitchRequest by remember(state.chapter.id) {
        mutableStateOf<TranslationSwitchRequest?>(null)
    }
    var requestedTtsChapterSyncTarget by remember(state.chapter.id) { mutableStateOf<Long?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pendingProgrammaticTtsBlockIndex by remember(state.chapter.id) { mutableStateOf<Int?>(null) }
    var suppressManualTtsPauseUntilMs by remember(state.chapter.id) { mutableLongStateOf(0L) }
    val shouldHideWebViewUntilReveal = state.enableJs
    var webProgressPercent by remember(state.chapter.id) {
        mutableIntStateOf(state.lastSavedWebProgressPercent.coerceIn(0, 100))
    }
    var webAutoScrollNearEnd by remember(state.chapter.id) { mutableStateOf(false) }
    var shouldRestoreWebScroll by remember(state.chapter.id) { mutableStateOf(true) }
    var webViewPageReadyForAutoScroll by remember(state.chapter.id) { mutableStateOf(false) }
    var appliedWebCssFingerprint by remember(state.chapter.id) { mutableStateOf<String?>(null) }
    // Deliberately a plain holder instead of Compose state: it has to survive an in-place chapter
    // switch (so it cannot be keyed on the chapter id) and it is written from the AndroidView update
    // block, where a snapshot state write would schedule recomposition during a layout pass.
    val appliedSeamlessSwitchToken = remember { longArrayOf(state.seamlessSwitchToken) }
    var hasReportedReadingProgress by remember(state.chapter.id, showWebView, state.readerSettings.pageReader) {
        mutableStateOf(false)
    }
    val autoScrollPreferenceWriter = remember(readerPreferences, sourceId, hasSourceOverride) {
        NovelReaderAutoScrollPreferenceWriter(
            readerPreferences = readerPreferences,
            sourceId = sourceId,
            hasSourceOverride = hasSourceOverride,
        )
    }
    fun persistAutoScrollEnabledPreference(enabled: Boolean) =
        autoScrollPreferenceWriter.persistAutoScrollEnabledPreference(enabled)

    fun persistAutoScrollIntervalPreference(interval: Int) =
        autoScrollPreferenceWriter.persistAutoScrollIntervalPreference(interval)

    fun persistAutoScrollAdaptiveDelayPreference(enabled: Boolean) =
        autoScrollPreferenceWriter.persistAutoScrollAdaptiveDelayPreference(enabled)

    fun persistAutoScrollChapterEndBehaviorPreference(behavior: NovelAutoScrollChapterEndBehavior) =
        autoScrollPreferenceWriter.persistAutoScrollChapterEndBehaviorPreference(behavior)

    fun persistAutoScrollEndPauseMsPreference(pauseMs: Long) =
        autoScrollPreferenceWriter.persistAutoScrollEndPauseMsPreference(pauseMs)

    fun reportReadingProgress(
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long?,
        flashDisplay: Boolean = false,
        isInitialPositionRestored: Boolean = true,
    ) {
        // Book mode keeps progress in its own domain (spine section + fraction, persisted as an
        // encoded book location). The classic per-chapter reporters (web scroll listener, paginated
        // and native readers) stay wired up for the normal reader, so ignore them while book mode is
        // active instead of letting a per-chapter percentage overwrite the book location.
        if (isBookMode) return
        if (flashDisplay && flashOnPageChange && eInkProfile.isEnabled && hasReportedReadingProgress) {
            displayRefreshHost.flash()
        }
        hasReportedReadingProgress = true
        onReadingProgress(currentIndex, totalItems, persistedProgress, isInitialPositionRestored)
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val viewConfiguration = LocalViewConfiguration.current
    val batteryLevel by rememberBatteryLevel(context)
    val timeText by rememberCurrentTimeText(context)
    val geminiTranslationLabel = stringResource(AYMR.strings.novel_reader_gemini_button)
    val googleTranslationLabel = stringResource(AYMR.strings.novel_reader_google_translate)
    val disableGeminiForGoogleMessage = stringResource(
        AYMR.strings.novel_reader_google_translate_disable_other_first,
        geminiTranslationLabel,
    )
    val disableGoogleForGeminiMessage = stringResource(
        AYMR.strings.novel_reader_google_translate_disable_other_first,
        googleTranslationLabel,
    )
    fun requestGoogleTranslationStart() {
        when {
            state.isGeminiTranslating -> {
                Toast.makeText(
                    context,
                    disableGeminiForGoogleMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            state.isGeminiTranslationVisible || state.hasGeminiTranslationCache -> {
                translationSwitchRequest = TranslationSwitchRequest(
                    from = TranslationKind.Gemini,
                    to = TranslationKind.Google,
                )
            }
            else -> onStartGoogleTranslation()
        }
    }
    fun requestGeminiTranslationStart() {
        when {
            state.isGoogleTranslating -> {
                Toast.makeText(
                    context,
                    disableGoogleForGeminiMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            state.isGoogleTranslationVisible || state.hasGoogleTranslationCache -> {
                translationSwitchRequest = TranslationSwitchRequest(
                    from = TranslationKind.Google,
                    to = TranslationKind.Gemini,
                )
            }
            else -> onStartGeminiTranslation()
        }
    }
    val missingCustomBackgroundMessage =
        stringResource(AYMR.strings.novel_reader_background_custom_missing_fallback)
    val customBackgroundId = state.readerSettings.customBackgroundId
    val customBackgroundPath = state.readerSettings.customBackgroundPath
    val customBackgroundItems = remember(
        customBackgroundId,
        customBackgroundPath,
    ) {
        if (
            customBackgroundPath.isNotBlank() &&
            customBackgroundId.isNotBlank() &&
            customBackgroundId == customBackgroundPath
        ) {
            ensureLegacyNovelReaderBackgroundItem(
                context = context,
                legacyPath = customBackgroundPath,
                preferredId = customBackgroundId,
            )
        }

        readNovelReaderCustomBackgroundItems(context)
    }
    val customBackgroundExists = remember(
        customBackgroundId,
        customBackgroundPath,
        customBackgroundItems,
    ) {
        val selectedPathFromCatalog = customBackgroundItems
            .firstOrNull { it.id == customBackgroundId }
            ?.absolutePath
        val candidatePath = selectedPathFromCatalog ?: customBackgroundPath
        candidatePath.isNotBlank() && File(candidatePath).exists()
    }
    val backgroundSource = readerSettings.backgroundSource
    val backgroundSelection = remember(
        backgroundSource,
        readerSettings.backgroundPresetId,
        customBackgroundId,
        customBackgroundPath,
        customBackgroundItems,
        customBackgroundExists,
    ) {
        resolveReaderBackgroundSelection(
            backgroundSource = backgroundSource,
            backgroundPresetId = readerSettings.backgroundPresetId,
            customBackgroundId = customBackgroundId,
            customBackgroundItems = customBackgroundItems,
            customBackgroundPath = customBackgroundPath,
            customBackgroundExists = customBackgroundExists,
        )
    }
    val backgroundImageModel = remember(backgroundSelection) {
        resolveReaderBackgroundImageModel(backgroundSelection)
    }
    val customBackgroundLuminance = remember(backgroundSelection.customPath) {
        backgroundSelection.customPath?.let(::sampleReaderBackgroundLuminance)
    }
    val effectiveBackgroundLuminance = remember(
        backgroundSelection.source,
        backgroundSelection.preset.isDarkPreferred,
        customBackgroundLuminance,
    ) {
        when (backgroundSelection.source) {
            NovelReaderBackgroundSource.PRESET -> {
                if (backgroundSelection.preset.isDarkPreferred) {
                    0.2f
                } else {
                    0.8f
                }
            }
            NovelReaderBackgroundSource.CUSTOM -> {
                customBackgroundLuminance ?: when (backgroundSelection.customIsDarkHint) {
                    true -> 0.2f
                    false -> 0.8f
                    null -> if (backgroundSelection.preset.isDarkPreferred) 0.2f else 0.8f
                }
            }
        }
    }
    val backgroundModeTextColor = remember(effectiveBackgroundLuminance) {
        resolveReaderTextColorForBackgroundMode(effectiveBackgroundLuminance)
    }
    val backgroundModeBaseColor = remember(backgroundSelection) {
        resolveReaderBackgroundBackdropColor(backgroundSelection)
    }
    val backgroundModeWebImageUrl = remember(backgroundSelection) {
        resolveReaderBackgroundWebImageUrl(backgroundSelection)
    }
    val backgroundModeIdentity = remember(backgroundSelection) {
        resolveReaderBackgroundIdentity(backgroundSelection)
    }
    val isEInkMode = AuroraTheme.colors.isEInk
    val appearanceMode = readerSettings.appearanceMode
    val isBackgroundMode = appearanceMode == NovelReaderAppearanceMode.BACKGROUND
    val activeBackgroundTexture = if (isBackgroundMode || isEInkMode) {
        NovelReaderBackgroundTexture.NONE
    } else {
        readerSettings.backgroundTexture
    }
    val activeOledEdgeGradient = if (isBackgroundMode || isEInkMode) {
        false
    } else {
        state.readerSettings.oledEdgeGradient == true
    }
    val theme = readerSettings.theme
    val isDarkTheme = when {
        isEInkMode -> AuroraTheme.colors.isDark
        else -> when (theme) {
            NovelReaderTheme.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < 0.5f
            NovelReaderTheme.DARK -> true
            NovelReaderTheme.LIGHT -> false
        }
    }
    val fallbackTextColor = if (isEInkMode) {
        AuroraTheme.colors.textPrimary
    } else if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFFEDEDED)
    } else {
        androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    }
    val fallbackBackground = if (isEInkMode) {
        AuroraTheme.colors.background
    } else if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFF121212)
    } else {
        androidx.compose.ui.graphics.Color.White
    }
    val themeModeTextColor = parseReaderColor(state.readerSettings.textColor)
        .takeIf { state.readerSettings.textColor?.isNotBlank() == true }
        ?: fallbackTextColor
    val themeModeBackground = parseReaderColor(state.readerSettings.backgroundColor)
        .takeIf { state.readerSettings.backgroundColor?.isNotBlank() == true }
        ?: fallbackBackground
    val textColor = when {
        isEInkMode -> AuroraTheme.colors.textPrimary
        isBackgroundMode -> backgroundModeTextColor
        else -> themeModeTextColor
    }
    val chapterTitleTextColor = textColor
    val textBackground = when {
        isEInkMode -> AuroraTheme.colors.background
        isBackgroundMode -> backgroundModeBaseColor
        else -> themeModeBackground
    }

    SideEffect {
        NovelReaderBackdropSession.update(textBackground)
    }

    LaunchedEffect(
        isBackgroundMode,
        isEInkMode,
        backgroundSource,
        customBackgroundPath,
        customBackgroundExists,
    ) {
        if (isBackgroundMode &&
            !isEInkMode &&
            backgroundSource == NovelReaderBackgroundSource.CUSTOM &&
            customBackgroundPath.isNotBlank() &&
            !customBackgroundExists
        ) {
            Toast.makeText(context, missingCustomBackgroundMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val readerFontCatalog = remember(context) {
        buildNovelReaderFontCatalog(context)
    }
    val selectedReaderFont = remember(state.readerSettings.fontFamily, readerFontCatalog) {
        resolveNovelReaderSelectedFont(
            fonts = readerFontCatalog,
            selectedFontId = state.readerSettings.fontFamily,
        )
    }
    val composeTypeface = remember(
        selectedReaderFont.id,
        state.readerSettings.forceBoldText,
        state.readerSettings.forceItalicText,
        context,
    ) {
        loadNovelReaderTypeface(
            context = context,
            font = selectedReaderFont,
            forceBoldText = state.readerSettings.forceBoldText,
            forceItalicText = state.readerSettings.forceItalicText,
        )
    }
    val composeFontFamily = remember(selectedReaderFont.id, composeTypeface) {
        resolveNovelReaderComposeFontFamily(
            font = selectedReaderFont,
            typeface = composeTypeface,
        )
    }
    val chapterTitleTypeface = remember(
        context,
        state.readerSettings.forceBoldText,
        state.readerSettings.forceItalicText,
    ) {
        novelReaderBuiltInFonts.firstOrNull { it.id == "domine" }?.let { font ->
            loadNovelReaderTypeface(
                context = context,
                font = font,
                forceBoldText = state.readerSettings.forceBoldText,
                forceItalicText = state.readerSettings.forceItalicText,
            )
        }
    }
    val chapterTitleFontFamily = remember {
        novelReaderBuiltInFonts.firstOrNull { it.id == "domine" }?.fontResId?.let { FontFamily(Font(it)) }
    }
    val paragraphSpacing = remember(state.readerSettings.paragraphSpacing) {
        resolveParagraphSpacingDp(state.readerSettings.paragraphSpacing)
    }
    val initialNativeReaderIndex = remember(
        state.lastSavedIndex,
        state.lastSavedPageReaderProgress,
        state.contentBlocks.size,
    ) {
        resolveInitialNativeReaderIndex(
            nativeLastSavedIndex = state.lastSavedIndex,
            savedPageReaderProgress = state.lastSavedPageReaderProgress,
            itemCount = state.contentBlocks.size,
        )
    }
    // Keyed by the novel while book mode is active: over a book the chapter anchor follows the
    // text, so keying the list state on it would recreate the list — and reset the scroll to the
    // book start — every time the reader crosses into another chapter and updateContent re-runs.
    val textListState = key(if (isBookMode) state.novel.id else state.chapter.id) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialNativeReaderIndex
                .coerceIn(0, (state.contentBlocks.lastIndex).coerceAtLeast(0)),
            initialFirstVisibleItemScrollOffset = if (state.lastSavedPageReaderProgress != null) {
                0
            } else {
                state.lastSavedScrollOffsetPx.coerceAtLeast(0)
            },
        )
    }

    // РџРѕР»СѓС‡Р°РµРј СЂР°Р·РјРµСЂС‹ system bars
    val view = LocalView.current
    val density = LocalDensity.current
    val rootInsets = ViewCompat.getRootWindowInsets(view)
    val statusBarHeight = rootInsets
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
        ?.top
        ?: rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
        ?: 0
    val navigationBarHeight = rootInsets
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        ?.bottom
        ?: rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
        ?: 0

    // AppBar height (~64dp + status bar).
    val appBarHeight = with(density) { (64.dp + statusBarHeight.toDp()).toPx().toInt() }
    // Р’С‹СЃРѕС‚Р° Bottom bar (~80dp + navigation bar)
    val bottomBarHeight = with(density) { (80.dp + navigationBarHeight.toDp()).toPx().toInt() }
    val statusBarTopPadding = with(density) { statusBarHeight.toDp() }
    val volumeScrollStepPx = with(density) { (configuration.screenHeightDp.dp * 0.25f).toPx() }
    val baseContentPadding = MaterialTheme.padding.small
    val contentPaddingPx = with(density) {
        resolveReaderContentPaddingPx(
            showReaderUi = showReaderUi,
            basePaddingPx = baseContentPadding.roundToPx(),
        ).toDp()
    }
    val ttsScrollTopPadding = resolveNovelReaderTtsScrollTopPadding(
        hasActiveTtsSession = state.ttsUiState.activeSession != null,
        statusBarTopPadding = statusBarTopPadding,
    )
    val scrollContentBlocks = remember(state.chapter.id, state.contentBlocks) {
        state.contentBlocks.takeIf { it.isNotEmpty() }
            ?: state.textBlocks.map { NovelReaderScreenModel.ContentBlock.Text(it) }
    }
    val showPageChapterTitle = state.readerSettings.showPageChapterTitle
    val pageReaderTextBlocks = remember(state.chapter.id, scrollContentBlocks, showPageChapterTitle) {
        stripPageReaderChapterTitleBlocks(
            textBlocks = scrollContentBlocks
                .mapIndexedNotNull { index, block ->
                    val text = (block as? NovelReaderScreenModel.ContentBlock.Text)?.text?.takeIf { it.isNotBlank() }
                        ?: return@mapIndexedNotNull null
                    PlainPageReaderTextBlock(
                        sourceBlockIndex = index,
                        text = text,
                    )
                },
            chapterTitle = resolvePageReaderChapterTitleForFiltering(
                showPageChapterTitle = showPageChapterTitle,
                chapterTitle = state.chapter.name,
            ),
        )
    }
    val richScrollBlocks = remember(state.chapter.id, state.richContentBlocks) {
        state.richContentBlocks
    }
    val pageReaderRichBlocks = remember(state.chapter.id, richScrollBlocks, showPageChapterTitle) {
        stripPageReaderChapterTitleRichBlocks(
            richBlocks = richScrollBlocks.withIndex().toList(),
            chapterTitle = resolvePageReaderChapterTitleForFiltering(
                showPageChapterTitle = showPageChapterTitle,
                chapterTitle = state.chapter.name,
            ),
        )
    }
    val shouldPaginatePageReader = shouldPaginateForPageReader(
        pageReaderEnabled = state.readerSettings.pageReader,
        contentBlocksCount = state.contentBlocks.size,
    )
    val pageReaderLayoutTextAlign = remember(
        state.readerSettings.textAlign,
        state.readerSettings.preserveSourceTextAlignInNative,
    ) {
        resolvePageReaderLayoutTextAlign(
            globalTextAlign = state.readerSettings.textAlign,
            preserveSourceTextAlignInNative = state.readerSettings.preserveSourceTextAlignInNative,
        )
    }
    val novelSpreadMinWidthPx = with(density) { NOVEL_SPREAD_MIN_WIDTH_DP.dp.roundToPx() }
    val novelSpreadColumns = resolveNovelSpreadColumns(
        twoPageLandscapeEnabled = state.readerSettings.twoPageLandscape,
        viewportWidthPx = pageViewportSize.width,
        viewportHeightPx = pageViewportSize.height,
        minSpreadWidthPx = novelSpreadMinWidthPx,
    )
    val pageReaderPages: List<List<PlainPageSlice>> = remember(
        state.chapter.id,
        pageReaderTextBlocks,
        showPageChapterTitle,
        shouldPaginatePageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        pageReaderLayoutTextAlign,
        composeTypeface,
        chapterTitleTypeface,
        pageViewportSize,
        contentPaddingPx,
        statusBarTopPadding,
        novelSpreadColumns,
    ) {
        if (!shouldPaginatePageReader || pageReaderTextBlocks.isEmpty()) {
            emptyList()
        } else {
            val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
            val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
            val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
            val bookBottomInsetPx = with(density) {
                resolveNovelPageReaderBookBottomInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val pageFitSafetyPx = with(density) {
                resolveNovelPageReaderPageFitSafetyInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val verticalPaddingPx = topPaddingPx +
                bottomPaddingPx +
                bookBottomInsetPx +
                pageFitSafetyPx +
                navigationBarHeight
            // The margin is the breathing room around the whole spread, not doubled per column: a
            // spread column's width is the full content area (screen minus margins) split evenly,
            // with a thin gap between the two columns rather than each getting its own margin.
            val contentWidthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1)
            val spreadColumnWidthPx = if (novelSpreadColumns > 1) {
                val gapPx = with(density) { NOVEL_SPREAD_COLUMN_GAP_DP.dp.roundToPx() }
                ((contentWidthPx - gapPx) / novelSpreadColumns).coerceAtLeast(1)
            } else {
                contentWidthPx
            }
            paginatePlainPageBlocks(
                textBlocks = pageReaderTextBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = spreadColumnWidthPx,
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                chapterTitle = if (showPageChapterTitle) state.chapter.name else null,
            )
        }
    }
    val shouldPaginateRichForPageReader = shouldUseRichNativePageRenderer(
        richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
        pageReaderEnabled = shouldPaginatePageReader,
        bionicReadingEnabled = state.readerSettings.bionicReading,
        richContentBlocks = state.richContentBlocks,
        richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
    )
    val richPageReaderPagination = remember(
        state.chapter.id,
        pageReaderRichBlocks,
        shouldPaginateRichForPageReader,
        showPageChapterTitle,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        pageReaderLayoutTextAlign,
        composeTypeface,
        chapterTitleTypeface,
        pageViewportSize,
        contentPaddingPx,
        statusBarTopPadding,
        novelSpreadColumns,
    ) {
        if (!shouldPaginateRichForPageReader) {
            MixedRichPagePagination(blockTexts = emptyList(), pages = emptyList())
        } else {
            val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
            val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
            val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
            val bookBottomInsetPx = with(density) {
                resolveNovelPageReaderBookBottomInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val pageFitSafetyPx = with(density) {
                resolveNovelPageReaderPageFitSafetyInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val verticalPaddingPx = topPaddingPx +
                bottomPaddingPx +
                bookBottomInsetPx +
                pageFitSafetyPx +
                navigationBarHeight
            val contentWidthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1)
            val spreadColumnWidthPx = if (novelSpreadColumns > 1) {
                val gapPx = with(density) { NOVEL_SPREAD_COLUMN_GAP_DP.dp.roundToPx() }
                ((contentWidthPx - gapPx) / novelSpreadColumns).coerceAtLeast(1)
            } else {
                contentWidthPx
            }
            paginateMixedRichPageBlocks(
                richBlocks = pageReaderRichBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = spreadColumnWidthPx,
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                chapterTitle = if (showPageChapterTitle) state.chapter.name else null,
            )
        }
    }
    val richPageReaderBlockTexts = richPageReaderPagination.blockTexts
    val richPageReaderPages = richPageReaderPagination.pages
    val usePageReader = shouldPaginatePageReader &&
        (
            pageReaderPages.isNotEmpty() || richPageReaderPages.isNotEmpty()
            )
    val useRichPageReader = usePageReader && richPageReaderPages.isNotEmpty()
    val pageReaderCharacterCounts = remember(
        useRichPageReader,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
    ) {
        if (useRichPageReader) {
            richPageReaderPages.map { page ->
                richPageReaderCharacterCount(page, richPageReaderBlockTexts)
            }
        } else {
            pageReaderPages.map { page ->
                plainPageReaderCharacterCount(page, pageReaderTextBlocks)
            }
        }
    }
    val pageReaderContentPages = remember(
        useRichPageReader,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        showPageChapterTitle,
    ) {
        normalizePageReaderContentPages(
            useRichPageReader = useRichPageReader,
            plainPages = pageReaderPages,
            richPages = richPageReaderPages,
            plainTextBlocks = pageReaderTextBlocks,
            richBlockTexts = richPageReaderBlockTexts,
            paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
            forceParagraphIndent = state.readerSettings.forceParagraphIndent,
            chapterTitle = if (showPageChapterTitle) state.chapter.name else null,
        )
    }
    val activePageTransitionStyle = remember(state.readerSettings.pageTransitionStyle) {
        resolveActivePageTransitionStyle(
            requestedStyle = state.readerSettings.pageTransitionStyle,
            pageTurnRendererSupported = true,
            isEInkMode = isEInkMode,
        )
    }
    val pageReaderRendererRoute = remember(usePageReader, activePageTransitionStyle) {
        resolvePageReaderRendererRoute(
            usePageReader = usePageReader,
            activeStyle = activePageTransitionStyle,
        )
    }
    val pageReaderItemsCount = pageReaderContentPages.size
    // Every index-based consumer below (TTS, auto-scroll, boundary pages, progress %) keeps
    // addressing single-column pages via pageReaderItemsCount unmodified; only the pager/page-turn
    // slot count and virtual-index math are adjusted, so a spread slot pairs pages
    // [n*columns, n*columns + columns - 1] without touching any of those consumers' own indexing.
    val pageReaderSpreadSlotCount = resolveSpreadSlotCount(pageReaderItemsCount, novelSpreadColumns)
    // The pager only needs an extra virtual page before/after the chapter while the intermediate
    // "next/previous chapter" placeholder is shown. Seamless chapter transitions hide that
    // placeholder, so the extra slots must not exist either: otherwise the edge page falls back to
    // clamped content and the last page of the chapter is rendered twice.
    val composePagerBoundaryPagesEnabled = !seamlessChapterTransitionEnabled
    val composePagerHasPreviousChapter = state.previousChapterId != null && composePagerBoundaryPagesEnabled
    val composePagerHasNextChapter = state.nextChapterId != null && composePagerBoundaryPagesEnabled
    val composePagerVirtualPageCount = remember(
        pageReaderSpreadSlotCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
    ) {
        resolveComposePagerVirtualPageCount(
            contentPageCount = pageReaderSpreadSlotCount,
            hasPreviousChapter = composePagerHasPreviousChapter,
            hasNextChapter = composePagerHasNextChapter,
        )
    }
    val pageReaderChapterHandoffTarget = remember(state.chapter.id) {
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff()
    }
    val useRichNativeScroll = shouldUseRichNativeScrollRenderer(
        richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
        showWebView = showWebView,
        usePageReader = usePageReader,
        bionicReadingEnabled = state.readerSettings.bionicReading,
        richContentBlocks = richScrollBlocks,
        richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
    )
    val nativeScrollItemsCount = if (useRichNativeScroll) richScrollBlocks.size else scrollContentBlocks.size
    // Book mode streams the whole novel into one document, so the per-chapter content blocks stay
    // empty and readiness has to come from the book session instead. Otherwise auto-scroll and the
    // reader chrome wait forever for a chapter payload that book mode never loads.
    // Surface published by the mounted book renderer, see NovelBookScrollSurface. Declared here,
    // above auto-scroll readiness, because readiness has to know whether the book renderer is
    // mounted: book mode has no chapter WebView to ask.
    val bookContentHandle = remember(state.novel.id) { NovelBookContentHandle() }
    val autoScrollContentReady = when {
        // `webViewPageReadyForAutoScroll` is only ever set by the chapter WebView, which book mode
        // never mounts, so requiring it here kept readiness false forever and auto-scroll could
        // never evaluate the end of the document.
        isBookMode -> bookContentHandle.isReady
        showWebView -> webViewPageReadyForAutoScroll && scrollContentBlocks.isNotEmpty()
        else -> scrollContentBlocks.isNotEmpty() || richScrollBlocks.isNotEmpty()
    }
    val autoScrollHasRenderableItems = when {
        // Same reason: `webViewInstance` is the chapter WebView and stays null over a book. What is
        // renderable there is the book surface, or the native list that hosts the book sections.
        isBookMode -> bookContentHandle.hasRenderableItems
        showWebView -> webViewInstance != null
        usePageReader -> pageReaderItemsCount > 0
        else -> nativeScrollItemsCount > 0
    }
    val initialContentPage = resolveInitialPageReaderPage(
        savedPageReaderProgress = state.lastSavedPageReaderProgress,
        legacyLastSavedIndex = state.lastSavedIndex,
        pageCount = pageReaderItemsCount.coerceAtLeast(1),
        chapterHandoffTarget = pageReaderChapterHandoffTarget,
    )
    val initialContentSpreadSlot = resolveSpreadSlotForPageIndex(initialContentPage, novelSpreadColumns)
    val initialPagerPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
        resolveComposePagerVirtualPageIndex(
            actualPageIndex = initialContentSpreadSlot,
            hasPreviousChapter = composePagerHasPreviousChapter,
        )
    } else {
        initialContentSpreadSlot
    }
    val pagerState = key(state.chapter.id) {
        rememberPagerState(
            initialPage = initialPagerPage,
            pageCount = {
                if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                    composePagerVirtualPageCount.coerceAtLeast(1)
                } else {
                    pageReaderSpreadSlotCount.coerceAtLeast(1)
                }
            },
        )
    }
    val pageReaderTtsNavigationAdapter = remember(
        pagerState,
        pageReaderRendererRoute,
        composePagerHasPreviousChapter,
        novelSpreadColumns,
    ) {
        PageReaderTtsNavigationAdapter(
            navigator = object : PageReaderTtsNavigator {
                override suspend fun scrollToPage(pageIndex: Int) {
                    val spreadSlot = resolveSpreadSlotForPageIndex(pageIndex, novelSpreadColumns)
                    val targetPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        resolveComposePagerVirtualPageIndex(
                            actualPageIndex = spreadSlot,
                            hasPreviousChapter = composePagerHasPreviousChapter,
                        )
                    } else {
                        spreadSlot
                    }.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                    pagerState.scrollToPage(targetPage)
                }
            },
        )
    }
    val nativeScrollTtsNavigationAdapter = remember(textListState) {
        NativeScrollTtsNavigationAdapter(
            navigator = object : NativeScrollTtsNavigator {
                override suspend fun scrollToBlock(blockIndex: Int, scrollOffsetPx: Int) {
                    textListState.scrollToItem(blockIndex, scrollOffsetPx)
                }
            },
        )
    }
    // Book mode DOM work runs against the renderer's own WebView (NovelBookReader). The legacy
    // chapter-WebView path below was dead: in book mode that WebView is never mounted
    // (shouldStartInWebView returns false), so commands and relocate bridges targeting it never
    // executed and the position only ever arrived through NovelBookEngine/onBookEngineLocationChanged
    // (scrolled) or the native list relocation.
    // Book mode is renderer independent: the reader settings pick the book renderer, and the WebView
    // adapter only has to switch the document's flow. "Pages" therefore works in book mode too, over
    // the same spine, sections and progress as the scrolled flow.
    // The renderer choice is derived from the reader settings once, deterministically, so the
    // content branch and the chrome never disagree about which renderer is mounted. The book host
    // publishes its window data through the handle, but the mount decision must not depend on a
    // handle field that is written later in composition (that race left the book showing only the
    // background on its first frame).
    val bookRendererDecision = remember(
        state.readerSettings.pageReader,
        state.readerSettings.richNativeRendererExperimental,
        state.readerSettings.bionicReading,
        state.readerSettings.customCSS,
        state.readerSettings.customJS,
        state.richContentUnsupportedFeaturesDetected,
    ) {
        resolveNovelBookRendererDecision(
            pageReaderEnabled = state.readerSettings.pageReader,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            bionicReadingEnabled = state.readerSettings.bionicReading,
            customStylesPresent = state.readerSettings.customCSS.isNotBlank() ||
                state.readerSettings.customJS.isNotBlank(),
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
        )
    }
    val useNativeBookScroll = isBookMode && !bookRendererDecision.renderer.usesWebView
    val webViewTtsNavigationAdapter = remember(state.chapter.id, scrollContentBlocks.size) {
        WebViewTtsNavigationAdapter(
            navigator = object : WebViewTtsNavigator {
                override suspend fun evaluateJavascript(script: String): String? {
                    val view = webViewInstance ?: return null
                    if (!view.settings.javaScriptEnabled) return null
                    return suspendCancellableCoroutine { continuation ->
                        view.post {
                            view.evaluateJavascript(script) { result ->
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            }
                        }
                    }
                }
            },
            totalBlocks = scrollContentBlocks.size.coerceAtLeast(1),
        )
    }
    // Book TTS state is owned by NovelBookContentHost and published through the handle.
    val bookTtsBlockAnchor = bookContentHandle.ttsBlockAnchor
    val bookTtsNavigationAdapter = bookContentHandle.ttsNavigationAdapter
    SideEffect {
        pageReaderTtsNavigationAdapter.hashCode()
        nativeScrollTtsNavigationAdapter.hashCode()
        webViewTtsNavigationAdapter.hashCode()
        bookTtsNavigationAdapter?.hashCode()
    }
    // The initial page of a paged chapter is applied exactly once. Re-running this effect whenever
    // one of its keys changes (content re-measure after a settings change, boundary-page flags
    // flipping, or the book navigation updating while crossing a chapter boundary) would yank the
    // reader back to the saved position mid-reading: sometimes a few pages, sometimes to the
    // chapter start, and in book mode into the previous chapter's boundary pages.
    val appliedPagerRestoreChapterId = remember { longArrayOf(state.chapter.id) }
    LaunchedEffect(
        state.chapter.id,
        pageReaderRendererRoute,
        pageReaderItemsCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
        initialPagerPage,
    ) {
        if (appliedPagerRestoreChapterId[0] == state.chapter.id) return@LaunchedEffect
        // Book mode is one continuous document: the current chapter changes while reading and the
        // pager position must never be reset under the reader.
        if (isBookMode) {
            appliedPagerRestoreChapterId[0] = state.chapter.id
            return@LaunchedEffect
        }
        // Retry while the content has not been measured yet instead of marking the restore done:
        // the pager may have mounted before the page list arrived.
        if (pageReaderItemsCount <= 0) return@LaunchedEffect
        appliedPagerRestoreChapterId[0] = state.chapter.id
        if (pagerState.currentPage != initialPagerPage) {
            pagerState.scrollToPage(initialPagerPage)
        }
    }
    // A seamless in-place chapter switch reuses this composition, so the scrolled reader keeps the
    // lazy list state of the previous chapter and stays near its end instead of jumping to the new
    // chapter's saved position. The pager route above resets itself the same way.
    val appliedNativeScrollRestoreChapterId = remember { longArrayOf(state.chapter.id) }
    LaunchedEffect(state.chapter.id, nativeScrollItemsCount) {
        if (appliedNativeScrollRestoreChapterId[0] == state.chapter.id) return@LaunchedEffect
        // Book mode is one continuous document: the current chapter changes while reading and the
        // list position must never be reset under the reader.
        if (isBookMode) {
            appliedNativeScrollRestoreChapterId[0] = state.chapter.id
            return@LaunchedEffect
        }
        if (nativeScrollItemsCount <= 0) return@LaunchedEffect
        appliedNativeScrollRestoreChapterId[0] = state.chapter.id
        textListState.scrollToItem(
            initialNativeReaderIndex.coerceIn(0, (nativeScrollItemsCount - 1).coerceAtLeast(0)),
            if (state.lastSavedPageReaderProgress != null) {
                0
            } else {
                state.lastSavedScrollOffsetPx.coerceAtLeast(0)
            },
        )
    }
    var pageTurnCurrentPage by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableIntStateOf(initialPagerPage)
    }
    var pageTurnRequestedPage by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableIntStateOf(-1)
    }
    var pageTurnChapterNavigationRequest by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableStateOf<PageTurnChapterNavigationRequest?>(null)
    }
    var pageTurnChapterNavigationRequestToken by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableStateOf(0L)
    }
    val pageReaderProgressPageIndex by remember(
        pageReaderRendererRoute,
        pagerState.currentPage,
        pageTurnCurrentPage,
        composePagerHasPreviousChapter,
        pageReaderSpreadSlotCount,
        pageReaderItemsCount,
        novelSpreadColumns,
    ) {
        derivedStateOf {
            // pageTurnCurrentPage is already a real page index (PageTurnPageRenderer resolves it
            // itself before reporting), so only the compose-pager route's slot index needs
            // expanding back to the first real page of that slot.
            val slotOrRealIndex = resolvePageReaderCurrentPage(
                pageReaderRendererRoute = pageReaderRendererRoute,
                pagerCurrentPage = pagerState.currentPage,
                pageTurnCurrentPage = pageTurnCurrentPage,
                composePagerContentPageCount = pageReaderSpreadSlotCount,
                composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                pageTurnContentPageCount = pageReaderItemsCount,
                pageTurnHasPreviousChapter = composePagerHasPreviousChapter,
            )
            if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                resolveSpreadSlotFirstPageIndex(slotOrRealIndex, novelSpreadColumns)
            } else {
                slotOrRealIndex
            }
        }
    }
    var isInitialPositionRestored by remember(state.chapter.id) {
        mutableStateOf(false)
    }
    LaunchedEffect(state.chapter.id, initialContentPage, pageReaderProgressPageIndex) {
        if (pageReaderProgressPageIndex == initialContentPage) {
            isInitialPositionRestored = true
        }
    }
    val latestPageReaderProgressPageIndex by rememberUpdatedState(pageReaderProgressPageIndex)
    val latestPageReaderItemsCount by rememberUpdatedState(pageReaderItemsCount)
    val pageReaderTtsPosition = remember(
        usePageReader,
        useRichPageReader,
        pageReaderProgressPageIndex,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
    ) {
        if (!usePageReader) {
            null
        } else {
            val blockTexts = if (useRichPageReader) {
                buildSourceIndexedPageReaderTextList(
                    richPageReaderBlockTexts.map {
                        PlainPageReaderTextBlock(
                            sourceBlockIndex = it.sourceBlockIndex,
                            text = it.text.text,
                        )
                    },
                )
            } else {
                buildSourceIndexedPageReaderTextList(pageReaderTextBlocks)
            }
            val pages = if (useRichPageReader) {
                richPageReaderPages.map { page ->
                    page.filterIsInstance<RichPageSlice.Text>().map { slice ->
                        NovelTtsPageSlice(
                            blockIndex = slice.blockIndex,
                            start = slice.range.start,
                            endExclusive = slice.range.endExclusive,
                        )
                    }
                }
            } else {
                pageReaderPages.map { page ->
                    page.map { slice ->
                        NovelTtsPageSlice(
                            blockIndex = slice.blockIndex,
                            start = slice.range.start,
                            endExclusive = slice.range.endExclusive,
                        )
                    }
                }
            }
            NovelTtsPageReaderPosition(
                pageIndex = pageReaderProgressPageIndex,
                blockTexts = blockTexts,
                pages = pages,
            )
        }
    }
    val currentTtsBlockIndex by remember(
        showWebView,
        usePageReader,
        pageReaderProgressPageIndex,
        pageReaderPages,
        richPageReaderPages,
        textListState.firstVisibleItemIndex,
        scrollContentBlocks.size,
        richScrollBlocks.size,
        webProgressPercent,
    ) {
        derivedStateOf {
            when {
                showWebView -> {
                    val targetBlockCount = scrollContentBlocks.size.coerceAtLeast(1)
                    (((webProgressPercent.coerceIn(0, 100) / 100f) * (targetBlockCount - 1)).roundToInt())
                        .coerceIn(0, targetBlockCount - 1)
                }
                usePageReader -> {
                    if (useRichPageReader) {
                        richPageReaderPages
                            .getOrNull(pageReaderProgressPageIndex)
                            ?.filterIsInstance<RichPageSlice.Text>()
                            ?.firstOrNull()
                            ?.blockIndex
                            ?: 0
                    } else {
                        pageReaderPages
                            .getOrNull(pageReaderProgressPageIndex)
                            ?.firstOrNull()
                            ?.blockIndex
                            ?: 0
                    }
                }
                useRichNativeScroll -> textListState.firstVisibleItemIndex.coerceIn(
                    0,
                    richScrollBlocks.lastIndex.coerceAtLeast(0),
                )
                else -> textListState.firstVisibleItemIndex.coerceIn(0, scrollContentBlocks.lastIndex.coerceAtLeast(0))
            }
        }
    }
    val currentTtsStartRequest by remember(
        currentTtsBlockIndex,
        pageReaderTtsPosition,
        usePageReader,
    ) {
        derivedStateOf {
            NovelTtsPlaybackStartRequest(
                fallbackBlockIndex = currentTtsBlockIndex,
                pageReaderPosition = if (usePageReader) pageReaderTtsPosition else null,
            )
        }
    }
    LaunchedEffect(
        state.chapter.id,
        state.nextChapterId,
        state.ttsUiState.pendingChapterHandoffId,
        state.ttsUiState.activeSession?.chapterId,
    ) {
        val targetChapterId = state.ttsUiState.pendingChapterHandoffId
            ?: resolveTtsAutoAdvancedChapterNavigationTarget(
                currentChapterId = state.chapter.id,
                activeTtsChapterId = state.ttsUiState.activeSession?.chapterId,
                nextChapterId = state.nextChapterId,
            )
            ?: return@LaunchedEffect
        if (requestedTtsChapterSyncTarget == targetChapterId) return@LaunchedEffect
        requestedTtsChapterSyncTarget = targetChapterId
        // The book already holds the next chapter. Opening it here reloaded the whole reader in the
        // middle of playback - the screen flashed, the document was rebuilt and the voice lost its
        // place - to arrive at markup that was on screen already. Over a book the handoff is only a
        // change of the spoken chapter; the follow-along anchor moves the reader across the border.
        if (isBookMode) return@LaunchedEffect
        NovelReaderTtsChapterHandoffPolicy.markPendingRestore(targetChapterId)
        webViewInstance?.clearFocus()
        onOpenNextChapter?.invoke(targetChapterId)
    }
    val activePageReaderTtsAnchors = remember(
        usePageReader,
        pageReaderTtsPosition,
        state.ttsUiState.activeSession?.model,
    ) {
        val sessionModel = state.ttsUiState.activeSession?.model
        val position = pageReaderTtsPosition
        if (!usePageReader || sessionModel == null || position == null) {
            emptyMap()
        } else {
            resolvePlainPageReaderTtsAnchors(
                textBlocks = position.blockTexts,
                pages = position.pages,
                chapterModel = sessionModel,
            )
        }
    }
    // Also keyed on the spoken block: a long utterance can walk several blocks, and keying only on
    // the utterance id left follow-along standing on the first one until the voice moved on.
    LaunchedEffect(
        state.ttsUiState.activeSession?.utterance?.id,
        state.ttsUiState.activeSourceBlockIndex,
        state.readerSettings.ttsFollowAlong,
        showWebView,
        usePageReader,
        pageReaderProgressPageIndex,
        activePageReaderTtsAnchors,
        isBookMode,
        bookContentHandle.surface,
    ) {
        if (!state.readerSettings.ttsFollowAlong) return@LaunchedEffect
        val session = state.ttsUiState.activeSession ?: return@LaunchedEffect
        val segment = session.model.findSegmentForUtterance(session.utterance.id) ?: return@LaunchedEffect
        pendingProgrammaticTtsBlockIndex = segment.sourceBlockIndex
        suppressManualTtsPauseUntilMs = SystemClock.elapsedRealtime() + 1_500L
        when {
            // Checked first: over a book `showWebView` is false and `usePageReader` is irrelevant,
            // so follow-along used to fall into the native branch and scroll an empty chapter list.
            isBookMode -> bookTtsNavigationAdapter?.syncToSegment(segment)
            showWebView -> webViewTtsNavigationAdapter.syncToSegment(segment)
            usePageReader -> {
                val anchor = activePageReaderTtsAnchors[session.utterance.id]
                val targetPage = when {
                    anchor == null -> segment.pageCandidates.firstOrNull()
                    anchor.pageCandidates.contains(pageReaderProgressPageIndex) -> pageReaderProgressPageIndex
                    else -> anchor.pageIndex
                } ?: return@LaunchedEffect
                pageReaderTtsNavigationAdapter.restorePosition(
                    NovelTtsNavigationAnchor(pageIndex = targetPage),
                )
            }
            else -> nativeScrollTtsNavigationAdapter.syncToSegment(segment)
        }
    }
    LaunchedEffect(currentTtsBlockIndex, pendingProgrammaticTtsBlockIndex, suppressManualTtsPauseUntilMs) {
        val pendingBlockIndex = pendingProgrammaticTtsBlockIndex ?: return@LaunchedEffect
        if (currentTtsBlockIndex == pendingBlockIndex ||
            SystemClock.elapsedRealtime() >= suppressManualTtsPauseUntilMs
        ) {
            pendingProgrammaticTtsBlockIndex = null
            suppressManualTtsPauseUntilMs = 0L
        }
    }
    val ttsHighlightState = remember(
        usePageReader,
        pageReaderProgressPageIndex,
        activePageReaderTtsAnchors,
        state.ttsUiState.activeSession?.utterance?.id,
        state.ttsUiState.activeSourceBlockIndex,
        state.ttsUiState.activeUtteranceText,
        state.ttsUiState.activeWordRange,
        state.ttsUiState.activeHighlightMode,
        bookTtsBlockAnchor,
    ) {
        val activeUtterance = state.ttsUiState.activeSession?.utterance
        val activePageAnchor = if (usePageReader) {
            activeUtterance?.id?.let(activePageReaderTtsAnchors::get)
        } else {
            null
        }
        NovelReaderTtsHighlightState(
            sourceBlockIndex = state.ttsUiState.activeSourceBlockIndex,
            utteranceText = state.ttsUiState.activeUtteranceText,
            wordRange = state.ttsUiState.activeWordRange,
            pageIndex = activePageAnchor?.pageCandidates
                ?.firstOrNull { it == pageReaderProgressPageIndex }
                ?: activePageAnchor?.pageIndex,
            blockTextStart = activePageAnchor?.blockTextStart ?: activeUtterance?.blockTextStart,
            blockTextEndExclusive = activePageAnchor?.blockTextEndExclusive ?: activeUtterance?.blockTextEndExclusive,
            mode = state.ttsUiState.activeHighlightMode,
            blockAnchor = bookTtsBlockAnchor,
        )
    }
    val ttsHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val readingProgressPercent by remember(
        showWebView,
        webProgressPercent,
        nativeScrollItemsCount,
        pageReaderItemsCount,
        pageReaderProgressPageIndex,
        textListState.firstVisibleItemIndex,
        textListState.canScrollForward,
        usePageReader,
        isBookMode,
        state.bookMode.bookProgressFraction,
    ) {
        derivedStateOf {
            when {
                // In book mode the reader never leaves the book, so progress, "time to end" and the
                // word counter all describe the whole novel instead of the section under the viewport.
                isBookMode -> {
                    (state.bookMode.bookProgressFraction * 100f).roundToInt().coerceIn(0, 100)
                }
                showWebView -> webProgressPercent
                usePageReader -> {
                    resolvePageReaderReadingProgressPercent(
                        pageIndex = pageReaderProgressPageIndex,
                        pageCount = pageReaderItemsCount,
                    )
                }
                nativeScrollItemsCount <= 0 -> 0
                !textListState.canScrollForward -> 100
                else -> {
                    (((textListState.firstVisibleItemIndex + 1).toFloat() / nativeScrollItemsCount.toFloat()) * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
                }
            }
        }
    }
    val totalWords = remember(state.chapter.id, pageReaderTextBlocks) {
        countNovelWords(pageReaderTextBlocks.map { it.text })
    }
    val readWords by remember(totalWords, readingProgressPercent) {
        derivedStateOf {
            estimateNovelReadWords(
                totalWords = totalWords,
                readingProgressPercent = readingProgressPercent,
            )
        }
    }
    LaunchedEffect(
        state.ttsUiState.isPlaying,
        state.readerSettings.ttsPauseOnManualNavigation,
        usePageReader,
        currentTtsBlockIndex,
        pageReaderProgressPageIndex,
        state.ttsUiState.activeSourceBlockIndex,
        state.ttsUiState.activeSession?.utterance?.id,
        activePageReaderTtsAnchors,
    ) {
        val activePageCandidates = if (usePageReader) {
            state.ttsUiState.activeSession
                ?.utterance
                ?.id
                ?.let(activePageReaderTtsAnchors::get)
                ?.pageCandidates
                ?.toSet()
        } else {
            null
        }
        val shouldPauseForManualNavigation = resolveShouldPauseTtsForManualNavigation(
            isPlaying = state.ttsUiState.isPlaying,
            pauseOnManualNavigation = state.readerSettings.ttsPauseOnManualNavigation,
            nowMs = SystemClock.elapsedRealtime(),
            suppressUntilMs = suppressManualTtsPauseUntilMs,
            usePageReader = usePageReader,
            currentBlockIndex = currentTtsBlockIndex,
            activeSourceBlockIndex = state.ttsUiState.activeSourceBlockIndex,
            currentPageIndex = if (usePageReader) pageReaderProgressPageIndex else null,
            activePageCandidates = activePageCandidates,
        )
        if (shouldPauseForManualNavigation) {
            onPauseTtsForManualNavigation(currentTtsStartRequest)
        }
    }
    var readingPaceState by remember(state.chapter.id) {
        mutableStateOf(NovelReaderReadingPaceState())
    }
    LaunchedEffect(state.chapter.id, readingProgressPercent) {
        readingPaceState = updateNovelReaderReadingPace(
            paceState = readingPaceState,
            readingProgressPercent = readingProgressPercent,
            timestampMs = SystemClock.elapsedRealtime(),
        )
    }
    val remainingMinutes = remember(readingPaceState, readingProgressPercent) {
        estimateNovelReaderRemainingMinutes(
            paceState = readingPaceState,
            readingProgressPercent = readingProgressPercent,
        )
    }
    val showBottomInfoOverlay = shouldShowBottomInfoOverlay(
        showReaderUi = showReaderUi,
        showBatteryAndTime = state.readerSettings.showBatteryAndTime,
        showKindleInfoBlock = state.readerSettings.showKindleInfoBlock,
        showTimeToEnd = state.readerSettings.showTimeToEnd,
        showWordCount = state.readerSettings.showWordCount,
    )
    val minVerticalChapterSwipeDistancePx = with(density) { 120.dp.toPx() }
    val verticalChapterSwipeHorizontalTolerancePx = with(density) { 20.dp.toPx() }
    val minVerticalChapterSwipeHoldDurationMillis = 180L

    LaunchedEffect(state.chapter.id) {
        if (state.readerSettings.autoScroll) {
            persistAutoScrollEnabledPreference(enabled = true)
        }
    }

    // РЈРїСЂР°РІР»РµРЅРёРµ System UI РґР»СЏ fullscreen СЂРµР¶РёРјР°
    LaunchedEffect(state.chapter.id, usePageReader) {
        onSetShowReaderUi(
            resolveReaderUiAfterChapterChange(
                currentShowReaderUi = showReaderUi,
                usePageReader = usePageReader,
            ),
        )
    }

    // Volume Buttons Handler
    val coroutineScope = rememberCoroutineScope()
    val latestShowReaderUi by rememberUpdatedState(showReaderUi)
    val latestTapToScrollEnabled by rememberUpdatedState(state.readerSettings.tapToScroll)
    val bookFlipPageAnimationDurationMillis = resolveBookFlipPageAnimationDurationMillis(
        transitionStyle = activePageTransitionStyle,
        animationSpeed = state.readerSettings.bookFlipAnimationSpeed,
    )

    // Chapter navigation and auto-scroll end handling live in a plain class with provider lambdas,
    // so the instance never captures a stale snapshot of the reader state across recompositions.
    val chapterNavigator = remember {
        NovelReaderChapterNavigator(
            state = { state },
            isBookMode = { isBookMode },
            webViewInstance = { webViewInstance },
            onOpenPreviousChapter = onOpenPreviousChapter,
            onOpenNextChapter = onOpenNextChapter,
            onPrepareAutoScrollHandoff = onPrepareAutoScrollHandoff,
            onCancelAutoScrollHandoff = onCancelAutoScrollHandoff,
            bookContentHandle = { bookContentHandle },
            showReaderUi = { latestShowReaderUi },
            autoScrollEnabled = { autoScrollEnabled },
            setAutoScrollEnabled = { autoScrollEnabled = it },
            autoScrollSpeed = { autoScrollSpeed },
            setAutoScrollEndStableFrames = { autoScrollEndStableFrames = it },
            autoScrollEndDwellActive = { autoScrollEndDwellActive },
            setAutoScrollEndDwellActive = { autoScrollEndDwellActive = it },
            setAutoScrollEndDwellRemainingSeconds = { autoScrollEndDwellRemainingSeconds = it },
            pageTurnChapterNavigationRequest = { pageTurnChapterNavigationRequest },
            setPageTurnChapterNavigationRequest = { pageTurnChapterNavigationRequest = it },
            pageTurnChapterNavigationRequestToken = { pageTurnChapterNavigationRequestToken },
            setPageTurnChapterNavigationRequestToken = { pageTurnChapterNavigationRequestToken = it },
        )
    }
    fun requestPageTurnChapterNavigation(direction: PageTurnChapterNavigationDirection) =
        chapterNavigator.requestPageTurnChapterNavigation(direction)

    fun openPreviousChapterFromReader() =
        chapterNavigator.openPreviousChapterFromReader()

    fun isAtEndOfBook(): Boolean =
        chapterNavigator.isAtEndOfBook()

    fun openNextChapterFromReader() =
        chapterNavigator.openNextChapterFromReader()

    fun handleAutoScrollChapterEnd() =
        chapterNavigator.handleAutoScrollChapterEnd()

    suspend fun handleAutoScrollStableChapterEndAfterDwell() =
        chapterNavigator.handleAutoScrollStableChapterEndAfterDwell()

    suspend fun moveBackwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        if (isBookMode) {
            // The book is one continuous document, so stepping backwards never leaves it and chapter
            // navigation must not kick in. The mounted book surface knows whether a step is a page
            // (paginated flow) or a viewport of scroll; before it is mounted there is nothing to move.
            val surface = bookContentHandle.surface ?: return
            if (surface.isPaginated()) {
                surface.step(forward = false)
            } else {
                surface.scrollBy(-volumeScrollStepPx.roundToInt())
            }
            return
        }
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(-1)) {
                webView.scrollBy(0, -volumeScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                openPreviousChapterFromReader()
            }
        } else if (usePageReader) {
            if (
                pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER &&
                activePageTransitionStyle == NovelPageTransitionStyle.CURL
            ) {
                // With the seamless transition there is no boundary "previous chapter" page, so on
                // the first page the curl has nowhere to go: open the adjacent chapter directly.
                if (
                    seamlessChapterTransitionEnabled &&
                    pageReaderItemsCount > 0 &&
                    pageReaderProgressPageIndex <= 0 &&
                    state.previousChapterId != null
                ) {
                    openPreviousChapterFromReader()
                } else {
                    requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.PREVIOUS)
                }
            } else {
                val currentSpreadSlot = resolveSpreadSlotForPageIndex(
                    pageReaderProgressPageIndex,
                    novelSpreadColumns,
                )
                val currentVirtualPage = resolveComposePagerVirtualPageIndex(
                    actualPageIndex = currentSpreadSlot,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                )
                if (currentVirtualPage > 0) {
                    val targetVirtualPage = currentVirtualPage - 1
                    val targetSpreadSlot = resolveComposePagerActualPageIndex(
                        currentPage = targetVirtualPage,
                        contentPageCount = pageReaderSpreadSlotCount,
                        hasPreviousChapter = composePagerHasPreviousChapter,
                    )
                    pageTurnCurrentPage = resolveSpreadSlotFirstPageIndex(targetSpreadSlot, novelSpreadColumns)
                    if (pageAnimationDurationMillis != null) {
                        pagerState.animateScrollToPage(
                            targetVirtualPage,
                            animationSpec = tween(durationMillis = pageAnimationDurationMillis),
                        )
                    } else {
                        pagerState.animateScrollToPage(targetVirtualPage)
                    }
                } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                    openPreviousChapterFromReader()
                }
            }
        } else if (textListState.canScrollBackward) {
            textListState.scrollBy(-volumeScrollStepPx)
        } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
            openPreviousChapterFromReader()
        }
    }

    suspend fun moveForwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        if (isBookMode) {
            val surface = bookContentHandle.surface ?: return
            if (surface.isPaginated()) {
                surface.step(forward = true)
            } else {
                surface.scrollBy(volumeScrollStepPx.roundToInt())
            }
            return
        }
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(1)) {
                webView.scrollBy(0, volumeScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                openNextChapterFromReader()
            }
        } else if (usePageReader) {
            if (
                pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER &&
                activePageTransitionStyle == NovelPageTransitionStyle.CURL
            ) {
                // With the seamless transition there is no boundary "next chapter" page, so on the
                // last page the curl has nowhere to go: open the adjacent chapter directly.
                if (
                    seamlessChapterTransitionEnabled &&
                    pageReaderItemsCount > 0 &&
                    pageReaderProgressPageIndex >= pageReaderItemsCount - 1 &&
                    state.nextChapterId != null
                ) {
                    openNextChapterFromReader()
                } else {
                    requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.NEXT)
                }
            } else {
                val currentSpreadSlot = resolveSpreadSlotForPageIndex(
                    pageReaderProgressPageIndex,
                    novelSpreadColumns,
                )
                val currentVirtualPage = resolveComposePagerVirtualPageIndex(
                    actualPageIndex = currentSpreadSlot,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                )
                val virtualLastPage = composePagerVirtualPageCount - 1
                if (currentVirtualPage < virtualLastPage) {
                    val targetVirtualPage = currentVirtualPage + 1
                    val targetSpreadSlot = resolveComposePagerActualPageIndex(
                        currentPage = targetVirtualPage,
                        contentPageCount = pageReaderSpreadSlotCount,
                        hasPreviousChapter = composePagerHasPreviousChapter,
                    )
                    pageTurnCurrentPage = resolveSpreadSlotFirstPageIndex(targetSpreadSlot, novelSpreadColumns)
                    if (pageAnimationDurationMillis != null) {
                        pagerState.animateScrollToPage(
                            targetVirtualPage,
                            animationSpec = tween(durationMillis = pageAnimationDurationMillis),
                        )
                    } else {
                        pagerState.animateScrollToPage(targetVirtualPage)
                    }
                } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                    openNextChapterFromReader()
                }
            }
        } else if (textListState.canScrollForward) {
            textListState.scrollBy(volumeScrollStepPx)
        } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
            openNextChapterFromReader()
        }
    }

    suspend fun moveBackwardByReaderAction() {
        moveBackwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
    }

    suspend fun moveForwardByReaderAction() {
        moveForwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
    }

    val latestCustomTapZonesEnabled by rememberUpdatedState(state.readerSettings.customTapZones)
    val latestTapZoneActions by rememberUpdatedState(
        parseNovelReaderTapZoneActions(state.readerSettings.tapZoneActions),
    )
    val latestReaderShortTapHandler by rememberUpdatedState<(Float, Float, Float, Float) -> Unit> {
            tapX,
            tapY,
            width,
            height,
        ->
        dispatchConfiguredReaderTapAction(
            tapX = tapX,
            tapY = tapY,
            width = width,
            height = height,
            customTapZonesEnabled = latestCustomTapZonesEnabled,
            tapZoneActions = latestTapZoneActions,
            tapToScrollEnabled = latestTapToScrollEnabled,
            onToggleUi = { onSetShowReaderUi(!latestShowReaderUi) },
            onBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
            onForward = { coroutineScope.launch { moveForwardByReaderAction() } },
            onNextChapter = { openNextChapterFromReader() },
            onPrevChapter = { openPreviousChapterFromReader() },
        )
    }

    fun handleVolumeKey(event: KeyEvent): Boolean {
        return when (
            resolveVolumeKeyAction(
                keyCode = event.keyCode,
                action = event.action,
                useVolumeButtons = state.readerSettings.useVolumeButtons,
                showReaderUi = { latestShowReaderUi },
            )
        ) {
            VolumeKeyAction.BACKWARD -> {
                coroutineScope.launch { moveBackwardByReaderAction() }
                true
            }
            VolumeKeyAction.FORWARD -> {
                coroutineScope.launch { moveForwardByReaderAction() }
                true
            }
            VolumeKeyAction.CONSUME -> true
            VolumeKeyAction.NONE -> false
        }
    }

    DisposableEffect(
        view,
        state.readerSettings.useVolumeButtons,
        usePageReader,
        showWebView,
        showReaderUi,
        pageReaderItemsCount,
        nativeScrollItemsCount,
    ) {
        val listener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            handleVolumeKey(event)
        }
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, _, event -> handleVolumeKey(event) }
        ViewCompat.addOnUnhandledKeyEventListener(view, listener)
        onDispose {
            view.setOnKeyListener(null)
            ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
        }
    }

    // The auto-scroll state machine lives outside the effect, so the touch cooldown, the speed ramp
    // and the sub-pixel remainder survive the effect restarting on a settings or surface change.
    val autoScrollController = remember { NovelAutoScrollController() }

    LaunchedEffect(
        autoScrollEnabled,
        autoScrollSpeed,
        state.readerSettings.autoScrollInterval,
        state.readerSettings.autoScrollAdaptiveDelay,
        usePageReader,
        showReaderUi,
        showWebView,
        webViewInstance,
        state.nextChapterId,
        state.readerSettings.autoScrollChapterEndBehavior,
        pageReaderItemsCount,
        autoScrollContentReady,
        autoScrollHasRenderableItems,
        hasCompletedInitialReaderLayout,
        isBookMode,
        bookContentHandle.surface,
    ) {
        if (!autoScrollEnabled) {
            autoScrollController.stop()
            // The WebView book runs auto-scroll as a rAF loop inside its document; the loop has no
            // other way to learn that the chrome stopped asking for frames.
            bookContentHandle.surface?.stopAutoScroll()
            return@LaunchedEffect
        }
        autoScrollController.start()
        // One target per surface, resolved once per effect run: every input that can change the
        // surface is already a key of this effect, so the loop below never branches per surface.
        val target: NovelAutoScrollTarget? = when {
            isBookMode && (bookContentHandle.surface != null || !useNativeBookScroll) ->
                bookContentHandle.surface?.let { surface ->
                    BookSurfaceAutoScrollTarget(
                        surface = surface,
                        hasSectionsLeft = {
                            // Over a compiled book the spine sections are blocks, not chapters, so
                            // "sections left" would report true far past the real end; whole-book
                            // progress is the honest "is there still text below" signal.
                            state.bookMode.bookProgressFraction < 1f
                        },
                    )
                }
            showWebView -> webViewInstance?.let { webView ->
                WebViewAutoScrollTarget(
                    webView = webView,
                    reachedProgressThreshold = { webAutoScrollNearEnd },
                )
            }
            usePageReader -> PageReaderAutoScrollTarget(
                currentPageIndex = { pageReaderProgressPageIndex },
                pageCount = { pageReaderItemsCount },
                characterCount = {
                    pageReaderCharacterCounts.getOrNull(pageReaderProgressPageIndex) ?: 0
                },
                onStepPage = {
                    moveForwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
                },
            )
            else -> LazyListAutoScrollTarget(
                listState = textListState,
                nearConfiguredEnd = {
                    val layoutInfo = textListState.layoutInfo
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                    state.readerSettings.autoScrollOffset > 0 &&
                        lastVisibleItem != null &&
                        lastVisibleItem.index >= nativeScrollItemsCount - 1 &&
                        lastVisibleItem.offset + lastVisibleItem.size <=
                        layoutInfo.viewportEndOffset + state.readerSettings.autoScrollOffset
                },
            )
        }
        while (isActive && autoScrollEnabled) {
            if (showReaderUi) {
                autoScrollController.pause()
                // Pausing must stop the in-document loop too: it would otherwise keep advancing the
                // viewport while the reader UI is visible and the chrome is not asking for frames.
                bookContentHandle.surface?.stopAutoScroll()
                delay(120)
                continue
            }
            autoScrollController.resume()

            // The surface this run belongs to is not mounted yet (book renderer or chapter WebView
            // still attaching). Every input that can produce it is a key of this effect, so idling
            // here is enough: the effect restarts once it exists.
            if (target == null) {
                autoScrollController.resetFrameState()
                autoScrollEndStableFrames = 0
                delay(120)
                continue
            }

            if (resolveAutoScrollPrefetchNeededByPercent(
                    progressPercent = readingProgressPercent,
                    behavior = state.readerSettings.autoScrollChapterEndBehavior,
                )
            ) {
                onRequestAutoScrollNextChapterPrefetch()
            }

            if (!autoScrollController.tickSpeedFactor(
                    nowNanos = System.nanoTime(),
                    delta = AUTO_SCROLL_SPEED_FACTOR_DELTA,
                )
            ) {
                delay(100)
                continue
            }
            speedFactor = autoScrollController.speedFactor

            if (target.isPaginated()) {
                autoScrollController.resetFrameState()
                autoScrollEndStableFrames = 0
                // Only the page reader counts characters; continuous-but-paginated surfaces report
                // 0, which keeps the delay at the plain interval exactly as before.
                val pageCharacterCount = target.pageDelayCharacterCount()
                delay(
                    autoScrollPageDelayMsForCharacterCount(
                        intervalSeconds = state.readerSettings.autoScrollInterval,
                        characterCount = pageCharacterCount,
                        adaptiveEnabled = state.readerSettings.autoScrollAdaptiveDelay &&
                            pageCharacterCount > 0,
                    ),
                )
                if (showReaderUi || !autoScrollEnabled) continue
                if (target.canScrollForward()) {
                    target.stepPage(forward = true)
                } else if (
                    autoScrollContentReady &&
                    hasCompletedInitialReaderLayout &&
                    autoScrollHasRenderableItems
                ) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
                continue
            }

            val stepPx = autoScrollController.frameStepPx(
                speed = autoScrollSpeed,
                frameTimeNanos = withFrameNanos { it },
            )
            if (stepPx == 0) continue
            val consumedPx = target.scrollBy(stepPx)
            val frameResult = autoScrollController.onScrolled(
                canScrollForward = target.canScrollForward(),
                scrollConsumedPx = consumedPx,
                isContentReady = autoScrollContentReady,
                hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                hasRenderableItems = autoScrollHasRenderableItems,
            )
            autoScrollEndStableFrames = autoScrollController.stableEndFrames
            if (frameResult == NovelAutoScrollFrameResult.ReachedEnd) {
                handleAutoScrollStableChapterEndAfterDwell()
            }
        }
    }

    val sourceManager = remember { Injekt.get<NovelSourceManager>() }
    val refererUrl = remember(sourceId) {
        (sourceManager.get(sourceId) as? NovelSiteSource)?.siteUrl
    }

    CompositionLocalProvider(LocalNovelReaderReferer provides refererUrl) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(textBackground)
                .onSizeChanged { size ->
                    pageViewportSize = size
                    if (size.width > 0 && size.height > 0) {
                        hasCompletedInitialReaderLayout = true
                    }
                }
                .pointerInput(autoScrollEnabled) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val touchNanos = System.nanoTime()
                        touchCooldownUntilNanos = touchNanos + AUTO_SCROLL_COOLDOWN_MS * 1_000_000L
                        // The controller owns the cooldown the auto-scroll loop reads now; the
                        // field stays in sync for the rest of the reader that still observes it.
                        autoScrollController.noteTouch(
                            nowNanos = touchNanos,
                            cooldownMs = AUTO_SCROLL_COOLDOWN_MS,
                        )
                    }
                },
        ) {
            if (
                shouldShowNovelAtmosphereBackground(
                    usePageReader = usePageReader,
                    activePageTransitionStyle = activePageTransitionStyle,
                )
            ) {
                NovelAtmosphereBackground(
                    backgroundColor = textBackground,
                    backgroundTexture = activeBackgroundTexture,
                    nativeTextureStrengthPercent = if (isBackgroundMode) {
                        0
                    } else {
                        state.readerSettings.nativeTextureStrengthPercent
                    },
                    oledEdgeGradient = activeOledEdgeGradient,
                    isDarkTheme = isDarkTheme,
                    pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                    pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                    backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                )
            }
            // Контент главы занимает весь экран; padding уже учтён в contentPadding.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!showWebView && (scrollContentBlocks.isNotEmpty() || richScrollBlocks.isNotEmpty())) {
                    val chapterImageModels = remember(scrollContentBlocks, richScrollBlocks, refererUrl) {
                        extractChapterImageModels(scrollContentBlocks.ifEmpty { richScrollBlocks }, refererUrl)
                    }
                    LaunchedEffect(state.chapter.id, chapterImageModels, pageReaderProgressPageIndex) {
                        if (chapterImageModels.isNotEmpty()) {
                            NovelChapterImagePrefetcher.prefetch(
                                context = context,
                                imageModels = chapterImageModels,
                                activeImageIndex = pageReaderProgressPageIndex,
                            )
                        }
                    }
                    // Track progress according to the active reader mode.
                    if (usePageReader) {
                        LaunchedEffect(pageReaderProgressPageIndex, pageReaderItemsCount, isInitialPositionRestored) {
                            reportReadingProgress(
                                pageReaderProgressPageIndex,
                                pageReaderItemsCount,
                                encodePageReaderProgress(
                                    index = pageReaderProgressPageIndex,
                                    totalItems = pageReaderItemsCount,
                                ),
                                flashDisplay = true,
                                isInitialPositionRestored = isInitialPositionRestored,
                            )
                        }
                        DisposableEffect(pagerState, state.chapter.id) {
                            onDispose {
                                val latestIndex = latestPageReaderProgressPageIndex
                                val latestTotal = latestPageReaderItemsCount.coerceAtLeast(1)
                                reportReadingProgress(
                                    latestIndex,
                                    latestTotal,
                                    encodePageReaderProgress(
                                        index = latestIndex,
                                        totalItems = latestTotal,
                                    ),
                                    isInitialPositionRestored = isInitialPositionRestored,
                                )
                            }
                        }
                    } else {
                        LaunchedEffect(
                            textListState.firstVisibleItemIndex,
                            textListState.canScrollForward,
                            nativeScrollItemsCount,
                        ) {
                            val (progressIndex, progressTotal) = resolveNativeScrollProgressForTracking(
                                firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                                textBlocksCount = nativeScrollItemsCount,
                                canScrollForward = textListState.canScrollForward,
                            )
                            reportReadingProgress(
                                progressIndex,
                                progressTotal,
                                encodeNativeScrollProgress(
                                    index = textListState.firstVisibleItemIndex,
                                    offsetPx = textListState.firstVisibleItemScrollOffset,
                                    totalItems = nativeScrollItemsCount,
                                ),
                            )
                        }
                        DisposableEffect(textListState, textListState.canScrollForward, nativeScrollItemsCount) {
                            onDispose {
                                val (progressIndex, progressTotal) = resolveNativeScrollProgressForTracking(
                                    firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                                    textBlocksCount = nativeScrollItemsCount,
                                    canScrollForward = textListState.canScrollForward,
                                )
                                reportReadingProgress(
                                    progressIndex,
                                    progressTotal,
                                    encodeNativeScrollProgress(
                                        index = textListState.firstVisibleItemIndex,
                                        offsetPx = textListState.firstVisibleItemScrollOffset,
                                        totalItems = nativeScrollItemsCount,
                                    ),
                                    flashDisplay = true,
                                )
                            }
                        }
                    }

                    // Page Reader Mode (РїРѕСЃС‚СЂР°РЅРёС‡РЅС‹Р№ СЂРµР¶РёРј)
                    val bookReaderPaddingPx = with(density) { 4.dp.roundToPx() }
                    val bookReaderMaxStatusInsetPx = with(density) { 16.dp.roundToPx() }
                    val bookReaderPaddingTop = resolveWebViewPaddingTopPx(
                        statusBarHeightPx = statusBarHeight,
                        showReaderUi = showReaderUi,
                        appBarHeightPx = appBarHeight,
                        basePaddingPx = bookReaderPaddingPx,
                        maxStatusBarInsetPx = bookReaderMaxStatusInsetPx,
                    )
                    val bookReaderPaddingBottom = resolveWebViewPaddingBottomPx(
                        navigationBarHeightPx = navigationBarHeight,
                        showReaderUi = showReaderUi,
                        bottomBarHeightPx = bottomBarHeight,
                        basePaddingPx = bookReaderPaddingPx,
                    )
                    val bookReaderPaddingHorizontal = with(density) {
                        state.readerSettings.margin.dp.roundToPx()
                    }
                    val bookReaderParagraphSpacingPx = with(density) {
                        state.readerSettings.paragraphSpacing.dp.roundToPx()
                    }
                    val bookReaderTextAlignCss = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                    val bookReaderFirstLineIndentCss = resolveWebViewFirstLineIndentCss(
                        forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                    )
                    val bookReaderTextShadowCss = resolveWebReaderTextShadowCss(
                        textShadowEnabled = state.readerSettings.textShadow,
                        textShadowColor = state.readerSettings.textShadowColor,
                        textShadowBlur = state.readerSettings.textShadowBlur,
                        textShadowX = state.readerSettings.textShadowX,
                        textShadowY = state.readerSettings.textShadowY,
                        textColor = textColor,
                        backgroundColor = textBackground,
                    )
                    val bookReaderSelectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                    val bookReaderBaseCss = buildWebReaderCssText(
                        fontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont),
                        paddingTop = bookReaderPaddingTop,
                        paddingBottom = bookReaderPaddingBottom,
                        paddingHorizontal = bookReaderPaddingHorizontal,
                        fontSizePx = state.readerSettings.fontSize,
                        lineHeightMultiplier = state.readerSettings.lineHeight,
                        paragraphSpacingPx = bookReaderParagraphSpacingPx,
                        textAlignCss = bookReaderTextAlignCss,
                        firstLineIndentCss = bookReaderFirstLineIndentCss,
                        textColorHex = colorToCssHex(textColor),
                        backgroundHex = colorToCssHex(textBackground),
                        appearanceMode = appearanceMode,
                        backgroundTexture = activeBackgroundTexture,
                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                        fontFamilyName = bookReaderSelectedFontFamily,
                        customCss = state.readerSettings.customCSS,
                        textShadowCss = bookReaderTextShadowCss,
                        forceBoldText = state.readerSettings.forceBoldText,
                        forceItalicText = state.readerSettings.forceItalicText,
                    )
                    val bookReaderCss = remember(bookReaderBaseCss) {
                        withNovelBookReaderContentOverrides(bookReaderBaseCss)
                    }

                    if (isBookMode) {
                        NovelBookContentHost(
                            state = state,
                            spine = bookEngineSpine,
                            initialLocation = bookInitialLocation,
                            seekRequest = bookSeekRequest,
                            window = bookWindow,
                            textListState = textListState,
                            handle = bookContentHandle,
                            loadDocument = loadBookEngineDocument,
                            loadSectionHtml = loadBookSectionHtml,
                            nativeBookBlocksForSection = nativeBookBlocksForSection,
                            onSeekApplied = onBookSeekApplied,
                            onLocationChanged = onBookEngineLocationChanged,
                            onScroll = { sectionIndex, sectionFraction ->
                                onBookModeScroll(sectionIndex, sectionFraction)
                            },
                            onToggleReaderUi = { onSetShowReaderUi(!showReaderUi) },
                            onShortTap = latestReaderShortTapHandler,
                            onSurfaceChanged = { surface -> bookContentHandle.surface = surface },
                            readerCss = bookReaderCss,
                            resolveResource = { requestUrl ->
                                resolveReaderBackgroundWebResourceResponse(
                                    requestUrl = requestUrl,
                                    context = context,
                                    selection = backgroundSelection,
                                ) ?: resolveReaderFontWebResourceResponse(
                                    requestUrl = requestUrl,
                                    selectedFont = selectedReaderFont,
                                )
                            },
                            textColor = textColor,
                            textBackground = textBackground,
                            activeBackgroundTexture = activeBackgroundTexture,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            isBackgroundMode = isBackgroundMode,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                            pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                            backgroundImageModel = backgroundImageModel,
                            activePageTransitionStyle = activePageTransitionStyle,
                            statusBarTopPadding = statusBarTopPadding,
                            paragraphSpacing = paragraphSpacing,
                            textTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                            onPlainTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            onRetrySection = { sectionIndex ->
                                onBookModeRetrySection(sectionIndex)
                            },
                            contentPaddingTop = contentPaddingPx + ttsScrollTopPadding,
                            contentPaddingBottom = contentPaddingPx,
                            bookRendererDecision = bookRendererDecision,
                        )
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        ComposePagerPageRenderer(
                            pagerState = pagerState,
                            contentPages = pageReaderContentPages,
                            spreadColumns = novelSpreadColumns,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            chapterTitleTextColor = chapterTitleTextColor,
                            backgroundTexture = activeBackgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                            pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                            textTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            hasPreviousChapter = state.previousChapterId != null,
                            previousChapterName = state.previousChapterName,
                            hasNextChapter = state.nextChapterId != null,
                            nextChapterName = state.nextChapterName,
                            previousChapterLabel = stringResource(MR.strings.action_previous_chapter),
                            nextChapterLabel = stringResource(MR.strings.action_next_chapter),
                            boundaryChapterHint = stringResource(MR.strings.reader_boundary_release_to_open),
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            onMoveBackward = {
                                coroutineScope.launch {
                                    moveBackwardByReaderActionWithAnimation(
                                        bookFlipPageAnimationDurationMillis,
                                    )
                                }
                            },
                            onMoveForward = {
                                coroutineScope.launch {
                                    moveForwardByReaderActionWithAnimation(
                                        bookFlipPageAnimationDurationMillis,
                                    )
                                }
                            },
                            onOpenPreviousChapter = {
                                openPreviousChapterFromReader()
                            },
                            onOpenNextChapter = { openNextChapterFromReader() },
                            onTextTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            onImageLongClick = { url ->
                                activeImageActionsUrl = url
                            },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER &&
                        novelSpreadColumns > 1
                    ) {
                        // The curl library always folds across its own full measured width, so a
                        // single wide PageCurl behind a two-column spread curls the whole spread as
                        // one leaf, edge to edge, ignoring the spine. SpreadPageTurnPageRenderer runs
                        // two half-width PageCurl surfaces instead, one per column, so the fold
                        // anchors at the spine like a real book page.
                        SpreadPageTurnPageRenderer(
                            pagerState = pagerState,
                            chapterId = state.chapter.id,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            chapterTitleTextColor = chapterTitleTextColor,
                            backgroundTexture = activeBackgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                            backgroundModeIdentity = if (isBackgroundMode) backgroundModeIdentity else "",
                            isBackgroundMode = isBackgroundMode,
                            activeBackgroundTexture = activeBackgroundTexture,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                            pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                            textTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            hasPreviousChapter = state.previousChapterId != null,
                            previousChapterName = state.previousChapterName,
                            hasNextChapter = state.nextChapterId != null,
                            nextChapterName = state.nextChapterName,
                            previousChapterLabel = stringResource(MR.strings.action_previous_chapter),
                            nextChapterLabel = stringResource(MR.strings.action_next_chapter),
                            boundaryChapterHint = stringResource(MR.strings.reader_boundary_release_to_open),
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            requestedPage = pageTurnRequestedPage,
                            onRequestedPageConsumed = { pageTurnRequestedPage = -1 },
                            onCurrentPageChange = { pageTurnCurrentPage = it },
                            onOpenPreviousChapter = {
                                openPreviousChapterFromReader()
                            },
                            onOpenNextChapter = { openNextChapterFromReader() },
                            chapterNavigationRequest = pageTurnChapterNavigationRequest,
                            onChapterNavigationRequestConsumed = {
                                pageTurnChapterNavigationRequest = null
                            },
                            onTextTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        PageTurnPageRenderer(
                            pagerState = pagerState,
                            chapterId = state.chapter.id,
                            contentPages = pageReaderContentPages,
                            spreadColumns = novelSpreadColumns,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            chapterTitleTextColor = chapterTitleTextColor,
                            backgroundTexture = activeBackgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                            backgroundModeIdentity = if (isBackgroundMode) backgroundModeIdentity else "",
                            isBackgroundMode = isBackgroundMode,
                            activeBackgroundTexture = activeBackgroundTexture,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                            pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                            textTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            hasPreviousChapter = state.previousChapterId != null,
                            previousChapterName = state.previousChapterName,
                            hasNextChapter = state.nextChapterId != null,
                            nextChapterName = state.nextChapterName,
                            previousChapterLabel = stringResource(MR.strings.action_previous_chapter),
                            nextChapterLabel = stringResource(MR.strings.action_next_chapter),
                            boundaryChapterHint = stringResource(MR.strings.reader_boundary_release_to_open),
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            requestedPage = pageTurnRequestedPage,
                            onRequestedPageConsumed = { pageTurnRequestedPage = -1 },
                            onCurrentPageChange = { pageTurnCurrentPage = it },
                            onMoveBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
                            onMoveForward = { coroutineScope.launch { moveForwardByReaderAction() } },
                            onOpenPreviousChapter = {
                                openPreviousChapterFromReader()
                            },
                            onOpenNextChapter = { openNextChapterFromReader() },
                            chapterNavigationRequest = pageTurnChapterNavigationRequest,
                            onChapterNavigationRequestConsumed = {
                                pageTurnChapterNavigationRequest = null
                            },
                            onTextTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else {
                        // Scroll mode.
                        // Shared across the two chapter-swipe detectors below: a diagonal swipe can
                        // satisfy both the horizontal and the vertical one. The horizontal detector
                        // fires first, mid-drag; once it has handled the gesture the vertical one has
                        // to stay silent, otherwise one gesture opens two chapters and — because the
                        // seamless in-place switch has already advanced the chapter — skips one.
                        // Mirrors the `horizontalSwipeHandled` guard the WebView touch listener uses.
                        var horizontalChapterSwipeHandled by remember { mutableStateOf(false) }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    state.readerSettings.swipeToPrevChapter,
                                    state.readerSettings.swipeToNextChapter,
                                    state.previousChapterId,
                                    state.nextChapterId,
                                    nativeScrollItemsCount,
                                ) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = true)
                                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                        val elapsedMillis = up.uptimeMillis - down.uptimeMillis
                                        if (elapsedMillis >= viewConfiguration.longPressTimeoutMillis) {
                                            return@awaitEachGesture
                                        }
                                        latestReaderShortTapHandler(
                                            up.position.x,
                                            up.position.y,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                        )
                                    }
                                }
                                .then(
                                    if (state.readerSettings.swipeGestures) {
                                        Modifier.pointerInput(
                                            state.previousChapterId,
                                            state.nextChapterId,
                                        ) {
                                            var totalDrag = 0f
                                            var handled = false
                                            detectHorizontalDragGestures(
                                                onDragStart = {
                                                    totalDrag = 0f
                                                    handled = false
                                                    horizontalChapterSwipeHandled = false
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (handled) return@detectHorizontalDragGestures
                                                    totalDrag += dragAmount
                                                    if (
                                                        totalDrag > 160f &&
                                                        state.previousChapterId != null
                                                    ) {
                                                        handled = true
                                                        horizontalChapterSwipeHandled = true
                                                        openPreviousChapterFromReader()
                                                    } else if (
                                                        totalDrag < -160f &&
                                                        state.nextChapterId != null
                                                    ) {
                                                        handled = true
                                                        horizontalChapterSwipeHandled = true
                                                        openNextChapterFromReader()
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (
                                        state.readerSettings.swipeToNextChapter ||
                                        state.readerSettings.swipeToPrevChapter
                                    ) {
                                        Modifier.pointerInput(
                                            state.readerSettings.swipeToNextChapter,
                                            state.readerSettings.swipeToPrevChapter,
                                            usePageReader,
                                            showReaderUi,
                                            showWebView,
                                            state.previousChapterId,
                                            state.nextChapterId,
                                        ) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                // A new gesture starts: clear the marker the horizontal
                                                // detector may have set on the previous one.
                                                horizontalChapterSwipeHandled = false
                                                var currentPosition = down.position
                                                var gestureEndUptime = down.uptimeMillis
                                                val wasNearChapterEndAtDown =
                                                    !textListState.canScrollForward || readingProgressPercent > 97
                                                val wasNearChapterStartAtDown =
                                                    !textListState.canScrollBackward || readingProgressPercent < 3

                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: event.changes.firstOrNull()
                                                        ?: break
                                                    currentPosition = change.position
                                                    gestureEndUptime = change.uptimeMillis
                                                    if (!change.pressed) break
                                                }

                                                if (showReaderUi || showWebView || usePageReader) {
                                                    return@awaitEachGesture
                                                }

                                                // The horizontal detector already handled this gesture
                                                // (a diagonal swipe at the chapter edge): firing the
                                                // chapter switch again here would skip one, because the
                                                // seamless in-place switch has already advanced
                                                // `nextChapterId` past the target.
                                                if (horizontalChapterSwipeHandled) {
                                                    return@awaitEachGesture
                                                }

                                                val deltaX = currentPosition.x - down.position.x
                                                val deltaY = currentPosition.y - down.position.y
                                                val isNearChapterEnd =
                                                    !textListState.canScrollForward || readingProgressPercent > 97
                                                val isNearChapterStart =
                                                    !textListState.canScrollBackward || readingProgressPercent < 3
                                                val gestureDurationMillis = (gestureEndUptime - down.uptimeMillis)
                                                    .coerceAtLeast(0L)
                                                when (
                                                    resolveVerticalChapterSwipeAction(
                                                        swipeGesturesEnabled = state.readerSettings.swipeGestures,
                                                        swipeToNextChapter = state.readerSettings.swipeToNextChapter,
                                                        swipeToPrevChapter = state.readerSettings.swipeToPrevChapter,
                                                        deltaX = deltaX,
                                                        deltaY = deltaY,
                                                        minSwipeDistancePx = minVerticalChapterSwipeDistancePx,
                                                        horizontalTolerancePx = verticalChapterSwipeHorizontalTolerancePx,
                                                        gestureDurationMillis = gestureDurationMillis,
                                                        minHoldDurationMillis = minVerticalChapterSwipeHoldDurationMillis,
                                                        wasNearChapterEndAtDown = wasNearChapterEndAtDown,
                                                        wasNearChapterStartAtDown = wasNearChapterStartAtDown,
                                                        isNearChapterEnd = isNearChapterEnd,
                                                        isNearChapterStart = isNearChapterStart,
                                                    )
                                                ) {
                                                    VerticalChapterSwipeAction.NEXT -> {
                                                        val id = state.nextChapterId
                                                        val open = onOpenNextChapter
                                                        if (id != null && open != null) {
                                                            open(id)
                                                        }
                                                    }
                                                    VerticalChapterSwipeAction.PREVIOUS -> {
                                                        val id = state.previousChapterId
                                                        val open = onOpenPreviousChapter
                                                        if (id != null && open != null) {
                                                            open(id)
                                                        }
                                                    }
                                                    VerticalChapterSwipeAction.NONE -> Unit
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .onGloballyPositioned { coordinates ->
                                    // Reference frame for the book blocks below: follow-along
                                    // scrolls by the distance between the spoken block and the
                                    // reading band, and both are measured in window coordinates.
                                    bookContentHandle.ttsBlockPositions.recordViewport(
                                        topInWindow = coordinates.positionInWindow().y,
                                        heightPx = coordinates.size.height.toFloat(),
                                    )
                                },
                            state = textListState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = contentPaddingPx + ttsScrollTopPadding,
                                bottom = contentPaddingPx,
                                start = state.readerSettings.margin.dp,
                                end = state.readerSettings.margin.dp,
                            ),
                        ) {
                            if (useRichNativeScroll) {
                                itemsIndexed(
                                    richScrollBlocks,
                                    // Keyed by position, not by content: a content-derived key
                                    // changes for every block when a translation is applied, which
                                    // forces the whole list to be disposed and recreated at once.
                                    key = { index, _ -> "rich-${state.chapter.id}-$index" },
                                ) { index, block ->
                                    NovelRichNativeScrollItem(
                                        block = block,
                                        index = index,
                                        lastIndex = richScrollBlocks.lastIndex,
                                        chapterTitle = state.chapter.name,
                                        novelTitle = state.novel.title,
                                        sourceId = state.novel.source,
                                        chapterWebUrl = state.chapterWebUrl,
                                        novelUrl = state.novel.url,
                                        statusBarTopPadding = statusBarTopPadding,
                                        textColor = textColor,
                                        backgroundColor = textBackground,
                                        readerSettings = state.readerSettings,
                                        textTypeface = composeTypeface,
                                        chapterTitleTypeface = chapterTitleTypeface,
                                        paragraphSpacing = paragraphSpacing,
                                        ttsHighlightState = ttsHighlightState,
                                        ttsHighlightColor = ttsHighlightColor,
                                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                        onPlainTap = { tapX, tapY, width, height ->
                                            latestReaderShortTapHandler(tapX, tapY, width, height)
                                        },
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    scrollContentBlocks,
                                    key = { index, _ -> "plain-${state.chapter.id}-$index" },
                                ) { index, block ->
                                    when (block) {
                                        is NovelReaderScreenModel.ContentBlock.Text -> {
                                            val isChapterTitle = index == 0 &&
                                                isNativeChapterTitleText(block.text, state.chapter.name)
                                            val baseTextContent = if (state.readerSettings.bionicReading) {
                                                toBionicText(block.text)
                                            } else {
                                                AnnotatedString(block.text)
                                            }
                                            val textContent = applyNovelReaderTtsHighlight(
                                                text = baseTextContent,
                                                blockText = block.text,
                                                sourceBlockIndex = index,
                                                highlightState = ttsHighlightState,
                                                highlightColor = ttsHighlightColor,
                                            )
                                            if (isChapterTitle) {
                                                Column(
                                                    modifier = Modifier.padding(
                                                        top = statusBarTopPadding + 10.dp,
                                                        bottom = if (index == scrollContentBlocks.lastIndex) {
                                                            0.dp
                                                        } else {
                                                            18.dp
                                                        },
                                                    ),
                                                ) {
                                                    NovelPageReaderTextBlock(
                                                        text = textContent,
                                                        isChapterTitle = true,
                                                        firstLineIndentEm = null,
                                                        readerSettings = state.readerSettings,
                                                        textColor = textColor,
                                                        textBackground = textBackground,
                                                        textAlign = state.readerSettings.textAlign,
                                                        textTypeface = composeTypeface,
                                                        chapterTitleTypeface = chapterTitleTypeface,
                                                        chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                                                        textShadowEnabled = state.readerSettings.textShadow,
                                                        textShadowColor = state.readerSettings.textShadowColor,
                                                        textShadowBlur = state.readerSettings.textShadowBlur,
                                                        textShadowX = state.readerSettings.textShadowX,
                                                        textShadowY = state.readerSettings.textShadowY,
                                                        selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                                                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                                        onPlainTap = { tapX, tapY, width, height ->
                                                            latestReaderShortTapHandler(tapX, tapY, width, height)
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(top = 8.dp)
                                                            .fillMaxWidth(0.72f)
                                                            .height(1.dp)
                                                            .background(
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                            ),
                                                    )
                                                }
                                            } else {
                                                NovelPageReaderTextBlock(
                                                    text = textContent,
                                                    isChapterTitle = false,
                                                    firstLineIndentEm = if (state.readerSettings.forceParagraphIndent) {
                                                        FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
                                                    } else {
                                                        null
                                                    },
                                                    readerSettings = state.readerSettings,
                                                    textColor = textColor,
                                                    textBackground = textBackground,
                                                    textAlign = state.readerSettings.textAlign,
                                                    textTypeface = composeTypeface,
                                                    chapterTitleTypeface = chapterTitleTypeface,
                                                    chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                                                    textShadowEnabled = state.readerSettings.textShadow,
                                                    textShadowColor = state.readerSettings.textShadowColor,
                                                    textShadowBlur = state.readerSettings.textShadowBlur,
                                                    textShadowX = state.readerSettings.textShadowX,
                                                    textShadowY = state.readerSettings.textShadowY,
                                                    selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                                                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                                    onPlainTap = { tapX, tapY, width, height ->
                                                        latestReaderShortTapHandler(tapX, tapY, width, height)
                                                    },
                                                    modifier = Modifier.padding(
                                                        top = if (index == 0) statusBarTopPadding else 0.dp,
                                                        bottom = if (index == scrollContentBlocks.lastIndex) {
                                                            0.dp
                                                        } else {
                                                            paragraphSpacing
                                                        },
                                                    ),
                                                )
                                            }
                                        }
                                        is NovelReaderScreenModel.ContentBlock.Image -> {
                                            val referer = LocalNovelReaderReferer.current
                                            val imageModel = if (NovelPluginImage.isSupported(block.url)) {
                                                NovelPluginImage(block.url)
                                            } else if (referer != null) {
                                                NovelReaderRefererImage(
                                                    url = block.url,
                                                    referer = referer,
                                                )
                                            } else {
                                                block.url
                                            }
                                            AsyncImage(
                                                model = imageModel,
                                                contentDescription = block.alt,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        top = if (index == 0) statusBarTopPadding else 0.dp,
                                                        bottom = if (index ==
                                                            scrollContentBlocks.lastIndex
                                                        ) {
                                                            0.dp
                                                        } else {
                                                            paragraphSpacing
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val backgroundColor = resolveReaderWebViewBackgroundColor(
                        isBackgroundMode = isBackgroundMode,
                        backgroundColor = textBackground,
                    )
                    val baseUrl = remember(state.chapterWebUrl) {
                        state.chapterWebUrl
                    }

                    DisposableEffect(state.chapter.id) {
                        onDispose {
                            val webView = webViewInstance
                            val resolvedProgress = webView?.resolveCurrentWebViewProgressPercent()
                            val finalProgress = resolveFinalWebViewProgressPercent(
                                resolvedPercent = resolvedProgress,
                                cachedPercent = webProgressPercent,
                            )
                            reportReadingProgress(
                                finalProgress,
                                100,
                                encodeWebScrollProgressPercent(finalProgress),
                            )
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            val webView = webViewInstance
                            webView?.apply {
                                setOnTouchListener(null)
                                setOnScrollChangeListener(null)
                                webViewClient = object : WebViewClient() {}
                                stopLoading()
                                destroy()
                            }
                            webViewInstance = null
                        }
                    }

                    val initialWebReaderPaddingPx = with(density) { 4.dp.roundToPx() }
                    val initialMaxWebViewStatusInsetPx = with(density) { 16.dp.roundToPx() }
                    val initialPaddingTop = resolveWebViewPaddingTopPx(
                        statusBarHeightPx = statusBarHeight,
                        showReaderUi = showReaderUi,
                        appBarHeightPx = appBarHeight,
                        basePaddingPx = initialWebReaderPaddingPx,
                        maxStatusBarInsetPx = initialMaxWebViewStatusInsetPx,
                    )
                    val initialPaddingBottom = resolveWebViewPaddingBottomPx(
                        navigationBarHeightPx = navigationBarHeight,
                        showReaderUi = showReaderUi,
                        bottomBarHeightPx = bottomBarHeight,
                        basePaddingPx = initialWebReaderPaddingPx,
                    )
                    val initialPaddingHorizontal = with(density) { state.readerSettings.margin.dp.roundToPx() }
                    val initialCssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                    val initialCssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                        forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                    )
                    val initialTextShadowCss = resolveWebReaderTextShadowCss(
                        textShadowEnabled = state.readerSettings.textShadow,
                        textShadowColor = state.readerSettings.textShadowColor,
                        textShadowBlur = state.readerSettings.textShadowBlur,
                        textShadowX = state.readerSettings.textShadowX,
                        textShadowY = state.readerSettings.textShadowY,
                        textColor = textColor,
                        backgroundColor = textBackground,
                    )
                    val initialSelectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                    val initialFontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont)

                    val webReaderDocumentTag: Any = state.html
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val initialFactoryWebViewHtml = buildInitialWebReaderHtml(
                                rawHtml = state.html,
                                readerCss = buildWebReaderCssText(
                                    fontFaceCss = initialFontFaceCss,
                                    paddingTop = initialPaddingTop,
                                    paddingBottom = initialPaddingBottom,
                                    paddingHorizontal = initialPaddingHorizontal,
                                    fontSizePx = state.readerSettings.fontSize,
                                    lineHeightMultiplier = state.readerSettings.lineHeight,
                                    paragraphSpacingPx = with(density) {
                                        state.readerSettings.paragraphSpacing.dp.roundToPx()
                                    },
                                    textAlignCss = initialCssTextAlign,
                                    firstLineIndentCss = initialCssFirstLineIndent,
                                    textColorHex = colorToCssHex(textColor),
                                    backgroundHex = colorToCssHex(textBackground),
                                    appearanceMode = appearanceMode,
                                    backgroundTexture = activeBackgroundTexture,
                                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                    fontFamilyName = initialSelectedFontFamily,
                                    customCss = state.readerSettings.customCSS,
                                    textShadowCss = initialTextShadowCss,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                ),
                                hideUntilReveal = shouldHideWebViewUntilReveal,
                            )
                            val factoryShouldEarlyReveal = shouldUseEarlyWebViewReveal(state.html)
                            val factoryWebViewClient = object : WebViewClient() {
                                private var hasEarlyRevealedPage = false

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    if (!factoryShouldEarlyReveal || hasEarlyRevealedPage) return
                                    hasEarlyRevealedPage = true
                                    view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    resolveReaderBackgroundWebResourceResponse(
                                        requestUrl = requestUrl,
                                        context = context,
                                        selection = backgroundSelection,
                                    )?.let { response ->
                                        return response
                                    }
                                    resolveReaderFontWebResourceResponse(
                                        requestUrl = requestUrl,
                                        selectedFont = selectedReaderFont,
                                    )?.let { response ->
                                        return response
                                    }
                                    if (!NovelPluginImage.isSupported(requestUrl)) {
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    val image = NovelPluginImageResolver.resolveBlocking(requestUrl)
                                        ?: return super.shouldInterceptRequest(view, request)
                                    return WebResourceResponse(
                                        image.mimeType,
                                        null,
                                        ByteArrayInputStream(image.bytes),
                                    )
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    webViewPageReadyForAutoScroll = true
                                    view?.applyReaderCss(
                                        fontFaceCss = initialFontFaceCss,
                                        paddingTop = initialPaddingTop,
                                        paddingBottom = initialPaddingBottom,
                                        paddingHorizontal = initialPaddingHorizontal,
                                        fontSizePx = state.readerSettings.fontSize,
                                        lineHeightMultiplier = state.readerSettings.lineHeight,
                                        paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                        textAlignCss = initialCssTextAlign,
                                        firstLineIndentCss = initialCssFirstLineIndent,
                                        textColorHex = colorToCssHex(textColor),
                                        backgroundHex = colorToCssHex(textBackground),
                                        appearanceMode = appearanceMode,
                                        backgroundTexture = activeBackgroundTexture,
                                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                        fontFamilyName = initialSelectedFontFamily,
                                        customCss = state.readerSettings.customCSS,
                                        textShadowCss = initialTextShadowCss,
                                        forceBoldText = state.readerSettings.forceBoldText,
                                        forceItalicText = state.readerSettings.forceItalicText,
                                        bionicReadingEnabled = state.readerSettings.bionicReading,
                                    )
                                    appliedWebCssFingerprint = buildWebReaderCssFingerprint(
                                        chapterId = state.chapter.id,
                                        paddingTop = initialPaddingTop,
                                        paddingBottom = initialPaddingBottom,
                                        paddingHorizontal = initialPaddingHorizontal,
                                        fontSizePx = state.readerSettings.fontSize,
                                        lineHeightMultiplier = state.readerSettings.lineHeight,
                                        paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                        textAlignCss = initialCssTextAlign,
                                        firstLineIndentCss = initialCssFirstLineIndent,
                                        textColorHex = colorToCssHex(textColor),
                                        backgroundHex = colorToCssHex(textBackground),
                                        appearanceMode = appearanceMode,
                                        backgroundTexture = activeBackgroundTexture,
                                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                        backgroundImageIdentity = if (isBackgroundMode) backgroundModeIdentity else null,
                                        fontFamilyName = initialSelectedFontFamily,
                                        customCss = state.readerSettings.customCSS,
                                        textShadowCss = initialTextShadowCss,
                                        forceBoldText = state.readerSettings.forceBoldText,
                                        forceItalicText = state.readerSettings.forceItalicText,
                                        bionicReadingEnabled = state.readerSettings.bionicReading,
                                    )

                                    if (state.readerSettings.customJS.isNotEmpty()) {
                                        view?.evaluateJavascript(
                                            """
                                        (function() {
                                            ${state.readerSettings.customJS}
                                        })();
                                            """.trimIndent(),
                                            null,
                                        )
                                    }

                                    if (shouldRestoreWebScroll) {
                                        view?.restoreWebViewScroll(
                                            progressPercent = state.lastSavedWebProgressPercent.coerceIn(0, 100),
                                            onComplete = { restored ->
                                                shouldRestoreWebScroll = !restored
                                                if (restored) {
                                                    val settledProgress = view.resolveCurrentWebViewProgressPercent()
                                                    if (shouldDispatchWebProgressUpdate(
                                                            false,
                                                            settledProgress,
                                                            webProgressPercent,
                                                        )
                                                    ) {
                                                        webProgressPercent = settledProgress
                                                        webAutoScrollNearEnd = settledProgress >= 100
                                                        reportReadingProgress(
                                                            settledProgress,
                                                            100,
                                                            encodeWebScrollProgressPercent(settledProgress),
                                                        )
                                                    }
                                                }
                                                view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                            },
                                        )
                                    } else {
                                        val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                            ?: webProgressPercent
                                        if (shouldDispatchWebProgressUpdate(
                                                false,
                                                settledProgress,
                                                webProgressPercent,
                                            )
                                        ) {
                                            webProgressPercent = settledProgress
                                            webAutoScrollNearEnd = settledProgress >= 100
                                            reportReadingProgress(
                                                settledProgress,
                                                100,
                                                encodeWebScrollProgressPercent(settledProgress),
                                            )
                                        }
                                        view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                    }
                                }
                            }

                            createNovelReaderWebView(context).apply {
                                webViewInstance = this
                                setBackgroundColor(backgroundColor)
                                alpha = if (shouldHideWebViewUntilReveal) 0f else 1f
                                // This is the chapter WebView; book mode is served by its own host
                                // (NovelBookContentHost) and never mounts this view.
                                settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(
                                    pluginRequestsJavaScript = state.enableJs,
                                    bookModeEnabled = false,
                                )
                                settings.domStorageEnabled = false
                                registerWebReaderSelectionBridge(
                                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                )

                                webViewClient = factoryWebViewClient
                                setOnScrollChangeListener { view, _, scrollY, _, _ ->
                                    val webView = view as? WebView ?: return@setOnScrollChangeListener
                                    if (!shouldTrackWebViewProgress(shouldRestoreWebScroll)) {
                                        return@setOnScrollChangeListener
                                    }
                                    val totalScrollable = resolveWebViewTotalScrollablePx(
                                        contentHeightPx = webView.resolveWebViewContentHeightPx(),
                                        viewHeightPx = webView.height,
                                    )
                                    webAutoScrollNearEnd = resolveWebViewAutoScrollNearEnd(
                                        totalScrollablePx = totalScrollable,
                                        scrollYPx = scrollY,
                                        endOffsetPx = state.readerSettings.autoScrollOffset,
                                    )
                                    val newPercent = webView.resolveCurrentWebViewProgressPercent(
                                        scrollYOverride = scrollY,
                                    )

                                    if (shouldDispatchWebProgressUpdate(
                                            shouldRestoreWebScroll,
                                            newPercent,
                                            webProgressPercent,
                                        )
                                    ) {
                                        webProgressPercent = newPercent
                                        reportReadingProgress(
                                            newPercent,
                                            100,
                                            encodeWebScrollProgressPercent(newPercent),
                                        )
                                    }
                                }
                                loadDataWithBaseURL(baseUrl, initialFactoryWebViewHtml, "text/html", "utf-8", null)
                                tag = webReaderDocumentTag
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                            if (webView is NovelReaderWebView) {
                                webView.isDictionaryEnabled = state.novelDictionaryEnabled
                                webView.isTranslationEnabled = state.readerSettings.selectedTextTranslationEnabled
                            }
                            webView.setBackgroundColor(backgroundColor)
                            // Chapter WebView only; book mode never reaches this update block.
                            webView.settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(
                                pluginRequestsJavaScript = state.enableJs,
                                bookModeEnabled = false,
                            )
                            webView.registerWebReaderSelectionBridge(
                                selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                            )
                            val minWebSwipeDistancePx = minVerticalChapterSwipeDistancePx
                            val webSwipeHorizontalTolerancePx = verticalChapterSwipeHorizontalTolerancePx
                            val minWebSwipeHoldDurationMillis = minVerticalChapterSwipeHoldDurationMillis
                            var touchStartX = 0f
                            var touchStartY = 0f
                            var touchStartEventTime = 0L
                            var wasNearChapterEndAtDown = false
                            var wasNearChapterStartAtDown = false
                            var horizontalSwipeHandled = false
                            val gestureDetector = GestureDetector(
                                webView.context,
                                object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                        // Let the WebView open links itself: a tap on an anchor must
                                        // not also run the configured tap zone action.
                                        val hitResultType = webView.hitTestResult?.type
                                        if (
                                            hitResultType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                            hitResultType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                        ) {
                                            return false
                                        }
                                        val viewWidth = webView.width.takeIf { it > 0 } ?: return false
                                        val viewHeight = webView.height.takeIf { it > 0 } ?: return false
                                        return when (
                                            resolveConfiguredNovelReaderTapAction(
                                                tapX = e.x,
                                                tapY = e.y,
                                                width = viewWidth.toFloat(),
                                                height = viewHeight.toFloat(),
                                                customTapZonesEnabled = latestCustomTapZonesEnabled,
                                                tapZoneActions = latestTapZoneActions,
                                                tapToScrollEnabled = latestTapToScrollEnabled,
                                            )
                                        ) {
                                            NovelReaderTapZoneAction.TOGGLE_UI -> {
                                                onSetShowReaderUi(!latestShowReaderUi)
                                                true
                                            }
                                            NovelReaderTapZoneAction.BACKWARD -> {
                                                coroutineScope.launch { moveBackwardByReaderAction() }
                                                true
                                            }
                                            NovelReaderTapZoneAction.FORWARD -> {
                                                coroutineScope.launch { moveForwardByReaderAction() }
                                                true
                                            }
                                            NovelReaderTapZoneAction.NEXT_CHAPTER -> {
                                                openNextChapterFromReader()
                                                true
                                            }
                                            NovelReaderTapZoneAction.PREV_CHAPTER -> {
                                                openPreviousChapterFromReader()
                                                true
                                            }
                                            NovelReaderTapZoneAction.NONE -> true
                                        }
                                    }
                                },
                            )
                            webView.setOnTouchListener { _, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        touchStartX = event.x
                                        touchStartY = event.y
                                        touchStartEventTime = event.eventTime
                                        wasNearChapterEndAtDown = !webView.canScrollVertically(1)
                                        wasNearChapterStartAtDown = !webView.canScrollVertically(-1)
                                        horizontalSwipeHandled = false
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        if (!latestShowReaderUi && !horizontalSwipeHandled) {
                                            when (
                                                resolveHorizontalChapterSwipeAction(
                                                    swipeGesturesEnabled = state.readerSettings.swipeGestures,
                                                    deltaX = event.x - touchStartX,
                                                    deltaY = event.y - touchStartY,
                                                    thresholdPx = 160f,
                                                    hasPreviousChapter = state.previousChapterId != null,
                                                    hasNextChapter = state.nextChapterId != null,
                                                )
                                            ) {
                                                HorizontalChapterSwipeAction.PREVIOUS -> {
                                                    horizontalSwipeHandled = true
                                                    openPreviousChapterFromReader()
                                                }
                                                HorizontalChapterSwipeAction.NEXT -> {
                                                    horizontalSwipeHandled = true
                                                    openNextChapterFromReader()
                                                }
                                                HorizontalChapterSwipeAction.NONE -> Unit
                                            }
                                        }
                                        if (!latestShowReaderUi && !horizontalSwipeHandled) {
                                            val deltaX = event.x - touchStartX
                                            val deltaY = event.y - touchStartY
                                            val gestureDurationMillis = (event.eventTime - touchStartEventTime)
                                                .coerceAtLeast(0L)
                                            val isNearChapterEnd =
                                                wasNearChapterEndAtDown && !webView.canScrollVertically(1)
                                            val isNearChapterStart =
                                                wasNearChapterStartAtDown && !webView.canScrollVertically(-1)

                                            when (
                                                resolveWebViewVerticalChapterSwipeAction(
                                                    swipeGesturesEnabled = state.readerSettings.swipeGestures,
                                                    swipeToNextChapter = state.readerSettings.swipeToNextChapter,
                                                    swipeToPrevChapter = state.readerSettings.swipeToPrevChapter,
                                                    deltaX = deltaX,
                                                    deltaY = deltaY,
                                                    minSwipeDistancePx = minWebSwipeDistancePx,
                                                    horizontalTolerancePx = webSwipeHorizontalTolerancePx,
                                                    gestureDurationMillis = gestureDurationMillis,
                                                    minHoldDurationMillis = minWebSwipeHoldDurationMillis,
                                                    wasNearChapterEndAtDown = wasNearChapterEndAtDown,
                                                    wasNearChapterStartAtDown = wasNearChapterStartAtDown,
                                                    isNearChapterEnd = isNearChapterEnd,
                                                    isNearChapterStart = isNearChapterStart,
                                                )
                                            ) {
                                                VerticalChapterSwipeAction.NEXT -> {
                                                    openNextChapterFromReader()
                                                }
                                                VerticalChapterSwipeAction.PREVIOUS -> {
                                                    openPreviousChapterFromReader()
                                                }
                                                VerticalChapterSwipeAction.NONE -> Unit
                                            }
                                        }
                                    }
                                }
                                if (!horizontalSwipeHandled) {
                                    gestureDetector.onTouchEvent(event)
                                }
                                false
                            }

                            val webReaderPaddingPx = with(density) { 4.dp.roundToPx() }
                            val maxWebViewStatusInsetPx = with(density) { 16.dp.roundToPx() }
                            val paddingTop = resolveWebViewPaddingTopPx(
                                statusBarHeightPx = statusBarHeight,
                                showReaderUi = showReaderUi,
                                appBarHeightPx = appBarHeight,
                                basePaddingPx = webReaderPaddingPx,
                                maxStatusBarInsetPx = maxWebViewStatusInsetPx,
                            )
                            val paddingBottom = resolveWebViewPaddingBottomPx(
                                navigationBarHeightPx = navigationBarHeight,
                                showReaderUi = showReaderUi,
                                bottomBarHeightPx = bottomBarHeight,
                                basePaddingPx = webReaderPaddingPx,
                            )
                            val paddingHorizontal = with(density) { state.readerSettings.margin.dp.roundToPx() }
                            val cssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                            val cssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                            )
                            val selectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                            val fontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont)
                            val currentTextColorCss = colorToCssHex(textColor)
                            val currentBackgroundCss = colorToCssHex(textBackground)
                            // The legacy chapter WebView is not mounted in book mode (the book engine
                            // owns its own document), so no book section styles are needed here.
                            val currentCustomCss = state.readerSettings.customCSS
                            val currentCustomJs = state.readerSettings.customJS
                            val currentTextShadowCss = resolveWebReaderTextShadowCss(
                                textShadowEnabled = state.readerSettings.textShadow,
                                textShadowColor = state.readerSettings.textShadowColor,
                                textShadowBlur = state.readerSettings.textShadowBlur,
                                textShadowX = state.readerSettings.textShadowX,
                                textShadowY = state.readerSettings.textShadowY,
                                textColor = textColor,
                                backgroundColor = textBackground,
                            )
                            val paragraphSpacingPx =
                                with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() }
                            val styleFingerprint = buildWebReaderCssFingerprint(
                                chapterId = state.chapter.id,
                                paddingTop = paddingTop,
                                paddingBottom = paddingBottom,
                                paddingHorizontal = paddingHorizontal,
                                fontSizePx = state.readerSettings.fontSize,
                                lineHeightMultiplier = state.readerSettings.lineHeight,
                                paragraphSpacingPx = paragraphSpacingPx,
                                textAlignCss = cssTextAlign,
                                firstLineIndentCss = cssFirstLineIndent,
                                textColorHex = colorToCssHex(textColor),
                                backgroundHex = colorToCssHex(textBackground),
                                appearanceMode = appearanceMode,
                                backgroundTexture = activeBackgroundTexture,
                                oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                backgroundImageIdentity = if (isBackgroundMode) backgroundModeIdentity else null,
                                fontFamilyName = selectedFontFamily,
                                customCss = state.readerSettings.customCSS,
                                textShadowCss = currentTextShadowCss,
                                forceBoldText = state.readerSettings.forceBoldText,
                                forceItalicText = state.readerSettings.forceItalicText,
                                bionicReadingEnabled = state.readerSettings.bionicReading,
                            )
                            val currentFontSize = state.readerSettings.fontSize
                            val currentLineHeight = state.readerSettings.lineHeight
                            val currentRestoreProgress = state.lastSavedWebProgressPercent.coerceIn(0, 100)
                            val shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html)
                            webView.webViewClient = object : WebViewClient() {
                                private var hasEarlyRevealedPage = false

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    if (!shouldEarlyRevealWebView || hasEarlyRevealedPage) return
                                    hasEarlyRevealedPage = true
                                    view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    resolveReaderBackgroundWebResourceResponse(
                                        requestUrl = requestUrl,
                                        context = webView.context,
                                        selection = backgroundSelection,
                                    )?.let { response ->
                                        return response
                                    }
                                    resolveReaderFontWebResourceResponse(
                                        requestUrl = requestUrl,
                                        selectedFont = selectedReaderFont,
                                    )?.let { response ->
                                        return response
                                    }
                                    if (!NovelPluginImage.isSupported(requestUrl)) {
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    val image = NovelPluginImageResolver.resolveBlocking(requestUrl)
                                        ?: return super.shouldInterceptRequest(view, request)
                                    return WebResourceResponse(
                                        image.mimeType,
                                        null,
                                        ByteArrayInputStream(image.bytes),
                                    )
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    webViewPageReadyForAutoScroll = true
                                    view?.applyReaderCss(
                                        fontFaceCss = fontFaceCss,
                                        paddingTop = paddingTop,
                                        paddingBottom = paddingBottom,
                                        paddingHorizontal = paddingHorizontal,
                                        fontSizePx = currentFontSize,
                                        lineHeightMultiplier = currentLineHeight,
                                        paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                        textAlignCss = cssTextAlign,
                                        firstLineIndentCss = cssFirstLineIndent,
                                        textColorHex = currentTextColorCss,
                                        backgroundHex = currentBackgroundCss,
                                        appearanceMode = appearanceMode,
                                        backgroundTexture = activeBackgroundTexture,
                                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                        fontFamilyName = selectedFontFamily,
                                        customCss = currentCustomCss,
                                        textShadowCss = currentTextShadowCss,
                                        forceBoldText = state.readerSettings.forceBoldText,
                                        forceItalicText = state.readerSettings.forceItalicText,
                                        bionicReadingEnabled = state.readerSettings.bionicReading,
                                    )
                                    appliedWebCssFingerprint = styleFingerprint

                                    if (currentCustomJs.isNotEmpty()) {
                                        view?.evaluateJavascript(
                                            """
                                        (function() {
                                            $currentCustomJs
                                        })();
                                            """.trimIndent(),
                                            null,
                                        )
                                    }

                                    if (shouldRestoreWebScroll) {
                                        view?.restoreWebViewScroll(
                                            progressPercent = currentRestoreProgress,
                                            onComplete = { restored ->
                                                shouldRestoreWebScroll = !restored
                                                if (restored) {
                                                    val settledProgress = view.resolveCurrentWebViewProgressPercent()
                                                    if (shouldDispatchWebProgressUpdate(
                                                            false,
                                                            settledProgress,
                                                            webProgressPercent,
                                                        )
                                                    ) {
                                                        webProgressPercent = settledProgress
                                                        reportReadingProgress(
                                                            settledProgress,
                                                            100,
                                                            encodeWebScrollProgressPercent(settledProgress),
                                                        )
                                                    }
                                                }
                                                view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                            },
                                        )
                                    } else {
                                        val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                            ?: webProgressPercent
                                        if (shouldDispatchWebProgressUpdate(
                                                false,
                                                settledProgress,
                                                webProgressPercent,
                                            )
                                        ) {
                                            webProgressPercent = settledProgress
                                            reportReadingProgress(
                                                settledProgress,
                                                100,
                                                encodeWebScrollProgressPercent(settledProgress),
                                            )
                                        }
                                        view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                    }
                                }
                            }

                            if (webView.tag != webReaderDocumentTag) {
                                val currentRestoreProgress = state.lastSavedWebProgressPercent.coerceIn(0, 100)
                                // A seamless chapter switch replaces the document inside the live
                                // WebView. Keeping the old frame visible until the next document
                                // paints removes the fade-to-background flash at the chapter seam.
                                val isSeamlessChapterSwap =
                                    state.seamlessSwitchToken != appliedSeamlessSwitchToken[0]
                                appliedSeamlessSwitchToken[0] = state.seamlessSwitchToken
                                val seamlessInstantSwap = isSeamlessChapterSwap &&
                                    currentRestoreProgress <= 0
                                val currentReaderCss = buildWebReaderCssText(
                                    fontFaceCss = fontFaceCss,
                                    paddingTop = paddingTop,
                                    paddingBottom = paddingBottom,
                                    paddingHorizontal = paddingHorizontal,
                                    fontSizePx = currentFontSize,
                                    lineHeightMultiplier = currentLineHeight,
                                    paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                    textAlignCss = cssTextAlign,
                                    firstLineIndentCss = cssFirstLineIndent,
                                    textColorHex = currentTextColorCss,
                                    backgroundHex = currentBackgroundCss,
                                    appearanceMode = appearanceMode,
                                    backgroundTexture = activeBackgroundTexture,
                                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                    fontFamilyName = selectedFontFamily,
                                    customCss = currentCustomCss,
                                    textShadowCss = currentTextShadowCss,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                )
                                val initialWebViewHtml = buildInitialWebReaderHtml(
                                    rawHtml = state.html,
                                    readerCss = currentReaderCss,
                                    hideUntilReveal = shouldHideWebViewUntilReveal && !seamlessInstantSwap,
                                )
                                val shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html)
                                shouldRestoreWebScroll = true
                                appliedWebCssFingerprint = null
                                webView.animate().cancel()
                                webView.alpha = if (shouldHideWebViewUntilReveal && !seamlessInstantSwap) 0f else 1f
                                webView.loadDataWithBaseURL(baseUrl, initialWebViewHtml, "text/html", "utf-8", null)
                                webView.tag = webReaderDocumentTag
                            } else if (appliedWebCssFingerprint != styleFingerprint) {
                                webView.applyReaderCss(
                                    fontFaceCss = fontFaceCss,
                                    paddingTop = paddingTop,
                                    paddingBottom = paddingBottom,
                                    paddingHorizontal = paddingHorizontal,
                                    fontSizePx = state.readerSettings.fontSize,
                                    lineHeightMultiplier = state.readerSettings.lineHeight,
                                    paragraphSpacingPx = paragraphSpacingPx,
                                    textAlignCss = cssTextAlign,
                                    firstLineIndentCss = cssFirstLineIndent,
                                    textColorHex = colorToCssHex(textColor),
                                    backgroundHex = colorToCssHex(textBackground),
                                    appearanceMode = appearanceMode,
                                    backgroundTexture = activeBackgroundTexture,
                                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                    fontFamilyName = selectedFontFamily,
                                    customCss = state.readerSettings.customCSS,
                                    textShadowCss = currentTextShadowCss,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                    bionicReadingEnabled = state.readerSettings.bionicReading,
                                )
                                appliedWebCssFingerprint = styleFingerprint
                            }
                        },
                        onRelease = { webView ->
                            webView.clearFocus()
                        },
                    )
                }
            }

            // UI overlay above the content.
            NovelReaderInfoOverlay(
                visible = showBottomInfoOverlay,
                settings = state.readerSettings,
                batteryLevel = batteryLevel,
                timeText = timeText,
                remainingMinutes = remainingMinutes,
                readWords = readWords,
                totalWords = totalWords,
                bottomBarHeightPx = bottomBarHeight,
                density = density,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )

            val seekbarItemsCount = if (showWebView) {
                101
            } else if (usePageReader) {
                pageReaderItemsCount
            } else {
                nativeScrollItemsCount
            }
            NovelReaderVerticalSeekbar(
                showReaderUi = showReaderUi,
                settings = state.readerSettings,
                showWebView = showWebView,
                usePageReader = usePageReader,
                webProgressPercent = webProgressPercent,
                pagerCurrentPage = pagerState.currentPage,
                pageTurnCurrentPage = pageTurnCurrentPage,
                firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                canScrollForward = textListState.canScrollForward,
                seekbarItemsCount = seekbarItemsCount,
                readingProgressPercent = readingProgressPercent,
                isBookMode = isBookMode,
                pageReaderRendererRoute = pageReaderRendererRoute,
                pageReaderItemsCount = pageReaderItemsCount,
                pageReaderSpreadSlotCount = pageReaderSpreadSlotCount,
                spreadColumns = novelSpreadColumns,
                composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                nativeScrollItemsCount = nativeScrollItemsCount,
                pageReaderProgressPageIndex = pageReaderProgressPageIndex,
                isGeminiTranslating = state.isGeminiTranslating,
                hasGeminiTranslationCache = state.hasGeminiTranslationCache,
                isGeminiTranslationVisible = state.isGeminiTranslationVisible,
                geminiTranslationProgress = state.geminiTranslationProgress,
                backgroundTranslatingChapterCount = state.backgroundTranslatingChapterCount(state.chapter.id),
                isGoogleTranslating = state.isGoogleTranslating,
                hasGoogleTranslationCache = state.hasGoogleTranslationCache,
                isGoogleTranslationVisible = state.isGoogleTranslationVisible,
                googleTranslationProgress = state.googleTranslationProgress,
                onSetShowReaderUi = onSetShowReaderUi,
                onSeekBookModeProgress = onSeekBookModeProgress,
                onSeekWebProgress = { clampedValue, isFinal ->
                    val targetPercent = (clampedValue * 100f).roundToInt().coerceIn(0, 100)
                    webProgressPercent = targetPercent
                    val webView = webViewInstance
                    if (webView != null) {
                        val totalScrollable = resolveWebViewTotalScrollablePx(
                            contentHeightPx = webView.resolveWebViewContentHeightPx(),
                            viewHeightPx = webView.height,
                        )
                        val targetY = if (totalScrollable > 0) {
                            ((targetPercent.toFloat() / 100f) * totalScrollable.toFloat())
                                .roundToInt()
                                .coerceIn(0, totalScrollable)
                        } else {
                            0
                        }
                        webView.scrollTo(0, targetY)
                        if (isFinal) {
                            webView.post {
                                val finalTotalScrollable = resolveWebViewTotalScrollablePx(
                                    contentHeightPx = webView.resolveWebViewContentHeightPx(),
                                    viewHeightPx = webView.height,
                                )
                                val finalPercent = resolveWebViewScrollProgressPercent(
                                    scrollY = webView.scrollY,
                                    totalScrollable = finalTotalScrollable,
                                )
                                webProgressPercent = finalPercent
                                reportReadingProgress(
                                    finalPercent,
                                    100,
                                    encodeWebScrollProgressPercent(finalPercent),
                                )
                            }
                        }
                    }
                    reportReadingProgress(
                        targetPercent,
                        100,
                        encodeWebScrollProgressPercent(targetPercent),
                    )
                },
                onSeekPage = { target ->
                    pageTurnCurrentPage = target
                    if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        pageTurnRequestedPage = target
                    } else {
                        val targetSpreadSlot = resolveSpreadSlotForPageIndex(target, novelSpreadColumns)
                        val virtualTarget = resolveComposePagerVirtualPageIndex(
                            actualPageIndex = targetSpreadSlot,
                            hasPreviousChapter = composePagerHasPreviousChapter,
                        )
                        coroutineScope.launch {
                            pagerState.scrollToPage(
                                virtualTarget.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0)),
                            )
                        }
                    }
                },
                onScrollToNativeIndex = { target ->
                    coroutineScope.launch {
                        textListState.scrollToItem(target)
                    }
                },
                onScrollToPagerPage = { target ->
                    coroutineScope.launch {
                        pagerState.scrollToPage(target)
                    }
                },
                onStopGeminiTranslation = onStopGeminiTranslation,
                onToggleGeminiTranslationVisibility = onToggleGeminiTranslationVisibility,
                onStartGeminiTranslation = { requestGeminiTranslationStart() },
                onStopGoogleTranslation = onStopGoogleTranslation,
                onToggleGoogleTranslationVisibility = onToggleGoogleTranslationVisibility,
                onStartGoogleTranslation = { requestGoogleTranslationStart() },
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
            )

            if (shouldShowPersistentProgressLine(showReaderUi = showReaderUi)) {
                val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(lineColor.copy(alpha = 0.18f)),
                )
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .fillMaxWidth(readingProgressPercent.coerceIn(0, 100) / 100f)
                        .height(2.dp)
                        .background(lineColor),
                )
            }

            NovelReaderTopBarPanel(
                visible = showReaderUi,
                novelTitle = state.novel.title,
                chapterName = state.chapter.name,
                chapterBookmarked = state.chapter.bookmark,
                autoScrollExpanded = autoScrollExpanded,
                usePageReader = usePageReader,
                autoScrollIntervalSeconds = state.readerSettings.autoScrollInterval,
                autoScrollAdaptiveDelay = state.readerSettings.autoScrollAdaptiveDelay,
                autoScrollSpeed = autoScrollSpeed,
                chapterEndBehavior = state.readerSettings.autoScrollChapterEndBehavior,
                autoScrollEndPauseMs = state.readerSettings.autoScrollEndPauseMs,
                autoScrollEnabled = autoScrollEnabled,
                showFloatingButton = state.readerSettings.showAutoScrollFloatingButton,
                adaptiveDelayCharacterCount = {
                    pageReaderCharacterCounts.getOrNull(pageReaderProgressPageIndex) ?: 0
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                onBack = onBack,
                onToggleBookmark = onToggleBookmark,
                onHapticTap = { appHaptics.tap() },
                onIntervalChange = { persistAutoScrollIntervalPreference(it) },
                onAdaptiveDelayChange = { persistAutoScrollAdaptiveDelayPreference(it) },
                onSpeedChange = { newSpeed ->
                    autoScrollSpeed = newSpeed
                    persistAutoScrollIntervalPreference(
                        interval = autoScrollSpeedToInterval(newSpeed),
                    )
                },
                onChapterEndBehaviorChange = { persistAutoScrollChapterEndBehaviorPreference(it) },
                onEndPauseMsChange = { persistAutoScrollEndPauseMsPreference(it) },
                onToggleAutoScroll = {
                    val nextState = resolveAutoScrollUiStateOnToggle(
                        currentEnabled = autoScrollEnabled,
                        showReaderUi = showReaderUi,
                        autoScrollExpanded = autoScrollExpanded,
                    )
                    autoScrollEnabled = nextState.autoScrollEnabled
                    if (!nextState.autoScrollEnabled) {
                        onCancelAutoScrollHandoff()
                        autoScrollEndStableFrames = 0
                        autoScrollEndDwellActive = false
                    }
                    onSetShowReaderUi(nextState.showReaderUi)
                    autoScrollExpanded = nextState.autoScrollExpanded
                },
                onShowFloatingButtonChange = {
                    readerPreferences.showAutoScrollFloatingButton().set(it)
                },
                onToggleExpanded = { autoScrollExpanded = !autoScrollExpanded },
            )

            NovelReaderBottomPanel(
                visible = showReaderUi,
                ttsEnabled = state.readerSettings.ttsEnabled,
                ttsUiState = state.ttsUiState,
                ttsStartRequest = currentTtsStartRequest,
                previousChapterId = state.previousChapterId,
                nextChapterId = state.nextChapterId,
                chapterWebUrl = state.chapterWebUrl,
                chapterUrl = state.chapter.url,
                novelUrl = state.novel.url,
                ttsPlacement = ttsPlacement,
                geminiEnabled = state.readerSettings.geminiEnabled,
                isGeminiTranslating = state.isGeminiTranslating,
                geminiButtonActiveLabel = stringResource(AYMR.strings.novel_reader_gemini_button_active),
                geminiButtonLabel = stringResource(AYMR.strings.novel_reader_gemini_button),
                googleTranslationEnabled = state.readerSettings.googleTranslationEnabled,
                isGoogleTranslating = state.isGoogleTranslating,
                hasGoogleTranslationCache = state.hasGoogleTranslationCache,
                isGoogleTranslationVisible = state.isGoogleTranslationVisible,
                dictionaryQuickAccessEnabled = onOpenDictionaryHistory != null &&
                    remember { Injekt.get<NovelReaderPreferences>().novelDictionaryQuickAccess() }
                        .collectAsState().value,
                onOpenDictionaryHistory = onOpenDictionaryHistory,
                onOpenPreviousChapter = onOpenPreviousChapter,
                onOpenNextChapter = onOpenNextChapter,
                onOpenChapterList = {
                    appHaptics.tap()
                    showChapterList = true
                },
                onOpenWebView = { url ->
                    if (url.isNotBlank()) {
                        context.startActivity(
                            WebViewActivity.newIntent(
                                context = context,
                                url = url,
                                sourceId = state.novel.source,
                                title = state.novel.title,
                            ),
                        )
                    }
                },
                onScrollToTop = {
                    coroutineScope.launch {
                        if (showWebView) {
                            webViewInstance?.scrollTo(0, 0)
                        } else if (usePageReader) {
                            pagerState.animateScrollToPage(0)
                        } else {
                            textListState.animateScrollToItem(0)
                        }
                    }
                },
                onOpenSettings = { showSettings = true },
                onOpenTtsBehaviorSettings = { showTtsBehaviorSettings = true },
                onOpenGeminiDialog = { showGeminiDialog = true },
                onOpenGoogleDialog = { showGoogleDialog = true },
                onToggleTtsPlayback = { onToggleTtsPlayback(currentTtsStartRequest) },
                onStopTtsPlayback = onStopTtsPlayback,
                onSkipPreviousTts = onSkipPreviousTts,
                onSkipNextTts = onSkipNextTts,
                onSetTtsEnginePackage = onSetTtsEnginePackage,
                onSetTtsVoiceId = onSetTtsVoiceId,
                onSetTtsLocaleTag = onSetTtsLocaleTag,
                onSetTtsSpeechRate = onSetTtsSpeechRate,
                onSetTtsPitch = onSetTtsPitch,
                onDisableTts = onDisableTts,
                onPreviewTtsVoice = onPreviewTtsVoice,
                onStopTtsVoicePreview = onStopTtsVoicePreview,
                onOpenPreviousChapterFromReader = { openPreviousChapterFromReader() },
                onOpenNextChapterFromReader = { openNextChapterFromReader() },
                navigationBarHeightPx = navigationBarHeight,
                density = density,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )

            SelectedTextTranslationOverlay(
                state = state,
                onTranslate = onTranslateSelectedText,
                onRetry = onRetrySelectedTextTranslation,
                onDismiss = onDismissSelectedTextTranslation,
                onLookupDefinition = onLookupSelectedTextDefinition,
                onRetryDictionary = onRetryNovelDictionary,
                onDismissDictionary = onDismissNovelDictionary,
                onPlayPronunciation = onPlaySelectedTextPronunciation,
                modifier = Modifier.align(Alignment.BottomEnd),
            )

            if (autoScrollEnabled) {
                autoScrollWasUsed = true
            }

            if (flashOnPageChange && eInkProfile.isEnabled) {
                DisplayRefreshHost(
                    hostState = displayRefreshHost,
                )
            }

            NovelReaderAutoScrollEndOverlay(
                visible = autoScrollEndDwellActive && autoScrollEnabled && !showReaderUi,
                nextChapterName = state.nextChapterName,
                remainingSeconds = autoScrollEndDwellRemainingSeconds,
                isEInkMode = eInkProfile.isEnabled || isEInkMode,
                onGoNow = {
                    autoScrollEndDwellActive = false
                    handleAutoScrollChapterEnd()
                },
                onStop = {
                    autoScrollEnabled = false
                    autoScrollEndDwellActive = false
                    autoScrollEndStableFrames = 0
                    onCancelAutoScrollHandoff()
                },
                onStay = {
                    autoScrollEnabled = false
                    autoScrollEndDwellActive = false
                    autoScrollEndStableFrames = 0
                    onCancelAutoScrollHandoff()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 72.dp),
            )

            AutoScrollActionFab(
                autoScrollEnabled = autoScrollEnabled,
                showFab = state.readerSettings.showAutoScrollFloatingButton && !showReaderUi,
                contentDescription = stringResource(
                    if (autoScrollEnabled) {
                        AYMR.strings.novel_reader_auto_scroll_pause_description
                    } else {
                        AYMR.strings.novel_reader_auto_scroll_play_description
                    },
                ),
                longClickLabel = stringResource(AYMR.strings.novel_reader_auto_scroll_settings_description),
                onClick = {
                    autoScrollEnabled = !autoScrollEnabled
                    if (autoScrollEnabled) {
                        onSetShowReaderUi(false)
                    } else {
                        onCancelAutoScrollHandoff()
                        autoScrollEndStableFrames = 0
                        autoScrollEndDwellActive = false
                    }
                },
                onLongClick = { autoScrollExpanded = !autoScrollExpanded },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )

            // Dialogs & sheets host
            NovelReaderDialogHost(
                showSettings = showSettings,
                showChapterList = showChapterList,
                showTtsBehaviorSettings = showTtsBehaviorSettings,
                showGeminiDialog = showGeminiDialog,
                showGoogleDialog = showGoogleDialog,
                translationSwitchRequest = translationSwitchRequest,
                state = state,
                showWebView = showWebView,
                usePageReader = usePageReader,
                ttsPlacement = ttsPlacement,
                actions = NovelReaderDialogActions(
                    onDismissSettings = { showSettings = false },
                    onDismissChapterList = { showChapterList = false },
                    onOpenBottomSheet = onOpenBottomSheet,
                    onOpenChapter = onOpenChapter,
                    onDownloadChapter = onDownloadChapter,
                    onDismissTtsBehaviorSettings = { showTtsBehaviorSettings = false },
                    onDismissGeminiDialog = { showGeminiDialog = false },
                    onDismissGoogleDialog = { showGoogleDialog = false },
                    onDismissTranslationSwitchRequest = { translationSwitchRequest = null },
                    requestGeminiTranslationStart = { requestGeminiTranslationStart() },
                    requestGoogleTranslationStart = { requestGoogleTranslationStart() },
                    onPrepareWholeBook = onPrepareWholeBook,
                    onStopGeminiTranslation = onStopGeminiTranslation,
                    onToggleGeminiTranslationVisibility = onToggleGeminiTranslationVisibility,
                    onClearGeminiTranslation = onClearGeminiTranslation,
                    onClearAllGeminiTranslationCache = onClearAllGeminiTranslationCache,
                    onAddAiTranslationLog = onAddAiTranslationLog,
                    onClearGeminiLogs = onClearGeminiLogs,
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
                    onStopGoogleTranslation = onStopGoogleTranslation,
                    onResumeGoogleTranslation = onResumeGoogleTranslation,
                    onToggleGoogleTranslationVisibility = onToggleGoogleTranslationVisibility,
                    onClearGoogleTranslation = onClearGoogleTranslation,
                    onSetGoogleTranslationAutoStart = onSetGoogleTranslationAutoStart,
                    onSetGoogleTranslationSourceLang = onSetGoogleTranslationSourceLang,
                    onSetGoogleTranslationTargetLang = onSetGoogleTranslationTargetLang,
                    onStartGeminiTranslation = onStartGeminiTranslation,
                    onStartGoogleTranslation = onStartGoogleTranslation,
                ),
            )

            activeImageActionsUrl?.let { imageUrl ->
                val localContext = androidx.compose.ui.platform.LocalContext.current
                NovelImageActionsDialog(
                    imageUrl = imageUrl,
                    onDismissRequest = { activeImageActionsUrl = null },
                    onSaveImage = {
                        coroutineScope.launch {
                            val file = NovelImageActionHelper.resolveImageFile(localContext, imageUrl)
                            if (file != null) {
                                val imageSaver = eu.kanade.tachiyomi.data.saver.ImageSaver(localContext)
                                val uri = runCatching {
                                    imageSaver.save(
                                        eu.kanade.tachiyomi.data.saver.Image.Page(
                                            inputStream = { file.inputStream() },
                                            name = "novel_${state.chapter.name}_${file.nameWithoutExtension}",
                                            location = eu.kanade.tachiyomi.data.saver.Location.Pictures("Novel"),
                                        ),
                                    )
                                }.getOrDefault(android.net.Uri.fromFile(file))
                                val notifier = eu.kanade.tachiyomi.ui.reader.SaveImageNotifier(localContext)
                                notifier.onComplete(uri)
                            } else {
                                val notifier = eu.kanade.tachiyomi.ui.reader.SaveImageNotifier(localContext)
                                notifier.onError("Failed to resolve image")
                            }
                        }
                    },
                    onShareImage = {
                        coroutineScope.launch {
                            val file = NovelImageActionHelper.resolveImageFile(localContext, imageUrl)
                            NovelImageActionHelper.shareImage(localContext, imageUrl, file)
                        }
                    },
                    onCopyLink = {
                        NovelImageActionHelper.copyToClipboard(localContext, "Image Link", imageUrl)
                    },
                )
            }
        }
    }
}
