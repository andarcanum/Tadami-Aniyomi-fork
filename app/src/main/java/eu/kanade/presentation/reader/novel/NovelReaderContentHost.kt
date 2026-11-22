package eu.kanade.presentation.reader.novel

import android.content.Context
import android.os.SystemClock
import android.text.TextPaint
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.tadami.aurora.BuildConfig
import eu.kanade.domain.easteregg.lattice.LatticeCarrier
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.easteregg.lattice.LatticeCarrierSlot
import eu.kanade.presentation.library.novel.quotes.NovelQuoteCardShareSheet
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
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.collectAsState as androidxCollectAsState

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
    chapterHighlights: Flow<List<NovelHighlight>> = emptyFlow(),
    // Novel-wide highlight items (joined with chapter names) for the panel/quotes surfaces.
    highlightItems: Flow<List<NovelHighlightWithChapter>> = emptyFlow(),
    defaultHighlightColor: Long = 0L,
    actions: NovelReaderScreenActions,
) {
    val onBack = actions.onBack
    val onReadingProgress = actions.onReadingProgress
    val onSeekBookModeProgress = actions.onSeekBookModeProgress
    val onToggleBookmark = actions.onToggleBookmark
    val onOpenDictionaryHistory = actions.onOpenDictionaryHistory
    val onStartGeminiTranslation = actions.onStartGeminiTranslation
    val onStartGeminiTranslationForProfile = actions.onStartGeminiTranslationForProfile
    val onClearSuppressedGeminiCompletion = actions.onClearSuppressedGeminiCompletion
    val onStopGeminiTranslation = actions.onStopGeminiTranslation
    val onToggleGeminiTranslationVisibility = actions.onToggleGeminiTranslationVisibility
    val onClearGeminiTranslation = actions.onClearGeminiTranslation
    val onClearGeminiTranslationForSwitch = actions.onClearGeminiTranslationForSwitch
    val onClearAllGeminiTranslationCache = actions.onClearAllGeminiTranslationCache
    val onAddAiTranslationLog = actions.onAddAiTranslationLog
    val onClearGeminiLogs = actions.onClearGeminiLogs
    val onSetTranslationProvider = actions.onSetTranslationProvider
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
    val onSetTtsSleepTimer = actions.onSetTtsSleepTimer
    val onSetTtsSleepTimerEndOfChapter = actions.onSetTtsSleepTimerEndOfChapter
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
    var requestedShowWebView by remember(
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
        requestedShowWebView = syncShowWebViewWithReaderSettings(
            currentShowWebView = requestedShowWebView,
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            pageReaderEnabled = state.readerSettings.pageReader,
            contentBlocksCount = state.contentBlocks.size,
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
            bookModeEnabled = isBookMode,
        )
    }
    val readerPreferences = remember { Injekt.get<NovelReaderPreferences>() }
    // Opt-in experimental chapter handoff: respects per-source overrides resolved into readerSettings.
    val seamlessChapterTransitionEnabled = state.readerSettings.seamlessChapterTransition
    val displayRefreshPreferences = remember { Injekt.get<ReaderPreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val flashOnPageChange by displayRefreshPreferences.flashOnPageChange().collectAsState()
    val eInkProfile by uiPreferences.eInkProfile().collectAsState()
    val displayRefreshHost = remember { DisplayRefreshHost() }
    val sourceId = state.novel.source
    // Reactive on purpose: toggling "For this source" in the settings dialog must immediately
    // redirect the auto-scroll preference writer below. A remember(sourceId) snapshot went stale
    // for the whole session, writing per-source changes into the global prefs (and recreating a
    // just-deleted override through updateSourceOverride's empty-default branch).
    val hasSourceOverrideFlow = remember(sourceId) {
        readerPreferences.sourceOverrides().changes().map { it.containsKey(sourceId) }
    }
    val hasSourceOverride by hasSourceOverrideFlow.androidxCollectAsState(
        initial = readerPreferences.getSourceOverride(sourceId) != null,
    )
    val documentOwnerKey: Any = if (isBookMode) {
        "book-${state.novel.id}"
    } else {
        state.chapter.id
    }
    // Viewport dimensions belong to the host container, not the chapter ID; resetting them on
    // seamless in-place chapter switches leaves IntSize.Zero active if onSizeChanged does not fire.
    var pageViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var hasCompletedInitialReaderLayout by remember { mutableStateOf(false) }
    val autoScrollHandoff = remember(documentOwnerKey) {
        onConsumeAutoScrollHandoff(state.chapter.id)
    }
    var autoScrollEnabled by remember(documentOwnerKey, autoScrollHandoff) {
        mutableStateOf(
            resolveInitialAutoScrollEnabled(
                savedPreferenceEnabled = state.readerSettings.autoScroll,
                handoff = autoScrollHandoff,
            ),
        )
    }
    var autoScrollSpeed by remember(documentOwnerKey, state.readerSettings.autoScrollInterval, autoScrollHandoff) {
        mutableIntStateOf(
            autoScrollHandoff?.speed ?: intervalToAutoScrollSpeed(state.readerSettings.autoScrollInterval),
        )
    }
    var autoScrollExpanded by remember(documentOwnerKey) { mutableStateOf(false) }
    var autoScrollWasUsed by remember(documentOwnerKey) { mutableStateOf(false) }
    var touchCooldownUntilNanos by remember(documentOwnerKey) { mutableLongStateOf(0L) }
    var speedFactor by remember(documentOwnerKey) { mutableFloatStateOf(1f) }
    var autoScrollEndStableFrames by remember(documentOwnerKey) { mutableIntStateOf(0) }
    var autoScrollEndDwellActive by remember(documentOwnerKey) { mutableStateOf(false) }
    var autoScrollEndDwellRemainingSeconds by remember(documentOwnerKey) { mutableIntStateOf(0) }
    var showGeminiDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var showGoogleDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var activeImageActionsUrl by remember(state.chapter.id) { mutableStateOf<String?>(null) }
    var activeImageViewerUrl by remember(state.chapter.id) { mutableStateOf<String?>(null) }
    var activeFootnoteDialog by remember(state.chapter.id) { mutableStateOf<NovelFootnoteDialogData?>(null) }
    var translationSwitchRequest by remember(state.chapter.id) {
        mutableStateOf<TranslationSwitchRequest?>(null)
    }
    var requestedTtsChapterSyncTarget by remember(state.chapter.id) { mutableStateOf<Long?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    // The chapter WebView must not leave the composition in the same frame the renderer decision
    // flips: removing the focused WebView re-focuses the window root, and that cascade re-enters
    // Compose layout inside applyChanges ("Cannot start a writer when another writer is pending").
    // The unmount lags one focus-safe frame; the mount direction stays immediate.
    val showWebView = rememberFocusSafeInteropUnmount(
        unmountRequested = !requestedShowWebView,
        prepareUnmount = { webViewInstance?.prepareForFocusSafeUnmount() },
        cancelPrepare = { webViewInstance?.cancelFocusSafeUnmount() },
    ).not()
    mountedReaderRenderer[0] = showWebView
    mountedRendererSwitchToken[0] = state.seamlessSwitchToken

    // Paint saved highlights into whichever WebView is mounted: re-applied when the WebView
    // becomes ready, when the highlight list changes, and after each document load from
    // onPageFinished.
    val chapterHighlightList by chapterHighlights.androidxCollectAsState(emptyList())
    val highlightItemList by highlightItems.androidxCollectAsState(emptyList())
    var editingHighlightId by remember { mutableStateOf<Long?>(null) }
    var showHighlightsPanel by remember { mutableStateOf(false) }
    LaunchedEffect(webViewInstance, chapterHighlightList) {
        webViewInstance?.applyNovelHighlightsToPage(chapterHighlightList)
    }

    val panelContext = LocalContext.current
    NovelHighlightsPanel(
        visible = showHighlightsPanel,
        novelTitle = state.novel.title,
        items = highlightItemList,
        defaultColorArgb = defaultHighlightColor,
        onDefaultColorChange = actions.onDefaultHighlightColorChanged,
        onDismiss = { showHighlightsPanel = false },
        onEdit = { highlightId ->
            showHighlightsPanel = false
            editingHighlightId = highlightId
        },
        onDelete = { highlightId -> actions.onDeleteHighlight(highlightId) },
        onCopy = { text ->
            val clipboard = panelContext.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("highlight", text))
            android.widget.Toast.makeText(
                panelContext,
                panelContext.getString(AYMR.strings.novel_highlight_action_copy.resourceId),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        },
    )

    val editingHighlightItem = editingHighlightId?.let { id ->
        highlightItemList.firstOrNull { it.highlight.id == id }
    }
    var cardShareItem by remember { mutableStateOf<NovelHighlightWithChapter?>(null) }
    if (editingHighlightItem != null) {
        val context = LocalContext.current
        NovelHighlightEditorSheet(
            item = editingHighlightItem,
            onDismiss = { editingHighlightId = null },
            onSave = { note, colorArgb ->
                actions.onUpdateHighlight(editingHighlightItem.highlight.id, note, colorArgb)
                // Выбранный цвет запоминается как последний использованный для новых выделений.
                actions.onDefaultHighlightColorChanged(colorArgb)
                editingHighlightId = null
            },
            onDelete = {
                actions.onDeleteHighlight(editingHighlightItem.highlight.id)
                editingHighlightId = null
            },
            onCopy = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("highlight", text))
                Toast.makeText(
                    context,
                    context.getString(AYMR.strings.novel_highlight_action_copy.resourceId),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onShare = { text ->
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(intent, null))
            },
            onShareCard = { note, colorArgb ->
                cardShareItem = editingHighlightItem.copy(
                    highlight = editingHighlightItem.highlight.copy(note = note, colorArgb = colorArgb),
                )
                editingHighlightId = null
            },
        )
    }
    cardShareItem?.let { item ->
        NovelQuoteCardShareSheet(
            item = item,
            onDismiss = { cardShareItem = null },
        )
    }
    var pendingProgrammaticTtsBlockIndex by remember(state.chapter.id) { mutableStateOf<Int?>(null) }
    var suppressManualTtsPauseUntilMs by remember(state.chapter.id) { mutableLongStateOf(0L) }
    val chapterHandoffTarget = remember(state.chapter.id) {
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff()
    }
    val initialWebRestoreProgressPercent = resolveInitialWebViewProgressPercent(
        savedWebProgressPercent = state.lastSavedWebProgressPercent,
        chapterHandoffTarget = chapterHandoffTarget,
    )
    val shouldHideWebViewUntilReveal = state.enableJs
    var webProgressPercent by remember(state.chapter.id) {
        mutableIntStateOf(initialWebRestoreProgressPercent)
    }
    var webAutoScrollNearEnd by remember(state.chapter.id) { mutableStateOf(false) }
    var shouldRestoreWebScroll by remember(state.chapter.id) { mutableStateOf(true) }

    /** Relative scroll percent (0-100) to restore after WebView reload when only settings/content of the same chapter changed (no chapter switch). -1 = no restore needed. Stored in a plain int array instead of Compose state because it is written from the AndroidView update block, where a snapshot state write would schedule recomposition during a layout pass. */
    val scrollTargetPercentAfterReload = remember(state.chapter.id) { intArrayOf(-1) }
    val lastWebScrollFraction = remember { floatArrayOf(-1f) }
    val anchoredWebViewWidthPx = remember { intArrayOf(0) }
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
        onReadingProgress(state.chapter.id, currentIndex, totalItems, persistedProgress, isInitialPositionRestored)
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
    fun requestGeminiTranslationStart(
        profileId: String? = null,
        forceRetranslate: Boolean = false,
    ) {
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
                    profileId = profileId,
                    forceRetranslate = forceRetranslate,
                )
            }
            profileId != null || forceRetranslate -> onStartGeminiTranslationForProfile(profileId, forceRetranslate)
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
    val globalTextColorOverride = parseReaderColor(state.readerSettings.globalTextColor)
    val textColor = when {
        isEInkMode -> AuroraTheme.colors.textPrimary
        else -> resolveEffectiveReaderTextColor(
            globalTextColor = globalTextColorOverride,
            isBackgroundMode = isBackgroundMode,
            backgroundModeTextColor = backgroundModeTextColor,
            themeModeTextColor = themeModeTextColor,
        )
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
        state.chapter.id,
        state.lastSavedIndex,
        state.lastSavedPageReaderProgress,
        state.contentBlocks.size,
        chapterHandoffTarget,
    ) {
        resolveInitialNativeReaderIndex(
            nativeLastSavedIndex = state.lastSavedIndex,
            savedPageReaderProgress = state.lastSavedPageReaderProgress,
            itemCount = state.contentBlocks.size,
            chapterHandoffTarget = chapterHandoffTarget,
        )
    }
    // Keyed by the novel while book mode is active: over a book the chapter anchor follows the
    // text, so keying the list state on it would recreate the list — and reset the scroll to the
    // book start — every time the reader crosses into another chapter and updateContent re-runs.
    val textListState = key(if (isBookMode) state.novel.id else state.chapter.id) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialNativeReaderIndex
                .coerceIn(0, (state.contentBlocks.lastIndex).coerceAtLeast(0)),
            initialFirstVisibleItemScrollOffset = if (
                chapterHandoffTarget != NovelReaderPageReaderHandoffTarget.SAVED ||
                state.lastSavedPageReaderProgress != null
            ) {
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
    val pageReaderImageBlocks = remember(state.chapter.id, scrollContentBlocks) {
        scrollContentBlocks.mapIndexedNotNull { index, block ->
            (block as? NovelReaderScreenModel.ContentBlock.Image)?.let { image ->
                PlainPageImageBlock(
                    sourceBlockIndex = index,
                    imageUrl = image.url,
                    contentDescription = image.alt,
                )
            }
        }
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
    val configWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val configHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val effectivePageViewportSize = resolveEffectivePageReaderViewportSize(
        measuredWidthPx = pageViewportSize.width,
        measuredHeightPx = pageViewportSize.height,
        configWidthPx = configWidthPx,
        configHeightPx = configHeightPx,
    )
    val novelSpreadColumns = resolveNovelSpreadColumns(
        twoPageLandscapeEnabled = state.readerSettings.twoPageLandscape,
        viewportWidthPx = effectivePageViewportSize.width,
        viewportHeightPx = effectivePageViewportSize.height,
        minSpreadWidthPx = novelSpreadMinWidthPx,
    )
    // Keep paper full-bleed, but reserve the same physical safe edges in native foreground
    // and pagination. The spread setting may opt out of camera protection, never side navigation.
    val pageHorizontalSafeInsets = rememberNovelHorizontalSafeInsets(
        includeDisplayCutout = novelSpreadColumns <= 1 || state.readerSettings.spreadCutoutGuard,
    )
    val spreadCutoutLeftPx = with(density) { pageHorizontalSafeInsets.left.roundToPx() }
    val spreadCutoutRightPx = with(density) { pageHorizontalSafeInsets.right.roundToPx() }
    val scrollHorizontalSafeInsets = rememberNovelHorizontalSafeInsets()
    val scrollCutoutLeftDp = scrollHorizontalSafeInsets.left
    val scrollCutoutRightDp = scrollHorizontalSafeInsets.right
    // A spread page's contentPadding is bumped by a small vertical safety so the first and last
    // lines stay clear of a device's rounded screen corners. Pagination must use the same bumped
    // value (via spreadContentPaddingPx below) so the rendered page and its text box agree.
    val spreadContentPadding = if (novelSpreadColumns > 1) {
        contentPaddingPx + with(density) { NOVEL_SPREAD_VERTICAL_SAFE_DP.dp }
    } else {
        contentPaddingPx
    }
    val spreadContentPaddingPx = with(density) { spreadContentPadding.roundToPx() }
    val pageReaderCutoutTopPx = rootInsets
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
        ?.top ?: 0
    val pageReaderCutoutTop = with(density) { pageReaderCutoutTopPx.toDp() }
    val pageReaderCutoutBottomPx = rootInsets
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
        ?.bottom ?: 0
    val isReaderFullScreen = state.readerSettings.fullScreenMode
    val pageReaderStatusBarTopPadding = resolvePageReaderStableTopPadding(
        isFullScreen = isReaderFullScreen,
        cutoutTopDp = pageReaderCutoutTop,
        statusBarTopPaddingDp = statusBarTopPadding,
    )
    val pageReaderNavigationBarHeightPx = resolvePageReaderStableNavigationBarHeightPx(
        isFullScreen = isReaderFullScreen,
        cutoutBottomPx = pageReaderCutoutBottomPx,
        navigationBarHeightPx = navigationBarHeight,
    )
    val shouldPaginateRichForPageReader = shouldUseRichNativePageRenderer(
        richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
        pageReaderEnabled = shouldPaginatePageReader,
        bionicReadingEnabled = state.readerSettings.bionicReading,
        richContentBlocks = state.richContentBlocks,
        richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
    )
    val shouldPaginatePlainForPageReader = shouldPaginatePageReader && !shouldPaginateRichForPageReader
    val baseMeasurementTextPaint = remember(context) {
        TextPaint().apply {
            set(TextView(context).paint)
        }
    }
    val activePageTransitionStyle = remember(state.readerSettings.pageTransitionStyle, isEInkMode) {
        resolveActivePageTransitionStyle(
            requestedStyle = state.readerSettings.pageTransitionStyle,
            pageTurnRendererSupported = true,
            isEInkMode = isEInkMode,
        )
    }
    // PageTextView enables DynamicLayout only when selectable (`setTextIsSelectable(true)`).
    // Non-selectable text renders via BoringLayout / StaticLayout.
    val pageReaderTextSelectable =
        state.readerSettings.textSelectionEnabled ||
            state.readerSettings.selectedTextTranslationEnabled ||
            state.readerSettings.novelDictionaryEnabled
    val currentPaginationLayoutKey = PagePaginationLayoutKey(
        showPageChapterTitle = showPageChapterTitle,
        fontSize = state.readerSettings.fontSize,
        lineHeight = state.readerSettings.lineHeight,
        margin = state.readerSettings.margin,
        textAlign = state.readerSettings.textAlign,
        preserveSourceTextAlignInNative = state.readerSettings.preserveSourceTextAlignInNative,
        paragraphSpacing = state.readerSettings.paragraphSpacing,
        forceParagraphIndent = state.readerSettings.forceParagraphIndent,
        bionicReading = state.readerSettings.bionicReading,
        forceBoldText = state.readerSettings.forceBoldText,
        forceItalicText = state.readerSettings.forceItalicText,
        richNativeRendererExperimental = state.readerSettings.richNativeRendererExperimental,
        hyphenationEnabled = state.readerSettings.hyphenationEnabled,
        layoutTextAlign = pageReaderLayoutTextAlign,
        composeTypeface = composeTypeface,
        chapterTitleTypeface = chapterTitleTypeface,
        viewportSize = effectivePageViewportSize,
        contentPaddingPx = contentPaddingPx,
        spreadContentPaddingPx = spreadContentPaddingPx,
        statusBarTopPadding = pageReaderStatusBarTopPadding,
        navigationBarHeightPx = pageReaderNavigationBarHeightPx,
        spreadColumns = novelSpreadColumns,
        spreadCutoutLeftPx = spreadCutoutLeftPx,
        spreadCutoutRightPx = spreadCutoutRightPx,
        densityScale = density.density,
        textSelectable = pageReaderTextSelectable,
    )
    val chapterPaginationCache = remember { NovelPagePaginationCache() }

    var plainPaginationState by remember {
        mutableStateOf(PlainPagePaginationResult())
    }
    LaunchedEffect(
        state.chapter.id,
        pageReaderTextBlocks,
        pageReaderImageBlocks,
        state.chapter.name,
        shouldPaginatePlainForPageReader,
        currentPaginationLayoutKey,
        baseMeasurementTextPaint,
    ) {
        val targetChapterId = state.chapter.id
        if (!shouldPaginatePlainForPageReader || (pageReaderTextBlocks.isEmpty() && pageReaderImageBlocks.isEmpty())) {
            plainPaginationState = PlainPagePaginationResult(
                chapterId = targetChapterId,
                pages = emptyList(),
                completed = true,
            )
            return@LaunchedEffect
        }
        val cachedPlain = chapterPaginationCache.getPlain(
            chapterId = targetChapterId,
            chapterName = state.chapter.name,
            layoutKey = currentPaginationLayoutKey,
            textBlocks = pageReaderTextBlocks,
            imageBlocks = pageReaderImageBlocks,
        )
        if (cachedPlain != null) {
            plainPaginationState = PlainPagePaginationResult(
                chapterId = targetChapterId,
                pages = cachedPlain.pages,
                completed = true,
            )
            return@LaunchedEffect
        }
        // Clear stale pages from a different chapter immediately, but retain the last valid
        // pagination result for the same chapter while reflow runs on rotation/font changes.
        if (plainPaginationState.chapterId != targetChapterId) {
            plainPaginationState = PlainPagePaginationResult()
        }
        val budget = resolveNovelPageReaderPageBudget(
            screenWidthPx = effectivePageViewportSize.width,
            screenHeightPx = effectivePageViewportSize.height,
            density = density,
            fontSize = state.readerSettings.fontSize,
            lineHeight = state.readerSettings.lineHeight,
            marginDp = state.readerSettings.margin,
            contentPaddingDp = contentPaddingPx,
            spreadContentPaddingPx = spreadContentPaddingPx,
            statusBarTopPaddingDp = pageReaderStatusBarTopPadding,
            navigationBarHeightPx = pageReaderNavigationBarHeightPx,
            spreadColumns = novelSpreadColumns,
            cutoutLeftPx = spreadCutoutLeftPx,
            cutoutRightPx = spreadCutoutRightPx,
        )
        val paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() }
        val textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() }
        val lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f)
        val forceParagraphIndent = state.readerSettings.forceParagraphIndent
        val chapterTitle = if (showPageChapterTitle) state.chapter.name else null
        val bionicReadingEnabled = state.readerSettings.bionicReading
        val forcedTypefaceStyle = resolveForcedReaderTypefaceStyle(
            forceBoldText = state.readerSettings.forceBoldText,
            forceItalicText = state.readerSettings.forceItalicText,
        )
        val densityScale = density.density
        val hyphenationEnabled = state.readerSettings.hyphenationEnabled
        val paintTemplate = TextPaint(baseMeasurementTextPaint)
        if (BuildConfig.DEBUG) {
            Log.d(
                PLAIN_PAGE_PAGINATION_LOG_TAG,
                buildString {
                    append("input")
                    append(" full=").append(isReaderFullScreen)
                    append(" viewport=").append(effectivePageViewportSize.width)
                        .append('x').append(effectivePageViewportSize.height)
                    append(" width=").append(budget.widthPx)
                    append(" spread=").append(novelSpreadColumns)
                    append(" top=").append(budget.topPaddingPx)
                    append(" bottom=").append(budget.bottomPaddingPx)
                    append(" bookInset=").append(budget.bookBottomInsetPx)
                    append(" fitSafety=0")
                    append(" navbar=").append(pageReaderNavigationBarHeightPx)
                    append(" cutoutBottom=").append(pageReaderCutoutBottomPx)
                    append(" budget=").append(budget.heightPx)
                    append(" textSizePx=").append(textSizePx)
                    append(" density=").append(densityScale)
                    append(" fontScale=").append(density.fontScale)
                    append(" bionic=").append(bionicReadingEnabled)
                    append(" forceBold=").append(state.readerSettings.forceBoldText)
                    append(" forceItalic=").append(state.readerSettings.forceItalicText)
                    append(" selectable=").append(pageReaderTextSelectable)
                    append(" hyphen=").append(hyphenationEnabled)
                },
            )
        }
        val computedPages = withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            val workerPaint = TextPaint(paintTemplate)
            val result = paginatePlainPageBlocksWithImages(
                textBlocks = pageReaderTextBlocks,
                imageBlocks = pageReaderImageBlocks,
                paragraphSpacingPx = paragraphSpacingPx,
                widthPx = budget.widthPx,
                heightPx = budget.heightPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = forceParagraphIndent,
                chapterTitle = chapterTitle,
                bionicReadingEnabled = bionicReadingEnabled,
                forcedTypefaceStyle = forcedTypefaceStyle,
                density = densityScale,
                measurementPaint = workerPaint,
                preferDynamicLayout = pageReaderTextSelectable,
                hyphenationEnabled = hyphenationEnabled,
            )
            coroutineContext.ensureActive()
            result
        }
        chapterPaginationCache.put(
            CachedChapterPagePagination.Plain(
                chapterId = targetChapterId,
                chapterName = state.chapter.name,
                layoutKey = currentPaginationLayoutKey,
                textBlocks = pageReaderTextBlocks,
                imageBlocks = pageReaderImageBlocks,
                pages = computedPages,
            ),
        )
        plainPaginationState = PlainPagePaginationResult(
            chapterId = targetChapterId,
            pages = computedPages,
            completed = true,
        )
    }

    var richPaginationState by remember {
        mutableStateOf(RichPagePaginationResult())
    }
    LaunchedEffect(
        state.chapter.id,
        pageReaderRichBlocks,
        shouldPaginateRichForPageReader,
        state.chapter.name,
        currentPaginationLayoutKey,
        baseMeasurementTextPaint,
    ) {
        val targetChapterId = state.chapter.id
        if (!shouldPaginateRichForPageReader) {
            richPaginationState = RichPagePaginationResult(
                chapterId = targetChapterId,
                pagination = MixedRichPagePagination(blockTexts = emptyList(), pages = emptyList()),
                completed = true,
            )
            return@LaunchedEffect
        }
        val cachedRich = chapterPaginationCache.getRich(
            chapterId = targetChapterId,
            chapterName = state.chapter.name,
            layoutKey = currentPaginationLayoutKey,
            richBlocks = pageReaderRichBlocks,
        )
        if (cachedRich != null) {
            richPaginationState = RichPagePaginationResult(
                chapterId = targetChapterId,
                pagination = cachedRich.pagination,
                completed = true,
            )
            return@LaunchedEffect
        }
        if (richPaginationState.chapterId != targetChapterId) {
            richPaginationState = RichPagePaginationResult()
        }
        val budget = resolveNovelPageReaderPageBudget(
            screenWidthPx = effectivePageViewportSize.width,
            screenHeightPx = effectivePageViewportSize.height,
            density = density,
            fontSize = state.readerSettings.fontSize,
            lineHeight = state.readerSettings.lineHeight,
            marginDp = state.readerSettings.margin,
            contentPaddingDp = contentPaddingPx,
            spreadContentPaddingPx = spreadContentPaddingPx,
            statusBarTopPaddingDp = pageReaderStatusBarTopPadding,
            navigationBarHeightPx = pageReaderNavigationBarHeightPx,
            spreadColumns = novelSpreadColumns,
            cutoutLeftPx = spreadCutoutLeftPx,
            cutoutRightPx = spreadCutoutRightPx,
        )
        val paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() }
        val textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() }
        val lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f)
        val textAlign = state.readerSettings.textAlign
        val preserveSourceTextAlignInNative = state.readerSettings.preserveSourceTextAlignInNative
        val forceParagraphIndent = state.readerSettings.forceParagraphIndent
        val chapterTitle = if (showPageChapterTitle) state.chapter.name else null
        val forcedTypefaceStyle = resolveForcedReaderTypefaceStyle(
            forceBoldText = state.readerSettings.forceBoldText,
            forceItalicText = state.readerSettings.forceItalicText,
        )
        val densityScale = density.density
        val hyphenationEnabled = state.readerSettings.hyphenationEnabled
        val paintTemplate = TextPaint(baseMeasurementTextPaint)
        if (BuildConfig.DEBUG) {
            Log.d(
                PLAIN_PAGE_PAGINATION_LOG_TAG,
                buildString {
                    append("richInput")
                    append(" blocks=").append(pageReaderRichBlocks.size)
                    append(" full=").append(isReaderFullScreen)
                    append(" viewport=").append(effectivePageViewportSize.width)
                        .append('x').append(effectivePageViewportSize.height)
                    append(" width=").append(budget.widthPx)
                    append(" budget=").append(budget.heightPx)
                    append(" textSizePx=").append(textSizePx)
                    append(" selectable=").append(pageReaderTextSelectable)
                    append(" hyphen=").append(hyphenationEnabled)
                },
            )
        }
        val computedPagination = withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            val workerPaint = TextPaint(paintTemplate)
            val result = paginateMixedRichPageBlocks(
                richBlocks = pageReaderRichBlocks,
                paragraphSpacingPx = paragraphSpacingPx,
                widthPx = budget.widthPx,
                heightPx = budget.heightPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = textAlign,
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                forceParagraphIndent = forceParagraphIndent,
                chapterTitle = chapterTitle,
                forcedTypefaceStyle = forcedTypefaceStyle,
                density = densityScale,
                measurementPaint = workerPaint,
                preferDynamicLayout = pageReaderTextSelectable,
                hyphenationEnabled = hyphenationEnabled,
            )
            coroutineContext.ensureActive()
            result
        }
        chapterPaginationCache.put(
            CachedChapterPagePagination.Rich(
                chapterId = targetChapterId,
                chapterName = state.chapter.name,
                layoutKey = currentPaginationLayoutKey,
                richBlocks = pageReaderRichBlocks,
                pagination = computedPagination,
            ),
        )
        richPaginationState = RichPagePaginationResult(
            chapterId = targetChapterId,
            pagination = computedPagination,
            completed = true,
        )
    }

    val promotedPlainPagination = if (
        shouldPaginatePlainForPageReader &&
        plainPaginationState.chapterId != state.chapter.id
    ) {
        chapterPaginationCache.getPlain(
            chapterId = state.chapter.id,
            chapterName = state.chapter.name,
            layoutKey = currentPaginationLayoutKey,
            textBlocks = pageReaderTextBlocks,
            imageBlocks = pageReaderImageBlocks,
        )
    } else {
        null
    }
    val promotedRichPagination = if (
        shouldPaginateRichForPageReader &&
        richPaginationState.chapterId != state.chapter.id
    ) {
        chapterPaginationCache.getRich(
            chapterId = state.chapter.id,
            chapterName = state.chapter.name,
            layoutKey = currentPaginationLayoutKey,
            richBlocks = pageReaderRichBlocks,
        )
    } else {
        null
    }

    val pageReaderPages: List<List<PlainPageSlice>> = when {
        plainPaginationState.chapterId == state.chapter.id -> plainPaginationState.pages
        promotedPlainPagination != null -> promotedPlainPagination.pages
        else -> emptyList()
    }
    val richPageReaderPagination = when {
        richPaginationState.chapterId == state.chapter.id -> richPaginationState.pagination
        promotedRichPagination != null -> promotedRichPagination.pagination
        else -> MixedRichPagePagination(blockTexts = emptyList(), pages = emptyList())
    }
    val richPageReaderBlockTexts = richPageReaderPagination.blockTexts
    val richPageReaderPages = richPageReaderPagination.pages
    val isPageReaderPaginationCompleted = if (shouldPaginateRichForPageReader) {
        (richPaginationState.chapterId == state.chapter.id && richPaginationState.completed) ||
            promotedRichPagination != null
    } else {
        (plainPaginationState.chapterId == state.chapter.id && plainPaginationState.completed) ||
            promotedPlainPagination != null
    }
    val usePageReader = shouldPaginatePageReader &&
        (!isPageReaderPaginationCompleted || pageReaderPages.isNotEmpty() || richPageReaderPages.isNotEmpty())
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
        state.chapter.name,
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
    fun buildSeamlessAdjacentPagesSlice(
        previewPages: List<NovelPageContentPage>,
        takeLastSpread: Boolean,
    ): SeamlessAdjacentPagesSlice {
        if (previewPages.isEmpty()) return SeamlessAdjacentPagesSlice()
        val targetSlot = if (takeLastSpread) {
            (resolveSpreadSlotCount(previewPages.size, novelSpreadColumns) - 1).coerceAtLeast(0)
        } else {
            0
        }
        val firstIndex = resolveSpreadSlotFirstPageIndex(targetSlot, novelSpreadColumns)
        val slicePages = (firstIndex until (firstIndex + novelSpreadColumns)).map { idx ->
            previewPages.getOrElse(idx) { NovelPageContentPage(emptyList()) }
        }
        return SeamlessAdjacentPagesSlice(
            pages = slicePages,
            firstPageNumber = firstIndex + 1,
            totalPages = previewPages.size,
        )
    }

    fun tryBuildSeamlessSliceFromCache(
        preview: NovelReaderScreenModel.AdjacentChapterPreview,
        takeLastSpread: Boolean,
        layoutKey: PagePaginationLayoutKey,
        paragraphSpacingPx: Int,
        forceParagraphIndent: Boolean,
    ): SeamlessAdjacentPagesSlice? {
        val previewScrollBlocks = preview.contentBlocks
        if (previewScrollBlocks.isEmpty()) return null
        val useRichForPreview = shouldUseRichNativePageRenderer(
            richNativeRendererExperimentalEnabled = layoutKey.richNativeRendererExperimental,
            pageReaderEnabled = true,
            bionicReadingEnabled = layoutKey.bionicReading,
            richContentBlocks = preview.richContentBlocks,
            richContentUnsupportedFeaturesDetected = preview.richContentUnsupportedFeaturesDetected,
        )
        val titleForFiltering = resolvePageReaderChapterTitleForFiltering(
            showPageChapterTitle = layoutKey.showPageChapterTitle,
            chapterTitle = preview.chapterName,
        )
        val chapterTitle = if (layoutKey.showPageChapterTitle) preview.chapterName else null
        return if (useRichForPreview) {
            val filteredRich = stripPageReaderChapterTitleRichBlocks(
                richBlocks = preview.richContentBlocks.withIndex().toList(),
                chapterTitle = titleForFiltering,
            )
            val cached = chapterPaginationCache.getRich(
                chapterId = preview.chapterId,
                chapterName = preview.chapterName,
                layoutKey = layoutKey,
                richBlocks = filteredRich,
            ) ?: return null
            val pages = normalizePageReaderContentPages(
                useRichPageReader = true,
                plainPages = emptyList(),
                richPages = cached.pagination.pages,
                plainTextBlocks = emptyList(),
                richBlockTexts = cached.pagination.blockTexts,
                paragraphSpacingPx = paragraphSpacingPx,
                forceParagraphIndent = forceParagraphIndent,
                chapterTitle = chapterTitle,
            )
            buildSeamlessAdjacentPagesSlice(pages, takeLastSpread)
        } else {
            val plainTextBlocks = stripPageReaderChapterTitleBlocks(
                textBlocks = previewScrollBlocks.mapIndexedNotNull { index, block ->
                    val text = (block as? NovelReaderScreenModel.ContentBlock.Text)?.text?.takeIf { it.isNotBlank() }
                        ?: return@mapIndexedNotNull null
                    PlainPageReaderTextBlock(sourceBlockIndex = index, text = text)
                },
                chapterTitle = titleForFiltering,
            )
            val plainImageBlocks = previewScrollBlocks.mapIndexedNotNull { index, block ->
                (block as? NovelReaderScreenModel.ContentBlock.Image)?.let { image ->
                    PlainPageImageBlock(
                        sourceBlockIndex = index,
                        imageUrl = image.url,
                        contentDescription = image.alt,
                    )
                }
            }
            val cached = chapterPaginationCache.getPlain(
                chapterId = preview.chapterId,
                chapterName = preview.chapterName,
                layoutKey = layoutKey,
                textBlocks = plainTextBlocks,
                imageBlocks = plainImageBlocks,
            ) ?: return null
            val pages = normalizePageReaderContentPages(
                useRichPageReader = false,
                plainPages = cached.pages,
                richPages = emptyList(),
                plainTextBlocks = plainTextBlocks,
                richBlockTexts = emptyList(),
                paragraphSpacingPx = paragraphSpacingPx,
                forceParagraphIndent = forceParagraphIndent,
                chapterTitle = chapterTitle,
            )
            buildSeamlessAdjacentPagesSlice(pages, takeLastSpread)
        }
    }

    fun paginateAdjacentPreviewSlice(
        preview: NovelReaderScreenModel.AdjacentChapterPreview?,
        takeLastSpread: Boolean,
        layoutKey: PagePaginationLayoutKey,
        budget: NovelPageReaderPageBudget,
        paragraphSpacingPx: Int,
        textSizePx: Float,
        lineHeightMultiplier: Float,
        textAlign: TextAlign,
        preserveSourceTextAlignInNative: Boolean,
        forceParagraphIndent: Boolean,
        bionicReadingEnabled: Boolean,
        forcedTypefaceStyle: Int,
        richNativeRendererExperimental: Boolean,
        hyphenationEnabled: Boolean,
        measurementPaint: TextPaint,
    ): AdjacentPreviewPaginationOutput {
        if (preview == null) return AdjacentPreviewPaginationOutput()
        val previewScrollBlocks = preview.contentBlocks
        if (previewScrollBlocks.isEmpty()) return AdjacentPreviewPaginationOutput()
        val useRichForPreview = shouldUseRichNativePageRenderer(
            richNativeRendererExperimentalEnabled = richNativeRendererExperimental,
            pageReaderEnabled = true,
            bionicReadingEnabled = bionicReadingEnabled,
            richContentBlocks = preview.richContentBlocks,
            richContentUnsupportedFeaturesDetected = preview.richContentUnsupportedFeaturesDetected,
        )
        val titleForFiltering = resolvePageReaderChapterTitleForFiltering(
            showPageChapterTitle = showPageChapterTitle,
            chapterTitle = preview.chapterName,
        )
        var cachedPagination: CachedChapterPagePagination? = null
        val previewPages = if (useRichForPreview) {
            val filteredRich = stripPageReaderChapterTitleRichBlocks(
                richBlocks = preview.richContentBlocks.withIndex().toList(),
                chapterTitle = titleForFiltering,
            )
            val richPagination = paginateMixedRichPageBlocks(
                richBlocks = filteredRich,
                paragraphSpacingPx = paragraphSpacingPx,
                widthPx = budget.widthPx,
                heightPx = budget.heightPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = textAlign,
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                forceParagraphIndent = forceParagraphIndent,
                chapterTitle = if (showPageChapterTitle) preview.chapterName else null,
                forcedTypefaceStyle = forcedTypefaceStyle,
                density = density.density,
                measurementPaint = measurementPaint,
                preferDynamicLayout = pageReaderTextSelectable,
                hyphenationEnabled = hyphenationEnabled,
                maxPages = null,
            )
            cachedPagination = CachedChapterPagePagination.Rich(
                chapterId = preview.chapterId,
                chapterName = preview.chapterName,
                layoutKey = layoutKey,
                richBlocks = filteredRich,
                pagination = richPagination,
            )
            normalizePageReaderContentPages(
                useRichPageReader = true,
                plainPages = emptyList(),
                richPages = richPagination.pages,
                plainTextBlocks = emptyList(),
                richBlockTexts = richPagination.blockTexts,
                paragraphSpacingPx = paragraphSpacingPx,
                forceParagraphIndent = forceParagraphIndent,
                chapterTitle = if (showPageChapterTitle) preview.chapterName else null,
            )
        } else {
            val plainTextBlocks = stripPageReaderChapterTitleBlocks(
                textBlocks = previewScrollBlocks.mapIndexedNotNull { index, block ->
                    val text = (block as? NovelReaderScreenModel.ContentBlock.Text)?.text?.takeIf { it.isNotBlank() }
                        ?: return@mapIndexedNotNull null
                    PlainPageReaderTextBlock(sourceBlockIndex = index, text = text)
                },
                chapterTitle = titleForFiltering,
            )
            val plainImageBlocks = previewScrollBlocks.mapIndexedNotNull { index, block ->
                (block as? NovelReaderScreenModel.ContentBlock.Image)?.let { image ->
                    PlainPageImageBlock(
                        sourceBlockIndex = index,
                        imageUrl = image.url,
                        contentDescription = image.alt,
                    )
                }
            }
            if (plainTextBlocks.isEmpty() && plainImageBlocks.isEmpty()) {
                emptyList()
            } else {
                val plainPages = paginatePlainPageBlocksWithImages(
                    textBlocks = plainTextBlocks,
                    imageBlocks = plainImageBlocks,
                    paragraphSpacingPx = paragraphSpacingPx,
                    widthPx = budget.widthPx,
                    heightPx = budget.heightPx,
                    textSizePx = textSizePx,
                    lineHeightMultiplier = lineHeightMultiplier,
                    typeface = composeTypeface,
                    chapterTitleTypeface = chapterTitleTypeface,
                    textAlign = pageReaderLayoutTextAlign,
                    forceParagraphIndent = forceParagraphIndent,
                    chapterTitle = if (showPageChapterTitle) preview.chapterName else null,
                    bionicReadingEnabled = bionicReadingEnabled,
                    forcedTypefaceStyle = forcedTypefaceStyle,
                    density = density.density,
                    measurementPaint = measurementPaint,
                    preferDynamicLayout = pageReaderTextSelectable,
                    hyphenationEnabled = hyphenationEnabled,
                    maxPages = null,
                )
                cachedPagination = CachedChapterPagePagination.Plain(
                    chapterId = preview.chapterId,
                    chapterName = preview.chapterName,
                    layoutKey = layoutKey,
                    textBlocks = plainTextBlocks,
                    imageBlocks = plainImageBlocks,
                    pages = plainPages,
                )
                normalizePageReaderContentPages(
                    useRichPageReader = false,
                    plainPages = plainPages,
                    richPages = emptyList(),
                    plainTextBlocks = plainTextBlocks,
                    richBlockTexts = emptyList(),
                    paragraphSpacingPx = paragraphSpacingPx,
                    forceParagraphIndent = forceParagraphIndent,
                    chapterTitle = if (showPageChapterTitle) preview.chapterName else null,
                )
            }
        }
        return AdjacentPreviewPaginationOutput(
            slice = buildSeamlessAdjacentPagesSlice(previewPages, takeLastSpread),
            cachedPagination = cachedPagination,
        )
    }
    val activeNextPreview = state.nextChapterPreview?.takeIf { it.chapterId == state.nextChapterId }
    var nextSeamlessPreviewState by remember {
        mutableStateOf(SeamlessPreviewPaginationResult())
    }
    LaunchedEffect(
        activeNextPreview,
        usePageReader,
        seamlessChapterTransitionEnabled,
        isBookMode,
        currentPaginationLayoutKey,
        baseMeasurementTextPaint,
    ) {
        if (
            activeNextPreview == null ||
            !usePageReader ||
            !seamlessChapterTransitionEnabled ||
            isBookMode ||
            activeNextPreview.contentBlocks.isEmpty()
        ) {
            nextSeamlessPreviewState = SeamlessPreviewPaginationResult()
            return@LaunchedEffect
        }
        val paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() }
        val forceParagraphIndent = state.readerSettings.forceParagraphIndent
        val cachedSlice = tryBuildSeamlessSliceFromCache(
            preview = activeNextPreview,
            takeLastSpread = false,
            layoutKey = currentPaginationLayoutKey,
            paragraphSpacingPx = paragraphSpacingPx,
            forceParagraphIndent = forceParagraphIndent,
        )
        if (cachedSlice != null) {
            nextSeamlessPreviewState = SeamlessPreviewPaginationResult(
                chapterId = activeNextPreview.chapterId,
                slice = cachedSlice,
            )
            return@LaunchedEffect
        }
        if (nextSeamlessPreviewState.chapterId != activeNextPreview.chapterId) {
            nextSeamlessPreviewState = SeamlessPreviewPaginationResult()
        }
        val budget = resolveNovelPageReaderPageBudget(
            screenWidthPx = effectivePageViewportSize.width,
            screenHeightPx = effectivePageViewportSize.height,
            density = density,
            fontSize = state.readerSettings.fontSize,
            lineHeight = state.readerSettings.lineHeight,
            marginDp = state.readerSettings.margin,
            contentPaddingDp = contentPaddingPx,
            spreadContentPaddingPx = spreadContentPaddingPx,
            statusBarTopPaddingDp = pageReaderStatusBarTopPadding,
            navigationBarHeightPx = pageReaderNavigationBarHeightPx,
            spreadColumns = novelSpreadColumns,
            cutoutLeftPx = spreadCutoutLeftPx,
            cutoutRightPx = spreadCutoutRightPx,
        )
        val textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() }
        val lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f)
        val textAlign = state.readerSettings.textAlign
        val preserveSourceTextAlignInNative = state.readerSettings.preserveSourceTextAlignInNative
        val bionicReading = state.readerSettings.bionicReading
        val forcedTypefaceStyle = resolveForcedReaderTypefaceStyle(
            forceBoldText = state.readerSettings.forceBoldText,
            forceItalicText = state.readerSettings.forceItalicText,
        )
        val richNativeRendererExperimental = state.readerSettings.richNativeRendererExperimental
        val hyphenationEnabled = state.readerSettings.hyphenationEnabled
        val paintTemplate = TextPaint(baseMeasurementTextPaint)
        val output = withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            val workerPaint = TextPaint(paintTemplate)
            val result = paginateAdjacentPreviewSlice(
                preview = activeNextPreview,
                takeLastSpread = false,
                layoutKey = currentPaginationLayoutKey,
                budget = budget,
                paragraphSpacingPx = paragraphSpacingPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                textAlign = textAlign,
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                forceParagraphIndent = forceParagraphIndent,
                bionicReadingEnabled = bionicReading,
                forcedTypefaceStyle = forcedTypefaceStyle,
                richNativeRendererExperimental = richNativeRendererExperimental,
                hyphenationEnabled = hyphenationEnabled,
                measurementPaint = workerPaint,
            )
            coroutineContext.ensureActive()
            result
        }
        output.cachedPagination?.let(chapterPaginationCache::put)
        nextSeamlessPreviewState = SeamlessPreviewPaginationResult(
            chapterId = activeNextPreview.chapterId,
            slice = output.slice,
        )
    }
    val nextChapterSeamlessPreview = if (
        activeNextPreview != null &&
        nextSeamlessPreviewState.chapterId == activeNextPreview.chapterId
    ) {
        nextSeamlessPreviewState.slice
    } else {
        SeamlessAdjacentPagesSlice()
    }

    val activePrevPreview = state.previousChapterPreview?.takeIf { it.chapterId == state.previousChapterId }
    var prevSeamlessPreviewState by remember {
        mutableStateOf(SeamlessPreviewPaginationResult())
    }
    LaunchedEffect(
        activePrevPreview,
        usePageReader,
        seamlessChapterTransitionEnabled,
        isBookMode,
        currentPaginationLayoutKey,
        baseMeasurementTextPaint,
    ) {
        if (
            activePrevPreview == null ||
            !usePageReader ||
            !seamlessChapterTransitionEnabled ||
            isBookMode ||
            activePrevPreview.contentBlocks.isEmpty()
        ) {
            prevSeamlessPreviewState = SeamlessPreviewPaginationResult()
            return@LaunchedEffect
        }
        val paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() }
        val forceParagraphIndent = state.readerSettings.forceParagraphIndent
        val cachedSlice = tryBuildSeamlessSliceFromCache(
            preview = activePrevPreview,
            takeLastSpread = true,
            layoutKey = currentPaginationLayoutKey,
            paragraphSpacingPx = paragraphSpacingPx,
            forceParagraphIndent = forceParagraphIndent,
        )
        if (cachedSlice != null) {
            prevSeamlessPreviewState = SeamlessPreviewPaginationResult(
                chapterId = activePrevPreview.chapterId,
                slice = cachedSlice,
            )
            return@LaunchedEffect
        }
        if (prevSeamlessPreviewState.chapterId != activePrevPreview.chapterId) {
            prevSeamlessPreviewState = SeamlessPreviewPaginationResult()
        }
        val budget = resolveNovelPageReaderPageBudget(
            screenWidthPx = effectivePageViewportSize.width,
            screenHeightPx = effectivePageViewportSize.height,
            density = density,
            fontSize = state.readerSettings.fontSize,
            lineHeight = state.readerSettings.lineHeight,
            marginDp = state.readerSettings.margin,
            contentPaddingDp = contentPaddingPx,
            spreadContentPaddingPx = spreadContentPaddingPx,
            statusBarTopPaddingDp = pageReaderStatusBarTopPadding,
            navigationBarHeightPx = pageReaderNavigationBarHeightPx,
            spreadColumns = novelSpreadColumns,
            cutoutLeftPx = spreadCutoutLeftPx,
            cutoutRightPx = spreadCutoutRightPx,
        )
        val textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() }
        val lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f)
        val textAlign = state.readerSettings.textAlign
        val preserveSourceTextAlignInNative = state.readerSettings.preserveSourceTextAlignInNative
        val bionicReading = state.readerSettings.bionicReading
        val forcedTypefaceStyle = resolveForcedReaderTypefaceStyle(
            forceBoldText = state.readerSettings.forceBoldText,
            forceItalicText = state.readerSettings.forceItalicText,
        )
        val richNativeRendererExperimental = state.readerSettings.richNativeRendererExperimental
        val hyphenationEnabled = state.readerSettings.hyphenationEnabled
        val paintTemplate = TextPaint(baseMeasurementTextPaint)
        val output = withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            val workerPaint = TextPaint(paintTemplate)
            val result = paginateAdjacentPreviewSlice(
                preview = activePrevPreview,
                takeLastSpread = true,
                layoutKey = currentPaginationLayoutKey,
                budget = budget,
                paragraphSpacingPx = paragraphSpacingPx,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                textAlign = textAlign,
                preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                forceParagraphIndent = forceParagraphIndent,
                bionicReadingEnabled = bionicReading,
                forcedTypefaceStyle = forcedTypefaceStyle,
                richNativeRendererExperimental = richNativeRendererExperimental,
                hyphenationEnabled = hyphenationEnabled,
                measurementPaint = workerPaint,
            )
            coroutineContext.ensureActive()
            result
        }
        output.cachedPagination?.let(chapterPaginationCache::put)
        prevSeamlessPreviewState = SeamlessPreviewPaginationResult(
            chapterId = activePrevPreview.chapterId,
            slice = output.slice,
        )
    }
    val previousChapterSeamlessPreview = if (
        activePrevPreview != null &&
        prevSeamlessPreviewState.chapterId == activePrevPreview.chapterId
    ) {
        prevSeamlessPreviewState.slice
    } else {
        SeamlessAdjacentPagesSlice()
    }
    val pageReaderRendererRoute = remember(usePageReader, activePageTransitionStyle) {
        resolvePageReaderRendererRoute(
            usePageReader = usePageReader,
            activeStyle = activePageTransitionStyle,
        )
    }
    val pagedLayoutIdentity = rememberNovelPageReaderLayoutIdentity(
        ownerKey = documentOwnerKey,
        contentPages = pageReaderContentPages,
        viewportSize = effectivePageViewportSize,
        spreadColumns = novelSpreadColumns,
        rendererRoute = pageReaderRendererRoute,
    )
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
    val composePagerBoundaryPagesEnabled = resolveComposePagerBoundaryPagesEnabled(
        seamlessChapterTransitionEnabled = seamlessChapterTransitionEnabled,
        activePageTransitionStyle = activePageTransitionStyle,
    )
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
    val pageReaderChapterHandoffTarget = chapterHandoffTarget
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
    val initialSavedRawProgress = remember(state.chapter.id, usePageReader) {
        state.lastSavedRawProgress
    }
    val initialContentPage = resolveInitialPageReaderPage(
        savedRawProgress = initialSavedRawProgress,
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
    var pageTurnCurrentPage by remember(state.chapter.id) {
        mutableIntStateOf(resolveSpreadSlotFirstPageIndex(initialContentSpreadSlot, novelSpreadColumns))
    }
    var pageTurnRequestedPage by remember(state.chapter.id) {
        mutableIntStateOf(-1)
    }
    val pageReaderTtsNavigationAdapter = remember(
        pagerState,
        pageReaderRendererRoute,
        composePagerHasPreviousChapter,
        novelSpreadColumns,
        pageReaderItemsCount,
    ) {
        PageReaderTtsNavigationAdapter(
            navigator = object : PageReaderTtsNavigator {
                override suspend fun scrollToPage(pageIndex: Int) {
                    val spreadSlot = resolveSpreadSlotForPageIndex(pageIndex, novelSpreadColumns)
                    val slotFirstPage = resolveSpreadSlotFirstPageIndex(spreadSlot, novelSpreadColumns)
                        .coerceIn(0, (pageReaderItemsCount - 1).coerceAtLeast(0))
                    pageTurnCurrentPage = slotFirstPage
                    if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        pageTurnRequestedPage = slotFirstPage
                    } else {
                        val targetPage = resolveComposePagerVirtualPageIndex(
                            actualPageIndex = spreadSlot,
                            hasPreviousChapter = composePagerHasPreviousChapter,
                        ).coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                        pagerState.scrollToPage(targetPage)
                    }
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
        state.readerSettings.preferWebViewRenderer,
        state.readerSettings.customCSS,
        state.readerSettings.customJS,
        state.richContentUnsupportedFeaturesDetected,
    ) {
        resolveNovelBookRendererDecision(
            pageReaderEnabled = state.readerSettings.pageReader,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            bionicReadingEnabled = state.readerSettings.bionicReading,
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            customStylesPresent = state.readerSettings.customCSS.isNotBlank() ||
                state.readerSettings.customJS.isNotBlank(),
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
        )
    }
    val useNativeBookScroll = isBookMode && !bookRendererDecision.renderer.usesWebView
    // Theme-derived TTS highlight palette (approved V4 auto-contrast): the accent adapts to the
    // luminance of the reader background, so the spoken paragraph stays visible on every
    // paper/parchment/dark surface. One source for the native span color, the chapter WebView
    // script and the book engine CSS override.
    val ttsHighlightPalette = resolveNovelTtsHighlightPalette(
        accent = MaterialTheme.colorScheme.primary,
        backgroundColor = textBackground,
    )
    val latestTtsHighlightPalette by rememberUpdatedState(ttsHighlightPalette)
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
            highlightCss = { latestTtsHighlightPalette.toChapterWebViewCss() },
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
    // Wait for real viewport layout before restoring or committing initial progress, and never
    // re-run initial restore once applied for the active chapter.
    val appliedPagerRestoreChapterId = remember { longArrayOf(-1L) }
    var pageTurnChapterNavigationRequest by remember(state.chapter.id) {
        mutableStateOf<PageTurnChapterNavigationRequest?>(null)
    }
    var pageTurnChapterNavigationRequestToken by remember(state.chapter.id) {
        mutableStateOf(0L)
    }
    var currentPageAnchor by remember(state.chapter.id) {
        mutableStateOf<NovelPageReaderAnchor?>(null)
    }
    var acknowledgedPagedLayoutIdentity by remember(state.chapter.id) {
        mutableStateOf<Any?>(null)
    }
    var anchoredSpreadColumns by remember(state.chapter.id) {
        mutableIntStateOf(novelSpreadColumns)
    }
    var anchoredRendererRoute by remember(state.chapter.id) {
        mutableStateOf(pageReaderRendererRoute)
    }
    var isInitialPositionRestored by remember(state.chapter.id, usePageReader) {
        mutableStateOf(false)
    }

    val expectedInitialRestoredPage = resolveSpreadSlotFirstPageIndex(
        initialContentSpreadSlot,
        novelSpreadColumns,
    )
    LaunchedEffect(
        state.chapter.id,
        usePageReader,
        hasCompletedInitialReaderLayout,
        pageReaderRendererRoute,
        pageReaderItemsCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
        initialPagerPage,
        expectedInitialRestoredPage,
    ) {
        if (!usePageReader) {
            if (appliedPagerRestoreChapterId[0] == state.chapter.id) {
                appliedPagerRestoreChapterId[0] = -1L
            }
            return@LaunchedEffect
        }
        if (appliedPagerRestoreChapterId[0] == state.chapter.id) return@LaunchedEffect
        // Book mode is one continuous document: the current chapter changes while reading and the
        // pager position must never be reset under the reader.
        if (isBookMode) {
            appliedPagerRestoreChapterId[0] = state.chapter.id
            return@LaunchedEffect
        }
        // Wait for measured viewport + pages so fallback frame-1 dimensions never lock in page 0.
        if (!hasCompletedInitialReaderLayout || pageReaderItemsCount <= 0) return@LaunchedEffect
        val restoredRealPage = initialContentPage.coerceIn(0, pageReaderItemsCount - 1)
        currentPageAnchor = capturePageReaderAnchor(pageReaderContentPages, restoredRealPage)
        if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
            val restoreWon = pagerState.currentPage == initialPagerPage ||
                restoreNovelPagerPageAfterUserInput(pagerState, initialPagerPage)
            val settledRealPage = if (restoreWon) {
                expectedInitialRestoredPage
            } else {
                val settledSlot = resolveComposePagerActualPageIndex(
                    currentPage = pagerState.currentPage,
                    contentPageCount = pageReaderSpreadSlotCount,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                ).coerceIn(0, (pageReaderSpreadSlotCount - 1).coerceAtLeast(0))
                resolveSpreadSlotFirstPageIndex(settledSlot, novelSpreadColumns)
                    .coerceIn(0, pageReaderItemsCount - 1)
            }
            if (!restoreWon) {
                currentPageAnchor = capturePageReaderAnchor(pageReaderContentPages, settledRealPage)
                isInitialPositionRestored = true
            }
            pageTurnCurrentPage = settledRealPage
        } else {
            val needsPageTurnSnap = pageTurnCurrentPage != expectedInitialRestoredPage
            pageTurnCurrentPage = expectedInitialRestoredPage
            if (needsPageTurnSnap) {
                pageTurnRequestedPage = expectedInitialRestoredPage
            } else {
                isInitialPositionRestored = true
            }
        }
        appliedPagerRestoreChapterId[0] = state.chapter.id
        acknowledgedPagedLayoutIdentity = pagedLayoutIdentity
        anchoredSpreadColumns = novelSpreadColumns
        anchoredRendererRoute = pageReaderRendererRoute
    }

    // A seamless in-place chapter switch reuses this composition, so the scrolled reader keeps the
    // lazy list state of the previous chapter and stays near its end instead of jumping to the new
    // chapter's saved position. The pager route above resets itself the same way.
    val appliedNativeScrollRestoreChapterId = remember { longArrayOf(-1L) }
    LaunchedEffect(state.chapter.id, nativeScrollItemsCount, chapterHandoffTarget) {
        if (appliedNativeScrollRestoreChapterId[0] == state.chapter.id) return@LaunchedEffect
        // Book mode is one continuous document: the current chapter changes while reading and the
        // list position must never be reset under the reader.
        if (isBookMode) {
            appliedNativeScrollRestoreChapterId[0] = state.chapter.id
            return@LaunchedEffect
        }
        if (nativeScrollItemsCount <= 0) return@LaunchedEffect
        appliedNativeScrollRestoreChapterId[0] = state.chapter.id
        val targetIndex = resolveInitialNativeReaderIndex(
            nativeLastSavedIndex = state.lastSavedIndex,
            savedPageReaderProgress = state.lastSavedPageReaderProgress,
            itemCount = nativeScrollItemsCount,
            chapterHandoffTarget = chapterHandoffTarget,
        ).coerceIn(0, (nativeScrollItemsCount - 1).coerceAtLeast(0))
        val targetOffset = if (
            chapterHandoffTarget != NovelReaderPageReaderHandoffTarget.SAVED ||
            state.lastSavedPageReaderProgress != null
        ) {
            0
        } else {
            state.lastSavedScrollOffsetPx.coerceAtLeast(0)
        }
        textListState.scrollToItem(targetIndex, targetOffset)
    }

    var anchoredNativeScrollState by remember(documentOwnerKey) {
        mutableStateOf<NovelNativeScrollViewportAnchor?>(null)
    }
    LaunchedEffect(
        documentOwnerKey,
        textListState,
        nativeScrollItemsCount,
        usePageReader,
        showWebView,
        isBookMode,
        pageViewportSize.width,
    ) {
        if (isBookMode || usePageReader || showWebView || nativeScrollItemsCount <= 0) {
            return@LaunchedEffect
        }
        val targetWidthPx = pageViewportSize.width
        if (targetWidthPx <= 0) return@LaunchedEffect

        val previousAnchor = anchoredNativeScrollState
        if (
            previousAnchor != null &&
            previousAnchor.viewportWidthPx > 0 &&
            previousAnchor.viewportWidthPx != targetWidthPx &&
            appliedNativeScrollRestoreChapterId[0] == state.chapter.id
        ) {
            val safeIndex = previousAnchor.firstVisibleItemIndex.coerceIn(0, nativeScrollItemsCount - 1)
            val restored = restoreNovelNativeScrollViewportAnchor(
                listState = textListState,
                anchor = previousAnchor,
                targetItemIndex = safeIndex,
            )
            if (restored) {
                anchoredNativeScrollState = previousAnchor.copy(
                    firstVisibleItemIndex = safeIndex,
                    viewportWidthPx = targetWidthPx,
                )
            }
        }

        snapshotFlow {
            val visibleItem = textListState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == textListState.firstVisibleItemIndex
            } ?: textListState.layoutInfo.visibleItemsInfo.firstOrNull()
            val fraction = visibleItem?.let {
                resolveNativeScrollItemFraction(
                    scrollOffsetPx = textListState.firstVisibleItemScrollOffset,
                    itemHeightPx = it.size,
                )
            } ?: 0f
            textListState.firstVisibleItemIndex to fraction
        }
            .distinctUntilChanged()
            .collectLatest { (index, fraction) ->
                if (appliedNativeScrollRestoreChapterId[0] == state.chapter.id) {
                    anchoredNativeScrollState = NovelNativeScrollViewportAnchor(
                        firstVisibleItemIndex = index,
                        withinItemFraction = fraction,
                        viewportWidthPx = targetWidthPx,
                    )
                }
            }
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

    val isPagedReflowPending = usePageReader &&
        !isBookMode &&
        hasCompletedInitialReaderLayout &&
        isInitialPositionRestored &&
        appliedPagerRestoreChapterId[0] == state.chapter.id &&
        pageReaderItemsCount > 0 &&
        (
            acknowledgedPagedLayoutIdentity !== pagedLayoutIdentity ||
                anchoredSpreadColumns != novelSpreadColumns ||
                anchoredRendererRoute != pageReaderRendererRoute
            )
    val syncedPagedReflowTargetPage = if (isPagedReflowPending) {
        val anchor = currentPageAnchor
        if (anchor != null) {
            resolveAnchoredPageReaderPageIndex(anchor, pageReaderContentPages)
        } else {
            pageReaderProgressPageIndex.coerceIn(0, (pageReaderItemsCount - 1).coerceAtLeast(0))
        }
    } else {
        null
    }
    val suppressBoundaryChapterNavigation = !hasCompletedInitialReaderLayout ||
        !isInitialPositionRestored ||
        isPagedReflowPending ||
        pageTurnRequestedPage >= 0

    LaunchedEffect(
        state.chapter.id,
        usePageReader,
        hasCompletedInitialReaderLayout,
        isInitialPositionRestored,
        pagedLayoutIdentity,
        novelSpreadColumns,
        pageReaderRendererRoute,
        composePagerHasPreviousChapter,
    ) {
        if (!usePageReader || isBookMode) return@LaunchedEffect
        if (
            !hasCompletedInitialReaderLayout ||
            !isInitialPositionRestored ||
            appliedPagerRestoreChapterId[0] != state.chapter.id ||
            pageReaderItemsCount <= 0
        ) {
            return@LaunchedEffect
        }
        val reflowNeeded = acknowledgedPagedLayoutIdentity !== pagedLayoutIdentity ||
            anchoredSpreadColumns != novelSpreadColumns ||
            anchoredRendererRoute != pageReaderRendererRoute
        if (!reflowNeeded) return@LaunchedEffect

        val targetRealPage = (
            syncedPagedReflowTargetPage
                ?: pageReaderProgressPageIndex.coerceIn(0, pageReaderItemsCount - 1)
            ).coerceIn(0, pageReaderItemsCount - 1)
        val targetSlot = resolveSpreadSlotForPageIndex(targetRealPage, novelSpreadColumns)
        val slotFirstPage = resolveSpreadSlotFirstPageIndex(targetSlot, novelSpreadColumns)
        val maxVirtualPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
            (composePagerVirtualPageCount - 1).coerceAtLeast(0)
        } else {
            (pageReaderSpreadSlotCount - 1).coerceAtLeast(0)
        }
        val targetVirtualPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
            resolveComposePagerVirtualPageIndex(
                actualPageIndex = targetSlot,
                hasPreviousChapter = composePagerHasPreviousChapter,
            )
        } else {
            targetSlot
        }.coerceIn(0, maxVirtualPage)

        // Preserve existing fine-grained anchor across rotation/reflow; never re-capture the start
        // of the new wider page or rotating back will rewind to that wider page's first block.
        currentPageAnchor = resolvePageReaderAnchorAfterReflow(
            existingAnchor = currentPageAnchor,
            contentPages = pageReaderContentPages,
            targetPageIndex = targetRealPage,
        )
        if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
            // Scroll pager BEFORE clearing acknowledgedPagedLayoutIdentity so `isPagedReflowPending` stays true
            // until the pager lands on `targetVirtualPage` and never self-cancels mid-scroll.
            val restoreWon = pagerState.currentPage == targetVirtualPage ||
                restoreNovelPagerPageAfterUserInput(pagerState, targetVirtualPage)
            val settledRealPage = if (restoreWon) {
                slotFirstPage
            } else {
                val settledSlot = resolveComposePagerActualPageIndex(
                    currentPage = pagerState.currentPage,
                    contentPageCount = pageReaderSpreadSlotCount,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                ).coerceIn(0, (pageReaderSpreadSlotCount - 1).coerceAtLeast(0))
                resolveSpreadSlotFirstPageIndex(settledSlot, novelSpreadColumns)
                    .coerceIn(0, pageReaderItemsCount - 1)
            }
            if (!restoreWon) {
                currentPageAnchor = capturePageReaderAnchor(pageReaderContentPages, settledRealPage)
            }
            pageTurnCurrentPage = settledRealPage
            acknowledgedPagedLayoutIdentity = pagedLayoutIdentity
            anchoredSpreadColumns = novelSpreadColumns
            anchoredRendererRoute = pageReaderRendererRoute
        } else {
            pageTurnCurrentPage = slotFirstPage
            pageTurnRequestedPage = slotFirstPage
            acknowledgedPagedLayoutIdentity = pagedLayoutIdentity
            anchoredSpreadColumns = novelSpreadColumns
            anchoredRendererRoute = pageReaderRendererRoute
        }
    }

    LaunchedEffect(
        state.chapter.id,
        hasCompletedInitialReaderLayout,
        pageReaderItemsCount,
        expectedInitialRestoredPage,
        pageReaderProgressPageIndex,
        pageTurnRequestedPage,
    ) {
        if (
            hasCompletedInitialReaderLayout &&
            appliedPagerRestoreChapterId[0] == state.chapter.id &&
            pageReaderItemsCount > 0 &&
            pageTurnRequestedPage < 0 &&
            pageReaderProgressPageIndex == expectedInitialRestoredPage
        ) {
            isInitialPositionRestored = true
        }
    }

    LaunchedEffect(
        state.chapter.id,
        usePageReader,
        hasCompletedInitialReaderLayout,
        isInitialPositionRestored,
        isPagedReflowPending,
        pageTurnRequestedPage,
        pageReaderProgressPageIndex,
        pageReaderItemsCount,
    ) {
        if (
            !usePageReader ||
            isBookMode ||
            !hasCompletedInitialReaderLayout ||
            !isInitialPositionRestored ||
            isPagedReflowPending ||
            pageTurnRequestedPage >= 0 ||
            pageReaderItemsCount <= 0
        ) {
            return@LaunchedEffect
        }
        currentPageAnchor = resolveNextPageReaderAnchor(
            existingAnchor = currentPageAnchor,
            contentPages = pageReaderContentPages,
            settledPageIndex = pageReaderProgressPageIndex,
            spreadColumns = novelSpreadColumns,
        )
    }
    val latestPageReaderProgressPageIndex by rememberUpdatedState(pageReaderProgressPageIndex)
    val latestPageReaderItemsCount by rememberUpdatedState(pageReaderItemsCount)
    val latestHasCompletedInitialReaderLayout by rememberUpdatedState(hasCompletedInitialReaderLayout)
    val latestIsInitialPositionRestored by rememberUpdatedState(isInitialPositionRestored)
    val latestIsPagedReflowPending by rememberUpdatedState(isPagedReflowPending)
    val latestPageTurnRequestedPage by rememberUpdatedState(pageTurnRequestedPage)
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
                    page.filterIsInstance<PlainPageSlice.Text>().map { slice ->
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
        textListState,
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
        val targetChapterId = resolveTtsChapterNavigationTarget(
            pendingChapterHandoffId = state.ttsUiState.pendingChapterHandoffId,
            currentChapterId = state.chapter.id,
            activeTtsChapterId = state.ttsUiState.activeSession?.chapterId,
            nextChapterId = state.nextChapterId,
        ) ?: return@LaunchedEffect
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
        isBookMode,
        richScrollBlocks,
    ) {
        buildNovelReaderTtsHighlightState(
            activeUtterance = state.ttsUiState.activeSession?.utterance,
            activeSourceBlockIndex = state.ttsUiState.activeSourceBlockIndex,
            activeUtteranceText = state.ttsUiState.activeUtteranceText,
            activeWordRange = state.ttsUiState.activeWordRange,
            activeHighlightMode = state.ttsUiState.activeHighlightMode,
            isBookMode = isBookMode,
            usePageReader = usePageReader,
            pageReaderProgressPageIndex = pageReaderProgressPageIndex,
            activePageReaderTtsAnchors = activePageReaderTtsAnchors,
            bookTtsBlockAnchor = bookTtsBlockAnchor,
            richScrollBlocks = richScrollBlocks,
        )
    }
    val ttsHighlightColor = ttsHighlightPalette.paragraphBackground
    val readingProgressPercent by remember(
        showWebView,
        webProgressPercent,
        nativeScrollItemsCount,
        pageReaderItemsCount,
        pageReaderProgressPageIndex,
        textListState,
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
    val bookFlipPageAnimationDurationMillis = resolveComposePagerPageTurnAnimationDurationMillis(
        transitionStyle = activePageTransitionStyle,
        bookFlipAnimationSpeed = state.readerSettings.bookFlipAnimationSpeed,
        pageTurnSpeed = state.readerSettings.pageTurnSpeed,
    )

    // Chapter navigation and auto-scroll end handling live in a plain class constructed per
    // composition (deliberately NOT wrapped in remember{}): on a seamless in-place chapter switch
    // `state`, `onOpen*Chapter`, and every `remember(state.chapter.id)` state delegate are
    // replaced, so remembering this object would leave it pinned to the first chapter's state.
    val chapterNavigator = NovelReaderChapterNavigator(
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
    fun requestPageTurnChapterNavigation(direction: PageTurnChapterNavigationDirection) =
        chapterNavigator.requestPageTurnChapterNavigation(direction)

    fun openPreviousChapterFromReader() {
        clearFocusedNovelReaderTextView(view)
        chapterNavigator.openPreviousChapterFromReader()
    }

    fun isAtEndOfBook(): Boolean =
        chapterNavigator.isAtEndOfBook()

    fun openNextChapterFromReader() {
        clearFocusedNovelReaderTextView(view)
        chapterNavigator.openNextChapterFromReader()
    }

    fun handleAutoScrollChapterEnd() =
        chapterNavigator.handleAutoScrollChapterEnd()

    suspend fun handleAutoScrollStableChapterEndAfterDwell() =
        chapterNavigator.handleAutoScrollStableChapterEndAfterDwell()

    suspend fun moveBackwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        clearFocusedNovelReaderTextView(view)
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
            if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.PREVIOUS)
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
        clearFocusedNovelReaderTextView(view)
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
            if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.NEXT)
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
    val latestBookModeState by rememberUpdatedState(state.bookMode)
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

    LaunchedEffect(view, showReaderUi) {
        if (showReaderUi) {
            clearFocusedNovelReaderTextView(view)
        }
    }

    LaunchedEffect(view, textListState) {
        snapshotFlow { textListState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) {
                    clearFocusedNovelReaderTextView(view)
                }
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
        // A synchronous focus request here runs a focus search during composition apply,
        // force-remeasuring Lazy subcompositions while the outer composition still has pending
        // modifications — crashing with ComposeRuntimeError. Defer it to the next main-loop pass.
        view.post { view.requestFocus() }
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
        if (isBookMode) Unit else state.nextChapterId,
        state.readerSettings.autoScrollChapterEndBehavior,
        if (isBookMode) Unit else pageReaderItemsCount,
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
                            resolveBookAutoScrollHasSectionsLeft(
                                sectionCount = latestBookModeState.sectionCount,
                                currentSectionIndex = latestBookModeState.currentSectionIndex,
                                renderedSectionIndices = latestBookModeState.renderedSectionIndices,
                                preparingSectionIndices = latestBookModeState.preparingSectionIndices,
                                failedSectionIndices = latestBookModeState.failedSectionIndices,
                                isReady = latestBookModeState.isReady,
                            )
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
                target?.syncAutoScrollSpeed(0f)
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
                target.syncAutoScrollSpeed(0f)
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

            val basePhysicalRate = autoScrollPixelsPerSecond(autoScrollSpeed)
            val physicalRate = basePhysicalRate * speedFactor
            if (target.syncAutoScrollSpeed(physicalRate)) {
                autoScrollController.resetFrameState()
                delay(16)
                val canForward = target.canScrollForward()
                val frameResult = autoScrollController.onScrolled(
                    canScrollForward = canForward,
                    scrollConsumedPx = if (canForward) 1f else 0f,
                    isContentReady = autoScrollContentReady,
                    hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                    hasRenderableItems = autoScrollHasRenderableItems,
                )
                autoScrollEndStableFrames = autoScrollController.stableEndFrames
                if (frameResult == NovelAutoScrollFrameResult.ReachedEnd) {
                    target.syncAutoScrollSpeed(0f)
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
                    isBookMode = state.bookMode.isEnabled,
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
                        LaunchedEffect(
                            pageReaderProgressPageIndex,
                            pageReaderItemsCount,
                            isInitialPositionRestored,
                            hasCompletedInitialReaderLayout,
                            isPagedReflowPending,
                            pageTurnRequestedPage,
                        ) {
                            if (
                                !hasCompletedInitialReaderLayout ||
                                !isInitialPositionRestored ||
                                pageReaderItemsCount <= 0 ||
                                isPagedReflowPending ||
                                pageTurnRequestedPage >= 0
                            ) {
                                return@LaunchedEffect
                            }
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
                                val latestTotal = latestPageReaderItemsCount
                                if (
                                    shouldReportPageReaderProgressOnDispose(
                                        hasCompletedInitialLayout = latestHasCompletedInitialReaderLayout,
                                        isInitialPositionRestored = latestIsInitialPositionRestored,
                                        isPagedReflowPending = latestIsPagedReflowPending,
                                        requestedPage = latestPageTurnRequestedPage,
                                        totalItems = latestTotal,
                                    )
                                ) {
                                    val latestIndex = latestPageReaderProgressPageIndex
                                    reportReadingProgress(
                                        latestIndex,
                                        latestTotal,
                                        encodePageReaderProgress(
                                            index = latestIndex,
                                            totalItems = latestTotal,
                                        ),
                                        isInitialPositionRestored = latestIsInitialPositionRestored,
                                    )
                                }
                            }
                        }
                    } else {
                        LaunchedEffect(
                            state.chapter.id,
                            textListState,
                            textListState.firstVisibleItemIndex,
                            textListState.canScrollForward,
                            nativeScrollItemsCount,
                        ) {
                            if (
                                !shouldTrackNativeScrollProgress(
                                    totalItemsCount = textListState.layoutInfo.totalItemsCount,
                                    hasVisibleItems = textListState.layoutInfo.visibleItemsInfo.isNotEmpty(),
                                    restoredChapterId = appliedNativeScrollRestoreChapterId[0],
                                    currentChapterId = state.chapter.id,
                                )
                            ) {
                                return@LaunchedEffect
                            }
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
                        DisposableEffect(state.chapter.id, textListState, nativeScrollItemsCount) {
                            onDispose {
                                if (
                                    !shouldTrackNativeScrollProgress(
                                        totalItemsCount = textListState.layoutInfo.totalItemsCount,
                                        hasVisibleItems = textListState.layoutInfo.visibleItemsInfo.isNotEmpty(),
                                        restoredChapterId = appliedNativeScrollRestoreChapterId[0],
                                        currentChapterId = state.chapter.id,
                                    )
                                ) {
                                    return@onDispose
                                }
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
                    // Raw dp -> CSS px (WebView scales CSS px by devicePixelRatio == density).
                    val bookReaderPaddingHorizontal = state.readerSettings.margin
                    val bookReaderParagraphSpacingPx = state.readerSettings.paragraphSpacing
                    val bookHorizontalSafeInsets = rememberNovelHorizontalSafeInsets()
                    val bookCutoutLeftCssPx = bookHorizontalSafeInsets.left.value.roundToInt().coerceAtLeast(0)
                    val bookCutoutRightCssPx = bookHorizontalSafeInsets.right.value.roundToInt().coerceAtLeast(0)
                    val bookHorizontalCutoutCss = if (bookCutoutLeftCssPx > 0 || bookCutoutRightCssPx > 0) {
                        "\n#an-book-content { " +
                            "padding-left: ${bookReaderPaddingHorizontal + bookCutoutLeftCssPx}px !important; " +
                            "padding-right: ${bookReaderPaddingHorizontal + bookCutoutRightCssPx}px !important; }"
                    } else {
                        ""
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
                    val bookReaderCss = remember(bookReaderBaseCss, ttsHighlightPalette, bookHorizontalCutoutCss) {
                        withNovelBookReaderContentOverrides(bookReaderBaseCss) +
                            "\n" + ttsHighlightPalette.bookEngineOverrideCss() +
                            bookHorizontalCutoutCss
                    }

                    val latestUrlLinkState by rememberUpdatedState(state)
                    val onReaderUrlClick: (String) -> Unit = remember(context) {
                        { rawUrl ->
                            val currentState = latestUrlLinkState
                            val footnote = resolveNovelFootnoteFromHtml(
                                rawUrl = rawUrl,
                                rawHtml = currentState.html,
                                chapterWebUrl = currentState.chapterWebUrl,
                                novelUrl = currentState.novel.url,
                            )
                            if (footnote != null) {
                                activeFootnoteDialog = footnote
                            } else {
                                val resolvedUrl = resolveNovelReaderLinkUrl(
                                    rawUrl = rawUrl,
                                    chapterWebUrl = currentState.chapterWebUrl,
                                    novelUrl = currentState.novel.url,
                                )
                                if (!resolvedUrl.isNullOrBlank()) {
                                    context.startActivity(
                                        WebViewActivity.newIntent(
                                            context = context,
                                            url = resolvedUrl,
                                            sourceId = currentState.novel.source,
                                            title = currentState.novel.title,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    val onReaderImageClick: (String) -> Unit = remember {
                        { url -> activeImageViewerUrl = url }
                    }
                    val onReaderImageLongClick: (String) -> Unit = remember {
                        { url -> activeImageActionsUrl = url }
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
                    } else if (usePageReader && pageReaderItemsCount == 0) {
                        // Clean matching backdrop is already painted by NovelAtmosphereBackground
                        // while background pagination finishes on Dispatchers.Default.
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize())
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        ComposePagerPageRenderer(
                            pagerState = pagerState,
                            contentPages = pageReaderContentPages,
                            chapterId = state.chapter.id,
                            spreadColumns = novelSpreadColumns,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            seamlessBoundaryPages = seamlessChapterTransitionEnabled,
                            previousChapterPreview = previousChapterSeamlessPreview,
                            nextChapterPreview = nextChapterSeamlessPreview,
                            suppressBoundaryChapterNavigation = suppressBoundaryChapterNavigation,
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
                            contentPadding = spreadContentPadding,
                            statusBarTopPadding = pageReaderStatusBarTopPadding,
                            spreadCutoutLeftDp = with(density) { spreadCutoutLeftPx.toDp() },
                            spreadCutoutRightDp = with(density) { spreadCutoutRightPx.toDp() },
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
                            onImageClick = onReaderImageClick,
                            onImageLongClick = onReaderImageLongClick,
                            onUrlClick = onReaderUrlClick,
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
                            chapterId = state.chapter.id,
                            layoutGeneration = pagedLayoutIdentity.generation,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            seamlessBoundaryPages = seamlessChapterTransitionEnabled,
                            previousChapterPreview = previousChapterSeamlessPreview,
                            nextChapterPreview = nextChapterSeamlessPreview,
                            suppressBoundaryChapterNavigation = suppressBoundaryChapterNavigation,
                            syncedContentPageIndex = syncedPagedReflowTargetPage ?: pageTurnCurrentPage,
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
                            contentPadding = spreadContentPadding,
                            statusBarTopPadding = pageReaderStatusBarTopPadding,
                            spreadCutoutLeftDp = with(density) { spreadCutoutLeftPx.toDp() },
                            spreadCutoutRightDp = with(density) { spreadCutoutRightPx.toDp() },
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
                            onImageClick = onReaderImageClick,
                            onImageLongClick = onReaderImageLongClick,
                            onUrlClick = onReaderUrlClick,
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        PageTurnPageRenderer(
                            chapterId = state.chapter.id,
                            layoutGeneration = pagedLayoutIdentity.generation,
                            contentPages = pageReaderContentPages,
                            spreadColumns = novelSpreadColumns,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            seamlessBoundaryPages = seamlessChapterTransitionEnabled,
                            previousChapterPreview = previousChapterSeamlessPreview,
                            nextChapterPreview = nextChapterSeamlessPreview,
                            suppressBoundaryChapterNavigation = suppressBoundaryChapterNavigation,
                            syncedContentPageIndex = syncedPagedReflowTargetPage ?: pageTurnCurrentPage,
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
                            contentPadding = spreadContentPadding,
                            statusBarTopPadding = pageReaderStatusBarTopPadding,
                            spreadCutoutLeftDp = with(density) { spreadCutoutLeftPx.toDp() },
                            spreadCutoutRightDp = with(density) { spreadCutoutRightPx.toDp() },
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
                            onImageClick = onReaderImageClick,
                            onImageLongClick = onReaderImageLongClick,
                            onUrlClick = onReaderUrlClick,
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else {
                        NovelNativeScrollReaderContent(
                            state = state,
                            usePageReader = usePageReader,
                            showReaderUi = showReaderUi,
                            showWebView = showWebView,
                            useRichNativeScroll = useRichNativeScroll,
                            scrollContentBlocks = scrollContentBlocks,
                            richScrollBlocks = richScrollBlocks,
                            textListState = textListState,
                            bookContentHandle = bookContentHandle,
                            readingProgressPercent = readingProgressPercent,
                            viewportWidthPx = effectivePageViewportSize.width,
                            contentPaddingPx = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            ttsScrollTopPadding = ttsScrollTopPadding,
                            scrollCutoutLeftDp = scrollCutoutLeftDp,
                            scrollCutoutRightDp = scrollCutoutRightDp,
                            paragraphSpacing = paragraphSpacing,
                            textColor = textColor,
                            textBackground = textBackground,
                            composeTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            nextSelectedTextSelectionSessionId = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                            openPreviousChapterFromReader = { openPreviousChapterFromReader() },
                            openNextChapterFromReader = { openNextChapterFromReader() },
                            onReaderShortTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            onUrlClick = onReaderUrlClick,
                            onImageClick = onReaderImageClick,
                            onImageLongClick = onReaderImageLongClick,
                        )
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
                    // Canonical CSS unit for the chapter WebView is the density-independent px
                    // (CSS px == dp under the WebView viewport), the same unit fontSize is passed
                    // in on every path. Passing density-multiplied physical px here made the
                    // horizontal margin ~density-times too wide and, for paragraph spacing,
                    // visibly jump when onPageFinished re-applied the raw-dp value.
                    val paddingHorizontal = state.readerSettings.margin
                    val cssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                    val cssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                        forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                    )
                    val selectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                    val fontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont)
                    val currentTextColorCss = colorToCssHex(textColor)
                    val currentBackgroundCss = colorToCssHex(textBackground)
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
                    val paragraphSpacingCssUnits = state.readerSettings.paragraphSpacing
                    val styleFingerprint = buildWebReaderCssFingerprint(
                        chapterId = state.chapter.id,
                        paddingTop = paddingTop,
                        paddingBottom = paddingBottom,
                        paddingHorizontal = paddingHorizontal,
                        fontSizePx = state.readerSettings.fontSize,
                        lineHeightMultiplier = state.readerSettings.lineHeight,
                        paragraphSpacingPx = paragraphSpacingCssUnits,
                        textAlignCss = cssTextAlign,
                        firstLineIndentCss = cssFirstLineIndent,
                        textColorHex = currentTextColorCss,
                        backgroundHex = currentBackgroundCss,
                        appearanceMode = appearanceMode,
                        backgroundTexture = activeBackgroundTexture,
                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                        backgroundImageIdentity = if (isBackgroundMode) backgroundModeIdentity else null,
                        fontFamilyName = selectedFontFamily,
                        customCss = currentCustomCss,
                        textShadowCss = currentTextShadowCss,
                        forceBoldText = state.readerSettings.forceBoldText,
                        forceItalicText = state.readerSettings.forceItalicText,
                        bionicReadingEnabled = state.readerSettings.bionicReading,
                    )
                    val webClientConfig = WebReaderClientConfig(
                        chapterId = state.chapter.id,
                        shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html),
                        shouldHideWebViewUntilReveal = shouldHideWebViewUntilReveal,
                        backgroundSelection = backgroundSelection,
                        selectedReaderFont = selectedReaderFont,
                        fontFaceCss = fontFaceCss,
                        paddingTop = paddingTop,
                        paddingBottom = paddingBottom,
                        paddingHorizontal = paddingHorizontal,
                        fontSize = state.readerSettings.fontSize,
                        lineHeight = state.readerSettings.lineHeight,
                        paragraphSpacing = paragraphSpacingCssUnits,
                        cssTextAlign = cssTextAlign,
                        cssFirstLineIndent = cssFirstLineIndent,
                        textColorCss = currentTextColorCss,
                        backgroundColorCss = currentBackgroundCss,
                        appearanceMode = appearanceMode,
                        activeBackgroundTexture = activeBackgroundTexture,
                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                        selectedFontFamily = selectedFontFamily,
                        customCss = currentCustomCss,
                        customJs = currentCustomJs,
                        textShadowCss = currentTextShadowCss,
                        forceBoldText = state.readerSettings.forceBoldText,
                        forceItalicText = state.readerSettings.forceItalicText,
                        bionicReading = state.readerSettings.bionicReading,
                        styleFingerprint = styleFingerprint,
                        restoreProgressPercent = initialWebRestoreProgressPercent,
                        chapterHighlights = chapterHighlightList,
                    )
                    val clientConfigRef = remember { AtomicReference(webClientConfig) }
                    SideEffect {
                        clientConfigRef.set(webClientConfig)
                    }
                    val latestNextSelectionSessionId by rememberUpdatedState(nextSelectedTextSelectionSessionId)
                    val latestOnSelectedTextSelectionChanged by rememberUpdatedState(onSelectedTextSelectionChanged)
                    val latestOnHighlightClicked by rememberUpdatedState<(Long) -> Unit> { highlightId ->
                        editingHighlightId = highlightId
                    }
                    val latestSwipeGesturesEnabled by rememberUpdatedState(state.readerSettings.swipeGestures)
                    val latestSwipeToNextChapter by rememberUpdatedState(state.readerSettings.swipeToNextChapter)
                    val latestSwipeToPrevChapter by rememberUpdatedState(state.readerSettings.swipeToPrevChapter)
                    val latestHasPreviousChapter by rememberUpdatedState(state.previousChapterId != null)
                    val latestHasNextChapter by rememberUpdatedState(state.nextChapterId != null)
                    val latestMinWebSwipeDistancePx by rememberUpdatedState(minVerticalChapterSwipeDistancePx)
                    val latestWebSwipeHorizontalTolerancePx by rememberUpdatedState(
                        verticalChapterSwipeHorizontalTolerancePx,
                    )
                    val latestMinWebSwipeHoldDurationMillis by rememberUpdatedState(
                        minVerticalChapterSwipeHoldDurationMillis,
                    )
                    val latestOnSetShowReaderUi by rememberUpdatedState(onSetShowReaderUi)
                    val latestMoveBackward by rememberUpdatedState<suspend () -> Unit> { moveBackwardByReaderAction() }
                    val latestMoveForward by rememberUpdatedState<suspend () -> Unit> { moveForwardByReaderAction() }
                    val latestOpenPreviousChapter by rememberUpdatedState { openPreviousChapterFromReader() }
                    val latestOpenNextChapter by rememberUpdatedState { openNextChapterFromReader() }
                    val latestShouldRestoreWebScroll by rememberUpdatedState(shouldRestoreWebScroll)
                    val latestWebViewPageReady by rememberUpdatedState(webViewPageReadyForAutoScroll)
                    val latestWebScrollProgressHandler by rememberUpdatedState<
                        (
                            WebView,
                            Int,
                        ) -> Unit,
                        > { webView, scrollY ->
                        if (!shouldTrackWebViewProgress(latestShouldRestoreWebScroll)) {
                            return@rememberUpdatedState
                        }
                        val totalScrollable = resolveWebViewTotalScrollablePx(
                            contentHeightPx = webView.resolveWebViewContentHeightPx(),
                            viewHeightPx = webView.height,
                        )
                        if (anchoredWebViewWidthPx[0] <= 0 && webView.width > 0) {
                            anchoredWebViewWidthPx[0] = webView.width
                        }
                        if (webView.width == anchoredWebViewWidthPx[0] && totalScrollable > 0) {
                            lastWebScrollFraction[0] =
                                (scrollY.toFloat() / totalScrollable.toFloat()).coerceIn(0f, 1f)
                        }
                        webAutoScrollNearEnd = resolveWebViewAutoScrollNearEnd(
                            totalScrollablePx = totalScrollable,
                            scrollYPx = scrollY,
                            endOffsetPx = state.readerSettings.autoScrollOffset,
                        )
                        val newPercent = webView.resolveCurrentWebViewProgressPercent(scrollYOverride = scrollY)
                        if (
                            shouldDispatchWebProgressUpdate(
                                latestShouldRestoreWebScroll,
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

                    val webReaderDocumentTag: Any = state.html
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { factoryContext ->
                            val initialFactoryWebViewHtml = buildInitialWebReaderHtml(
                                rawHtml = state.html,
                                readerCss = buildWebReaderCssText(
                                    fontFaceCss = fontFaceCss,
                                    paddingTop = paddingTop,
                                    paddingBottom = paddingBottom,
                                    paddingHorizontal = paddingHorizontal,
                                    fontSizePx = state.readerSettings.fontSize,
                                    lineHeightMultiplier = state.readerSettings.lineHeight,
                                    paragraphSpacingPx = paragraphSpacingCssUnits,
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
                                ),
                                hideUntilReveal = shouldHideWebViewUntilReveal,
                            )
                            val singleWebViewClient = object : WebViewClient() {
                                private var hasEarlyRevealedPage = false

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    hasEarlyRevealedPage = false
                                }

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    val config = clientConfigRef.get()
                                    if (!config.shouldEarlyRevealWebView || hasEarlyRevealedPage) return
                                    hasEarlyRevealedPage = true
                                    view?.revealReaderDocumentAndWebView(config.shouldHideWebViewUntilReveal)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val config = clientConfigRef.get()
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    resolveReaderBackgroundWebResourceResponse(
                                        requestUrl = requestUrl,
                                        context = factoryContext,
                                        selection = config.backgroundSelection,
                                    )?.let { response ->
                                        return response
                                    }
                                    resolveReaderFontWebResourceResponse(
                                        requestUrl = requestUrl,
                                        selectedFont = config.selectedReaderFont,
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
                                    val config = clientConfigRef.get()
                                    webViewPageReadyForAutoScroll = true
                                    view?.applyReaderCss(
                                        fontFaceCss = config.fontFaceCss,
                                        paddingTop = config.paddingTop,
                                        paddingBottom = config.paddingBottom,
                                        paddingHorizontal = config.paddingHorizontal,
                                        fontSizePx = config.fontSize,
                                        lineHeightMultiplier = config.lineHeight,
                                        paragraphSpacingPx = config.paragraphSpacing,
                                        textAlignCss = config.cssTextAlign,
                                        firstLineIndentCss = config.cssFirstLineIndent,
                                        textColorHex = config.textColorCss,
                                        backgroundHex = config.backgroundColorCss,
                                        appearanceMode = config.appearanceMode,
                                        backgroundTexture = config.activeBackgroundTexture,
                                        oledEdgeGradient = config.oledEdgeGradient,
                                        backgroundImageUrl = config.backgroundImageUrl,
                                        fontFamilyName = config.selectedFontFamily,
                                        customCss = config.customCss,
                                        textShadowCss = config.textShadowCss,
                                        forceBoldText = config.forceBoldText,
                                        forceItalicText = config.forceItalicText,
                                        bionicReadingEnabled = config.bionicReading,
                                    )
                                    appliedWebCssFingerprint = config.styleFingerprint

                                    view?.applyNovelHighlightsToPage(config.chapterHighlights)

                                    if (config.customJs.isNotEmpty()) {
                                        view?.evaluateJavascript(
                                            """
                                        (function() {
                                            ${config.customJs}
                                        })();
                                            """.trimIndent(),
                                            null,
                                        )
                                    }

                                    val targetRestorePercent = when {
                                        scrollTargetPercentAfterReload[0] >= 0 -> {
                                            // Restore to the live scroll percent that was current before
                                            // WebView reload when only settings/content of the same chapter
                                            // changed. Uses restoreWebViewScroll so it waits for Blink layout
                                            // (up to 14 attempts), reveals only after positioning, and reports
                                            // progress — avoiding the 0-clamp, flashing and desync bugs.
                                            val target = scrollTargetPercentAfterReload[0]
                                            scrollTargetPercentAfterReload[0] = -1
                                            target
                                        }
                                        shouldRestoreWebScroll -> config.restoreProgressPercent
                                        else -> null
                                    }

                                    if (targetRestorePercent != null) {
                                        view?.restoreWebViewScroll(
                                            progressPercent = targetRestorePercent,
                                            onComplete = { restored ->
                                                shouldRestoreWebScroll = resolveWebViewRestoreGate(restored)
                                                if (restored) {
                                                    if (view != null) {
                                                        anchoredWebViewWidthPx[0] = view.width
                                                        val total = resolveWebViewTotalScrollablePx(
                                                            contentHeightPx = view.resolveWebViewContentHeightPx(),
                                                            viewHeightPx = view.height,
                                                        )
                                                        lastWebScrollFraction[0] = if (total > 0) {
                                                            (view.scrollY.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                                        } else {
                                                            0f
                                                        }
                                                    }
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
                                                view.revealReaderDocumentAndWebView(config.shouldHideWebViewUntilReveal)
                                            },
                                        )
                                    } else {
                                        if (view != null) {
                                            anchoredWebViewWidthPx[0] = view.width
                                            val total = resolveWebViewTotalScrollablePx(
                                                contentHeightPx = view.resolveWebViewContentHeightPx(),
                                                viewHeightPx = view.height,
                                            )
                                            lastWebScrollFraction[0] = if (total > 0) {
                                                (view.scrollY.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            }
                                        }
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
                                        view?.revealReaderDocumentAndWebView(config.shouldHideWebViewUntilReveal)
                                    }
                                }
                            }

                            createNovelReaderWebView(factoryContext).apply {
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
                                    selectionSessionIdProvider = { latestNextSelectionSessionId() },
                                    onSelectedTextSelectionChanged = { selection ->
                                        latestOnSelectedTextSelectionChanged(selection)
                                    },
                                    onHighlightClicked = { highlightId ->
                                        latestOnHighlightClicked(highlightId)
                                    },
                                )

                                var touchStartX = 0f
                                var touchStartY = 0f
                                var touchStartEventTime = 0L
                                var wasNearChapterEndAtDown = false
                                var wasNearChapterStartAtDown = false
                                var horizontalSwipeHandled = false
                                val gestureDetector = GestureDetector(
                                    factoryContext,
                                    object : GestureDetector.SimpleOnGestureListener() {
                                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                            // Let the WebView open links itself: a tap on an anchor must
                                            // not also run the configured tap zone action.
                                            val hitResultType = hitTestResult?.type
                                            if (
                                                hitResultType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                                hitResultType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                            ) {
                                                return false
                                            }
                                            val viewWidth = width.takeIf { it > 0 } ?: return false
                                            val viewHeight = height.takeIf { it > 0 } ?: return false
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
                                                    latestOnSetShowReaderUi(!latestShowReaderUi)
                                                    true
                                                }
                                                NovelReaderTapZoneAction.BACKWARD -> {
                                                    coroutineScope.launch { latestMoveBackward() }
                                                    true
                                                }
                                                NovelReaderTapZoneAction.FORWARD -> {
                                                    coroutineScope.launch { latestMoveForward() }
                                                    true
                                                }
                                                NovelReaderTapZoneAction.NEXT_CHAPTER -> {
                                                    latestOpenNextChapter()
                                                    true
                                                }
                                                NovelReaderTapZoneAction.PREV_CHAPTER -> {
                                                    latestOpenPreviousChapter()
                                                    true
                                                }
                                                NovelReaderTapZoneAction.NONE -> true
                                            }
                                        }
                                    },
                                )
                                setOnTouchListener { _, event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            touchStartX = event.x
                                            touchStartY = event.y
                                            touchStartEventTime = event.eventTime
                                            wasNearChapterEndAtDown = !canScrollVertically(1)
                                            wasNearChapterStartAtDown = !canScrollVertically(-1)
                                            horizontalSwipeHandled = false
                                        }
                                        MotionEvent.ACTION_UP -> {
                                            if (!latestShowReaderUi && !horizontalSwipeHandled) {
                                                when (
                                                    resolveHorizontalChapterSwipeAction(
                                                        swipeGesturesEnabled = latestSwipeGesturesEnabled,
                                                        deltaX = event.x - touchStartX,
                                                        deltaY = event.y - touchStartY,
                                                        thresholdPx = 160f,
                                                        hasPreviousChapter = latestHasPreviousChapter,
                                                        hasNextChapter = latestHasNextChapter,
                                                    )
                                                ) {
                                                    HorizontalChapterSwipeAction.PREVIOUS -> {
                                                        horizontalSwipeHandled = true
                                                        latestOpenPreviousChapter()
                                                    }
                                                    HorizontalChapterSwipeAction.NEXT -> {
                                                        horizontalSwipeHandled = true
                                                        latestOpenNextChapter()
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
                                                    wasNearChapterEndAtDown && !canScrollVertically(1)
                                                val isNearChapterStart =
                                                    wasNearChapterStartAtDown && !canScrollVertically(-1)

                                                when (
                                                    resolveWebViewVerticalChapterSwipeAction(
                                                        swipeToNextChapter = latestSwipeToNextChapter,
                                                        swipeToPrevChapter = latestSwipeToPrevChapter,
                                                        deltaX = deltaX,
                                                        deltaY = deltaY,
                                                        minSwipeDistancePx = latestMinWebSwipeDistancePx,
                                                        horizontalTolerancePx = latestWebSwipeHorizontalTolerancePx,
                                                        gestureDurationMillis = gestureDurationMillis,
                                                        minHoldDurationMillis = latestMinWebSwipeHoldDurationMillis,
                                                        wasNearChapterEndAtDown = wasNearChapterEndAtDown,
                                                        wasNearChapterStartAtDown = wasNearChapterStartAtDown,
                                                        isNearChapterEnd = isNearChapterEnd,
                                                        isNearChapterStart = isNearChapterStart,
                                                    )
                                                ) {
                                                    VerticalChapterSwipeAction.NEXT -> {
                                                        latestOpenNextChapter()
                                                    }
                                                    VerticalChapterSwipeAction.PREVIOUS -> {
                                                        latestOpenPreviousChapter()
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

                                webViewClient = singleWebViewClient
                                setOnScrollChangeListener { view, _, scrollY, _, _ ->
                                    val webView = view as? WebView ?: return@setOnScrollChangeListener
                                    latestWebScrollProgressHandler(webView, scrollY)
                                }
                                addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
                                    val webView = view as? WebView ?: return@addOnLayoutChangeListener
                                    val oldWidth = oldRight - oldLeft
                                    val newWidth = right - left
                                    if (
                                        oldWidth <= 0 ||
                                        newWidth <= 0 ||
                                        newWidth == oldWidth ||
                                        !latestWebViewPageReady ||
                                        latestShouldRestoreWebScroll
                                    ) {
                                        return@addOnLayoutChangeListener
                                    }
                                    val preservedFraction = lastWebScrollFraction[0]
                                    if (preservedFraction < 0f) {
                                        anchoredWebViewWidthPx[0] = newWidth
                                        return@addOnLayoutChangeListener
                                    }
                                    // Wait two message-queue turns so Blink finishes re-layout for the rotated width
                                    // before reading contentHeight and applying the fractional scroll offset.
                                    webView.post {
                                        webView.post {
                                            val targetTotal = resolveWebViewTotalScrollablePx(
                                                contentHeightPx = webView.resolveWebViewContentHeightPx(),
                                                viewHeightPx = webView.height,
                                            )
                                            if (targetTotal > 0) {
                                                val targetY = (preservedFraction * targetTotal.toFloat())
                                                    .roundToInt()
                                                    .coerceIn(0, targetTotal)
                                                webView.scrollTo(0, targetY)
                                                val updatedPercent = webView.resolveCurrentWebViewProgressPercent(
                                                    scrollYOverride = targetY,
                                                )
                                                webProgressPercent = updatedPercent
                                            }
                                            anchoredWebViewWidthPx[0] = newWidth
                                        }
                                    }
                                }
                                cancelReaderScrollRestore()
                                loadDataWithBaseURL(baseUrl, initialFactoryWebViewHtml, "text/html", "utf-8", null)
                                tag = webReaderDocumentTag
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                            clientConfigRef.set(webClientConfig)
                            if (webView is NovelReaderWebView) {
                                webView.isDictionaryEnabled = state.novelDictionaryEnabled
                                webView.isTranslationEnabled = state.readerSettings.selectedTextTranslationEnabled
                            }
                            webView.setBackgroundColor(backgroundColor)
                            // Chapter WebView only; book mode never reaches this update block.
                            val jsEnabled = shouldEnableJavaScriptInReaderWebView(
                                pluginRequestsJavaScript = state.enableJs,
                                bookModeEnabled = false,
                            )
                            if (webView.settings.javaScriptEnabled != jsEnabled) {
                                webView.settings.javaScriptEnabled = jsEnabled
                            }

                            if (webView.tag != webReaderDocumentTag) {
                                val currentRestoreProgress = initialWebRestoreProgressPercent
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
                                    fontSizePx = state.readerSettings.fontSize,
                                    lineHeightMultiplier = state.readerSettings.lineHeight,
                                    paragraphSpacingPx = paragraphSpacingCssUnits,
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
                                if (isSeamlessChapterSwap) {
                                    // Chapter switch — restore the saved progress position.
                                    scrollTargetPercentAfterReload[0] = -1
                                    shouldRestoreWebScroll = true
                                } else {
                                    // Only settings/content of the same chapter changed: save the
                                    // current live scroll percent and restore it after loading via
                                    // restoreWebViewScroll, instead of returning to the saved point
                                    // (rubber-band scroll bug). Relative percent survives translation
                                    // and reflow that change the document's pixel height.
                                    scrollTargetPercentAfterReload[0] = webProgressPercent
                                    shouldRestoreWebScroll = false
                                }
                                appliedWebCssFingerprint = null
                                webView.animate().cancel()
                                webView.alpha = if (shouldHideWebViewUntilReveal && !seamlessInstantSwap) 0f else 1f
                                webView.cancelReaderScrollRestore()
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
                                    paragraphSpacingPx = paragraphSpacingCssUnits,
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
                                    preserveScrollFraction = webViewPageReadyForAutoScroll && !shouldRestoreWebScroll,
                                )
                                appliedWebCssFingerprint = styleFingerprint
                            }
                        },
                        onRelease = { webView ->
                            webView.cancelReaderScrollRestore()
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
                    if (target in pageReaderContentPages.indices) {
                        currentPageAnchor = capturePageReaderAnchor(pageReaderContentPages, target)
                    }
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
                onOpenHighlights = { showHighlightsPanel = true },
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
                    autoScrollPreferenceWriter.persistShowFloatingButtonPreference(it)
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
                geminiEnabled =
                state.readerSettings.geminiEnabled || state.readerSettings.aiTranslationProfileNeedsConfiguration,
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
                            pageTurnCurrentPage = 0
                            if (pageReaderContentPages.isNotEmpty()) {
                                currentPageAnchor = capturePageReaderAnchor(pageReaderContentPages, 0)
                            }
                            if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                                pageTurnRequestedPage = 0
                            } else {
                                val firstVirtualPage = resolveComposePagerVirtualPageIndex(
                                    actualPageIndex = 0,
                                    hasPreviousChapter = composePagerHasPreviousChapter,
                                ).coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                                pagerState.animateScrollToPage(firstVirtualPage)
                            }
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
                onSetTtsSleepTimer = onSetTtsSleepTimer,
                onSetTtsSleepTimerEndOfChapter = onSetTtsSleepTimerEndOfChapter,
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
                    requestGeminiTranslationStartForProfile = { profileId, forceRetranslate ->
                        requestGeminiTranslationStart(profileId, forceRetranslate)
                    },
                    onClearSuppressedGeminiCompletion = onClearSuppressedGeminiCompletion,
                    requestGoogleTranslationStart = { requestGoogleTranslationStart() },
                    onPrepareWholeBook = onPrepareWholeBook,
                    onStopGeminiTranslation = onStopGeminiTranslation,
                    onToggleGeminiTranslationVisibility = onToggleGeminiTranslationVisibility,
                    onClearGeminiTranslation = onClearGeminiTranslation,
                    onClearGeminiTranslationForSwitch = onClearGeminiTranslationForSwitch,
                    onClearAllGeminiTranslationCache = onClearAllGeminiTranslationCache,
                    onAddAiTranslationLog = onAddAiTranslationLog,
                    onClearGeminiLogs = onClearGeminiLogs,
                    onSetGeminiApiKey = actions.onSetGeminiApiKey,
                    onSetGeminiModel = actions.onSetGeminiModel,
                    onRefreshGeminiModels = actions.onRefreshGeminiModels,
                    onSetGeminiBatchSize = actions.onSetGeminiBatchSize,
                    onSetGeminiConcurrency = actions.onSetGeminiConcurrency,
                    onSetGeminiRelaxedMode = actions.onSetGeminiRelaxedMode,
                    onSetGeminiDisableCache = actions.onSetGeminiDisableCache,
                    onSetGeminiReasoningEffort = actions.onSetGeminiReasoningEffort,
                    onSetGeminiBudgetTokens = actions.onSetGeminiBudgetTokens,
                    onSetGeminiTemperature = actions.onSetGeminiTemperature,
                    onSetGeminiTopP = actions.onSetGeminiTopP,
                    onSetGeminiTopK = actions.onSetGeminiTopK,
                    onSetGeminiPromptMode = actions.onSetGeminiPromptMode,
                    onSetGeminiSourceLang = actions.onSetGeminiSourceLang,
                    onSetGeminiTargetLang = actions.onSetGeminiTargetLang,
                    onSetGeminiStylePreset = actions.onSetGeminiStylePreset,
                    onSetGeminiEnabledPromptModifiers = actions.onSetGeminiEnabledPromptModifiers,
                    onSetGeminiCustomPromptModifier = actions.onSetGeminiCustomPromptModifier,
                    onSetGeminiAutoTranslateEnglishSource = actions.onSetGeminiAutoTranslateEnglishSource,
                    onSetGeminiPrefetchNextChapterTranslation = actions.onSetGeminiPrefetchNextChapterTranslation,
                    onSetGeminiPrivateUnlocked = actions.onSetGeminiPrivateUnlocked,
                    onSetGeminiPrivatePythonLikeMode = actions.onSetGeminiPrivatePythonLikeMode,
                    onSetTranslationProvider = onSetTranslationProvider,
                    onSetOpenRouterBaseUrl = actions.onSetOpenRouterBaseUrl,
                    onSetOpenRouterApiKey = actions.onSetOpenRouterApiKey,
                    onSetOpenRouterModel = actions.onSetOpenRouterModel,
                    onRefreshOpenRouterModels = actions.onRefreshOpenRouterModels,
                    onTestOpenRouterConnection = actions.onTestOpenRouterConnection,
                    onSetDeepSeekBaseUrl = actions.onSetDeepSeekBaseUrl,
                    onSetDeepSeekApiKey = actions.onSetDeepSeekApiKey,
                    onSetDeepSeekModel = actions.onSetDeepSeekModel,
                    onRefreshDeepSeekModels = actions.onRefreshDeepSeekModels,
                    onTestDeepSeekConnection = actions.onTestDeepSeekConnection,
                    onSetMistralBaseUrl = actions.onSetMistralBaseUrl,
                    onSetMistralApiKey = actions.onSetMistralApiKey,
                    onSetMistralModel = actions.onSetMistralModel,
                    onRefreshMistralModels = actions.onRefreshMistralModels,
                    onTestMistralConnection = actions.onTestMistralConnection,
                    onSetNvidiaBaseUrl = actions.onSetNvidiaBaseUrl,
                    onSetNvidiaApiKey = actions.onSetNvidiaApiKey,
                    onSetNvidiaModel = actions.onSetNvidiaModel,
                    onRefreshNvidiaModels = actions.onRefreshNvidiaModels,
                    onTestNvidiaConnection = actions.onTestNvidiaConnection,
                    onSetOllamaCloudBaseUrl = actions.onSetOllamaCloudBaseUrl,
                    onSetOllamaCloudApiKey = actions.onSetOllamaCloudApiKey,
                    onSetOllamaCloudModel = actions.onSetOllamaCloudModel,
                    onRefreshOllamaCloudModels = actions.onRefreshOllamaCloudModels,
                    onTestOllamaCloudConnection = actions.onTestOllamaCloudConnection,
                    onStopGoogleTranslation = onStopGoogleTranslation,
                    onResumeGoogleTranslation = onResumeGoogleTranslation,
                    onToggleGoogleTranslationVisibility = onToggleGoogleTranslationVisibility,
                    onClearGoogleTranslation = onClearGoogleTranslation,
                    onSetGoogleTranslationAutoStart = onSetGoogleTranslationAutoStart,
                    onSetGoogleTranslationSourceLang = onSetGoogleTranslationSourceLang,
                    onSetGoogleTranslationTargetLang = onSetGoogleTranslationTargetLang,
                    onStartGeminiTranslation = onStartGeminiTranslation,
                    onStartGeminiTranslationForProfile = onStartGeminiTranslationForProfile,
                    onStartGoogleTranslation = onStartGoogleTranslation,
                ),
            )

            val imageActionContext = androidx.compose.ui.platform.LocalContext.current
            fun saveReaderImage(targetUrl: String) {
                coroutineScope.launch {
                    val file = NovelImageActionHelper.resolveImageFile(imageActionContext, targetUrl)
                    if (file != null) {
                        val imageSaver = eu.kanade.tachiyomi.data.saver.ImageSaver(imageActionContext)
                        val uri = runCatching {
                            imageSaver.save(
                                eu.kanade.tachiyomi.data.saver.Image.Page(
                                    inputStream = { file.inputStream() },
                                    name = "novel_${state.chapter.name}_${file.nameWithoutExtension}",
                                    location = eu.kanade.tachiyomi.data.saver.Location.Pictures("Novel"),
                                ),
                            )
                        }.getOrDefault(android.net.Uri.fromFile(file))
                        val notifier = eu.kanade.tachiyomi.ui.reader.SaveImageNotifier(imageActionContext)
                        notifier.onComplete(uri)
                    } else {
                        val notifier = eu.kanade.tachiyomi.ui.reader.SaveImageNotifier(imageActionContext)
                        notifier.onError("Failed to resolve image")
                    }
                }
            }

            fun shareReaderImage(targetUrl: String) {
                coroutineScope.launch {
                    val file = NovelImageActionHelper.resolveImageFile(imageActionContext, targetUrl)
                    NovelImageActionHelper.shareImage(imageActionContext, targetUrl, file)
                }
            }

            fun copyReaderImageLink(targetUrl: String) {
                NovelImageActionHelper.copyToClipboard(imageActionContext, "Image Link", targetUrl)
            }

            activeImageActionsUrl?.let { imageUrl ->
                NovelImageActionsDialog(
                    imageUrl = imageUrl,
                    onDismissRequest = { activeImageActionsUrl = null },
                    onSaveImage = { saveReaderImage(imageUrl) },
                    onShareImage = { shareReaderImage(imageUrl) },
                    onCopyLink = { copyReaderImageLink(imageUrl) },
                )
            }

            activeImageViewerUrl?.let { imageUrl ->
                NovelImageViewerDialog(
                    imageUrl = imageUrl,
                    onDismiss = { activeImageViewerUrl = null },
                    onSaveImage = { saveReaderImage(imageUrl) },
                    onShareImage = { shareReaderImage(imageUrl) },
                    onCopyLink = { copyReaderImageLink(imageUrl) },
                )
            }

            activeFootnoteDialog?.let { footnote ->
                NovelFootnoteDialog(
                    footnote = footnote,
                    onDismiss = { activeFootnoteDialog = null },
                    onOpenExternalUrl = { externalUrl ->
                        imageActionContext.startActivity(
                            WebViewActivity.newIntent(
                                context = imageActionContext,
                                url = externalUrl,
                                sourceId = state.novel.source,
                                title = state.novel.title,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** Paints [highlights] into the reader WebView; idempotent, safe to call repeatedly. */
private fun WebView.applyNovelHighlightsToPage(highlights: List<NovelHighlight>) {
    evaluateJavascript(buildApplyNovelHighlightsJs(buildNovelHighlightsPayloadJson(highlights)), null)
}

/**
 * Isolated native Scroll-mode reader list extracted from [NovelReaderContentHost] to keep the
 * outer root Box lambda well below Dalvik's 255-argument capture limit in `invoke-direct/range`.
 */
@Composable
private fun NovelNativeScrollReaderContent(
    state: NovelReaderScreenModel.State.Success,
    usePageReader: Boolean,
    showReaderUi: Boolean,
    showWebView: Boolean,
    useRichNativeScroll: Boolean,
    scrollContentBlocks: List<NovelReaderScreenModel.ContentBlock>,
    richScrollBlocks: List<NovelRichContentBlock>,
    textListState: androidx.compose.foundation.lazy.LazyListState,
    bookContentHandle: NovelBookContentHandle,
    readingProgressPercent: Int,
    viewportWidthPx: Int,
    contentPaddingPx: androidx.compose.ui.unit.Dp,
    statusBarTopPadding: androidx.compose.ui.unit.Dp,
    ttsScrollTopPadding: androidx.compose.ui.unit.Dp,
    scrollCutoutLeftDp: androidx.compose.ui.unit.Dp,
    scrollCutoutRightDp: androidx.compose.ui.unit.Dp,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    textColor: Color,
    textBackground: Color,
    composeTypeface: android.graphics.Typeface?,
    chapterTitleTypeface: android.graphics.Typeface?,
    ttsHighlightState: NovelReaderTtsHighlightState?,
    ttsHighlightColor: Color,
    nextSelectedTextSelectionSessionId: () -> Long,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    openPreviousChapterFromReader: () -> Unit,
    openNextChapterFromReader: () -> Unit,
    onReaderShortTap: (Float, Float, Float, Float) -> Unit,
    onUrlClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val currentShortTapHandler by rememberUpdatedState(onReaderShortTap)
    val latestOpenPreviousChapterFromReader by rememberUpdatedState(openPreviousChapterFromReader)
    val latestOpenNextChapterFromReader by rememberUpdatedState(openNextChapterFromReader)
    val nativeScrollItemsCount = if (useRichNativeScroll) richScrollBlocks.size else scrollContentBlocks.size
    val minVerticalChapterSwipeDistancePx = with(density) { 120.dp.toPx() }
    val verticalChapterSwipeHorizontalTolerancePx = with(density) { 20.dp.toPx() }
    val minVerticalChapterSwipeHoldDurationMillis = 180L
    val scrollHorizontalPaddingPx = with(density) {
        (state.readerSettings.margin.dp + scrollCutoutLeftDp).roundToPx() +
            (state.readerSettings.margin.dp + scrollCutoutRightDp).roundToPx()
    }
    val scrollContentWidthPx = (viewportWidthPx - scrollHorizontalPaddingPx).coerceAtLeast(1)
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
                    currentShortTapHandler(
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
                                    latestOpenPreviousChapterFromReader()
                                } else if (
                                    totalDrag < -160f &&
                                    state.nextChapterId != null
                                ) {
                                    handled = true
                                    horizontalChapterSwipeHandled = true
                                    latestOpenNextChapterFromReader()
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
                                    if (state.nextChapterId != null) {
                                        latestOpenNextChapterFromReader()
                                    }
                                }
                                VerticalChapterSwipeAction.PREVIOUS -> {
                                    if (state.previousChapterId != null) {
                                        latestOpenPreviousChapterFromReader()
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
                bookContentHandle.ttsBlockPositions.recordViewport(
                    topInWindow = coordinates.positionInWindow().y,
                    heightPx = coordinates.size.height.toFloat(),
                )
            },
        state = textListState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = contentPaddingPx + ttsScrollTopPadding,
            bottom = contentPaddingPx,
            start =
            state.readerSettings.margin.dp +
                if (LocalLayoutDirection.current ==
                    androidx.compose.ui.unit.LayoutDirection.Rtl
                ) {
                    scrollCutoutRightDp
                } else {
                    scrollCutoutLeftDp
                },
            end =
            state.readerSettings.margin.dp +
                if (LocalLayoutDirection.current ==
                    androidx.compose.ui.unit.LayoutDirection.Rtl
                ) {
                    scrollCutoutLeftDp
                } else {
                    scrollCutoutRightDp
                },
        ),
    ) {
        if (useRichNativeScroll) {
            itemsIndexed(
                richScrollBlocks,
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
                    availableWidthPx = scrollContentWidthPx,
                    ttsHighlightState = ttsHighlightState,
                    ttsHighlightColor = ttsHighlightColor,
                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                    onPlainTap = { tapX, tapY, width, height ->
                        currentShortTapHandler(tapX, tapY, width, height)
                    },
                    onUrlClick = onUrlClick,
                    onImageClick = onImageClick,
                    onImageLongClick = onImageLongClick,
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
                                    availableWidthPx = scrollContentWidthPx,
                                    layoutMode = NovelPageReaderLayoutMode.SCROLL,
                                    selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                    selectionAnchorChapterId = state.chapter.id,
                                    selectionAnchorBlockIndex = index,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                    onPlainTap = { tapX, tapY, width, height ->
                                        currentShortTapHandler(tapX, tapY, width, height)
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
                                availableWidthPx = scrollContentWidthPx,
                                layoutMode = NovelPageReaderLayoutMode.SCROLL,
                                selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                                selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                selectionAnchorChapterId = state.chapter.id,
                                selectionAnchorBlockIndex = index,
                                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                onPlainTap = { tapX, tapY, width, height ->
                                    currentShortTapHandler(tapX, tapY, width, height)
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
                                .then(
                                    if (onImageClick != null || onImageLongClick != null) {
                                        Modifier.pointerInput(block.url, onImageClick, onImageLongClick) {
                                            detectTapGestures(
                                                onTap = { onImageClick?.invoke(block.url) },
                                                onLongPress = { onImageLongClick?.invoke(block.url) },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
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

internal fun shouldTrackNativeScrollProgress(
    totalItemsCount: Int,
    hasVisibleItems: Boolean,
    restoredChapterId: Long,
    currentChapterId: Long,
): Boolean {
    return totalItemsCount > 0 && hasVisibleItems && restoredChapterId == currentChapterId
}

private data class PlainPagePaginationResult(
    val chapterId: Long = -1L,
    val pages: List<List<PlainPageSlice>> = emptyList(),
    val completed: Boolean = false,
)

private data class RichPagePaginationResult(
    val chapterId: Long = -1L,
    val pagination: MixedRichPagePagination = MixedRichPagePagination(emptyList(), emptyList()),
    val completed: Boolean = false,
)

private data class SeamlessPreviewPaginationResult(
    val chapterId: Long = -1L,
    val slice: SeamlessAdjacentPagesSlice = SeamlessAdjacentPagesSlice(),
)

private data class AdjacentPreviewPaginationOutput(
    val slice: SeamlessAdjacentPagesSlice = SeamlessAdjacentPagesSlice(),
    val cachedPagination: CachedChapterPagePagination? = null,
)

private data class WebReaderClientConfig(
    val chapterId: Long,
    val shouldEarlyRevealWebView: Boolean,
    val shouldHideWebViewUntilReveal: Boolean,
    val backgroundSelection: ReaderBackgroundSelection,
    val selectedReaderFont: NovelReaderFontOption,
    val fontFaceCss: String,
    val paddingTop: Int,
    val paddingBottom: Int,
    val paddingHorizontal: Int,
    val fontSize: Int,
    val lineHeight: Float,
    val paragraphSpacing: Int,
    val cssTextAlign: String?,
    val cssFirstLineIndent: String?,
    val textColorCss: String,
    val backgroundColorCss: String,
    val appearanceMode: NovelReaderAppearanceMode,
    val activeBackgroundTexture: NovelReaderBackgroundTexture,
    val oledEdgeGradient: Boolean,
    val backgroundImageUrl: String?,
    val selectedFontFamily: String?,
    val customCss: String,
    val customJs: String,
    val textShadowCss: String?,
    val forceBoldText: Boolean,
    val forceItalicText: Boolean,
    val bionicReading: Boolean,
    val styleFingerprint: String,
    val restoreProgressPercent: Int,
    val chapterHighlights: List<NovelHighlight>,
)
