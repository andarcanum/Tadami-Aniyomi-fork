package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationUiState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NovelReaderScreen(
    state: NovelReaderScreenModel.State.Success,
    onBack: () -> Unit,
    onReadingProgress: (currentIndex: Int, totalItems: Int, persistedProgress: Long?) -> Unit,
    onToggleBookmark: () -> Unit = {},
    onStartGeminiTranslation: () -> Unit = {},
    onStopGeminiTranslation: () -> Unit = {},
    onToggleGeminiTranslationVisibility: () -> Unit = {},
    onClearGeminiTranslation: () -> Unit = {},
    onClearAllGeminiTranslationCache: () -> Unit = {},
    onAddGeminiLog: (String) -> Unit = {},
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
    onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit = {},
    onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit = {},
    onSetGeminiCustomPromptModifier: (String) -> Unit = {},
    onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit = {},
    onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit = {},
    onSetGeminiPrivateUnlocked: (Boolean) -> Unit = {},
    onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit = {},
    onSetTranslationProvider: (NovelTranslationProvider) -> Unit = {},
    onSetAirforceBaseUrl: (String) -> Unit = {},
    onSetAirforceApiKey: (String) -> Unit = {},
    onSetAirforceModel: (String) -> Unit = {},
    onRefreshAirforceModels: () -> Unit = {},
    onTestAirforceConnection: () -> Unit = {},
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
    onOpenPreviousChapter: ((Long) -> Unit)? = null,
    onOpenNextChapter: ((Long) -> Unit)? = null,
    showReaderUi: Boolean,
    onSetShowReaderUi: (Boolean) -> Unit,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onTranslateSelectedText: () -> Unit = {},
    onRetrySelectedTextTranslation: () -> Unit = onTranslateSelectedText,
    onDismissSelectedTextTranslation: () -> Unit = {},
) {
    var showSettings by remember { mutableStateOf(false) }
    var selectedTextSelectionSessionId by remember(state.chapter.id) {
        mutableIntStateOf(0)
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
    val sourceId = state.novel.source
    val hasSourceOverride = remember(sourceId) { readerPreferences.getSourceOverride(sourceId) != null }
    var pageViewportSize by remember(state.chapter.id) { mutableStateOf(IntSize.Zero) }
    var autoScrollEnabled by remember(state.chapter.id) {
        mutableStateOf(resolveInitialAutoScrollEnabled(savedPreferenceEnabled = state.readerSettings.autoScroll))
    }
    var autoScrollSpeed by remember(state.chapter.id, state.readerSettings.autoScrollInterval) {
        mutableIntStateOf(intervalToAutoScrollSpeed(state.readerSettings.autoScrollInterval))
    }
    var autoScrollExpanded by remember(state.chapter.id) { mutableStateOf(false) }
    var showGeminiDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val shouldHideWebViewUntilReveal = state.enableJs
    var webProgressPercent by remember(state.chapter.id) {
        mutableIntStateOf(state.lastSavedWebProgressPercent.coerceIn(0, 100))
    }
    var shouldRestoreWebScroll by remember(state.chapter.id) { mutableStateOf(true) }
    var appliedWebCssFingerprint by remember(state.chapter.id) { mutableStateOf<String?>(null) }
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val viewConfiguration = LocalViewConfiguration.current
    val batteryLevel by rememberBatteryLevel(context)
    val timeText by rememberCurrentTimeText(context)
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
    val backgroundSelection = remember(
        state.readerSettings.backgroundSource,
        state.readerSettings.backgroundPresetId,
        customBackgroundId,
        customBackgroundPath,
        customBackgroundItems,
        customBackgroundExists,
    ) {
        resolveReaderBackgroundSelection(
            backgroundSource = state.readerSettings.backgroundSource,
            backgroundPresetId = state.readerSettings.backgroundPresetId,
            customBackgroundId = customBackgroundId,
            customBackgroundItems = customBackgroundItems,
            customBackgroundPath = customBackgroundPath,
            customBackgroundExists = customBackgroundExists,
        )
    }
    val backgroundImageModel =
        remember(backgroundSelection.source, backgroundSelection.preset.id, backgroundSelection.customPath) {
            when (backgroundSelection.source) {
                NovelReaderBackgroundSource.PRESET -> backgroundSelection.preset.imageResId
                NovelReaderBackgroundSource.CUSTOM -> backgroundSelection.customPath?.let(::File)
            }
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
    val backgroundModeBaseColor = remember(backgroundModeTextColor) {
        if (backgroundModeTextColor.luminance() > 0.5f) {
            Color(0xFF121212)
        } else {
            Color(0xFFF6F2E7)
        }
    }
    val backgroundModeWebImageUrl = remember(backgroundSelection) {
        resolveReaderBackgroundWebImageUrl(backgroundSelection)
    }
    val backgroundModeIdentity = remember(backgroundSelection) {
        resolveReaderBackgroundIdentity(backgroundSelection)
    }
    val isEInkMode = AuroraTheme.colors.isEInk
    val isBackgroundMode = state.readerSettings.appearanceMode == NovelReaderAppearanceMode.BACKGROUND
    val activeBackgroundTexture = if (isBackgroundMode || isEInkMode) {
        NovelReaderBackgroundTexture.NONE
    } else {
        state.readerSettings.backgroundTexture
    }
    val activeOledEdgeGradient = if (isBackgroundMode || isEInkMode) {
        false
    } else {
        state.readerSettings.oledEdgeGradient
    }
    val isDarkTheme = when {
        isEInkMode -> false
        else -> when (state.readerSettings.theme) {
            NovelReaderTheme.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < 0.5f
            NovelReaderTheme.DARK -> true
            NovelReaderTheme.LIGHT -> false
        }
    }
    val fallbackTextColor = if (isEInkMode) {
        Color(0xFF000000)
    } else if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFFEDEDED)
    } else {
        androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    }
    val fallbackBackground = if (isEInkMode) {
        Color.White
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
        isEInkMode -> Color(0xFF000000)
        isBackgroundMode -> backgroundModeTextColor
        else -> themeModeTextColor
    }
    val chapterTitleTextColor = textColor
    val textBackground = when {
        isEInkMode -> Color.White
        isBackgroundMode -> backgroundModeBaseColor
        else -> themeModeBackground
    }

    LaunchedEffect(
        isBackgroundMode,
        isEInkMode,
        state.readerSettings.backgroundSource,
        customBackgroundPath,
        customBackgroundExists,
    ) {
        if (isBackgroundMode &&
            !isEInkMode &&
            state.readerSettings.backgroundSource == NovelReaderBackgroundSource.CUSTOM &&
            customBackgroundPath.isNotBlank() &&
            !customBackgroundExists
        ) {
            Toast.makeText(context, missingCustomBackgroundMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val readerFontCatalog = remember(state.readerSettings.fontFamily) {
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

    // Р’С‹СЃРѕС‚Р° AppBar (СЃС‚Р°РЅРґР°СЂС‚РЅР°СЏ Material3 AppBar ~64dp + statusBar)
    val appBarHeight = with(density) { (64.dp + statusBarHeight.toDp()).toPx().toInt() }
    // Р’С‹СЃРѕС‚Р° Bottom bar (~80dp + navigation bar)
    val bottomBarHeight = with(density) { (80.dp + navigationBarHeight.toDp()).toPx().toInt() }
    val statusBarTopPadding = with(density) { statusBarHeight.toDp() }
    val tapScrollStepPx = with(density) { (configuration.screenHeightDp.dp * 0.8f).toPx() }
    val baseContentPadding = MaterialTheme.padding.small
    val contentPaddingPx = with(density) {
        resolveReaderContentPaddingPx(
            showReaderUi = showReaderUi,
            basePaddingPx = baseContentPadding.roundToPx(),
        ).toDp()
    }
    val scrollContentBlocks = remember(state.chapter.id, state.contentBlocks) {
        state.contentBlocks.takeIf { it.isNotEmpty() }
            ?: state.textBlocks.map { NovelReaderScreenModel.ContentBlock.Text(it) }
    }
    val pageReaderTextBlocks = remember(state.chapter.id, state.textBlocks) {
        state.textBlocks.filter { it.isNotBlank() }
    }
    val richScrollBlocks = remember(state.chapter.id, state.richContentBlocks) {
        state.richContentBlocks
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
        state.textBlocks,
        shouldPaginatePageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        pageReaderLayoutTextAlign,
        composeTypeface,
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
            val pageFitSafetyPx = with(density) { 4.dp.roundToPx() }
            val verticalPaddingPx = topPaddingPx + bottomPaddingPx + bookBottomInsetPx + pageFitSafetyPx
            paginatePlainPageBlocks(
                textBlocks = pageReaderTextBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                chapterTitle = state.chapter.name,
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
        state.richContentBlocks,
        shouldPaginateRichForPageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        pageReaderLayoutTextAlign,
        composeTypeface,
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
            val pageFitSafetyPx = with(density) { 4.dp.roundToPx() }
            val verticalPaddingPx = topPaddingPx + bottomPaddingPx + bookBottomInsetPx + pageFitSafetyPx
            paginateMixedRichPageBlocks(
                richBlocks = state.richContentBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                chapterTitle = state.chapter.name,
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
    val pageReaderContentPages = remember(
        useRichPageReader,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
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
            chapterTitle = state.chapter.name,
        )
    }
    val activePageTransitionStyle = remember(state.readerSettings.pageTransitionStyle) {
        resolveActivePageTransitionStyle(
            requestedStyle = state.readerSettings.pageTransitionStyle,
            pageTurnRendererSupported = true,
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
    LaunchedEffect(
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
            )
        }
    }
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
    val totalWords = remember(state.chapter.id, state.textBlocks) {
        countNovelWords(state.textBlocks)
    }
    val readWords by remember(totalWords, readingProgressPercent) {
        derivedStateOf {
            estimateNovelReadWords(
                totalWords = totalWords,
                readingProgressPercent = readingProgressPercent,
            )
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

    suspend fun moveBackwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(-1)) {
                webView.scrollBy(0, -tapScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                onOpenPreviousChapter?.invoke(state.previousChapterId)
            }
        } else if (usePageReader) {
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
                onOpenPreviousChapter?.invoke(state.previousChapterId)
            }
        } else if (textListState.canScrollBackward) {
            textListState.scrollBy(-tapScrollStepPx)
        } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
            onOpenPreviousChapter?.invoke(state.previousChapterId)
        }
    }

    suspend fun moveForwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(1)) {
                webView.scrollBy(0, tapScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                onOpenNextChapter?.invoke(state.nextChapterId)
            }
        } else if (usePageReader) {
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
                onOpenNextChapter?.invoke(state.nextChapterId)
            }
        } else if (textListState.canScrollForward) {
            textListState.scrollBy(tapScrollStepPx)
        } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
            onOpenNextChapter?.invoke(state.nextChapterId)
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
        usePageReader,
        showReaderUi,
        showWebView,
        webViewInstance,
        state.nextChapterId,
        state.readerSettings.swipeToNextChapter,
        pageReaderItemsCount,
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
            if (showWebView) {
                val webView = webViewInstance
                if (webView == null) {
                    previousFrameNanos = null
                    stepRemainderPx = 0f
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
                )
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val canScrollBefore = webView.canScrollVertically(1)
                if (canScrollBefore) {
                    webView.scrollBy(0, stepPx)
                }
                val reachedEnd = !webView.canScrollVertically(1)
                if (reachedEnd && state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                    autoScrollEnabled = false
                    onOpenNextChapter?.invoke(state.nextChapterId)
                } else if (reachedEnd && !canScrollBefore) {
                    autoScrollEnabled = false
                }
                continue
            }
            if (usePageReader) {
                previousFrameNanos = null
                stepRemainderPx = 0f
                delay(autoScrollPageDelayMs(autoScrollSpeed))
                if (showReaderUi || showWebView || !autoScrollEnabled) continue
                val currentPage = pageReaderProgressPageIndex
                if (currentPage < pageReaderItemsCount - 1) {
                    pageTurnCurrentPage = currentPage + 1
                    if (bookFlipPageAnimationDurationMillis != null) {
                        pagerState.animateScrollToPage(
                            currentPage + 1,
                            animationSpec = tween(durationMillis = bookFlipPageAnimationDurationMillis),
                        )
                    } else {
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                    autoScrollEnabled = false
                    onOpenNextChapter?.invoke(state.nextChapterId)
                } else {
                    autoScrollEnabled = false
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
                )
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val consumed = textListState.scrollBy(stepPx.toFloat())
                val reachedEnd = consumed == 0f || !textListState.canScrollForward
                if (reachedEnd && state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                    autoScrollEnabled = false
                    onOpenNextChapter?.invoke(state.nextChapterId)
                } else if (reachedEnd) {
                    autoScrollEnabled = false
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { pageViewportSize = it },
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
                // РћС‚СЃР»РµР¶РёРІР°РЅРёРµ РїСЂРѕРіСЂРµСЃСЃР° РІ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё РѕС‚ СЂРµР¶РёРјР°
                if (usePageReader) {
                    LaunchedEffect(pageReaderProgressPageIndex, pageReaderItemsCount) {
                        onReadingProgress(
                            pageReaderProgressPageIndex,
                            pageReaderItemsCount,
                            encodePageReaderProgress(
                                index = pageReaderProgressPageIndex,
                                totalItems = pageReaderItemsCount,
                            ),
                        )
                    }
                    DisposableEffect(pagerState, pageReaderItemsCount) {
                        onDispose {
                            onReadingProgress(
                                pageReaderProgressPageIndex,
                                pageReaderItemsCount,
                                encodePageReaderProgress(
                                    index = pageReaderProgressPageIndex,
                                    totalItems = pageReaderItemsCount,
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
                        onReadingProgress(
                            progressIndex,
                            progressTotal,
                            encodeNativeScrollProgress(
                                index = textListState.firstVisibleItemIndex,
                                offsetPx = textListState.firstVisibleItemScrollOffset,
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
                            onReadingProgress(
                                progressIndex,
                                progressTotal,
                                encodeNativeScrollProgress(
                                    index = textListState.firstVisibleItemIndex,
                                    offsetPx = textListState.firstVisibleItemScrollOffset,
                                ),
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
                        backgroundTexture = state.readerSettings.backgroundTexture,
                        nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                        backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                        activeOledEdgeGradient = activeOledEdgeGradient,
                        isDarkTheme = isDarkTheme,
                        textTypeface = composeTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        contentPadding = contentPaddingPx,
                        statusBarTopPadding = statusBarTopPadding,
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
                            state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) }
                        },
                        onOpenNextChapter = { state.nextChapterId?.let { onOpenNextChapter?.invoke(it) } },
                        onTextTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                    )
                } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                    PageTurnPageRenderer(
                        pagerState = pagerState,
                        contentPages = pageReaderContentPages,
                        transitionStyle = activePageTransitionStyle,
                        readerSettings = state.readerSettings,
                        textColor = textColor,
                        textBackground = textBackground,
                        chapterTitleTextColor = chapterTitleTextColor,
                        backgroundTexture = state.readerSettings.backgroundTexture,
                        nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                        backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                        backgroundModeIdentity = if (isBackgroundMode) backgroundModeIdentity else "",
                        isBackgroundMode = isBackgroundMode,
                        activeBackgroundTexture = activeBackgroundTexture,
                        activeOledEdgeGradient = activeOledEdgeGradient,
                        isDarkTheme = isDarkTheme,
                        textTypeface = composeTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        contentPadding = contentPaddingPx,
                        statusBarTopPadding = statusBarTopPadding,
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
                            state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) }
                        },
                        onOpenNextChapter = { state.nextChapterId?.let { onOpenNextChapter?.invoke(it) } },
                        onTextTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                    )
                } else {
                    // Scroll Mode (СЂРµР¶РёРј РїСЂРѕРєСЂСѓС‚РєРё, РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ)
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
                                                    onOpenPreviousChapter?.invoke(state.previousChapterId)
                                                } else if (
                                                    totalDrag < -160f &&
                                                    state.nextChapterId != null
                                                ) {
                                                    handled = true
                                                    onOpenNextChapter?.invoke(state.nextChapterId)
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
                                                    state.nextChapterId?.let { onOpenNextChapter?.invoke(it) }
                                                }
                                                VerticalChapterSwipeAction.PREVIOUS -> {
                                                    state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) }
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
                            top = contentPaddingPx,
                            bottom = contentPaddingPx,
                            start = state.readerSettings.margin.dp,
                            end = state.readerSettings.margin.dp,
                        ),
                    ) {
                        if (useRichNativeScroll) {
                            itemsIndexed(richScrollBlocks) { index, block ->
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
                                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                    onPlainTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
                                )
                            }
                        } else {
                            itemsIndexed(scrollContentBlocks) { index, block ->
                                when (block) {
                                    is NovelReaderScreenModel.ContentBlock.Text -> {
                                        val isChapterTitle = index == 0 &&
                                            isNativeChapterTitleText(block.text, state.chapter.name)
                                        val textContent = if (state.readerSettings.bionicReading) {
                                            toBionicText(block.text)
                                        } else {
                                            AnnotatedString(block.text)
                                        }
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
                                                    onPlainTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
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
                                                onPlainTap = { tapX, width -> latestReaderShortTapHandler(tapX, width) },
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
                                        val imageModel = if (NovelPluginImage.isSupported(block.url)) {
                                            NovelPluginImage(block.url)
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
                val backgroundColor = textBackground.toArgb()
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
                        onReadingProgress(
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
                val initialPaddingHorizontal = state.readerSettings.margin
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
                val initialReaderCss = buildWebReaderCssText(
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
                    appearanceMode = state.readerSettings.appearanceMode,
                    backgroundTexture = activeBackgroundTexture,
                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                    fontFamilyName = initialSelectedFontFamily,
                    customCss = state.readerSettings.customCSS,
                    textShadowCss = initialTextShadowCss,
                    forceBoldText = state.readerSettings.forceBoldText,
                    forceItalicText = state.readerSettings.forceItalicText,
                )
                val initialFactoryWebViewHtml = buildInitialWebReaderHtml(
                    rawHtml = state.html,
                    readerCss = initialReaderCss,
                    hideUntilReveal = shouldHideWebViewUntilReveal,
                )

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
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
                                    appearanceMode = state.readerSettings.appearanceMode,
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
                                    appearanceMode = state.readerSettings.appearanceMode,
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
                                        onComplete = {
                                            shouldRestoreWebScroll = false
                                            val settledProgress = view.resolveCurrentWebViewProgressPercent()
                                            if (shouldDispatchWebProgressUpdate(
                                                    false,
                                                    settledProgress,
                                                    webProgressPercent,
                                                )
                                            ) {
                                                webProgressPercent = settledProgress
                                                onReadingProgress(
                                                    settledProgress,
                                                    100,
                                                    encodeWebScrollProgressPercent(settledProgress),
                                                )
                                            }
                                            view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                        },
                                    )
                                } else {
                                    val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                        ?: webProgressPercent
                                    if (shouldDispatchWebProgressUpdate(false, settledProgress, webProgressPercent)) {
                                        webProgressPercent = settledProgress
                                        onReadingProgress(
                                            settledProgress,
                                            100,
                                            encodeWebScrollProgressPercent(settledProgress),
                                        )
                                    }
                                    view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                }
                            }
                        }

                        WebView(context).apply {
                            webViewInstance = this
                            setBackgroundColor(backgroundColor)
                            alpha = if (shouldHideWebViewUntilReveal) 0f else 1f
                            settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(state.enableJs)
                            settings.domStorageEnabled = false

                            webViewClient = factoryWebViewClient
                            setOnScrollChangeListener { view, _, scrollY, _, _ ->
                                val webView = view as? WebView ?: return@setOnScrollChangeListener
                                if (!shouldTrackWebViewProgress(shouldRestoreWebScroll)) {
                                    return@setOnScrollChangeListener
                                }
                                val newPercent = webView.resolveCurrentWebViewProgressPercent(scrollYOverride = scrollY)

                                if (shouldDispatchWebProgressUpdate(
                                        shouldRestoreWebScroll,
                                        newPercent,
                                        webProgressPercent,
                                    )
                                ) {
                                    webProgressPercent = newPercent
                                    onReadingProgress(newPercent, 100, encodeWebScrollProgressPercent(newPercent))
                                }
                            }
                            loadDataWithBaseURL(baseUrl, initialFactoryWebViewHtml, "text/html", "utf-8", null)
                            tag = state.html
                        }
                    },
                    update = { webView ->
                        webViewInstance = webView
                        webView.setBackgroundColor(backgroundColor)
                        webView.settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(state.enableJs)
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
                                                state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) }
                                            }
                                            HorizontalChapterSwipeAction.NEXT -> {
                                                horizontalSwipeHandled = true
                                                state.nextChapterId?.let { onOpenNextChapter?.invoke(it) }
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
                                            wasNearChapterEndAtDown || !webView.canScrollVertically(1)
                                        val isNearChapterStart =
                                            wasNearChapterStartAtDown || !webView.canScrollVertically(-1)

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
                                                state.nextChapterId?.let { onOpenNextChapter?.invoke(it) }
                                            }
                                            VerticalChapterSwipeAction.PREVIOUS -> {
                                                state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) }
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
                        val styleFingerprint = buildWebReaderCssFingerprint(
                            chapterId = state.chapter.id,
                            paddingTop = paddingTop,
                            paddingBottom = paddingBottom,
                            paddingHorizontal = paddingHorizontal,
                            fontSizePx = state.readerSettings.fontSize,
                            lineHeightMultiplier = state.readerSettings.lineHeight,
                            paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                            textAlignCss = cssTextAlign,
                            firstLineIndentCss = cssFirstLineIndent,
                            textColorHex = colorToCssHex(textColor),
                            backgroundHex = colorToCssHex(textBackground),
                            appearanceMode = state.readerSettings.appearanceMode,
                            backgroundTexture = activeBackgroundTexture,
                            oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                            backgroundImageIdentity = if (isBackgroundMode) backgroundModeIdentity else null,
                            fontFamilyName = selectedFontFamily,
                            customCss = state.readerSettings.customCSS,
                            textShadowCss = currentTextShadowCss,
                            forceBoldText = state.readerSettings.forceBoldText,
                            forceItalicText = state.readerSettings.forceItalicText,
                        )
                        val currentRestoreProgress = state.lastSavedWebProgressPercent.coerceIn(0, 100)
                        val currentFontSize = state.readerSettings.fontSize
                        val currentLineHeight = state.readerSettings.lineHeight
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
                            appearanceMode = state.readerSettings.appearanceMode,
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
                                    appearanceMode = state.readerSettings.appearanceMode,
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
                                        onComplete = {
                                    shouldRestoreWebScroll = false
                                    val settledProgress = view.resolveCurrentWebViewProgressPercent()
                                    if (shouldDispatchWebProgressUpdate(
                                                    false,
                                                    settledProgress,
                                                    webProgressPercent,
                                                )
                                            ) {
                                                webProgressPercent = settledProgress
                                                onReadingProgress(
                                                    settledProgress,
                                                    100,
                                            encodeWebScrollProgressPercent(settledProgress),
                                        )
                                    }
                                            view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                        },
                                    )
                                } else {
                                    val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                        ?: webProgressPercent
                                    if (shouldDispatchWebProgressUpdate(false, settledProgress, webProgressPercent)) {
                                        webProgressPercent = settledProgress
                                        onReadingProgress(
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
                                paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                textAlignCss = cssTextAlign,
                                firstLineIndentCss = cssFirstLineIndent,
                                textColorHex = colorToCssHex(textColor),
                                backgroundHex = colorToCssHex(textBackground),
                                appearanceMode = state.readerSettings.appearanceMode,
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

        // UI overlay - РЅР°РєР»Р°РґС‹РІР°РµС‚СЃСЏ РїРѕРІРµСЂС… РєРѕРЅС‚РµРЅС‚Р°
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
                    val hasTranslationResult = state.hasGeminiTranslationCache || state.geminiTranslationProgress == 100
                    val quickActionIcon = when {
                        state.isGeminiTranslating -> Icons.Outlined.Pause
                        hasTranslationResult && state.isGeminiTranslationVisible -> Icons.Outlined.Public
                        else -> Icons.Outlined.PlayArrow
                    }
                    val quickActionDescription = when {
                        state.isGeminiTranslating -> "Остановить перевод"
                        hasTranslationResult && state.isGeminiTranslationVisible -> "Показать оригинал"
                        hasTranslationResult -> "Показать перевод"
                        else -> "Запустить перевод"
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
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    when {
                                        state.isGeminiTranslating -> onStopGeminiTranslation()
                                        hasTranslationResult -> onToggleGeminiTranslationVisibility()
                                        else -> onStartGeminiTranslation()
                                    }
                                },
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
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
                            onReadingProgress(targetPercent, 100, encodeWebScrollProgressPercent(targetPercent))
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
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(target)
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
        // Top AppBar with status bar support
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

                AnimatedVisibility(visible = autoScrollExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    val nextState = resolveAutoScrollUiStateOnToggle(
                                        currentEnabled = autoScrollEnabled,
                                        showReaderUi = showReaderUi,
                                        autoScrollExpanded = autoScrollExpanded,
                                    )
                                    autoScrollEnabled = nextState.autoScrollEnabled
                                    onSetShowReaderUi(nextState.showReaderUi)
                                    autoScrollExpanded = nextState.autoScrollExpanded
                                },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Icon(
                                    imageVector = if (autoScrollEnabled) {
                                        Icons.Outlined.Pause
                                    } else {
                                        Icons.Outlined.PlayArrow
                                    },
                                    contentDescription = null,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        AYMR.strings.novel_reader_auto_scroll_speed,
                                    ) + ": $autoScrollSpeed",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                        onClick = { state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) } },
                        enabled = state.previousChapterId != null && onOpenPreviousChapter != null,
                    ) {
                        Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null)
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
                        Icon(imageVector = Icons.Outlined.Public, contentDescription = null)
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
                    IconButton(
                        onClick = { state.nextChapterId?.let { onOpenNextChapter?.invoke(it) } },
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
            modifier = Modifier.align(Alignment.BottomEnd),
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
        if (showGeminiDialog && state.readerSettings.geminiEnabled) {
            GeminiTranslationDialog(
                readerSettings = state.readerSettings,
                isTranslating = state.isGeminiTranslating,
                translationProgress = state.geminiTranslationProgress,
                isVisible = state.isGeminiTranslationVisible,
                hasCache = state.hasGeminiTranslationCache,
                logs = state.geminiLogs,
                onStart = onStartGeminiTranslation,
                onStop = onStopGeminiTranslation,
                onToggleVisibility = onToggleGeminiTranslationVisibility,
                onClear = onClearGeminiTranslation,
                onClearAllCache = onClearAllGeminiTranslationCache,
                onAddLog = onAddGeminiLog,
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
                onSetGeminiStylePreset = onSetGeminiStylePreset,
                onSetGeminiEnabledPromptModifiers = onSetGeminiEnabledPromptModifiers,
                onSetGeminiCustomPromptModifier = onSetGeminiCustomPromptModifier,
                onSetGeminiAutoTranslateEnglishSource = onSetGeminiAutoTranslateEnglishSource,
                onSetGeminiPrefetchNextChapterTranslation = onSetGeminiPrefetchNextChapterTranslation,
                onSetGeminiPrivateUnlocked = onSetGeminiPrivateUnlocked,
                onSetGeminiPrivatePythonLikeMode = onSetGeminiPrivatePythonLikeMode,
                onSetTranslationProvider = onSetTranslationProvider,
                onSetAirforceBaseUrl = onSetAirforceBaseUrl,
                onSetAirforceApiKey = onSetAirforceApiKey,
                onSetAirforceModel = onSetAirforceModel,
                onRefreshAirforceModels = onRefreshAirforceModels,
                onTestAirforceConnection = onTestAirforceConnection,
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
                airforceModels = state.airforceModelIds,
                isAirforceModelsLoading = state.isAirforceModelsLoading,
                isTestingAirforceConnection = state.isTestingAirforceConnection,
                openRouterModels = state.openRouterModelIds,
                isOpenRouterModelsLoading = state.isOpenRouterModelsLoading,
                isTestingOpenRouterConnection = state.isTestingOpenRouterConnection,
                deepSeekModels = state.deepSeekModelIds,
                isDeepSeekModelsLoading = state.isDeepSeekModelsLoading,
                isTestingDeepSeekConnection = state.isTestingDeepSeekConnection,
                onDismiss = { showGeminiDialog = false },
            )
        }
    }
}

@Composable
private fun GeminiTranslationDialog(
    readerSettings: NovelReaderSettings,
    isTranslating: Boolean,
    translationProgress: Int,
    isVisible: Boolean,
    hasCache: Boolean,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    onClearAllCache: () -> Unit,
    onAddLog: (String) -> Unit,
    onClearLogs: () -> Unit,
    onSetGeminiApiKey: (String) -> Unit,
    onSetGeminiModel: (String) -> Unit,
    onSetGeminiBatchSize: (Int) -> Unit,
    onSetGeminiConcurrency: (Int) -> Unit,
    onSetGeminiRelaxedMode: (Boolean) -> Unit,
    onSetGeminiDisableCache: (Boolean) -> Unit,
    onSetGeminiReasoningEffort: (String) -> Unit,
    onSetGeminiBudgetTokens: (Int) -> Unit,
    onSetGeminiTemperature: (Float) -> Unit,
    onSetGeminiTopP: (Float) -> Unit,
    onSetGeminiTopK: (Int) -> Unit,
    onSetGeminiPromptMode: (GeminiPromptMode) -> Unit,
    onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit,
    onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit,
    onSetGeminiCustomPromptModifier: (String) -> Unit,
    onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit,
    onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit,
    onSetGeminiPrivateUnlocked: (Boolean) -> Unit,
    onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit,
    onSetTranslationProvider: (NovelTranslationProvider) -> Unit,
    onSetAirforceBaseUrl: (String) -> Unit,
    onSetAirforceApiKey: (String) -> Unit,
    onSetAirforceModel: (String) -> Unit,
    onRefreshAirforceModels: () -> Unit,
    onTestAirforceConnection: () -> Unit,
    onSetOpenRouterBaseUrl: (String) -> Unit,
    onSetOpenRouterApiKey: (String) -> Unit,
    onSetOpenRouterModel: (String) -> Unit,
    onRefreshOpenRouterModels: () -> Unit,
    onTestOpenRouterConnection: () -> Unit,
    onSetDeepSeekBaseUrl: (String) -> Unit,
    onSetDeepSeekApiKey: (String) -> Unit,
    onSetDeepSeekModel: (String) -> Unit,
    onRefreshDeepSeekModels: () -> Unit,
    onTestDeepSeekConnection: () -> Unit,
    airforceModels: List<String>,
    isAirforceModelsLoading: Boolean,
    isTestingAirforceConnection: Boolean,
    openRouterModels: List<String>,
    isOpenRouterModelsLoading: Boolean,
    isTestingOpenRouterConnection: Boolean,
    deepSeekModels: List<String>,
    isDeepSeekModelsLoading: Boolean,
    isTestingDeepSeekConnection: Boolean,
    onDismiss: () -> Unit,
) {
    val modelEntries = remember {
        listOf(
            "gemini-3-flash-preview" to "Gemini 3 Flash",
            "gemini-3-pro-preview" to "Gemini 3 Pro",
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite",
        )
    }
    val modelMap = remember(modelEntries) { modelEntries.toMap() }
    val speedPresets = remember {
        listOf(
            "100-1" to (100 to 1),
            "40-2" to (40 to 2),
            "50-2" to (50 to 2),
            "30-3" to (30 to 3),
        )
    }
    val openRouterAllModelEntries = remember(openRouterModels) {
        openRouterModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.endsWith(":free", ignoreCase = true) }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val deepSeekAllModelEntries = remember(deepSeekModels) {
        deepSeekModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }

    var tempKey by remember(readerSettings.geminiApiKey) { mutableStateOf(readerSettings.geminiApiKey) }
    var tempModel by remember(readerSettings.geminiModel) {
        mutableStateOf(
            when (readerSettings.geminiModel) {
                "gemini-3-flash" -> "gemini-3-flash-preview"
                "gemini-2.5-flash" -> "gemini-3.1-flash-lite-preview"
                else -> readerSettings.geminiModel
            },
        )
    }
    var tempBatch by remember(readerSettings.geminiBatchSize) {
        mutableStateOf(readerSettings.geminiBatchSize.toString())
    }
    var tempConcurrency by remember(readerSettings.geminiConcurrency) {
        mutableStateOf(readerSettings.geminiConcurrency.toString())
    }
    var tempRelaxed by remember(readerSettings.geminiRelaxedMode) { mutableStateOf(readerSettings.geminiRelaxedMode) }
    var tempDisableCache by remember(readerSettings.geminiDisableCache) {
        mutableStateOf(readerSettings.geminiDisableCache)
    }
    var tempReasoning by remember(readerSettings.geminiReasoningEffort) {
        mutableStateOf(readerSettings.geminiReasoningEffort)
    }
    var tempBudget by remember(readerSettings.geminiBudgetTokens) { mutableStateOf(readerSettings.geminiBudgetTokens) }
    var tempTemperature by remember(readerSettings.geminiTemperature) {
        mutableStateOf(readerSettings.geminiTemperature.toString())
    }
    var tempTopP by remember(readerSettings.geminiTopP) { mutableStateOf(readerSettings.geminiTopP.toString()) }
    var tempTopK by remember(readerSettings.geminiTopK) { mutableStateOf(readerSettings.geminiTopK.toString()) }
    var tempPromptMode by remember(readerSettings.geminiPromptMode) { mutableStateOf(readerSettings.geminiPromptMode) }
    var tempStylePreset by remember(readerSettings.geminiStylePreset) {
        mutableStateOf(readerSettings.geminiStylePreset)
    }
    var tempEnabledModifiers by remember(readerSettings.geminiEnabledPromptModifiers) {
        mutableStateOf(readerSettings.geminiEnabledPromptModifiers.toSet())
    }
    var tempCustomModifier by remember(readerSettings.geminiCustomPromptModifier) {
        mutableStateOf(readerSettings.geminiCustomPromptModifier)
    }
    var tempAutoTranslateEnglish by remember(readerSettings.geminiAutoTranslateEnglishSource) {
        mutableStateOf(readerSettings.geminiAutoTranslateEnglishSource)
    }
    var tempPrefetchNextChapterTranslation by remember(readerSettings.geminiPrefetchNextChapterTranslation) {
        mutableStateOf(readerSettings.geminiPrefetchNextChapterTranslation)
    }
    var tempProvider by remember(readerSettings.translationProvider) {
        mutableStateOf(readerSettings.translationProvider)
    }
    var tempPrivatePythonLikeMode by remember(readerSettings.geminiPrivatePythonLikeMode) {
        mutableStateOf(readerSettings.geminiPrivatePythonLikeMode)
    }
    val isPrivateProviderInstalled = remember { GeminiPrivateBridge.isInstalled() }
    val privateProviderLabel = remember(isPrivateProviderInstalled) {
        if (isPrivateProviderInstalled) GeminiPrivateBridge.providerLabel() else "Gemini Private"
    }
    var tempPrivatePassword by remember { mutableStateOf("") }
    var isPrivateProviderUnlocked by remember(isPrivateProviderInstalled, readerSettings.geminiPrivateUnlocked) {
        mutableStateOf(
            isPrivateProviderInstalled &&
                (readerSettings.geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked()),
        )
    }
    var tempOpenRouterBaseUrl by remember(readerSettings.openRouterBaseUrl) {
        mutableStateOf(readerSettings.openRouterBaseUrl)
    }
    var tempOpenRouterApiKey by remember(readerSettings.openRouterApiKey) {
        mutableStateOf(readerSettings.openRouterApiKey)
    }
    var tempOpenRouterModel by remember(readerSettings.openRouterModel) {
        mutableStateOf(readerSettings.openRouterModel)
    }
    var tempDeepSeekBaseUrl by remember(readerSettings.deepSeekBaseUrl) {
        mutableStateOf(readerSettings.deepSeekBaseUrl)
    }
    var tempDeepSeekApiKey by remember(readerSettings.deepSeekApiKey) {
        mutableStateOf(readerSettings.deepSeekApiKey)
    }
    var tempDeepSeekModel by remember(readerSettings.deepSeekModel) {
        mutableStateOf(readerSettings.deepSeekModel)
    }
    var showAdvanced by remember { mutableStateOf(false) }
    var showGenerationConfig by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }

    data class GenerationPreset(
        val id: String,
        val title: String,
        val temperature: Float,
        val topP: Float,
        val topK: Int?,
        val scenario: String,
        val advantage: String,
    )

    val defaultGenerationPresets = remember {
        listOf(
            GenerationPreset(
                id = "anchor_plus",
                title = "Канон+",
                temperature = 0.62f,
                topP = 0.9f,
                topK = 36,
                scenario = "Длинные главы с плотным лором " +
                    "и терминами",
                advantage = "Стабильный стиль, высокая " +
                    "связность и минимум случайного шума",
            ),
            GenerationPreset(
                id = "authorial",
                title = "Авторский",
                temperature = 0.76f,
                topP = 0.93f,
                topK = 48,
                scenario = "Повседневные сцены, внутренние " +
                    "монологи, драма",
                advantage = "Более литературная подача и " +
                    "живые формулировки без перегиба",
            ),
            GenerationPreset(
                id = "dialogue_plus",
                title = "Живые диалоги",
                temperature = 0.88f,
                topP = 0.95f,
                topK = 56,
                scenario = "Разговорные главы, пикировки, " +
                    "юмор, флирт",
                advantage = "Речь персонажей звучит естественнее " +
                    "и эмоциональнее",
            ),
            GenerationPreset(
                id = "private_pulse",
                title = "18+ Импульс",
                temperature = 0.98f,
                topP = 0.97f,
                topK = 72,
                scenario = "Эротические и напряжённые сцены",
                advantage = "Максимум чувственности, экспрессии " +
                    "и «живого» ритма",
            ),
            GenerationPreset(
                id = "unbound",
                title = "Без тормозов",
                temperature = 1.08f,
                topP = 0.985f,
                topK = 96,
                scenario = "Экспериментальный режим для самых " +
                    "дерзких глав",
                advantage = "Пиковая креативность и вариативность " +
                    "слога",
            ),
        )
    }
    val deepSeekGenerationPresets = remember {
        listOf(
            GenerationPreset(
                id = "deepseek_balanced",
                title = "DeepSeek Баланс",
                temperature = 1.3f,
                topP = 0.9f,
                topK = null,
                scenario = "Стабильный креативный перевод на " +
                    "каждый день",
                advantage = "Живой текст с контролируемым уровнем " +
                    "вариативности",
            ),
            GenerationPreset(
                id = "deepseek_expressive",
                title = "DeepSeek Экспрессия",
                temperature = 1.4f,
                topP = 0.93f,
                topK = null,
                scenario = "Диалоги, эмоции, романтика и 18+ " +
                    "сцены",
                advantage = "Более яркая и естественная подача " +
                    "реплик и тональности",
            ),
            GenerationPreset(
                id = "deepseek_creative",
                title = "DeepSeek Креатив",
                temperature = 1.5f,
                topP = 0.95f,
                topK = null,
                scenario = "Максимально смелый и вариативный " +
                    "стиль",
                advantage = "Пиковая творческая свобода без " +
                    "переусложнения настроек",
            ),
        )
    }
    val stylePresets = remember { NovelTranslationStylePresets.all }
    fun resolveSelectedGenerationPresetId(
        provider: NovelTranslationProvider,
        temperature: Float,
        topP: Float,
        topK: Int,
    ): String {
        val presets = if (provider == NovelTranslationProvider.DEEPSEEK) {
            deepSeekGenerationPresets
        } else {
            defaultGenerationPresets
        }
        if (presets.isEmpty()) return ""
        val epsilon = 0.0001f
        presets.firstOrNull { preset ->
            val tempMatch = abs(preset.temperature - temperature) <= epsilon
            val topPMatch = abs(preset.topP - topP) <= epsilon
            val topKMatch = when {
                provider == NovelTranslationProvider.DEEPSEEK -> true
                preset.topK == null -> true
                else -> preset.topK == topK
            }
            tempMatch && topPMatch && topKMatch
        }?.let { return it.id }

        return presets.minByOrNull { preset ->
            val topKDistance = when {
                provider == NovelTranslationProvider.DEEPSEEK -> 0f
                preset.topK == null -> 0f
                else -> abs((topK - preset.topK).toFloat()) / 100f
            }
            abs(preset.temperature - temperature) + abs(preset.topP - topP) + topKDistance
        }?.id ?: presets.first().id
    }
    var selectedGenerationPresetId by remember(
        tempProvider,
        readerSettings.geminiTemperature,
        readerSettings.geminiTopP,
        readerSettings.geminiTopK,
    ) {
        mutableStateOf(
            resolveSelectedGenerationPresetId(
                provider = tempProvider,
                temperature = readerSettings.geminiTemperature,
                topP = readerSettings.geminiTopP,
                topK = readerSettings.geminiTopK,
            ),
        )
    }

    fun applyBatchAndConcurrency() {
        tempBatch.toIntOrNull()?.let {
            onSetGeminiBatchSize(it.coerceIn(1, 100))
        }
        val maxConcurrency = if (tempProvider == NovelTranslationProvider.DEEPSEEK) 32 else 8
        tempConcurrency.toIntOrNull()?.let {
            onSetGeminiConcurrency(it.coerceIn(1, maxConcurrency))
        }
    }

    val progressValue = translationProgress.coerceIn(0, 100) / 100f
    val uiState = resolveGeminiTranslationUiState(
        isTranslating = isTranslating,
        hasCache = hasCache,
        isVisible = isVisible,
        translationProgress = translationProgress,
    )
    val status = geminiStatusPresentation(uiState)
    val hasTranslationResult = hasCache || translationProgress >= 100
    val isGeminiSelected = tempProvider == NovelTranslationProvider.GEMINI
    val isGeminiPrivateSelected = tempProvider == NovelTranslationProvider.GEMINI_PRIVATE
    val isGeminiFamilySelected = isGeminiSelected || isGeminiPrivateSelected
    val isPrivateSingleRequestMode =
        isGeminiPrivateSelected &&
            isPrivateProviderInstalled &&
            GeminiPrivateBridge.forceSingleChapterRequest()
    val privateBridgeInstalled = isGeminiPrivateSelected && isPrivateProviderInstalled
    val privateBridgeRequiresUnlock = privateBridgeInstalled
    val privateBridgeUnlocked = !privateBridgeRequiresUnlock || isPrivateProviderUnlocked
    val isOpenRouterSelected = tempProvider == NovelTranslationProvider.OPENROUTER
    val isDeepSeekSelected = tempProvider == NovelTranslationProvider.DEEPSEEK
    val activeGenerationPresets = if (isDeepSeekSelected) {
        deepSeekGenerationPresets
    } else {
        defaultGenerationPresets
    }
    val tabTitles = remember { persistentListOf("Основные", "Промпт", "Еще") }

    LaunchedEffect(tempProvider) {
        if (tempProvider == NovelTranslationProvider.AIRFORCE) {
            tempProvider = NovelTranslationProvider.GEMINI
            onSetTranslationProvider(NovelTranslationProvider.GEMINI)
            onAddLog("?? Airforce hidden. Switched to Gemini")
        }
    }

    LaunchedEffect(isPrivateProviderInstalled, readerSettings.geminiPrivateUnlocked) {
        isPrivateProviderUnlocked = isPrivateProviderInstalled &&
            (readerSettings.geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked())
    }

    LaunchedEffect(isOpenRouterSelected, openRouterModels.size) {
        if (isOpenRouterSelected && openRouterModels.isEmpty()) {
            onRefreshOpenRouterModels()
        }
    }

    LaunchedEffect(isDeepSeekSelected, deepSeekModels.size) {
        if (isDeepSeekSelected && deepSeekModels.isEmpty()) {
            onRefreshDeepSeekModels()
        }
    }

    TabbedDialog(
        onDismissRequest = onDismiss,
        tabTitles = tabTitles,
    ) { page ->
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "AI Переводчик",
                style = MaterialTheme.typography.titleMedium,
            )
            if (page == 0) {
                GeminiSettingsBlock(
                    title = "Статус и действия",
                    subtitle = "Запуск, остановка и переключение отображения",
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = status.title,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "$translationProgress%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = status.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (isTranslating) {
                                    onStop()
                                } else {
                                    if (privateBridgeUnlocked) {
                                        onStart()
                                    } else {
                                        onAddLog("🔒 $privateProviderLabel bridge is locked. Unlock it first.")
                                    }
                                }
                            },
                            enabled = isTranslating || privateBridgeUnlocked,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isTranslating) "Остановить" else "Запустить")
                        }
                        OutlinedButton(
                            onClick = onToggleVisibility,
                            enabled = hasTranslationResult,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isVisible) "Оригинал" else "Перевод")
                        }
                    }

                    if (hasTranslationResult) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onClear) {
                                Text("Очистить кэш главы")
                            }
                        }
                    }
                }
            }

            if (page == 0 || page == 1) {
                GeminiSettingsBlock(
                    title = "Основные параметры",
                    subtitle = "Модель, режим промпта и производительность",
                ) {
                    if (page == 0) {
                        Text(
                            "Провайдер",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val providerCards = listOf(
                            NovelTranslationProvider.GEMINI to "Gemini",
                            NovelTranslationProvider.GEMINI_PRIVATE to privateProviderLabel,
                            NovelTranslationProvider.OPENROUTER to "OpenRouter",
                            NovelTranslationProvider.DEEPSEEK to "DeepSeek",
                        )
                        providerCards.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { option ->
                                    val selected = tempProvider == option.first
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                tempProvider = option.first
                                                onSetTranslationProvider(option.first)
                                                onAddLog("?? Provider: ${option.second}")
                                                when (option.first) {
                                                    NovelTranslationProvider.GEMINI -> Unit
                                                    NovelTranslationProvider.GEMINI_PRIVATE -> Unit
                                                    NovelTranslationProvider.OPENROUTER -> onRefreshOpenRouterModels()
                                                    NovelTranslationProvider.DEEPSEEK -> onRefreshDeepSeekModels()
                                                    NovelTranslationProvider.AIRFORCE -> Unit
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                            },
                                        ),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        },
                                    ) {
                                        Text(
                                            text = option.second,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 12.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (page == 0) {
                        if (privateBridgeInstalled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = if (isPrivateProviderUnlocked) {
                                        "$privateProviderLabel bridge подключен " +
                                            "и разблокирован."
                                    } else {
                                        "$privateProviderLabel bridge подключен. " +
                                            "Для работы нужен unlock."
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        if (privateBridgeRequiresUnlock && !isPrivateProviderUnlocked) {
                            OutlinedTextField(
                                value = tempPrivatePassword,
                                onValueChange = { tempPrivatePassword = it },
                                label = { Text("Пароль $privateProviderLabel bridge") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val password = tempPrivatePassword.trim()
                                        if (password.isBlank()) {
                                            onAddLog("🔒 Enter bridge password")
                                        } else {
                                            val unlocked = GeminiPrivateBridge.unlock(password)
                                            if (unlocked) {
                                                isPrivateProviderUnlocked = true
                                                onSetGeminiPrivateUnlocked(true)
                                                tempPrivatePassword = ""
                                                onAddLog("✅ $privateProviderLabel bridge unlocked")
                                            } else {
                                                onAddLog("🔎 ${GeminiPrivateBridge.debugInfo()}")
                                                onAddLog("❌ Invalid bridge password")
                                            }
                                        }
                                    },
                                ) {
                                    Text("Unlock")
                                }
                                OutlinedButton(
                                    onClick = {
                                        tempPrivatePassword = ""
                                    },
                                ) {
                                    Text("Очистить")
                                }
                            }
                        }

                        when (tempProvider) {
                            NovelTranslationProvider.GEMINI,
                            NovelTranslationProvider.GEMINI_PRIVATE,
                            -> {
                                Text(
                                    "Модель",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                    value = tempModel,
                                    title = "Текущая модель",
                                    subtitle = modelMap[tempModel] ?: tempModel,
                                    icon = null,
                                    entries = modelMap,
                                    onValueChange = { selected ->
                                        tempModel = selected
                                        onSetGeminiModel(selected)
                                        onAddLog("?? Model: ${modelMap[selected] ?: selected}")
                                    },
                                )
                            }
                            NovelTranslationProvider.OPENROUTER -> {
                                Text(
                                    "OpenRouter модели (free)",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (openRouterAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempOpenRouterModel,
                                        title =
                                        "Бесплатные модели (${openRouterAllModelEntries.size})",
                                        subtitle = tempOpenRouterModel.ifBlank {
                                            "Выберите free модель (:free)"
                                        },
                                        icon = null,
                                        entries = openRouterAllModelEntries,
                                        onValueChange = { selected ->
                                            tempOpenRouterModel = selected
                                            onSetOpenRouterModel(selected)
                                            onAddLog("?? OpenRouter model: $selected")
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshOpenRouterModels) {
                                        Text(
                                            if (isOpenRouterModelsLoading) {
                                                "Загрузка моделей..."
                                            } else {
                                                "Обновить список"
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempOpenRouterModel,
                                    onValueChange = {
                                        tempOpenRouterModel = it
                                        onSetOpenRouterModel(it)
                                    },
                                    label = { Text("Model ID (только :free)") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            NovelTranslationProvider.DEEPSEEK -> {
                                Text(
                                    "DeepSeek модели",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (deepSeekAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempDeepSeekModel,
                                        title = "Модели (${deepSeekAllModelEntries.size})",
                                        subtitle = tempDeepSeekModel.ifBlank { "Выберите модель" },
                                        icon = null,
                                        entries = deepSeekAllModelEntries,
                                        onValueChange = { selected ->
                                            tempDeepSeekModel = selected
                                            onSetDeepSeekModel(selected)
                                            onAddLog("?? DeepSeek model: $selected")
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshDeepSeekModels) {
                                        Text(
                                            if (isDeepSeekModelsLoading) {
                                                "Загрузка моделей..."
                                            } else {
                                                "Обновить список"
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempDeepSeekModel,
                                    onValueChange = {
                                        tempDeepSeekModel = it
                                        onSetDeepSeekModel(it)
                                    },
                                    label = { Text("Model ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            NovelTranslationProvider.AIRFORCE -> Unit
                        }
                    }

                    if (page == 1) {
                        if (!isPrivateSingleRequestMode) {
                            Text(
                                "Режим промпта",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        GeminiPromptMode.CLASSIC to "Классический",
                                        GeminiPromptMode.ADULT_18 to "18+",
                                    ),
                                ) { option ->
                                    val selected = tempPromptMode == option.first
                                    OutlinedButton(
                                        onClick = {
                                            tempPromptMode = option.first
                                            onSetGeminiPromptMode(option.first)
                                            onAddLog("?? Prompt mode: ${option.second}")
                                        },
                                    ) {
                                        Text(if (selected) "• ${option.second}" else option.second)
                                    }
                                }
                            }

                            Text(
                                "Стиль перевода",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(stylePresets) { preset ->
                                    val selected = tempStylePreset == preset.id
                                    OutlinedButton(
                                        onClick = {
                                            tempStylePreset = preset.id
                                            onSetGeminiStylePreset(preset.id)
                                            onAddLog("?? Стиль: ${preset.title}")
                                        },
                                    ) {
                                        Text(if (selected) "• ${preset.title}" else preset.title)
                                    }
                                }
                            }
                            val selectedStylePreset = stylePresets.firstOrNull { it.id == tempStylePreset }
                            if (selectedStylePreset != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = selectedStylePreset.title,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            text = "Для чего: ${selectedStylePreset.scenario}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Преимущество: ${selectedStylePreset.advantage}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            Text(
                                "Модификаторы промпта",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(GeminiPromptModifiers.all) { modifier ->
                                    val selected = tempEnabledModifiers.contains(modifier.id)
                                    Surface(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable {
                                            tempEnabledModifiers = if (selected) {
                                                tempEnabledModifiers - modifier.id
                                            } else {
                                                tempEnabledModifiers + modifier.id
                                            }
                                            onSetGeminiEnabledPromptModifiers(
                                                tempEnabledModifiers.toList(),
                                            )
                                        },
                                    ) {
                                        Text(
                                            text = modifier.label,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                                item {
                                    Surface(
                                        color = if (tempCustomModifier.isNotBlank()) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable { showCustomPromptDialog = true },
                                    ) {
                                        Text(
                                            text = if (tempCustomModifier.isBlank()) "+ Свой" else "Свой",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text =
                                    "$privateProviderLabel: используются " +
                                        "защищённые правила private bridge " +
                                        "и авто-режим без пользовательских " +
                                        "модификаторов.",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (page == 0 && !isPrivateSingleRequestMode) {
                        Text(
                            "Скорость (батч-параллельность)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(speedPresets) { preset ->
                                val label = preset.first
                                val batch = preset.second.first
                                val concurrency = preset.second.second
                                val selected = tempBatch == batch.toString() &&
                                    tempConcurrency == concurrency.toString()
                                OutlinedButton(
                                    onClick = {
                                        tempBatch = batch.toString()
                                        tempConcurrency = concurrency.toString()
                                        onSetGeminiBatchSize(batch)
                                        onSetGeminiConcurrency(concurrency)
                                        onAddLog("?? Speed: $label")
                                    },
                                ) {
                                    Text(if (selected) "• $label" else label)
                                }
                            }
                        }
                    }

                    if (page == 0 && isPrivateSingleRequestMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text =
                                "$privateProviderLabel: отправка идёт " +
                                    "одним запросом на главу. При ошибке " +
                                    "включается fallback (batch=40, " +
                                    "concurrency=1).",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (
                        page == 1 &&
                        isGeminiFamilySelected &&
                        (
                            tempModel == "gemini-3-flash-preview" ||
                                tempModel == "gemini-3-pro-preview" ||
                                tempModel == "gemini-3.1-flash-lite-preview"
                            )
                    ) {
                        Text(
                            "Уровень размышления",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val reasoningOptions = if (tempModel == "gemini-3-pro-preview") {
                            listOf("low", "high")
                        } else {
                            listOf("minimal", "low", "medium", "high")
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(reasoningOptions) { option ->
                                OutlinedButton(
                                    onClick = {
                                        tempReasoning = option
                                        onSetGeminiReasoningEffort(option)
                                        onAddLog("?? Reasoning: ${option.uppercase()}")
                                    },
                                ) {
                                    Text(
                                        if (tempReasoning == option) {
                                            "• ${option.uppercase()}"
                                        } else {
                                            option.uppercase()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (page == 2) {
                GeminiSettingsBlock(
                    title = "Система и кэш",
                    subtitle = "API ключ, кэш и ручной контроль потоков",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Автостарт перевода для English",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = tempAutoTranslateEnglish,
                            onCheckedChange = { enabled ->
                                tempAutoTranslateEnglish = enabled
                                onSetGeminiAutoTranslateEnglishSource(enabled)
                                onAddLog("?? Auto English: ${if (enabled) "ON" else "OFF"}")
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Превентивный перевод следующей главы (30%)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = tempPrefetchNextChapterTranslation,
                            onCheckedChange = { enabled ->
                                tempPrefetchNextChapterTranslation = enabled
                                onSetGeminiPrefetchNextChapterTranslation(enabled)
                                onAddLog("?? Next chapter pre-translation: ${if (enabled) "ON" else "OFF"}")
                            },
                        )
                    }
                    if (isGeminiPrivateSelected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_gemini_private_python_like_mode),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = tempPrivatePythonLikeMode,
                                onCheckedChange = { enabled ->
                                    tempPrivatePythonLikeMode = enabled
                                    onSetGeminiPrivatePythonLikeMode(enabled)
                                    onAddLog("🔀 Private Python-like: ${if (enabled) "ON" else "OFF"}")
                                },
                            )
                        }
                    }
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(
                            text = if (showAdvanced) {
                                stringResource(AYMR.strings.novel_reader_gemini_advanced_hide)
                            } else {
                                stringResource(AYMR.strings.novel_reader_gemini_advanced_show)
                            },
                        )
                    }
                    if (showAdvanced) {
                        if (isOpenRouterSelected || isDeepSeekSelected) {
                            OutlinedTextField(
                                value = if (isOpenRouterSelected) tempOpenRouterBaseUrl else tempDeepSeekBaseUrl,
                                onValueChange = {
                                    if (isOpenRouterSelected) {
                                        tempOpenRouterBaseUrl = it
                                    } else {
                                        tempDeepSeekBaseUrl = it
                                    }
                                },
                                label = { Text("Base URL") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = when {
                                isOpenRouterSelected -> tempOpenRouterApiKey
                                isDeepSeekSelected -> tempDeepSeekApiKey
                                else -> tempKey
                            },
                            onValueChange = {
                                if (isOpenRouterSelected) {
                                    tempOpenRouterApiKey = it
                                } else if (isDeepSeekSelected) {
                                    tempDeepSeekApiKey = it
                                } else {
                                    tempKey = it
                                }
                            },
                            label = {
                                Text(
                                    when {
                                        isOpenRouterSelected -> "OpenRouter API key"
                                        isDeepSeekSelected -> "DeepSeek API key"
                                        else -> "API ключ"
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    if (isOpenRouterSelected) {
                                        onSetOpenRouterBaseUrl(tempOpenRouterBaseUrl)
                                        onSetOpenRouterApiKey(tempOpenRouterApiKey)
                                        onSetOpenRouterModel(tempOpenRouterModel)
                                        onAddLog("?? OpenRouter settings saved")
                                    } else if (isDeepSeekSelected) {
                                        onSetDeepSeekBaseUrl(tempDeepSeekBaseUrl)
                                        onSetDeepSeekApiKey(tempDeepSeekApiKey)
                                        onSetDeepSeekModel(tempDeepSeekModel)
                                        onAddLog("?? DeepSeek settings saved")
                                    } else {
                                        onSetGeminiApiKey(tempKey)
                                        onAddLog("?? API ключ сохранен")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Сохранить")
                            }
                            TextButton(
                                onClick = {
                                    tempRelaxed = !tempRelaxed
                                    onSetGeminiRelaxedMode(tempRelaxed)
                                    onAddLog("?? Relaxed: ${if (tempRelaxed) "ON" else "OFF"}")
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Relaxed: ${if (tempRelaxed) "ON" else "OFF"}")
                            }
                        }
                        if (isOpenRouterSelected || isDeepSeekSelected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    onClick = if (isOpenRouterSelected) {
                                        onTestOpenRouterConnection
                                    } else {
                                        onTestDeepSeekConnection
                                    },
                                    enabled = if (isOpenRouterSelected) {
                                        !isTestingOpenRouterConnection
                                    } else {
                                        !isTestingDeepSeekConnection
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val isTesting = if (isOpenRouterSelected) {
                                        isTestingOpenRouterConnection
                                    } else {
                                        isTestingDeepSeekConnection
                                    }
                                    Text(
                                        if (isTesting) {
                                            "Проверка..."
                                        } else {
                                            "Тест подключения"
                                        },
                                    )
                                }
                                TextButton(
                                    onClick = if (isOpenRouterSelected) {
                                        onRefreshOpenRouterModels
                                    } else {
                                        onRefreshDeepSeekModels
                                    },
                                    enabled = if (isOpenRouterSelected) {
                                        !isOpenRouterModelsLoading
                                    } else {
                                        !isDeepSeekModelsLoading
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val isLoading = if (isOpenRouterSelected) {
                                        isOpenRouterModelsLoading
                                    } else {
                                        isDeepSeekModelsLoading
                                    }
                                    Text(
                                        if (isLoading) {
                                            "Обновление..."
                                        } else {
                                            "Обновить модели"
                                        },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Кэш: ${if (tempDisableCache) "OFF" else "ON"}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = !tempDisableCache,
                                onCheckedChange = { enabled ->
                                    tempDisableCache = !enabled
                                    onSetGeminiDisableCache(tempDisableCache)
                                    onAddLog("?? Кэш: ${if (tempDisableCache) "OFF" else "ON"}")
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempBatch,
                                onValueChange = {
                                    tempBatch = it
                                    applyBatchAndConcurrency()
                                },
                                label = { Text("Батч") },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = tempConcurrency,
                                onValueChange = {
                                    tempConcurrency = it
                                    applyBatchAndConcurrency()
                                },
                                label = { Text("Потоки") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(onClick = {
                            onClearAllCache()
                            onAddLog("??? Очищен весь кэш")
                        }) {
                            Text("Очистить весь кэш")
                        }
                    }
                }
            }

            if (page == 1) {
                GeminiSettingsBlock(
                    title = "Генерация",
                    subtitle = "Пресеты и ручные параметры sampling",
                ) {
                    TextButton(onClick = { showGenerationConfig = !showGenerationConfig }) {
                        Text(if (showGenerationConfig) "Скрыть генерацию" else "Генерация")
                    }
                    if (showGenerationConfig) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(activeGenerationPresets) { preset ->
                                val isSelected = preset.id == selectedGenerationPresetId
                                OutlinedButton(
                                    onClick = {
                                        selectedGenerationPresetId = preset.id
                                        val name = preset.title
                                        val t = preset.temperature
                                        val p = preset.topP
                                        tempTemperature = t.toString()
                                        tempTopP = p.toString()
                                        onSetGeminiTemperature(t)
                                        onSetGeminiTopP(p)
                                        val k = preset.topK
                                        if (k != null) {
                                            tempTopK = k.toString()
                                            onSetGeminiTopK(k)
                                            onAddLog("?? Preset: $name (T:$t P:$p K:$k)")
                                        } else {
                                            onAddLog("?? Preset: $name (T:$t P:$p)")
                                        }
                                    },
                                ) {
                                    Text(if (isSelected) "• ${preset.title}" else preset.title)
                                }
                            }
                        }
                        val selectedPreset = activeGenerationPresets.firstOrNull { it.id == selectedGenerationPresetId }
                            ?: activeGenerationPresets.first()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = selectedPreset.title,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = "Для чего: ${selectedPreset.scenario}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Преимущество: ${selectedPreset.advantage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempTemperature,
                                onValueChange = {
                                    tempTemperature = it
                                    it.toFloatOrNull()?.let { value ->
                                        val normalized = if (isDeepSeekSelected) {
                                            value.coerceIn(1.3f, 1.5f)
                                        } else {
                                            value
                                        }
                                        onSetGeminiTemperature(normalized)
                                        onAddLog("?? Temp: $normalized")
                                    }
                                },
                                label = { Text("Temperature") },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = tempTopP,
                                onValueChange = {
                                    tempTopP = it
                                    it.toFloatOrNull()?.let { value ->
                                        val normalized = if (isDeepSeekSelected) {
                                            value.coerceIn(0.9f, 0.95f)
                                        } else {
                                            value
                                        }
                                        onSetGeminiTopP(normalized)
                                        onAddLog("?? TopP: $normalized")
                                    }
                                },
                                label = { Text("TopP") },
                                modifier = Modifier.weight(1f),
                            )
                            if (!isDeepSeekSelected) {
                                OutlinedTextField(
                                    value = tempTopK,
                                    onValueChange = {
                                        tempTopK = it
                                        it.toIntOrNull()?.let { value ->
                                            onSetGeminiTopK(value)
                                            onAddLog("?? TopK: $value")
                                        }
                                    },
                                    label = { Text("TopK") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (isDeepSeekSelected) {
                            Text(
                                text = "Для DeepSeek используется диапазон " +
                                    "Temperature 1.3-1.5 и TopP 0.9-0.95. " +
                                    "TopK не применяется.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (page == 2) {
                GeminiSettingsBlock(
                    title = "Логи",
                    subtitle = "Диагностика запросов и ответа модели",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Логи (${logs.size})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showLogs = !showLogs }) {
                                Text(if (showLogs) "Скрыть" else "Показать")
                            }
                            TextButton(onClick = onClearLogs) {
                                Text("Очистить")
                            }
                        }
                    }
                    if (showLogs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (logs.isEmpty()) {
                                Text("Логи пока пусты", style = MaterialTheme.typography.bodySmall)
                            } else {
                                logs.forEach { log ->
                                    Text(log, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomPromptDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPromptDialog = false },
            title = { Text("Свой модификатор промпта") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempCustomModifier,
                        onValueChange = { tempCustomModifier = it },
                        label = { Text("Свои инструкции") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Текст будет добавлен в системный " +
                            "промпт как дополнительная инструкция.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetGeminiCustomPromptModifier(tempCustomModifier)
                    onAddLog("?? Обновлен свой промпт")
                    showCustomPromptDialog = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        tempCustomModifier = ""
                        onSetGeminiCustomPromptModifier("")
                        showCustomPromptDialog = false
                    }) { Text("Очистить") }
                    TextButton(onClick = { showCustomPromptDialog = false }) { Text("Отмена") }
                }
            },
        )
    }
}

@Composable
private fun LnReaderVerticalSeekbar(
    progress: Float,
    topLabel: String?,
    bottomLabel: String?,
    tickFractions: List<Float> = emptyList(),
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val thumbProgress = if (isDragged) scrubProgress else progress
    val trackColor = MaterialTheme.colorScheme.outline
    val progressColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)

    LaunchedEffect(progress, isDragged) {
        if (!isDragged) {
            scrubProgress = progress
        }
    }

    fun normalizeProgressFromY(y: Float): Float {
        if (containerHeightPx <= 0f) return progress
        val trackTop = containerHeightPx * 0.125f
        val trackBottom = containerHeightPx * 0.875f
        val trackHeight = (trackBottom - trackTop).coerceAtLeast(1f)
        return ((y - trackTop) / trackHeight).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .background(
                color = containerColor,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
            .pointerInput(containerHeightPx) {
                detectTapGestures { offset ->
                    val newProgress = normalizeProgressFromY(offset.y)
                    scrubProgress = newProgress
                    onProgressChange(newProgress)
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                interactionSource = interactionSource,
                state = rememberDraggableState { delta ->
                    val trackTop = containerHeightPx * 0.125f
                    val trackHeight = (containerHeightPx * 0.75f).coerceAtLeast(1f)
                    val currentY = trackTop + (trackHeight * thumbProgress)
                    val newProgress = normalizeProgressFromY(currentY + delta)
                    scrubProgress = newProgress
                    onProgressChange(newProgress)
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                topLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(6f)
                    .fillMaxWidth(),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    val centerX = size.width / 2f
                    val trackTop = 0f
                    val trackBottom = size.height
                    val trackWidth = if (isDragged) 1.5.dp.toPx() else 1.dp.toPx()
                    val activeProgress = thumbProgress.coerceIn(0f, 1f)
                    val thumbY = trackTop + ((trackBottom - trackTop) * activeProgress)
                    val thumbRadius = if (isDragged) 5.5.dp.toPx() else 4.dp.toPx()
                    val thumbHaloRadius = if (isDragged) 9.dp.toPx() else 0f
                    val tickRadius = if (isDragged) 1.75.dp.toPx() else 1.5.dp.toPx()

                    drawLine(
                        color = trackColor,
                        start = Offset(centerX, trackTop),
                        end = Offset(centerX, trackBottom),
                        strokeWidth = trackWidth,
                    )
                    drawLine(
                        color = progressColor,
                        start = Offset(centerX, trackTop),
                        end = Offset(centerX, thumbY),
                        strokeWidth = trackWidth,
                    )
                    tickFractions.forEach { tickFraction ->
                        val normalizedTickFraction = tickFraction.coerceIn(0f, 1f)
                        val tickY = trackTop + ((trackBottom - trackTop) * normalizedTickFraction)
                        val tickColor = if (normalizedTickFraction <= activeProgress) {
                            progressColor.copy(alpha = if (isDragged) 0.95f else 0.85f)
                        } else {
                            trackColor.copy(alpha = 0.75f)
                        }
                        drawCircle(
                            color = tickColor,
                            radius = tickRadius,
                            center = Offset(centerX, tickY),
                        )
                    }
                    if (thumbHaloRadius > 0f) {
                        drawCircle(
                            color = progressColor.copy(alpha = 0.18f),
                            radius = thumbHaloRadius,
                            center = Offset(centerX, thumbY),
                        )
                    }
                    drawCircle(
                        color = if (isDragged) progressColor else progressColor.copy(alpha = 0.92f),
                        radius = thumbRadius,
                        center = Offset(centerX, thumbY),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                bottomLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderAtmosphereBackground(
    backgroundColor: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    oledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    backgroundImageModel: Any?,
) {
    val textureIntensityFactor = remember(nativeTextureStrengthPercent) {
        resolveNativeTextureIntensityFactor(nativeTextureStrengthPercent)
    }
    val radialLayers = remember(backgroundTexture, oledEdgeGradient, isDarkTheme, textureIntensityFactor) {
        buildReaderAtmosphereRadialLayers(
            backgroundTexture = backgroundTexture,
            oledEdgeGradient = oledEdgeGradient,
            isDarkTheme = isDarkTheme,
            intensityFactor = textureIntensityFactor,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (backgroundImageModel != null) {
            AsyncImage(
                model = backgroundImageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (backgroundTexture == NovelReaderBackgroundTexture.PAPER_GRAIN ||
            backgroundTexture == NovelReaderBackgroundTexture.LINEN
        ) {
            val imageRes = if (backgroundTexture == NovelReaderBackgroundTexture.PAPER_GRAIN) {
                R.drawable.texture_paper
            } else {
                R.drawable.texture_linen
            }

            val imageBitmap = ImageBitmap.imageResource(id = imageRes)
            val brush = remember(imageBitmap) {
                ShaderBrush(
                    ImageShader(
                        image = imageBitmap,
                        tileModeX = TileMode.Repeated,
                        tileModeY = TileMode.Repeated,
                    ),
                )
            }
            val baseTextureAlpha = textureIntensityFactor.coerceIn(0f, 1f)
            val boostTextureAlpha = ((textureIntensityFactor - 1f) / 3f).coerceIn(0f, 1f) * 0.45f
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (baseTextureAlpha > 0f) {
                    drawRect(brush = brush, alpha = baseTextureAlpha)
                }
                if (boostTextureAlpha > 0f) {
                    drawRect(
                        brush = brush,
                        alpha = boostTextureAlpha,
                        blendMode = BlendMode.Multiply,
                    )
                }
            }
        }

        if (radialLayers.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                radialLayers.forEach { layer ->
                    val center = Offset(
                        x = size.width * layer.centerXFraction,
                        y = size.height * layer.centerYFraction,
                    )
                    val radius = calculateRadialGradientFarthestCornerRadius(
                        size = size,
                        center = center,
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = layer.colorStops.toTypedArray(),
                            center = center,
                            radius = radius,
                        ),
                    )
                }
            }
        }

        if (pageEdgeShadow) {
            val edgeColor = resolvePageEdgeShadowColor(
                pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                backgroundColor = backgroundColor,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            0.0f to edgeColor,
                            0.04f to Color.Transparent,
                            0.96f to Color.Transparent,
                            1.0f to edgeColor,
                        ),
                    ),
            )
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

@Composable
private fun SelectedTextTranslationOverlay(
    state: NovelReaderScreenModel.State.Success,
    onTranslate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection = state.selectedTextTranslationSelection
    val translationState = state.selectedTextTranslationUiState

    if (selection == null && translationState is NovelSelectedTextTranslationUiState.Idle) {
        return
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 360.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (translationState) {
            is NovelSelectedTextTranslationUiState.SelectionAvailable -> {
                FloatingActionButton(
                    onClick = onTranslate,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = stringResource(
                            AYMR.strings.novel_reader_selected_text_translation_action_translate,
                        ),
                    )
                }
            }
            is NovelSelectedTextTranslationUiState.Translating -> {
                SelectedTextTranslationCard(
                    title = stringResource(AYMR.strings.novel_reader_selected_text_translation_loading),
                    subtitle = selection?.text,
                    onDismiss = onDismiss,
                    trailingContent = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                )
            }
            is NovelSelectedTextTranslationUiState.Result -> {
                SelectedTextTranslationCard(
                    title = selection?.text,
                    subtitle = translationState.translationResult.translation,
                    onDismiss = onDismiss,
                )
            }
            is NovelSelectedTextTranslationUiState.Error -> {
                SelectedTextTranslationCard(
                    title = selection?.text,
                    subtitle = translationErrorMessage(translationState.reason),
                    onDismiss = onDismiss,
                    actionLabel = stringResource(
                        AYMR.strings.novel_reader_selected_text_translation_action_retry,
                    ),
                    onAction = onRetry,
                )
            }
            is NovelSelectedTextTranslationUiState.Unavailable -> {
                SelectedTextTranslationCard(
                    title = selection?.text,
                    subtitle = translationErrorMessage(translationState.reason),
                    onDismiss = onDismiss,
                    actionLabel = stringResource(
                        AYMR.strings.novel_reader_selected_text_translation_action_retry,
                    ),
                    onAction = onRetry,
                )
            }
            NovelSelectedTextTranslationUiState.Idle -> {
                if (selection != null) {
                    FloatingActionButton(
                        onClick = onTranslate,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = stringResource(
                                AYMR.strings.novel_reader_selected_text_translation_action_translate,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedTextTranslationCard(
    title: String?,
    subtitle: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 328.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title?.takeIf { it.isNotBlank() }
                        ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_action_translate),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailingContent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailingContent()
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_close))
                }
            }
        }
    }
}

@Composable
private fun translationErrorMessage(reason: NovelSelectedTextTranslationErrorReason): String {
    return when (reason) {
        NovelSelectedTextTranslationErrorReason.EmptySelection,
        NovelSelectedTextTranslationErrorReason.TooLongSelection,
        NovelSelectedTextTranslationErrorReason.ParserFailure,
        NovelSelectedTextTranslationErrorReason.WebViewUnavailable -> {
            stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.BackendUnavailable -> {
            reason.message?.takeIf { it.isNotBlank() }
                ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.NetworkFailure -> {
            reason.message?.takeIf { it.isNotBlank() }
                ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.Cooldown -> {
            "${stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)} (${reason.remainingSeconds}s)"
        }
    }
}

@Composable
internal fun SystemUIController(
    fullScreenMode: Boolean,
    keepScreenOn: Boolean,
    showReaderUi: Boolean,
) {
    val view = LocalView.current

    val capturedSystemBarsState = remember(view) { mutableStateOf<ReaderSystemBarsState?>(null) }
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else {
            null
        }
        if (capturedSystemBarsState.value == null && insetsController != null) {
            capturedSystemBarsState.value = insetsController.captureReaderSystemBarsState()
        }
        onDispose {
            val activity = view.context.findActivity() ?: return@onDispose
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            val internalChapterReplace = NovelReaderSystemUiSession.consumeInternalChapterReplace()
            val restoredState = resolveReaderExitSystemBarsState(
                captured = capturedSystemBarsState.value,
                current = insetsController.captureReaderSystemBarsState(),
            )
            if (shouldRestoreSystemBarsOnDispose(isInternalChapterReplace = internalChapterReplace)) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            insetsController.restoreReaderSystemBarsState(restoredState)
            if (!internalChapterReplace) {
                NovelReaderSystemUiSession.clear()
            }
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        if (capturedSystemBarsState.value == null) {
            capturedSystemBarsState.value = insetsController.captureReaderSystemBarsState()
        }
        val baseSystemBarsState = capturedSystemBarsState.value ?: insetsController.captureReaderSystemBarsState()
        val activeSystemBarsState = resolveActiveReaderSystemBarsState(
            showReaderUi = showReaderUi,
            fullScreenMode = fullScreenMode,
            base = baseSystemBarsState,
        )

        // Keep Screen On
        if (keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Fullscreen Mode
        if (shouldHideSystemBars(fullScreenMode = fullScreenMode, showReaderUi = showReaderUi)) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        // Re-apply desired icon appearance after show/hide, as showing bars can
        // transiently restore prior icon mode on first reveal.
        insetsController.restoreReaderSystemBarsState(activeSystemBarsState)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
