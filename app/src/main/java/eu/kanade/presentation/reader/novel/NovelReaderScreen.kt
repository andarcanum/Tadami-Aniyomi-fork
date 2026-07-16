package eu.kanade.presentation.reader.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.ActionMode
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.ReaderChapterListItem
import eu.kanade.presentation.reader.ReaderChapterListSheet
import eu.kanade.presentation.reader.components.AutoScrollActionFab
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
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
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
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
import kotlin.math.roundToInt

@Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
internal fun resolveNovelReaderBackdropColor(
    settings: NovelReaderSettings,
    isSystemDark: Boolean,
): Color {
    val theme = safeEnum(settings.theme, NovelReaderTheme.SYSTEM)
    val themeFallback = when (theme) {
        NovelReaderTheme.SYSTEM -> if (isSystemDark) Color(0xFF121212) else Color.White
        NovelReaderTheme.LIGHT -> Color.White
        NovelReaderTheme.DARK -> Color(0xFF121212)
    }
    val themeBackground = parseReaderColor(settings.backgroundColor)
        .takeIf { settings.backgroundColor?.isNotBlank() == true }
        ?: themeFallback

    val appearanceMode = safeEnum(settings.appearanceMode, NovelReaderAppearanceMode.THEME)
    return when (appearanceMode) {
        NovelReaderAppearanceMode.THEME -> themeBackground
        NovelReaderAppearanceMode.BACKGROUND -> {
            resolveReaderBackgroundBackdropColor(
                resolveReaderBackgroundSelection(
                    backgroundSource = safeEnum(settings.backgroundSource, NovelReaderBackgroundSource.PRESET),
                    backgroundPresetId = settings.backgroundPresetId,
                    customBackgroundId = settings.customBackgroundId,
                    customBackgroundItems = emptyList(),
                    customBackgroundPath = settings.customBackgroundPath,
                    customBackgroundExists = settings.customBackgroundPath.orEmpty().isNotBlank() &&
                        File(settings.customBackgroundPath.orEmpty()).exists(),
                ),
            )
        }
    }
}

private fun buildSourceIndexedPageReaderTextList(
    blocks: List<PlainPageReaderTextBlock>,
): List<String> {
    val maxSourceBlockIndex = blocks.maxOfOrNull { it.sourceBlockIndex } ?: return emptyList()
    return MutableList(maxSourceBlockIndex + 1) { "" }.apply {
        blocks.forEach { block ->
            this[block.sourceBlockIndex] = block.text
        }
    }
}

private const val MENU_ID_DICTIONARY = 0x9001
private const val MENU_ID_TRANSLATION = 0x9002

class NovelReaderWebView(context: Context) : WebView(context) {
    var localSelection: NovelSelectedTextSelection? = null
    var onSelectedTextSelectionChanged: ((NovelSelectedTextSelection?) -> Unit)? = null
    var isExecutingAction = false
    var isDictionaryEnabled = false
    var isTranslationEnabled = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val wrappedCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val result = callback?.onCreateActionMode(mode, menu) ?: true
                if (menu != null) {
                    val menuOrder = 100
                    if (isDictionaryEnabled) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_DICTIONARY,
                            menuOrder,
                            context.getString(AYMR.strings.novel_reader_text_selection_action_dictionary.resourceId),
                        )
                    }
                    if (isTranslationEnabled) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_TRANSLATION,
                            menuOrder + 1,
                            context.getString(AYMR.strings.novel_reader_text_selection_action_translate.resourceId),
                        )
                    }
                }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return callback?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item != null) {
                    val selection = localSelection
                    if (selection != null) {
                        when (item.itemId) {
                            MENU_ID_DICTIONARY -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.DICTIONARY)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                mode?.finish()
                                return true
                            }
                            MENU_ID_TRANSLATION -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.TRANSLATION)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                mode?.finish()
                                return true
                            }
                        }
                    }
                }
                return callback?.onActionItemClicked(mode, item) ?: false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                callback?.onDestroyActionMode(mode)
            }
        }
        return super.startActionMode(wrappedCallback, type)
    }
}

fun createNovelReaderWebView(context: Context): WebView {
    return NovelReaderWebView(context)
}

@Suppress("ktlint:standard:max-line-length", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelReaderScreen(
    rawState: NovelReaderScreenModel.State.Success,
    onBack: () -> Unit,
    onReadingProgress: (currentIndex: Int, totalItems: Int, persistedProgress: Long?) -> Unit,
    onToggleBookmark: () -> Unit = {},
    onOpenDictionaryHistory: (() -> Unit)? = null,
    onStartGeminiTranslation: () -> Unit = {},
    onStopGeminiTranslation: () -> Unit = {},
    onToggleGeminiTranslationVisibility: () -> Unit = {},
    onClearGeminiTranslation: () -> Unit = {},
    onClearAllGeminiTranslationCache: () -> Unit = {},
    onAddAiTranslationLog: (String) -> Unit = {},
    onClearGeminiLogs: () -> Unit = {},
    onSetGeminiApiKey: (String) -> Unit = {},
    onSetGeminiModel: (String) -> Unit = {},
    onSetGeminiBatchSize: (Int) -> Unit = {},
    onSetGeminiConcurrency: (Int) -> Unit = {},
    onSetGeminiRelaxedMode: (Boolean) -> Unit = {},
    onSetGeminiDisableCache: (Boolean) -> Unit = {},
    onSetGeminiReasoningEffort: (String) -> Unit = {},
    onSetGeminiBudgetTokens: (Int) -> Unit = {},
    onSetGeminiTemperature: (Float) -> Unit = {},
    onSetGeminiTopP: (Float) -> Unit = {},
    onSetGeminiTopK: (Int) -> Unit = {},
    onSetGeminiPromptMode: (GeminiPromptMode) -> Unit = {},
    onSetGeminiSourceLang: (String) -> Unit = {},
    onSetGeminiTargetLang: (String) -> Unit = {},
    onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit = {},
    onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit = {},
    onSetGeminiCustomPromptModifier: (String) -> Unit = {},
    onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit = {},
    onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit = {},
    onSetGeminiPrivateUnlocked: (Boolean) -> Unit = {},
    onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit = {},
    onSetTranslationProvider: (NovelTranslationProvider) -> Unit = {},
    onSetOpenRouterBaseUrl: (String) -> Unit = {},
    onSetOpenRouterApiKey: (String) -> Unit = {},
    onSetOpenRouterModel: (String) -> Unit = {},
    onRefreshOpenRouterModels: () -> Unit = {},
    onTestOpenRouterConnection: () -> Unit = {},
    onSetDeepSeekBaseUrl: (String) -> Unit = {},
    onSetDeepSeekApiKey: (String) -> Unit = {},
    onSetDeepSeekModel: (String) -> Unit = {},
    onRefreshDeepSeekModels: () -> Unit = {},
    onTestDeepSeekConnection: () -> Unit = {},
    onSetMistralBaseUrl: (String) -> Unit = {},
    onSetMistralApiKey: (String) -> Unit = {},
    onSetMistralModel: (String) -> Unit = {},
    onRefreshMistralModels: () -> Unit = {},
    onTestMistralConnection: () -> Unit = {},
    onSetNvidiaBaseUrl: (String) -> Unit = {},
    onSetNvidiaApiKey: (String) -> Unit = {},
    onSetNvidiaModel: (String) -> Unit = {},
    onRefreshNvidiaModels: () -> Unit = {},
    onTestNvidiaConnection: () -> Unit = {},
    onSetOllamaCloudBaseUrl: (String) -> Unit = {},
    onSetOllamaCloudApiKey: (String) -> Unit = {},
    onSetOllamaCloudModel: (String) -> Unit = {},
    onRefreshOllamaCloudModels: () -> Unit = {},
    onTestOllamaCloudConnection: () -> Unit = {},
    onStartGoogleTranslation: () -> Unit = {},
    onStopGoogleTranslation: () -> Unit = {},
    onResumeGoogleTranslation: () -> Unit = {},
    onToggleGoogleTranslationVisibility: () -> Unit = {},
    onClearGoogleTranslation: () -> Unit = {},
    onSetGoogleTranslationEnabled: (Boolean) -> Unit = {},
    onSetGoogleTranslationAutoStart: (Boolean) -> Unit = {},
    onSetGoogleTranslationSourceLang: (String) -> Unit = {},
    onSetGoogleTranslationTargetLang: (String) -> Unit = {},
    onToggleTtsPlayback: (NovelTtsPlaybackStartRequest) -> Unit = {},
    onStopTtsPlayback: () -> Unit = {},
    onSkipPreviousTts: () -> Unit = {},
    onSkipNextTts: () -> Unit = {},
    onPauseTtsForManualNavigation: (NovelTtsPlaybackStartRequest) -> Unit = {},
    onSetTtsEnginePackage: (String) -> Unit = {},
    onSetTtsVoiceId: (String) -> Unit = {},
    onSetTtsLocaleTag: (String) -> Unit = {},
    onSetTtsSpeechRate: (Float) -> Unit = {},
    onSetTtsPitch: (Float) -> Unit = {},
    onDisableTts: () -> Unit = {},
    onOpenPreviousChapter: ((Long) -> Unit)? = null,
    onOpenNextChapter: ((Long) -> Unit)? = null,
    onPrepareAutoScrollHandoff: (targetChapterId: Long, speed: Int) -> Unit = { _, _ -> },
    onConsumeAutoScrollHandoff: (chapterId: Long) -> NovelAutoScrollHandoffState? = { null },
    onCancelAutoScrollHandoff: () -> Unit = {},
    onRequestAutoScrollNextChapterPrefetch: () -> Unit = {},
    onOpenChapter: ((Long) -> Unit)? = null,
    onDownloadChapter: ((Long) -> Unit)? = null,
    showReaderUi: Boolean,
    onSetShowReaderUi: (Boolean) -> Unit,
    onOpenBottomSheet: () -> Unit = {},
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onTranslateSelectedText: () -> Unit = {},
    onRetrySelectedTextTranslation: () -> Unit = onTranslateSelectedText,
    onDismissSelectedTextTranslation: () -> Unit = {},
    onLookupSelectedTextDefinition: () -> Unit = {},
    onRetryNovelDictionary: () -> Unit = onLookupSelectedTextDefinition,
    onDismissNovelDictionary: () -> Unit = {},
    onPlaySelectedTextPronunciation: (String) -> Unit = {},
) {
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
    var showWebView by remember(
        state.chapter.id,
        state.readerSettings.preferWebViewRenderer,
        state.contentBlocks.size,
    ) {
        mutableStateOf(
            shouldStartInWebView(
                preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
                richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
                pageReaderEnabled = state.readerSettings.pageReader,
                contentBlocksCount = state.contentBlocks.size,
                richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
            ),
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
    ) {
        showWebView = syncShowWebViewWithReaderSettings(
            currentShowWebView = showWebView,
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            pageReaderEnabled = state.readerSettings.pageReader,
            contentBlocksCount = state.contentBlocks.size,
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
        )
    }
    val readerPreferences = remember { Injekt.get<NovelReaderPreferences>() }
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
    var hasReportedReadingProgress by remember(state.chapter.id, showWebView, state.readerSettings.pageReader) {
        mutableStateOf(false)
    }
    fun persistAutoScrollEnabledPreference(
        enabled: Boolean,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScroll = enabled,
                )
            }
        } else {
            readerPreferences.autoScroll().set(enabled)
        }
    }
    fun persistAutoScrollIntervalPreference(
        interval: Int,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollInterval = interval,
                )
            }
        } else {
            readerPreferences.autoScrollInterval().set(interval)
        }
    }
    fun persistAutoScrollAdaptiveDelayPreference(
        enabled: Boolean,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollAdaptiveDelay = enabled,
                )
            }
        } else {
            readerPreferences.autoScrollAdaptiveDelay().set(enabled)
        }
    }
    fun persistAutoScrollChapterEndBehaviorPreference(
        behavior: NovelAutoScrollChapterEndBehavior,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollChapterEndBehavior = behavior,
                )
            }
        } else {
            readerPreferences.autoScrollChapterEndBehavior().set(behavior)
        }
    }
    fun persistAutoScrollEndPauseMsPreference(
        pauseMs: Long,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollEndPauseMs = pauseMs,
                )
            }
        } else {
            readerPreferences.autoScrollEndPauseMs().set(pauseMs)
        }
    }
    fun reportReadingProgress(
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long?,
        flashDisplay: Boolean = false,
    ) {
        if (flashDisplay && flashOnPageChange && eInkProfile.isEnabled && hasReportedReadingProgress) {
            displayRefreshHost.flash()
        }
        hasReportedReadingProgress = true
        onReadingProgress(currentIndex, totalItems, persistedProgress)
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
    val textListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialNativeReaderIndex
            .coerceIn(0, (state.contentBlocks.lastIndex).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = if (state.lastSavedPageReaderProgress != null) {
            0
        } else {
            state.lastSavedScrollOffsetPx.coerceAtLeast(0)
        },
    )

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
            paginatePlainPageBlocks(
                textBlocks = pageReaderTextBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
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
            paginateMixedRichPageBlocks(
                richBlocks = pageReaderRichBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
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
    val composePagerHasPreviousChapter = state.previousChapterId != null
    val composePagerHasNextChapter = state.nextChapterId != null
    val composePagerVirtualPageCount = remember(
        pageReaderItemsCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
    ) {
        resolveComposePagerVirtualPageCount(
            contentPageCount = pageReaderItemsCount,
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
    val autoScrollContentReady = when {
        showWebView -> webViewPageReadyForAutoScroll && scrollContentBlocks.isNotEmpty()
        else -> scrollContentBlocks.isNotEmpty() || richScrollBlocks.isNotEmpty()
    }
    val autoScrollHasRenderableItems = when {
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
    val initialPagerPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
        resolveComposePagerVirtualPageIndex(
            actualPageIndex = initialContentPage,
            hasPreviousChapter = composePagerHasPreviousChapter,
        )
    } else {
        initialContentPage
    }
    val pagerState = rememberPagerState(
        initialPage = initialPagerPage,
        pageCount = {
            if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                composePagerVirtualPageCount.coerceAtLeast(1)
            } else {
                pageReaderItemsCount.coerceAtLeast(1)
            }
        },
    )
    val pageReaderTtsNavigationAdapter = remember(
        pagerState,
        pageReaderRendererRoute,
        composePagerHasPreviousChapter,
    ) {
        PageReaderTtsNavigationAdapter(
            navigator = object : PageReaderTtsNavigator {
                override suspend fun scrollToPage(pageIndex: Int) {
                    val targetPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        resolveComposePagerVirtualPageIndex(
                            actualPageIndex = pageIndex,
                            hasPreviousChapter = composePagerHasPreviousChapter,
                        )
                    } else {
                        pageIndex
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
    SideEffect {
        pageReaderTtsNavigationAdapter.hashCode()
        nativeScrollTtsNavigationAdapter.hashCode()
        webViewTtsNavigationAdapter.hashCode()
    }
    LaunchedEffect(
        state.chapter.id,
        pageReaderRendererRoute,
        pageReaderItemsCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
        initialPagerPage,
    ) {
        if (pagerState.currentPage != initialPagerPage) {
            pagerState.scrollToPage(initialPagerPage)
        }
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
        pageReaderItemsCount,
    ) {
        derivedStateOf {
            resolvePageReaderCurrentPage(
                pageReaderRendererRoute = pageReaderRendererRoute,
                pagerCurrentPage = pagerState.currentPage,
                pageTurnCurrentPage = pageTurnCurrentPage,
                composePagerContentPageCount = pageReaderItemsCount,
                composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                pageTurnContentPageCount = pageReaderItemsCount,
                pageTurnHasPreviousChapter = composePagerHasPreviousChapter,
            )
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
        NovelReaderTtsChapterHandoffPolicy.markPendingRestore(targetChapterId)
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
    LaunchedEffect(
        state.ttsUiState.activeSession?.utterance?.id,
        state.readerSettings.ttsFollowAlong,
        showWebView,
        usePageReader,
        pageReaderProgressPageIndex,
        activePageReaderTtsAnchors,
    ) {
        if (!state.readerSettings.ttsFollowAlong) return@LaunchedEffect
        val session = state.ttsUiState.activeSession ?: return@LaunchedEffect
        val segment = session.model.findSegmentForUtterance(session.utterance.id) ?: return@LaunchedEffect
        pendingProgrammaticTtsBlockIndex = segment.sourceBlockIndex
        suppressManualTtsPauseUntilMs = SystemClock.elapsedRealtime() + 1_500L
        when {
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
    ) {
        derivedStateOf {
            when {
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

    fun requestPageTurnChapterNavigation(direction: PageTurnChapterNavigationDirection) {
        pageTurnChapterNavigationRequestToken += 1L
        pageTurnChapterNavigationRequest = PageTurnChapterNavigationRequest(
            direction = direction,
            token = pageTurnChapterNavigationRequestToken,
        )
    }

    fun openPreviousChapterFromReader() {
        val chapterId = state.previousChapterId ?: return
        NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
            NovelReaderPageReaderHandoffTarget.END,
        )
        onOpenPreviousChapter?.invoke(chapterId)
    }

    fun openNextChapterFromReader() {
        val chapterId = state.nextChapterId ?: return
        NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
            NovelReaderPageReaderHandoffTarget.START,
        )
        onOpenNextChapter?.invoke(chapterId)
    }

    fun handleAutoScrollChapterEnd() {
        val nextChapterId = state.nextChapterId
        val behavior = state.readerSettings.autoScrollChapterEndBehavior
        if (!shouldAutoScrollAdvanceToNextChapter(behavior, nextChapterId != null) || nextChapterId == null) {
            autoScrollEnabled = false
            autoScrollEndStableFrames = 0
            autoScrollEndDwellActive = false
            onCancelAutoScrollHandoff()
            return
        }
        if (shouldAutoScrollContinueAcrossChapters(behavior)) {
            onPrepareAutoScrollHandoff(nextChapterId, autoScrollSpeed)
        } else {
            onCancelAutoScrollHandoff()
        }
        autoScrollEnabled = false
        autoScrollEndStableFrames = 0
        autoScrollEndDwellActive = false
        openNextChapterFromReader()
    }

    suspend fun handleAutoScrollStableChapterEndAfterDwell() {
        val behavior = state.readerSettings.autoScrollChapterEndBehavior
        if (behavior == NovelAutoScrollChapterEndBehavior.StopAtEnd) {
            autoScrollEnabled = false
            autoScrollEndStableFrames = 0
            autoScrollEndDwellActive = false
            onCancelAutoScrollHandoff()
            return
        }

        autoScrollEndStableFrames = 0
        val endPauseMs = state.readerSettings.autoScrollEndPauseMs
        val totalSeconds = ((endPauseMs + 999L) / 1000L).toInt()
        autoScrollEndDwellRemainingSeconds = totalSeconds
        autoScrollEndDwellActive = true

        for (sec in totalSeconds downTo 1) {
            autoScrollEndDwellRemainingSeconds = sec
            delay(1000L)
            if (!autoScrollEnabled || showReaderUi || !autoScrollEndDwellActive) return
        }

        autoScrollEndDwellRemainingSeconds = 0
        autoScrollEndDwellActive = false
        handleAutoScrollChapterEnd()
    }

    suspend fun moveBackwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
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
                requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.PREVIOUS)
            } else {
                val currentPage = pageReaderProgressPageIndex
                val currentVirtualPage = resolveComposePagerVirtualPageIndex(
                    actualPageIndex = currentPage,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                )
                if (currentVirtualPage > 0) {
                    val targetVirtualPage = currentVirtualPage - 1
                    pageTurnCurrentPage = resolveComposePagerActualPageIndex(
                        currentPage = targetVirtualPage,
                        contentPageCount = pageReaderItemsCount,
                        hasPreviousChapter = composePagerHasPreviousChapter,
                    )
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
                requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.NEXT)
            } else {
                val currentPage = pageReaderProgressPageIndex
                val currentVirtualPage = resolveComposePagerVirtualPageIndex(
                    actualPageIndex = currentPage,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                )
                val virtualLastPage = composePagerVirtualPageCount - 1
                if (currentVirtualPage < virtualLastPage) {
                    val targetVirtualPage = currentVirtualPage + 1
                    pageTurnCurrentPage = resolveComposePagerActualPageIndex(
                        currentPage = targetVirtualPage,
                        contentPageCount = pageReaderItemsCount,
                        hasPreviousChapter = composePagerHasPreviousChapter,
                    )
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

    val latestReaderShortTapHandler by rememberUpdatedState<(Float, Float) -> Unit> { tapX, width ->
        dispatchReaderTapAction(
            tapX = tapX,
            width = width,
            tapToScrollEnabled = latestTapToScrollEnabled,
            onToggleUi = { onSetShowReaderUi(!latestShowReaderUi) },
            onBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
            onForward = { coroutineScope.launch { moveForwardByReaderAction() } },
        )
    }

    fun handleVolumeKey(event: KeyEvent): Boolean {
        if (!state.readerSettings.useVolumeButtons) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) return true
        if (event.action != KeyEvent.ACTION_UP) return false
        if (latestShowReaderUi) return true
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                coroutineScope.launch { moveBackwardByReaderAction() }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                coroutineScope.launch { moveForwardByReaderAction() }
                true
            }
            else -> false
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
    ) {
        if (!autoScrollEnabled) return@LaunchedEffect
        var previousFrameNanos: Long? = null
        var stepRemainderPx = 0f
        while (isActive && autoScrollEnabled) {
            if (showReaderUi) {
                previousFrameNanos = null
                stepRemainderPx = 0f
                delay(120)
                continue
            }

            if (resolveAutoScrollPrefetchNeeded(
                    currentIndex = readingProgressPercent,
                    totalItems = 100,
                    behavior = state.readerSettings.autoScrollChapterEndBehavior,
                )
            ) {
                onRequestAutoScrollNextChapterPrefetch()
            }

            val isInCooldown = System.nanoTime() < touchCooldownUntilNanos
            speedFactor = resolveAutoScrollSpeedFactor(
                currentFactor = speedFactor,
                inCooldown = isInCooldown,
                delta = AUTO_SCROLL_SPEED_FACTOR_DELTA,
            )
            if (isInCooldown && speedFactor <= 0f) {
                delay(100)
                continue
            }

            if (showWebView) {
                val webView = webViewInstance
                if (webView == null) {
                    previousFrameNanos = null
                    stepRemainderPx = 0f
                    autoScrollEndStableFrames = 0
                    delay(120)
                    continue
                }
                val frameTimeNanos = withFrameNanos { it }
                val previousNanos = previousFrameNanos
                previousFrameNanos = frameTimeNanos
                if (previousNanos == null) continue
                val frameDeltaNanos = (frameTimeNanos - previousNanos).coerceAtLeast(1L)
                val frameStepPx = autoScrollFrameStepPx(
                    speed = autoScrollSpeed,
                    frameDeltaNanos = frameDeltaNanos,
                ) * speedFactor
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val canScrollBefore = webView.canScrollVertically(1)
                if (canScrollBefore) {
                    webView.scrollBy(0, stepPx)
                }
                val reachedWebAutoScrollThreshold = webAutoScrollNearEnd || !webView.canScrollVertically(1)
                val endState = resolveNovelAutoScrollEndState(
                    canScrollForward = webView.canScrollVertically(1) && !reachedWebAutoScrollThreshold,
                    scrollConsumedPx = if (canScrollBefore) stepPx.toFloat() else 0f,
                    isContentReady = autoScrollContentReady,
                    hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                    hasRenderableItems = autoScrollHasRenderableItems,
                    previousStableEndFrameCount = autoScrollEndStableFrames,
                )
                autoScrollEndStableFrames = endState.stableEndFrameCount
                if (endState.shouldEnterDwell) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
                continue
            }
            if (usePageReader) {
                previousFrameNanos = null
                stepRemainderPx = 0f
                autoScrollEndStableFrames = 0
                delay(
                    autoScrollPageDelayMsForCharacterCount(
                        intervalSeconds = state.readerSettings.autoScrollInterval,
                        characterCount = pageReaderCharacterCounts.getOrNull(pageReaderProgressPageIndex) ?: 0,
                        adaptiveEnabled = state.readerSettings.autoScrollAdaptiveDelay,
                    ),
                )
                if (showReaderUi || showWebView || !autoScrollEnabled) continue
                val currentPage = pageReaderProgressPageIndex
                if (currentPage < pageReaderItemsCount - 1) {
                    moveForwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
                } else if (autoScrollContentReady && hasCompletedInitialReaderLayout && autoScrollHasRenderableItems) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
            } else {
                val frameTimeNanos = withFrameNanos { it }
                val previousNanos = previousFrameNanos
                previousFrameNanos = frameTimeNanos
                if (previousNanos == null) continue
                val frameDeltaNanos = (frameTimeNanos - previousNanos).coerceAtLeast(1L)
                val frameStepPx = autoScrollFrameStepPx(
                    speed = autoScrollSpeed,
                    frameDeltaNanos = frameDeltaNanos,
                ) * speedFactor
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val consumed = textListState.scrollBy(stepPx.toFloat())
                val layoutInfo = textListState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                val nativeNearConfiguredEndOffset = state.readerSettings.autoScrollOffset > 0 &&
                    lastVisibleItem != null &&
                    lastVisibleItem.index >= nativeScrollItemsCount - 1 &&
                    lastVisibleItem.offset + lastVisibleItem.size <=
                    layoutInfo.viewportEndOffset + state.readerSettings.autoScrollOffset
                val endState = resolveNovelAutoScrollEndState(
                    canScrollForward = textListState.canScrollForward && !nativeNearConfiguredEndOffset,
                    scrollConsumedPx = consumed,
                    isContentReady = autoScrollContentReady,
                    hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                    hasRenderableItems = autoScrollHasRenderableItems,
                    previousStableEndFrameCount = autoScrollEndStableFrames,
                )
                autoScrollEndStableFrames = endState.stableEndFrameCount
                if (endState.shouldEnterDwell) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
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
                        touchCooldownUntilNanos = System.nanoTime() + AUTO_SCROLL_COOLDOWN_MS * 1_000_000L
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
                if (!showWebView && scrollContentBlocks.isNotEmpty()) {
                    // Track progress according to the active reader mode.
                    if (usePageReader) {
                        LaunchedEffect(pageReaderProgressPageIndex, pageReaderItemsCount) {
                            reportReadingProgress(
                                pageReaderProgressPageIndex,
                                pageReaderItemsCount,
                                encodePageReaderProgress(
                                    index = pageReaderProgressPageIndex,
                                    totalItems = pageReaderItemsCount,
                                ),
                                flashDisplay = true,
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
                    if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        ComposePagerPageRenderer(
                            pagerState = pagerState,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
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
                            onTextTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        PageTurnPageRenderer(
                            pagerState = pagerState,
                            chapterId = state.chapter.id,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
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
                            onTextTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else {
                        // Scroll mode.
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
                                        latestReaderShortTapHandler(up.position.x, size.width.toFloat())
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
                                                        openPreviousChapterFromReader()
                                                    } else if (
                                                        totalDrag < -160f &&
                                                        state.nextChapterId != null
                                                    ) {
                                                        handled = true
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
                                ),
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
                                    key = { index, block -> "rich-$index-${block.hashCode()}" },
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
                                        onPlainTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    scrollContentBlocks,
                                    key = { index, block -> "plain-$index-${block.hashCode()}" },
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
                                                        onPlainTap = { tapX, width ->
                                                            latestReaderShortTapHandler(tapX, width)
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
                                                    onPlainTap = { tapX, width ->
                                                        latestReaderShortTapHandler(tapX, width)
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
                                settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(state.enableJs)
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
                                tag = state.html
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                            if (webView is NovelReaderWebView) {
                                webView.isDictionaryEnabled = state.novelDictionaryEnabled
                                webView.isTranslationEnabled = state.readerSettings.selectedTextTranslationEnabled
                            }
                            webView.setBackgroundColor(backgroundColor)
                            webView.settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(state.enableJs)
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
                                        val viewWidth = webView.width.takeIf { it > 0 } ?: return false
                                        return when (
                                            resolveReaderTapAction(
                                                tapX = e.x,
                                                width = viewWidth.toFloat(),
                                                tapToScrollEnabled = latestTapToScrollEnabled,
                                            )
                                        ) {
                                            ReaderTapAction.TOGGLE_UI -> {
                                                onSetShowReaderUi(!latestShowReaderUi)
                                                true
                                            }
                                            ReaderTapAction.BACKWARD -> {
                                                coroutineScope.launch { moveBackwardByReaderAction() }
                                                true
                                            }
                                            ReaderTapAction.FORWARD -> {
                                                coroutineScope.launch { moveForwardByReaderAction() }
                                                true
                                            }
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
                                        if (!showReaderUi && !horizontalSwipeHandled) {
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

                            if (webView.tag != state.html) {
                                val currentRestoreProgress = state.lastSavedWebProgressPercent.coerceIn(0, 100)
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
                                    hideUntilReveal = shouldHideWebViewUntilReveal,
                                )
                                val shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html)
                                shouldRestoreWebScroll = true
                                appliedWebCssFingerprint = null
                                webView.animate().cancel()
                                webView.alpha = if (shouldHideWebViewUntilReveal) 0f else 1f
                                webView.loadDataWithBaseURL(baseUrl, initialWebViewHtml, "text/html", "utf-8", null)
                                webView.tag = state.html
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
                    )
                }
            }

            // UI overlay above the content.
            AnimatedVisibility(
                visible = showBottomInfoOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(
                        bottom = with(density) { bottomBarHeight.toDp() } + MaterialTheme.padding.small,
                    ),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.small,
                            vertical = 6.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.readerSettings.showBatteryAndTime) {
                            Text(
                                text = "${batteryLevel.coerceIn(0, 100)}% $timeText",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (state.readerSettings.showKindleInfoBlock && state.readerSettings.showTimeToEnd) {
                            Text(
                                text = if (remainingMinutes == null) {
                                    stringResource(AYMR.strings.novel_reader_time_to_end_unknown)
                                } else {
                                    stringResource(
                                        AYMR.strings.novel_reader_time_to_end_minutes,
                                        remainingMinutes.coerceAtLeast(0),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (state.readerSettings.showKindleInfoBlock && state.readerSettings.showWordCount) {
                            Text(
                                text = stringResource(
                                    AYMR.strings.novel_reader_words_progress,
                                    readWords,
                                    totalWords,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            val seekbarItemsCount = if (showWebView) {
                101
            } else if (usePageReader) {
                pageReaderItemsCount
            } else {
                nativeScrollItemsCount
            }
            val showPageReaderDismissLayer = shouldShowPageReaderDismissLayer(
                showReaderUi = showReaderUi,
                usePageReader = usePageReader,
            )
            if (showPageReaderDismissLayer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(showPageReaderDismissLayer) {
                            detectTapGestures(
                                onTap = {
                                    onSetShowReaderUi(false)
                                },
                            )
                        },
                )
            }
            if (
                shouldShowVerticalSeekbar(
                    showReaderUi = showReaderUi,
                    verticalSeekbarEnabled = state.readerSettings.verticalSeekbar,
                    showWebView = showWebView,
                    usePageReader = usePageReader,
                    textBlocksCount = seekbarItemsCount,
                )
            ) {
                val seekbarValue by remember(
                    showWebView,
                    webProgressPercent,
                    usePageReader,
                    pagerState.currentPage,
                    pageTurnCurrentPage,
                    textListState.firstVisibleItemIndex,
                    textListState.canScrollForward,
                    seekbarItemsCount,
                    readingProgressPercent,
                ) {
                    derivedStateOf {
                        resolveReaderVerticalSeekbarValue(
                            showWebView = showWebView,
                            webProgressPercent = webProgressPercent,
                            usePageReader = usePageReader,
                            pageReaderRendererRoute = pageReaderRendererRoute,
                            pagerCurrentPage = pagerState.currentPage,
                            pageTurnCurrentPage = pageTurnCurrentPage,
                            composePagerContentPageCount = pageReaderItemsCount,
                            composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                            pageTurnContentPageCount = pageReaderItemsCount,
                            pageTurnHasPreviousChapter = composePagerHasPreviousChapter,
                            seekbarItemsCount = seekbarItemsCount,
                            readingProgressPercent = readingProgressPercent,
                        )
                    }
                }
                val (pageRailTopLabel, pageRailBottomLabel) = if (usePageReader) {
                    resolveReaderPageRailLabels(
                        pageIndex = pageReaderProgressPageIndex,
                        pageCount = pageReaderItemsCount,
                    )
                } else {
                    verticalSeekbarLabels(
                        readingProgressPercent = readingProgressPercent,
                        showScrollPercentage = state.readerSettings.showScrollPercentage,
                    )
                }
                val pageSeekbarTickFractions = if (usePageReader) {
                    resolveReaderVerticalSeekbarTickFractions(pageReaderItemsCount)
                } else {
                    emptyList()
                }
                Column(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .padding(end = MaterialTheme.padding.small)
                        .size(width = if (usePageReader) 40.dp else 30.dp, height = 270.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    if (state.readerSettings.geminiEnabled) {
                        val hasTranslationResult =
                            state.hasGeminiTranslationCache || state.geminiTranslationProgress == 100
                        val quickActionIcon = when {
                            state.isGeminiTranslating -> Icons.Filled.Pause
                            hasTranslationResult && state.isGeminiTranslationVisible -> Icons.Filled.Public
                            else -> Icons.Filled.PlayArrow
                        }
                        val quickActionDescription = when {
                            state.isGeminiTranslating -> stringResource(MR.strings.reader_action_stop_translation)
                            hasTranslationResult && state.isGeminiTranslationVisible -> stringResource(
                                MR.strings.reader_action_show_original,
                            )
                            hasTranslationResult -> stringResource(MR.strings.reader_action_show_translation)
                            else -> stringResource(MR.strings.reader_action_start_translation)
                        }
                        val quickActionContainerColor = when {
                            state.isGeminiTranslating -> MaterialTheme.colorScheme.errorContainer
                            hasTranslationResult && state.isGeminiTranslationVisible ->
                                MaterialTheme.colorScheme.tertiaryContainer
                            hasTranslationResult -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val quickActionContentColor = when {
                            state.isGeminiTranslating -> MaterialTheme.colorScheme.onErrorContainer
                            hasTranslationResult && state.isGeminiTranslationVisible ->
                                MaterialTheme.colorScheme.onTertiaryContainer
                            hasTranslationResult -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        Column(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = quickActionContainerColor,
                                contentColor = quickActionContentColor,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        when {
                                            state.isGeminiTranslating -> onStopGeminiTranslation()
                                            hasTranslationResult -> onToggleGeminiTranslationVisibility()
                                            else -> requestGeminiTranslationStart()
                                        }
                                    },
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = quickActionIcon,
                                        contentDescription = quickActionDescription,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            if (state.isGeminiTranslating) {
                                LinearProgressIndicator(
                                    progress = { state.geminiTranslationProgress.coerceIn(0, 100) / 100f },
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.readerSettings.googleTranslationEnabled) {
                        val hasGoogleResult = state.hasGoogleTranslationCache || state.googleTranslationProgress == 100
                        val googleQuickActionIcon = when {
                            state.isGoogleTranslating -> Icons.Filled.Pause
                            hasGoogleResult && state.isGoogleTranslationVisible -> Icons.Filled.Public
                            else -> Icons.Filled.PlayArrow
                        }
                        val googleQuickActionDescription = when {
                            state.isGoogleTranslating -> stringResource(AYMR.strings.novel_reader_google_translate_stop)
                            hasGoogleResult && state.isGoogleTranslationVisible -> stringResource(
                                AYMR.strings.novel_reader_google_translate_original,
                            )
                            hasGoogleResult -> stringResource(AYMR.strings.novel_reader_google_translate_translated)
                            else -> stringResource(AYMR.strings.novel_reader_google_translate_start)
                        }
                        val googleQuickActionContainerColor = when {
                            state.isGoogleTranslating -> MaterialTheme.colorScheme.errorContainer
                            hasGoogleResult && state.isGoogleTranslationVisible ->
                                MaterialTheme.colorScheme.tertiaryContainer
                            hasGoogleResult -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val googleQuickActionContentColor = when {
                            state.isGoogleTranslating -> MaterialTheme.colorScheme.onErrorContainer
                            hasGoogleResult && state.isGoogleTranslationVisible ->
                                MaterialTheme.colorScheme.onTertiaryContainer
                            hasGoogleResult ->
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        Column(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = googleQuickActionContainerColor,
                                contentColor = googleQuickActionContentColor,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        when {
                                            state.isGoogleTranslating -> onStopGoogleTranslation()
                                            hasGoogleResult -> onToggleGoogleTranslationVisibility()
                                            else -> requestGoogleTranslationStart()
                                        }
                                    },
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = googleQuickActionIcon,
                                        contentDescription = googleQuickActionDescription,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            if (state.isGoogleTranslating) {
                                LinearProgressIndicator(
                                    progress = { state.googleTranslationProgress.coerceIn(0, 100) / 100f },
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                    LnReaderVerticalSeekbar(
                        progress = seekbarValue,
                        topLabel = pageRailTopLabel,
                        bottomLabel = pageRailBottomLabel,
                        tickFractions = pageSeekbarTickFractions,
                        onProgressChange = { value ->
                            if (showWebView) {
                                val targetPercent = (value * 100f).roundToInt().coerceIn(0, 100)
                                webProgressPercent = targetPercent
                                val webView = webViewInstance
                                if (webView != null) {
                                    val totalScrollable = resolveWebViewTotalScrollablePx(
                                        contentHeightPx = webView.resolveWebViewContentHeightPx(),
                                        viewHeightPx = webView.height,
                                    )
                                    if (totalScrollable > 0) {
                                        val targetY = ((targetPercent.toFloat() / 100f) * totalScrollable.toFloat())
                                            .roundToInt()
                                            .coerceIn(0, totalScrollable)
                                        webView.scrollTo(0, targetY)
                                    } else {
                                        webView.scrollTo(0, 0)
                                    }
                                }
                                reportReadingProgress(targetPercent, 100, encodeWebScrollProgressPercent(targetPercent))
                            } else {
                                val maxIndex = (seekbarItemsCount - 1).coerceAtLeast(0)
                                val target = (value * maxIndex.toFloat())
                                    .roundToInt()
                                    .coerceIn(0, maxIndex)
                                if (usePageReader) {
                                    pageTurnCurrentPage = target
                                    if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                                        pageTurnRequestedPage = target
                                    } else {
                                        val virtualTarget = resolveComposePagerVirtualPageIndex(
                                            actualPageIndex = target,
                                            hasPreviousChapter = composePagerHasPreviousChapter,
                                        )
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(
                                                virtualTarget.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0)),
                                            )
                                        }
                                    }
                                } else {
                                    coroutineScope.launch {
                                        textListState.scrollToItem(target)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize(),
                    )
                }
            }

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

            val panelSlideSpec = spring<androidx.compose.ui.unit.IntOffset>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            val panelFadeSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            val panelBackgroundColor = MaterialTheme.colorScheme
                .surfaceColorAtElevation(3.dp)
                .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
            // AppBar height (~64dp + status bar).
            AnimatedVisibility(
                visible = showReaderUi,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            panelBackgroundColor,
                            RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                        )
                        .statusBarsPadding(),
                ) {
                    AppBar(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color.Transparent,
                        title = state.novel.title,
                        subtitle = state.chapter.name,
                        navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                        navigateUp = onBack,
                        actions = {
                            IconButton(onClick = onToggleBookmark) {
                                Icon(
                                    imageVector = if (state.chapter.bookmark) {
                                        Icons.Outlined.Bookmark
                                    } else {
                                        Icons.Outlined.BookmarkBorder
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                    )

                    if (autoScrollExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        )
                    }
                    AnimatedVisibility(visible = autoScrollExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column {
                                if (usePageReader) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(AYMR.strings.novel_reader_auto_scroll_page_delay),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.reader_auto_scroll_page_time_fixed,
                                                state.readerSettings.autoScrollInterval,
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Slider(
                                        value = state.readerSettings.autoScrollInterval.toFloat().coerceIn(2f, 60f),
                                        onValueChange = {
                                            persistAutoScrollIntervalPreference(it.roundToInt())
                                        },
                                        valueRange = 2f..60f,
                                        steps = 58,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                persistAutoScrollAdaptiveDelayPreference(
                                                    !state.readerSettings.autoScrollAdaptiveDelay,
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Switch(
                                            checked = state.readerSettings.autoScrollAdaptiveDelay,
                                            onCheckedChange = {
                                                persistAutoScrollAdaptiveDelayPreference(it)
                                            },
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                    if (state.readerSettings.autoScrollAdaptiveDelay) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.reader_auto_scroll_page_time,
                                                autoScrollPageDelayMsForCharacterCount(
                                                    intervalSeconds = state.readerSettings.autoScrollInterval,
                                                    characterCount =
                                                    pageReaderCharacterCounts.getOrNull(pageReaderProgressPageIndex)
                                                        ?: 0,
                                                    adaptiveEnabled = true,
                                                ) / 1000,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        Text(
                                            text = "$autoScrollSpeed",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Slider(
                                        value = autoScrollSpeed.toFloat(),
                                        onValueChange = {
                                            val newSpeed = it.roundToInt().coerceIn(1, 100)
                                            autoScrollSpeed = newSpeed
                                            persistAutoScrollIntervalPreference(
                                                interval = autoScrollSpeedToInterval(newSpeed),
                                            )
                                        },
                                        valueRange = 1f..100f,
                                        steps = 98,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }

                            // Chapter End Behavior Settings
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.novel_reader_auto_scroll_chapter_end_behavior,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    var dropdownExpanded by remember { mutableStateOf(false) }
                                    val behaviorEntries = novelAutoScrollChapterEndBehaviorEntries()
                                    Box {
                                        Text(
                                            text =
                                            behaviorEntries[state.readerSettings.autoScrollChapterEndBehavior] ?: "",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .clickable { dropdownExpanded = true }
                                                .padding(8.dp),
                                        )
                                        DropdownMenu(
                                            expanded = dropdownExpanded,
                                            onDismissRequest = { dropdownExpanded = false },
                                        ) {
                                            behaviorEntries.forEach { (behavior, label) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                                                    },
                                                    onClick = {
                                                        dropdownExpanded = false
                                                        persistAutoScrollChapterEndBehaviorPreference(behavior)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                if (state.readerSettings.autoScrollChapterEndBehavior !=
                                    NovelAutoScrollChapterEndBehavior.StopAtEnd
                                ) {
                                    val currentPauseSec = (state.readerSettings.autoScrollEndPauseMs / 1000L).toInt()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(AYMR.strings.novel_reader_auto_scroll_end_pause),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_reader_auto_scroll_end_pause_value,
                                                currentPauseSec,
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Slider(
                                        value = currentPauseSec.toFloat().coerceIn(0f, 10f),
                                        onValueChange = {
                                            val seconds = it.roundToInt().coerceIn(0, 10)
                                            persistAutoScrollEndPauseMsPreference(seconds * 1000L)
                                        },
                                        valueRange = 0f..10f,
                                        steps = 10,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }

                            // Play/Pause + FAB toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
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
                                    color = if (autoScrollEnabled) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (autoScrollEnabled) {
                                                Icons.Filled.Pause
                                            } else {
                                                Icons.Filled.PlayArrow
                                            },
                                            contentDescription = stringResource(
                                                if (autoScrollEnabled) {
                                                    AYMR.strings.novel_reader_auto_scroll_pause_description
                                                } else {
                                                    AYMR.strings.novel_reader_auto_scroll_play_description
                                                },
                                            ),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(
                                                if (autoScrollEnabled) {
                                                    MR.strings.action_pause
                                                } else {
                                                    MR.strings.action_start
                                                },
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(start = 12.dp, end = 4.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            readerPreferences.showAutoScrollFloatingButton().set(
                                                !state.readerSettings.showAutoScrollFloatingButton,
                                            )
                                        },
                                ) {
                                    Text(
                                        text = stringResource(AYMR.strings.reader_auto_scroll_floating_button),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.widthIn(max = 180.dp),
                                    )
                                    Switch(
                                        checked = state.readerSettings.showAutoScrollFloatingButton,
                                        onCheckedChange = {
                                            readerPreferences.showAutoScrollFloatingButton().set(it)
                                        },
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        IconButton(onClick = { autoScrollExpanded = !autoScrollExpanded }) {
                            Icon(
                                imageVector = if (autoScrollExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                            )
                        }
                    }
                }
            }

            // Bottom navigation in LNReader-like style
            AnimatedVisibility(
                visible = showReaderUi,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            panelBackgroundColor,
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        ),
                ) {
                    if (state.readerSettings.ttsEnabled) {
                        NovelReaderTtsControls(
                            uiState = state.ttsUiState,
                            onTogglePlayback = { onToggleTtsPlayback(currentTtsStartRequest) },
                            onStop = onStopTtsPlayback,
                            onSkipPrevious = onSkipPreviousTts,
                            onSkipNext = onSkipNextTts,
                            onSetEnginePackage = onSetTtsEnginePackage,
                            onSetVoiceId = onSetTtsVoiceId,
                            onSetLocaleTag = onSetTtsLocaleTag,
                            onSetSpeechRate = onSetTtsSpeechRate,
                            onSetPitch = onSetTtsPitch,
                            onDisableTts = onDisableTts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = MaterialTheme.padding.medium,
                                    end = MaterialTheme.padding.medium,
                                    top = MaterialTheme.padding.medium,
                                ),
                        )

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(top = MaterialTheme.padding.medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { openPreviousChapterFromReader() },
                            enabled = state.previousChapterId != null && onOpenPreviousChapter != null,
                        ) {
                            Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                appHaptics.tap()
                                showChapterList = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(MR.strings.chapters),
                            )
                        }
                        IconButton(
                            onClick = {
                                val chapterUrl =
                                    state.chapterWebUrl
                                        ?: state.chapter.url.takeIf { it.startsWith("http", ignoreCase = true) }
                                        ?: state.novel.url.takeIf { it.startsWith("http", ignoreCase = true) }
                                if (!chapterUrl.isNullOrBlank()) {
                                    context.startActivity(
                                        WebViewActivity.newIntent(
                                            context = context,
                                            url = chapterUrl,
                                            sourceId = state.novel.source,
                                            title = state.novel.title,
                                        ),
                                    )
                                }
                            },
                        ) {
                            Icon(imageVector = Icons.Filled.Public, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
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
                        ) {
                            Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                        }
                        if (onOpenDictionaryHistory != null) {
                            val dictionaryQuickAccess by remember { Injekt.get<NovelReaderPreferences>().novelDictionaryQuickAccess() }
                                .collectAsState()
                            if (dictionaryQuickAccess) {
                                IconButton(onClick = { onOpenDictionaryHistory() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = stringResource(AYMR.strings.novel_reader_dictionary_history),
                                    )
                                }
                            }
                        }
                        if (ttsPlacement.showFooterEntry) {
                            IconButton(onClick = { showTtsBehaviorSettings = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.SettingsVoice,
                                    contentDescription = stringResource(
                                        AYMR.strings.novel_reader_tts_behavior_settings,
                                    ),
                                )
                            }
                        }
                        if (state.readerSettings.geminiEnabled) {
                            IconButton(onClick = { showGeminiDialog = true }) {
                                Text(
                                    text = if (state.isGeminiTranslating) {
                                        stringResource(AYMR.strings.novel_reader_gemini_button_active)
                                    } else {
                                        stringResource(AYMR.strings.novel_reader_gemini_button)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        if (state.readerSettings.googleTranslationEnabled) {
                            IconButton(onClick = { showGoogleDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(AYMR.strings.novel_reader_google_translate),
                                    tint = if (state.isGoogleTranslating ||
                                        state.hasGoogleTranslationCache ||
                                        state.isGoogleTranslationVisible
                                    ) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = { openNextChapterFromReader() },
                            enabled = state.nextChapterId != null && onOpenNextChapter != null,
                        ) {
                            Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                    Spacer(
                        modifier = Modifier.padding(bottom = with(density) { navigationBarHeight.toDp() }),
                    )
                }
            }

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

            // Settings dialog
            if (showSettings) {
                NovelReaderSettingsDialog(
                    sourceId = state.novel.source,
                    currentWebViewActive = showWebView,
                    currentPageReaderActive = usePageReader,
                    onDismissRequest = { showSettings = false },
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
                    onDismissRequest = { showChapterList = false },
                    onChapterClick = { chapterId ->
                        if (chapterId == state.chapter.id) {
                            showChapterList = false
                        } else {
                            showChapterList = false
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
                    onDismissRequest = { showTtsBehaviorSettings = false },
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
                    onStart = { requestGeminiTranslationStart() },
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
                    onDismiss = { showGeminiDialog = false },
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
                    onStart = { requestGoogleTranslationStart() },
                    onStop = onStopGoogleTranslation,
                    onResume = onResumeGoogleTranslation,
                    onToggleVisibility = onToggleGoogleTranslationVisibility,
                    onClear = onClearGoogleTranslation,
                    onSetAutoStart = onSetGoogleTranslationAutoStart,
                    onSetSourceLang = onSetGoogleTranslationSourceLang,
                    onSetTargetLang = onSetGoogleTranslationTargetLang,
                    onDismiss = { showGoogleDialog = false },
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
                    onDismissRequest = { translationSwitchRequest = null },
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
                                translationSwitchRequest = null
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
                        TextButton(onClick = { translationSwitchRequest = null }) {
                            Text(text = stringResource(AYMR.strings.novel_reader_ai_translator_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NovelReaderAutoScrollEndOverlay(
    visible: Boolean,
    nextChapterName: String?,
    remainingSeconds: Int,
    isEInkMode: Boolean,
    onGoNow: () -> Unit,
    onStop: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (isEInkMode) fadeIn(animationSpec = tween(0)) else fadeIn(),
        exit = if (isEInkMode) fadeOut(animationSpec = tween(0)) else fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            ),
            tonalElevation = 8.dp,
            shadowElevation = if (isEInkMode) 0.dp else 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (isEInkMode) {
                        stringResource(AYMR.strings.novel_reader_auto_scroll_next_static_eink)
                    } else {
                        stringResource(
                            AYMR.strings.novel_reader_auto_scroll_next_countdown,
                            remainingSeconds,
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!nextChapterName.isNullOrBlank()) {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_reader_auto_scroll_next_chapter_named,
                            nextChapterName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onStay) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_stay_here))
                    }
                    TextButton(onClick = onStop) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_stop_here))
                    }
                    TextButton(onClick = onGoNow) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_go_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberBatteryLevel(context: Context): State<Int> {
    val batteryLevelState = remember(context) { mutableIntStateOf(readBatteryLevel(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                batteryLevelState.intValue = readBatteryLevel(context, intent)
            }
        }
        val stickyIntent = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        batteryLevelState.intValue = readBatteryLevel(context, stickyIntent)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return batteryLevelState
}

@Composable
private fun rememberCurrentTimeText(context: Context): State<String> {
    val timeState = remember(context) { mutableStateOf(currentTimeString(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                timeState.value = currentTimeString(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        timeState.value = currentTimeString(context)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return timeState
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> safeEnum(value: Any?, fallback: T): T {
    return if (value != null && fallback::class.java.isInstance(value)) {
        value as T
    } else {
        fallback
    }
}
