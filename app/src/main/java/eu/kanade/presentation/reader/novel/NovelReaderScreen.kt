package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateFormat
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.ui.reader.novel.PageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

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
) {
    var showSettings by remember { mutableStateOf(false) }
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

    // Получаем размеры system bars
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

    // Высота AppBar (стандартная Material3 AppBar ~64dp + statusBar)
    val appBarHeight = with(density) { (64.dp + statusBarHeight.toDp()).toPx().toInt() }
    // Высота Bottom bar (~80dp + navigation bar)
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
    val shouldPaginateForPageReader = state.readerSettings.pageReader &&
        scrollContentBlocks.none { it is NovelReaderScreenModel.ContentBlock.Image }
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
        shouldPaginateForPageReader,
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
        if (!shouldPaginateForPageReader || pageReaderTextBlocks.isEmpty()) {
            emptyList()
        } else {
            val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
            val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
            val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
            val verticalPaddingPx = topPaddingPx + bottomPaddingPx
            paginatePlainPageBlocks(
                textBlocks = pageReaderTextBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                textAlign = pageReaderLayoutTextAlign,
            )
        }
    }
    val shouldPaginateRichForPageReader = shouldUseRichNativePageRenderer(
        richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
        pageReaderEnabled = shouldPaginateForPageReader,
        bionicReadingEnabled = state.readerSettings.bionicReading,
        richContentBlocks = state.richContentBlocks,
        richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
    )
    val richPageReaderBlockTexts = remember(
        state.chapter.id,
        state.richContentBlocks,
        shouldPaginateRichForPageReader,
        state.readerSettings.forceParagraphIndent,
    ) {
        if (!shouldPaginateRichForPageReader) {
            emptyList()
        } else {
            state.richContentBlocks.map { block ->
                buildRichPageReaderBlockText(
                    block = block,
                    forcedParagraphFirstLineIndentEm = if (state.readerSettings.forceParagraphIndent) {
                        FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
                    } else {
                        null
                    },
                )
            }.filter { it.text.text.isNotBlank() }
        }
    }
    val richPageReaderPages: List<List<RichPageSlice>> = remember(
        state.chapter.id,
        richPageReaderBlockTexts,
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
            emptyList()
        } else {
            if (richPageReaderBlockTexts.isEmpty()) {
                emptyList()
            } else {
                val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                    ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
                val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                    ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
                val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
                val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
                val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
                val verticalPaddingPx = topPaddingPx + bottomPaddingPx
                paginateRichPageBlocks(
                    blockTexts = richPageReaderBlockTexts,
                    paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                    widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                    heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                    textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                    lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                    typeface = composeTypeface,
                    textAlign = pageReaderLayoutTextAlign,
                )
            }
        }
    }
    val usePageReader = shouldPaginateForPageReader &&
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
    val isInternalChapterHandoff = remember(state.chapter.id) {
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
    val pagerState = rememberPagerState(
        initialPage = resolveInitialPageReaderPage(
            savedPageReaderProgress = state.lastSavedPageReaderProgress,
            legacyLastSavedIndex = state.lastSavedIndex,
            pageCount = pageReaderItemsCount.coerceAtLeast(1),
            isInternalChapterHandoff = isInternalChapterHandoff,
        ),
        pageCount = { pageReaderItemsCount.coerceAtLeast(1) },
    )
    var pageTurnCurrentPage by remember(state.chapter.id) {
        mutableIntStateOf(pagerState.currentPage)
    }
    var pageTurnRequestedPage by remember(state.chapter.id) {
        mutableIntStateOf(-1)
    }
    val pageReaderProgressPageIndex by remember(
        pageReaderRendererRoute,
        pagerState.currentPage,
        pageTurnCurrentPage,
    ) {
        derivedStateOf {
            resolvePageReaderCurrentPage(
                pageReaderRendererRoute = pageReaderRendererRoute,
                pagerCurrentPage = pagerState.currentPage,
                pageTurnCurrentPage = pageTurnCurrentPage,
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

    // Управление System UI для fullscreen режима
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
    suspend fun moveBackwardByReaderAction() {
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(-1)) {
                webView.scrollBy(0, -tapScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                onOpenPreviousChapter?.invoke(state.previousChapterId)
            }
        } else if (usePageReader) {
            val currentPage = pageReaderProgressPageIndex
            if (currentPage > 0) {
                pageTurnCurrentPage = currentPage - 1
                pagerState.animateScrollToPage(currentPage - 1)
            } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                onOpenPreviousChapter?.invoke(state.previousChapterId)
            }
        } else if (textListState.canScrollBackward) {
            textListState.scrollBy(-tapScrollStepPx)
        } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
            onOpenPreviousChapter?.invoke(state.previousChapterId)
        }
    }

    suspend fun moveForwardByReaderAction() {
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(1)) {
                webView.scrollBy(0, tapScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                onOpenNextChapter?.invoke(state.nextChapterId)
            }
        } else if (usePageReader) {
            val currentPage = pageReaderProgressPageIndex
            if (currentPage < pageReaderItemsCount - 1) {
                pageTurnCurrentPage = currentPage + 1
                pagerState.animateScrollToPage(currentPage + 1)
            } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                onOpenNextChapter?.invoke(state.nextChapterId)
            }
        } else if (textListState.canScrollForward) {
            textListState.scrollBy(tapScrollStepPx)
        } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
            onOpenNextChapter?.invoke(state.nextChapterId)
        }
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
                    pagerState.animateScrollToPage(currentPage + 1)
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
        // Контент (текст главы) - занимает весь экран, padding ВНУТРИ через contentPadding
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!showWebView && scrollContentBlocks.isNotEmpty()) {
                // Отслеживание прогресса в зависимости от режима
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

                // Page Reader Mode (постраничный режим)
                if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        ComposePagerPageRenderer(
                            pagerState = pagerState,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            backgroundTexture = state.readerSettings.backgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            composeFontFamily = composeFontFamily,
                            chapterTitleFontFamily = chapterTitleFontFamily,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            hasPreviousChapter = state.previousChapterId != null,
                            hasNextChapter = state.nextChapterId != null,
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            onMoveBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
                            onMoveForward = { coroutineScope.launch { moveForwardByReaderAction() } },
                            onOpenPreviousChapter = { state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) } },
                            onOpenNextChapter = { state.nextChapterId?.let { onOpenNextChapter?.invoke(it) } },
                        )
                } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        PageTurnPageRenderer(
                            pagerState = pagerState,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            backgroundTexture = state.readerSettings.backgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                            backgroundModeIdentity = if (isBackgroundMode) backgroundModeIdentity else "",
                            isBackgroundMode = isBackgroundMode,
                            activeBackgroundTexture = activeBackgroundTexture,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            composeFontFamily = composeFontFamily,
                            chapterTitleFontFamily = chapterTitleFontFamily,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            hasPreviousChapter = state.previousChapterId != null,
                            hasNextChapter = state.nextChapterId != null,
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            requestedPage = pageTurnRequestedPage,
                            onRequestedPageConsumed = { pageTurnRequestedPage = -1 },
                            onCurrentPageChange = { pageTurnCurrentPage = it },
                            onMoveBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
                            onMoveForward = { coroutineScope.launch { moveForwardByReaderAction() } },
                            onOpenPreviousChapter = { state.previousChapterId?.let { onOpenPreviousChapter?.invoke(it) } },
                            onOpenNextChapter = { state.nextChapterId?.let { onOpenNextChapter?.invoke(it) } },
                        )
                } else {
                    // Scroll Mode (режим прокрутки, по умолчанию)
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
                                    detectTapGestures(
                                        onTap = { offset ->
                                            when (
                                                resolveReaderTapAction(
                                                    tapX = offset.x,
                                                    width = size.width.toFloat(),
                                                    tapToScrollEnabled = latestTapToScrollEnabled,
                                                )
                                            ) {
                                                ReaderTapAction.TOGGLE_UI -> onSetShowReaderUi(!latestShowReaderUi)
                                                ReaderTapAction.BACKWARD -> coroutineScope.launch {
                                                    moveBackwardByReaderAction()
                                                }
                                                ReaderTapAction.FORWARD -> coroutineScope.launch {
                                                moveForwardByReaderAction()
                                            }
                                        }
                                    },
                                )
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
                                    fontSize = state.readerSettings.fontSize,
                                    lineHeight = state.readerSettings.lineHeight,
                                    composeFontFamily = composeFontFamily,
                                    chapterTitleFontFamily = chapterTitleFontFamily,
                                    paragraphSpacing = paragraphSpacing,
                                    textAlign = state.readerSettings.textAlign,
                                    forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                                    preserveSourceTextAlignInNative =
                                    state.readerSettings.preserveSourceTextAlignInNative,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                    textShadow = state.readerSettings.textShadow,
                                    textShadowColor = state.readerSettings.textShadowColor,
                                    textShadowBlur = state.readerSettings.textShadowBlur,
                                    textShadowX = state.readerSettings.textShadowX,
                                    textShadowY = state.readerSettings.textShadowY,
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
                                        val baseStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = textColor,
                                            fontSize = if (isChapterTitle) {
                                                (state.readerSettings.fontSize * 1.12f).sp
                                            } else {
                                                state.readerSettings.fontSize.sp
                                            },
                                            lineHeight = if (isChapterTitle) {
                                                (state.readerSettings.lineHeight * 1.08f).em
                                            } else {
                                                state.readerSettings.lineHeight.em
                                            },
                                            fontFamily = if (isChapterTitle) {
                                                chapterTitleFontFamily ?: composeFontFamily
                                            } else {
                                                composeFontFamily
                                            },
                                            fontWeight = if (isChapterTitle) {
                                                FontWeight.SemiBold
                                            } else if (state.readerSettings.forceBoldText) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            fontStyle = if (state.readerSettings.forceItalicText) {
                                                FontStyle.Italic
                                            } else {
                                                FontStyle.Normal
                                            },
                                            shadow = if (state.readerSettings.textShadow) {
                                                val customColor = parseReaderColor(state.readerSettings.textShadowColor)
                                                val shadowColor = resolveAutoReaderShadowColor(
                                                    customShadowColor = customColor,
                                                    textColor = textColor,
                                                    backgroundColor = textBackground,
                                                )
                                                Shadow(
                                                    color = shadowColor,
                                                    blurRadius = state.readerSettings.textShadowBlur,
                                                    offset = androidx.compose.ui.geometry.Offset(
                                                        x = state.readerSettings.textShadowX,
                                                        y = state.readerSettings.textShadowY,
                                                    ),
                                                )
                                            } else {
                                                null
                                            },
                                        ).withOptionalTextAlign(
                                            resolveNativeTextAlign(
                                                globalTextAlign = state.readerSettings.textAlign,
                                                preserveSourceTextAlignInNative =
                                                state.readerSettings.preserveSourceTextAlignInNative,
                                            ),
                                        ).withOptionalFirstLineIndentEm(
                                            if (state.readerSettings.forceParagraphIndent && !isChapterTitle) {
                                                FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
                                            } else {
                                                null
                                            },
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
                                                Text(
                                                    text = textContent,
                                                    style = baseStyle.copy(
                                                        color = MaterialTheme.colorScheme.primary,
                                                    ),
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
                                            Text(
                                                text = textContent,
                                                style = baseStyle,
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
                )

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            setBackgroundColor(backgroundColor)
                            alpha = 0f
                            settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(state.enableJs)
                            settings.domStorageEnabled = false

                            webViewClient = object : WebViewClient() {}
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
                        )
                        val shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html)
                        webView.webViewClient = object : WebViewClient() {
                            private var hasEarlyRevealedPage = false

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                if (!shouldEarlyRevealWebView || hasEarlyRevealedPage) return
                                hasEarlyRevealedPage = true
                                view?.revealReaderDocumentAndWebView()
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
                                            view.revealReaderDocumentAndWebView()
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
                                    view?.revealReaderDocumentAndWebView()
                                }
                            }
                        }

                        if (webView.tag != state.html) {
                            shouldRestoreWebScroll = true
                            appliedWebCssFingerprint = null
                            webView.animate().cancel()
                            webView.alpha = 0f
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

        // UI overlay - накладывается поверх контента
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

private data class GeminiStatusPresentation(
    val title: String,
    val subtitle: String,
)

private fun geminiStatusPresentation(uiState: GeminiTranslationUiState): GeminiStatusPresentation {
    return when (uiState) {
        GeminiTranslationUiState.Translating -> GeminiStatusPresentation(
            title = "Перевод выполняется",
            subtitle = "Обновление текста в реальном времени",
        )
        GeminiTranslationUiState.CachedVisible -> GeminiStatusPresentation(
            title = "Показывается перевод",
            subtitle = "Можно быстро переключать оригинал и перевод",
        )
        GeminiTranslationUiState.CachedHidden -> GeminiStatusPresentation(
            title = "Кэш готов",
            subtitle = "Можно быстро переключать оригинал и перевод",
        )
        GeminiTranslationUiState.Ready -> GeminiStatusPresentation(
            title = "Готов к запуску",
            subtitle = "Выберите модель и запустите перевод главы",
        )
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
                scenario = "Длинные главы с плотным лором и терминами",
                advantage = "Стабильный стиль, высокая связность и минимум случайного шума",
            ),
            GenerationPreset(
                id = "authorial",
                title = "Авторский",
                temperature = 0.76f,
                topP = 0.93f,
                topK = 48,
                scenario = "Повседневные сцены, внутренние монологи, драма",
                advantage = "Более литературная подача и живые формулировки без перегиба",
            ),
            GenerationPreset(
                id = "dialogue_plus",
                title = "Живые диалоги",
                temperature = 0.88f,
                topP = 0.95f,
                topK = 56,
                scenario = "Разговорные главы, пикировки, юмор, флирт",
                advantage = "Речь персонажей звучит естественнее и эмоциональнее",
            ),
            GenerationPreset(
                id = "private_pulse",
                title = "18+ Импульс",
                temperature = 0.98f,
                topP = 0.97f,
                topK = 72,
                scenario = "Эротические и напряжённые сцены",
                advantage = "Максимум чувственности, экспрессии и «живого» ритма",
            ),
            GenerationPreset(
                id = "unbound",
                title = "Без тормозов",
                temperature = 1.08f,
                topP = 0.985f,
                topK = 96,
                scenario = "Экспериментальный режим для самых дерзких глав",
                advantage = "Пиковая креативность и вариативность слога",
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
                scenario = "Стабильный креативный перевод на каждый день",
                advantage = "Живой текст с контролируемым уровнем вариативности",
            ),
            GenerationPreset(
                id = "deepseek_expressive",
                title = "DeepSeek Экспрессия",
                temperature = 1.4f,
                topP = 0.93f,
                topK = null,
                scenario = "Диалоги, эмоции, романтика и 18+ сцены",
                advantage = "Более яркая и естественная подача реплик и тональности",
            ),
            GenerationPreset(
                id = "deepseek_creative",
                title = "DeepSeek Креатив",
                temperature = 1.5f,
                topP = 0.95f,
                topK = null,
                scenario = "Максимально смелый и вариативный стиль",
                advantage = "Пиковая творческая свобода без переусложнения настроек",
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
                                        "$privateProviderLabel bridge подключен и разблокирован."
                                    } else {
                                        "$privateProviderLabel bridge подключен. Для работы нужен unlock."
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
                                        title = "Бесплатные модели (${openRouterAllModelEntries.size})",
                                        subtitle = tempOpenRouterModel.ifBlank { "Выберите free модель (:free)" },
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
                                            if (isOpenRouterModelsLoading) "Загрузка моделей..." else "Обновить список",
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
                                            if (isDeepSeekModelsLoading) "Загрузка моделей..." else "Обновить список",
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
                                    "$privateProviderLabel: используются зашитые правила private bridge " +
                                        "и авто-режим без пользовательских модификаторов.",
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
                                "$privateProviderLabel: отправка идет одним запросом на главу. " +
                                    "При ошибке включается fallback (batch=40, concurrency=1).",
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
                                    Text(if (tempReasoning == option) "• ${option.uppercase()}" else option.uppercase())
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
                                text = "Для DeepSeek используется диапазон Temperature 1.3-1.5 " +
                                    "и TopP 0.9-0.95. TopK не применяется.",
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
                        "Текст будет добавлен в системный промпт как дополнительная инструкция.",
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
private fun GeminiSettingsBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}

private fun WebView.restoreWebViewScroll(
    progressPercent: Int,
    maxAttempts: Int = 14,
    onComplete: (() -> Unit)? = null,
) {
    if (progressPercent <= 0) {
        onComplete?.invoke()
        return
    }

    fun attemptRestore(attempt: Int) {
        val totalScrollable = resolveWebViewTotalScrollablePx(
            contentHeightPx = resolveWebViewContentHeightPx(),
            viewHeightPx = height,
        )
        if (totalScrollable <= 0 && attempt < maxAttempts) {
            postDelayed({ attemptRestore(attempt + 1) }, 42L)
            return
        }
        if (totalScrollable > 0) {
            val targetY = ((progressPercent.toFloat() / 100f) * totalScrollable.toFloat()).roundToInt()
            scrollTo(0, targetY.coerceIn(0, totalScrollable))
        }
        onComplete?.invoke()
    }

    post { attemptRestore(0) }
}

private fun WebView.resolveCurrentWebViewProgressPercent(
    scrollYOverride: Int? = null,
): Int {
    val contentHeight = resolveWebViewContentHeightPx()
    val totalScrollable = resolveWebViewTotalScrollablePx(
        contentHeightPx = contentHeight,
        viewHeightPx = height,
    )
    return resolveWebViewScrollProgressPercent(
        scrollY = scrollYOverride ?: scrollY,
        totalScrollable = totalScrollable,
    )
}

@Suppress("DEPRECATION")
private fun WebView.resolveWebViewContentHeightPx(): Int {
    val childHeight = getChildAt(0)?.height ?: 0
    val scaledContentHeight = (contentHeight * scale).roundToInt()
    return maxOf(childHeight, scaledContentHeight)
}

private const val WEB_READER_STYLE_ELEMENT_ID = "__an_reader_style__"
private const val WEB_READER_BOOTSTRAP_STYLE_ELEMENT_ID = "__an_reader_bootstrap_style__"

private fun WebView.revealReaderDocumentAndWebView() {
    evaluateJavascript(
        """
        (function() {
            const bootstrapStyle = document.getElementById('$WEB_READER_BOOTSTRAP_STYLE_ELEMENT_ID');
            if (bootstrapStyle) {
                bootstrapStyle.remove();
            }
        })();
        """.trimIndent(),
        { _ ->
            val reveal = {
                animate().cancel()
                if (alpha >= 1f) {
                    alpha = 1f
                } else {
                    animate()
                        .alpha(1f)
                        .setDuration(120L)
                        .start()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                postVisualStateCallback(
                    SystemClock.uptimeMillis(),
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            post(reveal)
                        }
                    },
                )
            } else {
                post(reveal)
            }
        },
    )
}

internal fun buildInitialWebReaderHtml(
    rawHtml: String,
    readerCss: String,
): String {
    val injection = buildString {
        append("<style id=\"")
        append(WEB_READER_STYLE_ELEMENT_ID)
        append("\">")
        append(escapeCssForInlineStyleTag(readerCss))
        append("</style>")
        append("<style id=\"")
        append(WEB_READER_BOOTSTRAP_STYLE_ELEMENT_ID)
        append("\">")
        append(buildWebReaderBootstrapCss())
        append("</style>")
    }
    return injectHtmlFragmentIntoHead(rawHtml, injection)
}

internal fun escapeCssForInlineStyleTag(css: String): String {
    return css.replace("</style", "<\\\\/style", ignoreCase = true)
}

private fun buildWebReaderBootstrapCss(): String {
    return "html, body { visibility: hidden !important; }"
}

private const val FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM = 2f
private const val EARLY_WEBVIEW_REVEAL_IMAGE_THRESHOLD = 6
private val webViewHtmlImageTagRegex = Regex("<img\\b", RegexOption.IGNORE_CASE)
private val hexNovelsPluginImageUrlRegex = Regex("""(?:novelimg|heximg)://hexnovels\b""", RegexOption.IGNORE_CASE)
private val novelWordRegex = Regex("""[\p{L}\p{N}']+""")

internal fun shouldUseEarlyWebViewReveal(rawHtml: String): Boolean {
    if (rawHtml.isBlank()) return false
    if (hexNovelsPluginImageUrlRegex.containsMatchIn(rawHtml)) return true

    val imageCount = webViewHtmlImageTagRegex
        .findAll(rawHtml)
        .take(EARLY_WEBVIEW_REVEAL_IMAGE_THRESHOLD)
        .count()
    return imageCount >= EARLY_WEBVIEW_REVEAL_IMAGE_THRESHOLD
}

private fun injectHtmlFragmentIntoHead(
    rawHtml: String,
    fragment: String,
): String {
    val headCloseRegex = Regex("</head>", RegexOption.IGNORE_CASE)
    if (headCloseRegex.containsMatchIn(rawHtml)) {
        return headCloseRegex.replaceFirst(rawHtml, "$fragment</head>")
    }

    val bodyOpenRegex = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)
    val bodyOpenMatch = bodyOpenRegex.find(rawHtml)
    if (bodyOpenMatch != null) {
        val insertIndex = bodyOpenMatch.range.last + 1
        return rawHtml.substring(0, insertIndex) + fragment + rawHtml.substring(insertIndex)
    }

    return fragment + rawHtml
}

internal fun buildWebReaderCssText(
    fontFaceCss: String,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Int,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageUrl: String?,
    fontFamilyName: String?,
    customCss: String,
    textShadowCss: String?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): String {
    val escapedFontFamily = fontFamilyName
        ?.replace("\\", "\\\\")
        ?.replace("'", "\\'")
    val fontVariable = escapedFontFamily?.let { "'$it', sans-serif" }.orEmpty()
    val resolvedParagraphSpacingPx = paragraphSpacingPx.coerceIn(0, 32)

    return buildString {
        append(fontFaceCss)
        append('\n')
        append(":root {\n")
        append("  --an-reader-bg: $backgroundHex;\n")
        append("  --an-reader-fg: $textColorHex;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  --an-reader-align: $textAlignCss;\n")
        }
        if (!firstLineIndentCss.isNullOrBlank()) {
            append("  --an-reader-first-line-indent: $firstLineIndentCss;\n")
        }
        append("  --an-reader-size: ${fontSizePx}px;\n")
        append("  --an-reader-line-height: ${lineHeightMultiplier.coerceAtLeast(1f)};\n")
        append("  --an-reader-paragraph-spacing: ${resolvedParagraphSpacingPx}px;\n")
        if (!textShadowCss.isNullOrBlank()) {
            append("  --an-reader-text-shadow: $textShadowCss;\n")
        }
        if (fontVariable.isNotBlank()) {
            append("  --an-reader-font: $fontVariable;\n")
        }
        append("  --an-reader-font-weight: ${if (forceBoldText) "700" else "400"};\n")
        append("  --an-reader-font-style: ${if (forceItalicText) "italic" else "normal"};\n")
        append("}\n")
        append("html, body {\n")
        append("  margin: 0 !important;\n")
        append("  min-height: 0 !important;\n")
        append("  height: auto !important;\n")
        append("  background: var(--an-reader-bg) !important;\n")
        append("  color: var(--an-reader-fg) !important;\n")
        append("}\n")
        append("body {\n")
        append("  padding-top: ${paddingTop}px !important;\n")
        append("  padding-bottom: ${paddingBottom}px !important;\n")
        append("  padding-left: ${paddingHorizontal}px !important;\n")
        append("  padding-right: ${paddingHorizontal}px !important;\n")
        append("  font-size: var(--an-reader-size) !important;\n")
        append("  line-height: var(--an-reader-line-height) !important;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  text-align: var(--an-reader-align) !important;\n")
        }
        append("  word-break: break-word !important;\n")
        append("  overflow-wrap: anywhere !important;\n")
        append("  -webkit-text-size-adjust: 100% !important;\n")
        if (!textShadowCss.isNullOrBlank()) {
            append("  text-shadow: var(--an-reader-text-shadow) !important;\n")
        }
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
        append("  font-weight: var(--an-reader-font-weight) !important;\n")
        append("  font-style: var(--an-reader-font-style) !important;\n")
        append("}\n")
        append("body > * {\n")
        append("  margin-top: 0 !important;\n")
        append("  padding-top: 0 !important;\n")
        append("}\n")
        append("body h1, body h2, body h3, body h4, body h5, body h6 {\n")
        append("  line-height: 1.35 !important;\n")
        append("  font-weight: 600 !important;\n")
        append("}\n")
        append("body h1 {\n")
        append("  font-size: 1.24em !important;\n")
        append("  margin-top: 0 !important;\n")
        append("  margin-bottom: 0.7em !important;\n")
        append("}\n")
        append("body p, body div, body article, body section, body blockquote, body pre, body li {\n")
        append("  margin-bottom: ${resolvedParagraphSpacingPx}px !important;\n")
        append("}\n")
        append("body span.an-reader-bionic {\n")
        append("  font-weight: 600 !important;\n")
        append("}\n")
        append("body h2 {\n")
        append("  font-size: 1.12em !important;\n")
        append("}\n")
        append("body h3 {\n")
        append("  font-size: 1.06em !important;\n")
        append("}\n")
        append("body .an-reader-chapter-title {\n")
        append("  font-size: 1.16em !important;\n")
        append("  font-weight: 600 !important;\n")
        append("  margin-top: 0 !important;\n")
        append("  margin-bottom: 0.85em !important;\n")
        append("}\n")
        append("body > :first-child {\n")
        append("  margin-top: 0 !important;\n")
        append("  padding-top: 0 !important;\n")
        append("}\n")
        append("body > :first-child > :first-child,\n")
        append("body > :first-child > :first-child > :first-child {\n")
        append("  margin-top: 0 !important;\n")
        append("  padding-top: 0 !important;\n")
        append("}\n")
        append("body > :last-child {\n")
        append("  margin-bottom: 0 !important;\n")
        append("  padding-bottom: 0 !important;\n")
        append("}\n")
        append("body > :last-child > :last-child,\n")
        append("body > :last-child > :last-child > :last-child {\n")
        append("  margin-bottom: 0 !important;\n")
        append("  padding-bottom: 0 !important;\n")
        append("}\n")
        append(
            "body p:first-child, body h1:first-child, body h2:first-child, body h3:first-child, " +
                "body h4:first-child, body h5:first-child, body h6:first-child, body ul:first-child, " +
                "body ol:first-child, body blockquote:first-child, body pre:first-child {\n",
        )
        append("  margin-top: 0 !important;\n")
        append("}\n")
        append("body, body *:not(img):not(svg):not(video):not(canvas):not(iframe), body *::before, body *::after {\n")
        append("  color: var(--an-reader-fg) !important;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  text-align: var(--an-reader-align) !important;\n")
        }
        append("  line-height: var(--an-reader-line-height) !important;\n")
        append("  background-color: transparent !important;\n")
        if (!textShadowCss.isNullOrBlank()) {
            append("  text-shadow: var(--an-reader-text-shadow) !important;\n")
        }
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
        append("}\n")
        append("a {\n")
        append("  color: var(--an-reader-fg) !important;\n")
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
        append("}\n")
        if (!firstLineIndentCss.isNullOrBlank()) {
            append("body p, body div, body article, body section {\n")
            append("  text-indent: var(--an-reader-first-line-indent) !important;\n")
            append("}\n")
            append("body .an-reader-chapter-title,\n")
            append("body h1, body h2, body h3, body h4, body h5, body h6 {\n")
            append("  text-indent: 0 !important;\n")
            append("}\n")
        }
        append(
            buildWebReaderAtmosphereCss(
                appearanceMode = appearanceMode,
                backgroundTexture = backgroundTexture,
                oledEdgeGradient = oledEdgeGradient,
                backgroundImageUrl = backgroundImageUrl,
            ),
        )
        append(customCss)
    }
}

internal fun buildWebReaderAtmosphereCss(
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageUrl: String?,
): String {
    if (appearanceMode == NovelReaderAppearanceMode.BACKGROUND) {
        if (backgroundImageUrl.isNullOrBlank()) return ""
        return buildString {
            append("html, body {\n")
            append("  background-color: var(--an-reader-bg) !important;\n")
            append("  background-image: url('$backgroundImageUrl') !important;\n")
            append("  background-repeat: no-repeat !important;\n")
            append("  background-size: cover !important;\n")
            append("  background-position: center center !important;\n")
            append("  background-attachment: fixed !important;\n")
            append("}\n")
        }
    }

    val textureLayers = when (backgroundTexture) {
        NovelReaderBackgroundTexture.NONE -> null
        NovelReaderBackgroundTexture.PAPER_GRAIN ->
            "url('file:///android_asset/textures/texture_paper.webp')"
        NovelReaderBackgroundTexture.LINEN ->
            "url('file:///android_asset/textures/texture_linen.webp')"
        NovelReaderBackgroundTexture.PARCHMENT ->
            "radial-gradient(circle at 20% 20%, rgba(255,255,255,0.14), transparent 45%), " +
                "radial-gradient(circle at 80% 75%, rgba(0,0,0,0.12), transparent 42%)"
    }
    val oledLayer = if (oledEdgeGradient) {
        "radial-gradient(circle at center, rgba(0,0,0,0.0) 38%, rgba(0,0,0,0.36) 100%)"
    } else {
        null
    }
    if (textureLayers == null && oledLayer == null) return ""

    return buildString {
        val layers = listOfNotNull(oledLayer, textureLayers)
        if (layers.isNotEmpty()) {
            val repeatValues = buildList {
                if (oledLayer != null) add("no-repeat")
                if (textureLayers != null) {
                    if (backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT) {
                        add("no-repeat")
                    } else {
                        add("repeat")
                    }
                }
            }.joinToString(", ")
            append("html, body {\n")
            append("  background-color: var(--an-reader-bg) !important;\n")
            append("  background-image: ${layers.joinToString(", ")} !important;\n")
            append("  background-repeat: $repeatValues !important;\n")
            append("  background-attachment: fixed !important;\n")
            append("}\n")
        }
    }
}

private fun WebView.applyReaderCss(
    fontFaceCss: String,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Int,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageUrl: String?,
    fontFamilyName: String?,
    customCss: String,
    textShadowCss: String?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    bionicReadingEnabled: Boolean,
) {
    val css = buildWebReaderCssText(
        fontFaceCss = fontFaceCss,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        paddingHorizontal = paddingHorizontal,
        fontSizePx = fontSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        paragraphSpacingPx = paragraphSpacingPx,
        textAlignCss = textAlignCss,
        firstLineIndentCss = firstLineIndentCss,
        textColorHex = textColorHex,
        backgroundHex = backgroundHex,
        appearanceMode = appearanceMode,
        backgroundTexture = backgroundTexture,
        oledEdgeGradient = oledEdgeGradient,
        backgroundImageUrl = backgroundImageUrl,
        fontFamilyName = fontFamilyName,
        customCss = customCss,
        textShadowCss = textShadowCss,
        forceBoldText = forceBoldText,
        forceItalicText = forceItalicText,
    )
    val escapedFontFamily = fontFamilyName
        ?.replace("\\", "\\\\")
        ?.replace("'", "\\'")
    val shouldForceFontFamily = escapedFontFamily != null
    val quotedCss = JSONObject.quote(css)
    val fontFlag = if (shouldForceFontFamily) "true" else "false"
    val alignFlag = if (textAlignCss.isNullOrBlank()) "false" else "true"
    val firstLineIndentFlag = if (firstLineIndentCss.isNullOrBlank()) "false" else "true"
    evaluateJavascript(
        """
        (function() {
            const styleId = '$WEB_READER_STYLE_ELEMENT_ID';
            let style = document.getElementById(styleId);
            if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
            }
            style.textContent = $quotedCss;

            const shouldForceFont = $fontFlag;
            const shouldForceAlign = $alignFlag;
            const shouldForceFirstLineIndent = $firstLineIndentFlag;
            const firstLineIndentTags = new Set(['p', 'div', 'article', 'section']);
            const emptyBlockTags = new Set(['p', 'div', 'article', 'section', 'li', 'blockquote', 'pre']);
            const invisibleSpaceRegex = /[\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]/g;
            const mediaContentSelector = 'img,svg,video,canvas,iframe,picture,hr';
            const root = document.body;
            if (!root) return;
            const nodes = root.querySelectorAll('*');
            for (const node of nodes) {
                if (!(node instanceof HTMLElement)) continue;
                if (shouldForceAlign) {
                    node.removeAttribute('align');
                }
                node.removeAttribute('bgcolor');
                node.removeAttribute('color');
                const tag = node.tagName.toLowerCase();
                if (
                    tag === 'img' || tag === 'svg' || tag === 'video' ||
                    tag === 'canvas' || tag === 'iframe' || tag === 'source' || tag === 'picture'
                ) {
                    continue;
                }
                if (emptyBlockTags.has(tag)) {
                    const normalizedText = (node.textContent || '')
                        .replace(invisibleSpaceRegex, ' ')
                        .trim();
                    const hasMediaContent = node.querySelector(mediaContentSelector) !== null;
                    if (!hasMediaContent && normalizedText.length === 0) {
                        node.style.setProperty('display', 'none', 'important');
                        node.style.setProperty('margin', '0', 'important');
                        node.style.setProperty('padding', '0', 'important');
                        continue;
                    }
                }
                node.style.setProperty('background-color', 'transparent', 'important');
                node.style.setProperty('color', 'var(--an-reader-fg)', 'important');
                if (shouldForceAlign) {
                    node.style.setProperty('text-align', 'var(--an-reader-align)', 'important');
                } else if (node.style.getPropertyValue('text-align').includes('--an-reader-align')) {
                    node.style.removeProperty('text-align');
                }
                if (shouldForceFirstLineIndent && firstLineIndentTags.has(tag)) {
                    node.style.setProperty('text-indent', 'var(--an-reader-first-line-indent)', 'important');
                } else if (node.style.getPropertyValue('text-indent').includes('--an-reader-first-line-indent')) {
                    node.style.removeProperty('text-indent');
                }
                node.style.setProperty('line-height', 'var(--an-reader-line-height)', 'important');
                if (shouldForceFont) {
                    node.style.setProperty('font-family', 'var(--an-reader-font)', 'important');
                }
            }
        })();
        """.trimIndent(),
        null,
    )
    val bionicJavascript = buildWebReaderBionicJavascript(bionicReadingEnabled)
    evaluateJavascript(
        if (bionicJavascript.isBlank()) buildWebReaderBionicResetJavascript() else bionicJavascript,
        null,
    )
}

internal fun buildWebReaderBionicJavascript(enabled: Boolean): String {
    if (!enabled) return ""
    return """
        (function() {
            const root = document.body;
            if (!root) return;
            const existing = Array.from(root.querySelectorAll('span.an-reader-bionic'));
            for (const span of existing) {
                span.replaceWith(document.createTextNode(span.textContent || ''));
            }
            root.normalize();
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                acceptNode(node) {
                    if (!node || !node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
                    const parent = node.parentElement;
                    if (!parent) return NodeFilter.FILTER_REJECT;
                    if (parent.closest('script,style,noscript,textarea,svg,.an-reader-bionic')) {
                        return NodeFilter.FILTER_REJECT;
                    }
                    return NodeFilter.FILTER_ACCEPT;
                }
            });
            const nodes = [];
            while (walker.nextNode()) nodes.push(walker.currentNode);
            for (const textNode of nodes) {
                const text = textNode.nodeValue || '';
                const fragment = document.createDocumentFragment();
                const parts = text.match(/\S+|\s+/g) || [];
                for (const token of parts) {
                    if (/^\s+$/.test(token)) {
                        fragment.appendChild(document.createTextNode(token));
                        continue;
                    }
                    const emphasizeCount = Math.max(1, Math.ceil(token.length * 0.5));
                    const span = document.createElement('span');
                    span.className = 'an-reader-bionic';
                    span.textContent = token.slice(0, emphasizeCount);
                    fragment.appendChild(span);
                    const remainder = token.slice(emphasizeCount);
                    if (remainder) {
                        fragment.appendChild(document.createTextNode(remainder));
                    }
                }
                textNode.replaceWith(fragment);
            }
        })();
    """.trimIndent()
}

private fun buildWebReaderBionicResetJavascript(): String {
    return """
        (function() {
            const root = document.body;
            if (!root) return;
            const spans = Array.from(root.querySelectorAll('span.an-reader-bionic'));
            for (const span of spans) {
                span.replaceWith(document.createTextNode(span.textContent || ''));
            }
            root.normalize();
        })();
    """.trimIndent()
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

internal data class ReaderAtmosphereRadialLayer(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val colorStops: List<Pair<Float, Color>>,
)

internal fun buildReaderAtmosphereRadialLayers(
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    intensityFactor: Float,
): List<ReaderAtmosphereRadialLayer> {
    val layers = mutableListOf<ReaderAtmosphereRadialLayer>()

    if (backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT) {
        val parchmentDarkAlpha = (0.12f * intensityFactor).coerceIn(0f, 0.48f)
        val parchmentLightAlpha = (0.14f * intensityFactor).coerceIn(0f, 0.56f)
        // Draw order must follow CSS background layering (bottom -> top on Canvas).
        // CSS for parchment: white highlight is listed before dark stain, so it is above it.
        // Canvas paints later layers on top, therefore we add dark first, then white.
        layers += ReaderAtmosphereRadialLayer(
            centerXFraction = 0.8f,
            centerYFraction = 0.75f,
            colorStops = listOf(
                0f to Color.Black.copy(alpha = parchmentDarkAlpha),
                0.42f to Color.Transparent,
                1f to Color.Transparent,
            ),
        )
        layers += ReaderAtmosphereRadialLayer(
            centerXFraction = 0.2f,
            centerYFraction = 0.2f,
            colorStops = listOf(
                0f to Color.White.copy(alpha = parchmentLightAlpha),
                0.45f to Color.Transparent,
                1f to Color.Transparent,
            ),
        )
    }

    if (oledEdgeGradient && isDarkTheme) {
        val oledBlendT = ((intensityFactor - 1f) / 3f).coerceIn(0f, 1f)
        val oledEdgeAlpha = 0.36f - (0.08f * oledBlendT)
        layers += ReaderAtmosphereRadialLayer(
            centerXFraction = 0.5f,
            centerYFraction = 0.5f,
            colorStops = listOf(
                0f to Color.Transparent,
                0.38f to Color.Transparent,
                1f to Color.Black.copy(alpha = oledEdgeAlpha),
            ),
        )
    }

    return layers
}

internal fun resolveNativeTextureIntensityFactor(strengthPercent: Int): Float {
    val clamped = strengthPercent.coerceIn(0, 200)
    return clamped / 50f
}

internal fun calculateRadialGradientFarthestCornerRadius(
    size: Size,
    center: Offset,
): Float {
    val topLeft = hypot(center.x.toDouble(), center.y.toDouble()).toFloat()
    val topRight = hypot((size.width - center.x).toDouble(), center.y.toDouble()).toFloat()
    val bottomLeft = hypot(center.x.toDouble(), (size.height - center.y).toDouble()).toFloat()
    val bottomRight = hypot((size.width - center.x).toDouble(), (size.height - center.y).toDouble()).toFloat()
    return max(max(topLeft, topRight), max(bottomLeft, bottomRight))
}

internal fun shouldShowBottomInfoOverlay(
    showReaderUi: Boolean,
    showBatteryAndTime: Boolean,
    showKindleInfoBlock: Boolean,
    showTimeToEnd: Boolean,
    showWordCount: Boolean,
): Boolean {
    val kindleInfoVisible = showKindleInfoBlock && (showTimeToEnd || showWordCount)
    return showReaderUi && (showBatteryAndTime || kindleInfoVisible)
}

internal fun shouldShowPersistentProgressLine(
    showReaderUi: Boolean,
): Boolean {
    return !showReaderUi
}

internal fun resolveParagraphSpacingDp(
    spacing: Int,
): androidx.compose.ui.unit.Dp {
    return spacing.coerceIn(0, 32).dp
}

internal data class NovelReaderReadingPaceState(
    val lastProgressPercent: Int? = null,
    val lastTimestampMs: Long? = null,
    val smoothedProgressPerMinute: Float? = null,
)

internal fun updateNovelReaderReadingPace(
    paceState: NovelReaderReadingPaceState,
    readingProgressPercent: Int,
    timestampMs: Long,
): NovelReaderReadingPaceState {
    val clampedProgress = readingProgressPercent.coerceIn(0, 100)
    val lastProgress = paceState.lastProgressPercent
    val lastTimestamp = paceState.lastTimestampMs
    if (lastProgress == null || lastTimestamp == null || timestampMs <= lastTimestamp) {
        return paceState.copy(lastProgressPercent = clampedProgress, lastTimestampMs = timestampMs)
    }

    val deltaProgress = (clampedProgress - lastProgress).toFloat()
    val deltaMs = timestampMs - lastTimestamp
    val sampled = if (deltaProgress > 0f && deltaMs in 5_000L..600_000L) {
        val rawPerMinute = deltaProgress / (deltaMs.toFloat() / 60_000f)
        when (val existing = paceState.smoothedProgressPerMinute) {
            null -> rawPerMinute
            else -> (existing * 0.7f) + (rawPerMinute * 0.3f)
        }
    } else {
        paceState.smoothedProgressPerMinute
    }

    return paceState.copy(
        lastProgressPercent = clampedProgress,
        lastTimestampMs = timestampMs,
        smoothedProgressPerMinute = sampled,
    )
}

internal fun estimateNovelReaderRemainingMinutes(
    paceState: NovelReaderReadingPaceState,
    readingProgressPercent: Int,
): Int? {
    val remaining = (100 - readingProgressPercent.coerceIn(0, 100)).toFloat()
    if (remaining <= 0f) return 0
    val speed = paceState.smoothedProgressPerMinute ?: return null
    if (speed <= 0.01f) return null
    return ceil(remaining / speed).toInt().coerceAtLeast(1)
}

internal fun resolvePageReaderReadingProgressPercent(
    pageIndex: Int,
    pageCount: Int,
): Int {
    val safePageCount = pageCount.coerceAtLeast(0)
    if (safePageCount <= 0) return 0
    if (safePageCount == 1) return 100

    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    return ((safePageIndex.toFloat() / (safePageCount - 1).toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}

internal fun resolveReaderPageRailLabels(
    pageIndex: Int,
    pageCount: Int,
): Pair<String?, String?> {
    val safePageCount = pageCount.coerceAtLeast(0)
    if (safePageCount <= 0) return null to null
    val currentPage = pageIndex.coerceIn(0, safePageCount - 1) + 1
    return currentPage.toString() to safePageCount.toString()
}

internal fun resolveReaderVerticalSeekbarTickFractions(pageCount: Int): List<Float> {
    val safePageCount = pageCount.coerceAtLeast(0)
    if (safePageCount <= 1) return emptyList()
    val denominator = (safePageCount - 1).toFloat()
    return List(safePageCount) { index -> index.toFloat() / denominator }
}

internal fun countNovelWords(blocks: List<String>): Int {
    if (blocks.isEmpty()) return 0
    return blocks.sumOf { block -> novelWordRegex.findAll(block).count() }
}

internal fun estimateNovelReadWords(
    totalWords: Int,
    readingProgressPercent: Int,
): Int {
    if (totalWords <= 0) return 0
    val clampedPercent = readingProgressPercent.coerceIn(0, 100)
    return ((totalWords.toFloat() * clampedPercent.toFloat()) / 100f).roundToInt().coerceIn(0, totalWords)
}

internal fun shouldShowVerticalSeekbar(
    showReaderUi: Boolean,
    verticalSeekbarEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") showWebView: Boolean,
    usePageReader: Boolean,
    textBlocksCount: Int,
): Boolean {
    return showReaderUi &&
        verticalSeekbarEnabled &&
        if (usePageReader) {
            textBlocksCount > 0
        } else {
            textBlocksCount > 1
        }
}

internal fun shouldShowPageReaderDismissLayer(
    showReaderUi: Boolean,
    usePageReader: Boolean,
): Boolean {
    return showReaderUi && usePageReader
}

internal fun shouldStartInWebView(
    preferWebViewRenderer: Boolean,
    richNativeRendererExperimentalEnabled: Boolean,
    pageReaderEnabled: Boolean,
    contentBlocksCount: Int,
    richContentUnsupportedFeaturesDetected: Boolean,
): Boolean {
    if (contentBlocksCount <= 0) return true
    if (pageReaderEnabled) return false
    if (richNativeRendererExperimentalEnabled && richContentUnsupportedFeaturesDetected) return true
    return preferWebViewRenderer
}

internal fun syncShowWebViewWithReaderSettings(
    currentShowWebView: Boolean,
    preferWebViewRenderer: Boolean,
    richNativeRendererExperimentalEnabled: Boolean,
    pageReaderEnabled: Boolean,
    contentBlocksCount: Int,
    richContentUnsupportedFeaturesDetected: Boolean,
): Boolean {
    val expectedShowWebView = shouldStartInWebView(
        preferWebViewRenderer = preferWebViewRenderer,
        richNativeRendererExperimentalEnabled = richNativeRendererExperimentalEnabled,
        pageReaderEnabled = pageReaderEnabled,
        contentBlocksCount = contentBlocksCount,
        richContentUnsupportedFeaturesDetected = richContentUnsupportedFeaturesDetected,
    )
    return if (currentShowWebView == expectedShowWebView) {
        currentShowWebView
    } else {
        expectedShowWebView
    }
}

internal fun resolveInitialPageReaderPage(
    savedPageReaderProgress: PageReaderProgress?,
    legacyLastSavedIndex: Int,
    pageCount: Int,
    isInternalChapterHandoff: Boolean = false,
): Int {
    val safePageCount = pageCount.coerceAtLeast(1)
    val lastPageIndex = safePageCount - 1
    if (!shouldRestoreSavedPageReaderProgress(isInternalChapterHandoff)) return 0
    val savedProgress = savedPageReaderProgress ?: return legacyLastSavedIndex.coerceIn(0, lastPageIndex)
    if (safePageCount == 1 || savedProgress.totalItems <= 1) return 0
    val sourceLastPageIndex = (savedProgress.totalItems - 1).coerceAtLeast(1)
    val normalizedProgress = savedProgress.index.toFloat() / sourceLastPageIndex.toFloat()
    return (normalizedProgress * lastPageIndex.toFloat()).roundToInt().coerceIn(0, lastPageIndex)
}

internal fun resolveInitialNativeReaderIndex(
    nativeLastSavedIndex: Int,
    savedPageReaderProgress: PageReaderProgress?,
    itemCount: Int,
): Int {
    val safeItemCount = itemCount.coerceAtLeast(1)
    val lastItemIndex = safeItemCount - 1
    val savedProgress = savedPageReaderProgress ?: return nativeLastSavedIndex.coerceIn(0, lastItemIndex)
    if (safeItemCount == 1 || savedProgress.totalItems <= 1) return 0
    val sourceLastPageIndex = (savedProgress.totalItems - 1).coerceAtLeast(1)
    val normalizedProgress = savedProgress.index.toFloat() / sourceLastPageIndex.toFloat()
    return (normalizedProgress * lastItemIndex.toFloat()).roundToInt().coerceIn(0, lastItemIndex)
}

internal fun shouldUseRichNativeScrollRenderer(
    richNativeRendererExperimentalEnabled: Boolean,
    showWebView: Boolean,
    usePageReader: Boolean,
    bionicReadingEnabled: Boolean,
    richContentBlocks: List<NovelRichContentBlock>,
    richContentUnsupportedFeaturesDetected: Boolean,
): Boolean {
    if (!richNativeRendererExperimentalEnabled) return false
    if (showWebView || usePageReader || bionicReadingEnabled) return false
    if (richContentUnsupportedFeaturesDetected) return false
    return richContentBlocks.isNotEmpty()
}

internal fun shouldUseRichNativePageRenderer(
    richNativeRendererExperimentalEnabled: Boolean,
    pageReaderEnabled: Boolean,
    bionicReadingEnabled: Boolean,
    richContentBlocks: List<NovelRichContentBlock>,
    richContentUnsupportedFeaturesDetected: Boolean,
): Boolean {
    if (!pageReaderEnabled) return false
    if (!richNativeRendererExperimentalEnabled) return false
    if (bionicReadingEnabled) return false
    if (richContentUnsupportedFeaturesDetected) return false
    if (richContentBlocks.isEmpty()) return false
    return richContentBlocks.none { it is NovelRichContentBlock.Image }
}

internal enum class NovelPageTransitionEngine {
    COMPOSE_PAGER,
    PAGE_TURN_RENDERER,
}

internal fun resolvePageTransitionEngine(
    style: NovelPageTransitionStyle,
): NovelPageTransitionEngine {
    return when (style) {
        NovelPageTransitionStyle.INSTANT,
        NovelPageTransitionStyle.SLIDE,
        NovelPageTransitionStyle.DEPTH,
        -> NovelPageTransitionEngine.COMPOSE_PAGER
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> NovelPageTransitionEngine.PAGE_TURN_RENDERER
    }
}

internal fun resolveActivePageTransitionStyle(
    requestedStyle: NovelPageTransitionStyle,
    pageTurnRendererSupported: Boolean,
): NovelPageTransitionStyle {
    val requestedEngine = resolvePageTransitionEngine(requestedStyle)
    if (requestedEngine == NovelPageTransitionEngine.PAGE_TURN_RENDERER && !pageTurnRendererSupported) {
        return NovelPageTransitionStyle.SLIDE
    }
    return requestedStyle
}

internal enum class NovelPageReaderRendererRoute {
    COMPOSE_PAGER,
    PAGE_TURN_RENDERER,
}

internal fun resolvePageReaderRendererRoute(
    usePageReader: Boolean,
    activeStyle: NovelPageTransitionStyle,
): NovelPageReaderRendererRoute? {
    if (!usePageReader) return null
    return when (resolvePageTransitionEngine(activeStyle)) {
        NovelPageTransitionEngine.COMPOSE_PAGER -> NovelPageReaderRendererRoute.COMPOSE_PAGER
        NovelPageTransitionEngine.PAGE_TURN_RENDERER -> NovelPageReaderRendererRoute.PAGE_TURN_RENDERER
    }
}

internal fun resolvePageReaderCurrentPage(
    pageReaderRendererRoute: NovelPageReaderRendererRoute?,
    pagerCurrentPage: Int,
    pageTurnCurrentPage: Int,
): Int {
    return if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
        resolvePageTurnRendererProgressPageIndex(pageTurnCurrentPage)
    } else {
        pagerCurrentPage.coerceAtLeast(0)
    }
}

internal fun resolveReaderVerticalSeekbarValue(
    showWebView: Boolean,
    webProgressPercent: Int,
    usePageReader: Boolean,
    pageReaderRendererRoute: NovelPageReaderRendererRoute?,
    pagerCurrentPage: Int,
    pageTurnCurrentPage: Int,
    seekbarItemsCount: Int,
    readingProgressPercent: Int,
): Float {
    return when {
        showWebView -> webProgressPercent.coerceIn(0, 100) / 100f
        !usePageReader -> {
            // For long paragraphs/index-based lists, index ratio can lag behind.
            // Use effective reading progress so thumb reaches the real chapter end.
            readingProgressPercent.coerceIn(0, 100) / 100f
        }
        else -> {
            val max = (seekbarItemsCount - 1).coerceAtLeast(1)
            val current = resolvePageReaderCurrentPage(
                pageReaderRendererRoute = pageReaderRendererRoute,
                pagerCurrentPage = pagerCurrentPage,
                pageTurnCurrentPage = pageTurnCurrentPage,
            )
            current.toFloat() / max.toFloat()
        }
    }
}

internal enum class ReaderTapAction {
    TOGGLE_UI,
    BACKWARD,
    FORWARD,
}

internal fun resolveReaderTapAction(
    tapX: Float,
    width: Float,
    tapToScrollEnabled: Boolean,
): ReaderTapAction {
    val safeWidth = width.coerceAtLeast(1f)
    val leftBoundary = safeWidth * 0.3f
    val rightBoundary = safeWidth * 0.7f
    val clampedTapX = tapX.coerceIn(0f, safeWidth)
    val inCenter = clampedTapX > leftBoundary && clampedTapX < rightBoundary
    if (inCenter || !tapToScrollEnabled) return ReaderTapAction.TOGGLE_UI
    return if (clampedTapX <= leftBoundary) ReaderTapAction.BACKWARD else ReaderTapAction.FORWARD
}

internal fun resolvePageReaderBlocks(
    shouldPaginate: Boolean,
    textBlocks: List<String>,
    paragraphSpacingDp: Int,
    paginate: (List<String>, Int) -> List<String>,
): List<String> {
    val fallbackBlocks = textBlocks.takeIf { it.isNotEmpty() } ?: listOf("")
    if (!shouldPaginate) return fallbackBlocks

    val nonBlankBlocks = textBlocks.filter { it.isNotBlank() }
    if (nonBlankBlocks.isEmpty()) return fallbackBlocks

    return paginate(nonBlankBlocks, paragraphSpacingDp.coerceIn(0, 32)).ifEmpty { fallbackBlocks }
}

internal data class PlainPageSlice(
    val blockIndex: Int,
    val range: TextPageRange,
)

internal data class RichPageSlice(
    val blockIndex: Int,
    val range: TextPageRange,
)

internal data class PlainPageRenderBlock(
    val text: String,
    val spacingBeforePx: Int,
    val firstLineIndentEm: Float?,
    val isChapterTitle: Boolean = false,
)

internal data class RichPageBlockText(
    val text: AnnotatedString,
    val firstLineIndentEm: Float?,
    val sourceTextAlign: NovelRichBlockTextAlign? = null,
)

internal data class RichPageRenderBlock(
    val text: AnnotatedString,
    val spacingBeforePx: Int,
    val firstLineIndentEm: Float?,
    val sourceTextAlign: NovelRichBlockTextAlign? = null,
    val isChapterTitle: Boolean = false,
)

internal sealed interface NovelPageContentBlock {
    val spacingBeforePx: Int
    val firstLineIndentEm: Float?
    val isChapterTitle: Boolean

    data class Plain(
        val text: String,
        override val spacingBeforePx: Int,
        override val firstLineIndentEm: Float?,
        override val isChapterTitle: Boolean = false,
    ) : NovelPageContentBlock

    data class Rich(
        val text: AnnotatedString,
        override val spacingBeforePx: Int,
        override val firstLineIndentEm: Float?,
        val sourceTextAlign: NovelRichBlockTextAlign? = null,
        override val isChapterTitle: Boolean = false,
    ) : NovelPageContentBlock
}

internal data class NovelPageContentPage(
    val blocks: List<NovelPageContentBlock>,
)

internal fun paginatePlainPageBlocks(
    textBlocks: List<String>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): List<List<PlainPageSlice>> {
    val safeHeight = heightPx.coerceAtLeast(1)
    val approximateMetrics = ApproximateTextMetrics(
        charsPerLine = (widthPx.coerceAtLeast(1) / (textSizePx.coerceAtLeast(1f) * 0.55f))
            .toInt()
            .coerceAtLeast(15),
        lineHeightPx = (textSizePx.coerceAtLeast(1f) * lineHeightMultiplier.coerceAtLeast(1f))
            .toInt()
            .coerceAtLeast(1),
    )
    val layouts = textBlocks.map { block ->
        block to buildReaderStaticLayout(
            text = block,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = typeface,
            textAlign = textAlign,
        )
    }

    if (layouts.isEmpty()) return emptyList()

    val pages = mutableListOf<MutableList<PlainPageSlice>>()
    var currentPage = mutableListOf<PlainPageSlice>()
    var remainingHeight = safeHeight

    fun flushPage() {
        if (currentPage.isNotEmpty()) {
            pages += currentPage
            currentPage = mutableListOf()
        }
        remainingHeight = safeHeight
    }

    layouts.forEachIndexed { blockIndex, (text, layout) ->
        if (text.isBlank()) return@forEachIndexed
        if (layout == null) {
            val blockPages = paginateTextIntoPageRanges(
                text = text,
                widthPx = widthPx,
                heightPx = safeHeight,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface,
                textAlign = textAlign,
            )
            val blockHeight = measureApproximateTextHeight(
                text = text,
                metrics = approximateMetrics,
            )
            blockPages.forEachIndexed { sliceIndex, range ->
                val spacingBefore = if (currentPage.isNotEmpty() && sliceIndex == 0) {
                    paragraphSpacingPx.coerceAtLeast(0)
                } else {
                    0
                }
                if (remainingHeight <= spacingBefore) {
                    flushPage()
                }
                if (
                    currentPage.isNotEmpty() &&
                    sliceIndex == 0 &&
                    blockHeight <= safeHeight &&
                    blockHeight + spacingBefore > remainingHeight
                ) {
                    flushPage()
                }
                val sliceHeight = measureApproximateTextHeight(
                    text = text.substring(range.start, range.endExclusive),
                    metrics = approximateMetrics,
                ).coerceAtMost(safeHeight)
                currentPage += PlainPageSlice(blockIndex = blockIndex, range = range)
                remainingHeight -= spacingBefore + sliceHeight
                if (sliceIndex < blockPages.lastIndex || remainingHeight <= 0) {
                    flushPage()
                }
            }
            return@forEachIndexed
        }

        val blockHeight = resolveStaticLayoutHeight(layout)
        var startLine = 0
        var isFirstSliceOfBlock = true

        while (startLine < layout.lineCount) {
            val spacingBefore = if (currentPage.isNotEmpty() && isFirstSliceOfBlock) {
                paragraphSpacingPx.coerceAtLeast(0)
            } else {
                0
            }

            if (remainingHeight <= spacingBefore) {
                flushPage()
                continue
            }

            if (
                currentPage.isNotEmpty() &&
                isFirstSliceOfBlock &&
                blockHeight <= safeHeight &&
                blockHeight + spacingBefore > remainingHeight
            ) {
                flushPage()
                continue
            }

            val slice = resolveStaticLayoutSliceForHeight(
                text = text,
                layout = layout,
                startLine = startLine,
                availableHeight = remainingHeight - spacingBefore,
            )

            if (slice == null) {
                flushPage()
                continue
            }

            currentPage += PlainPageSlice(blockIndex = blockIndex, range = slice.range)
            remainingHeight -= spacingBefore + slice.heightPx
            startLine = slice.nextStartLine
            isFirstSliceOfBlock = false

            if (remainingHeight <= 0) {
                flushPage()
            }
        }
    }

    flushPage()
    return pages
}

internal fun buildRichPageReaderChapterAnnotatedText(
    richBlocks: List<NovelRichContentBlock>,
    paragraphSpacingDp: Int,
    forcedParagraphFirstLineIndentEm: Float? = null,
): AnnotatedString {
    if (richBlocks.isEmpty()) return AnnotatedString("")

    val paragraphSeparator = resolvePageReaderParagraphSeparator(paragraphSpacingDp)

    return buildAnnotatedString {
        var appendedAny = false
        richBlocks.forEach { block ->
            val blockText = buildRichPageReaderBlockAnnotatedText(
                block = block,
                forcedParagraphFirstLineIndentEm = forcedParagraphFirstLineIndentEm,
            )
            if (blockText.text.isBlank()) return@forEach
            if (appendedAny) append(paragraphSeparator)
            append(blockText)
            appendedAny = true
        }
    }
}

internal fun buildRichPageReaderBlockAnnotatedText(
    block: NovelRichContentBlock,
    forcedParagraphFirstLineIndentEm: Float? = null,
): AnnotatedString {
    return buildRichPageReaderBlockText(
        block = block,
        forcedParagraphFirstLineIndentEm = forcedParagraphFirstLineIndentEm,
    ).text
}

internal fun buildRichPageReaderBlockText(
    block: NovelRichContentBlock,
    forcedParagraphFirstLineIndentEm: Float? = null,
): RichPageBlockText {
    val blockText: AnnotatedString = when (block) {
        is NovelRichContentBlock.Paragraph -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.Heading -> buildNovelRichAnnotatedString(block.segments)
        is NovelRichContentBlock.BlockQuote -> buildNovelRichAnnotatedString(block.segments)
        NovelRichContentBlock.HorizontalRule -> AnnotatedString("* * *")
        is NovelRichContentBlock.Image -> AnnotatedString("")
    }
    if (blockText.text.isBlank()) {
        return RichPageBlockText(
            text = AnnotatedString(""),
            firstLineIndentEm = null,
            sourceTextAlign = null,
        )
    }
    if (block !is NovelRichContentBlock.Paragraph) {
        val sourceTextAlign = when (block) {
            is NovelRichContentBlock.Heading -> block.textAlign
            is NovelRichContentBlock.BlockQuote -> block.textAlign
            else -> null
        }
        return RichPageBlockText(
            text = blockText,
            firstLineIndentEm = null,
            sourceTextAlign = sourceTextAlign,
        )
    }

    val firstLineIndentEm = forcedParagraphFirstLineIndentEm ?: block.firstLineIndentEm
    return RichPageBlockText(
        text = blockText,
        firstLineIndentEm = firstLineIndentEm,
        sourceTextAlign = block.textAlign,
    )
}

internal fun paginateRichPageBlocks(
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): List<List<RichPageSlice>> {
    val safeHeight = heightPx.coerceAtLeast(1)
    val approximateMetrics = ApproximateTextMetrics(
        charsPerLine = (widthPx.coerceAtLeast(1) / (textSizePx.coerceAtLeast(1f) * 0.55f))
            .toInt()
            .coerceAtLeast(15),
        lineHeightPx = (textSizePx.coerceAtLeast(1f) * lineHeightMultiplier.coerceAtLeast(1f))
            .toInt()
            .coerceAtLeast(1),
    )
    val layouts = blockTexts.map { blockText ->
        blockText to buildReaderStaticLayout(
            text = blockText.text.text,
            widthPx = widthPx,
            textSizePx = textSizePx,
            lineHeightMultiplier = lineHeightMultiplier,
            typeface = typeface,
            textAlign = textAlign,
        )
    }

    if (layouts.isEmpty()) return emptyList()

    val pages = mutableListOf<MutableList<RichPageSlice>>()
    var currentPage = mutableListOf<RichPageSlice>()
    var remainingHeight = safeHeight

    fun flushPage() {
        if (currentPage.isNotEmpty()) {
            pages += currentPage
            currentPage = mutableListOf()
        }
        remainingHeight = safeHeight
    }

    layouts.forEachIndexed { blockIndex, (blockText, layout) ->
        if (blockText.text.text.isBlank()) return@forEachIndexed
        if (layout == null) {
            val blockPages = paginateTextIntoPageRanges(
                text = blockText.text.text,
                widthPx = widthPx,
                heightPx = safeHeight,
                textSizePx = textSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface,
                textAlign = textAlign,
            )
            val blockHeight = measureApproximateTextHeight(
                text = blockText.text.text,
                metrics = approximateMetrics,
            )
            blockPages.forEachIndexed { sliceIndex, range ->
                val spacingBefore = if (currentPage.isNotEmpty() && sliceIndex == 0) {
                    paragraphSpacingPx.coerceAtLeast(0)
                } else {
                    0
                }
                if (remainingHeight <= spacingBefore) {
                    flushPage()
                }
                if (
                    currentPage.isNotEmpty() &&
                    sliceIndex == 0 &&
                    blockHeight <= safeHeight &&
                    blockHeight + spacingBefore > remainingHeight
                ) {
                    flushPage()
                }
                val sliceHeight = measureApproximateTextHeight(
                    text = blockText.text.text.substring(range.start, range.endExclusive),
                    metrics = approximateMetrics,
                ).coerceAtMost(safeHeight)
                currentPage += RichPageSlice(blockIndex = blockIndex, range = range)
                remainingHeight -= spacingBefore + sliceHeight
                if (sliceIndex < blockPages.lastIndex || remainingHeight <= 0) {
                    flushPage()
                }
            }
            return@forEachIndexed
        }

        val blockHeight = resolveStaticLayoutHeight(layout)
        var startLine = 0
        var isFirstSliceOfBlock = true

        while (startLine < layout.lineCount) {
            val spacingBefore = if (currentPage.isNotEmpty() && isFirstSliceOfBlock) {
                paragraphSpacingPx.coerceAtLeast(0)
            } else {
                0
            }

            if (remainingHeight <= spacingBefore) {
                flushPage()
                continue
            }

            if (
                currentPage.isNotEmpty() &&
                isFirstSliceOfBlock &&
                blockHeight <= safeHeight &&
                blockHeight + spacingBefore > remainingHeight
            ) {
                flushPage()
                continue
            }

            val slice = resolveStaticLayoutSliceForHeight(
                text = blockText.text.text,
                layout = layout,
                startLine = startLine,
                availableHeight = remainingHeight - spacingBefore,
            )

            if (slice == null) {
                flushPage()
                continue
            }

            currentPage += RichPageSlice(blockIndex = blockIndex, range = slice.range)
            remainingHeight -= spacingBefore + slice.heightPx
            startLine = slice.nextStartLine
            isFirstSliceOfBlock = false

            if (remainingHeight <= 0) {
                flushPage()
            }
        }
    }

    flushPage()
    return pages
}

internal fun buildPlainPageRenderBlocks(
    page: List<PlainPageSlice>,
    textBlocks: List<String>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<PlainPageRenderBlock> {
    if (page.isEmpty()) return emptyList()
    return page.mapIndexed { index, slice ->
        val previousBlockIndex = page.getOrNull(index - 1)?.blockIndex
        val startsNewBlock = index > 0 && previousBlockIndex != slice.blockIndex
        val fullBlockText = textBlocks[slice.blockIndex]
        PlainPageRenderBlock(
            text = fullBlockText.substring(slice.range.start, slice.range.endExclusive),
            spacingBeforePx = if (startsNewBlock) paragraphSpacingPx.coerceAtLeast(0) else 0,
            firstLineIndentEm = if (forceParagraphIndent && slice.range.start == 0) {
                FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
            } else {
                null
            },
            isChapterTitle = chapterTitle != null &&
                slice.blockIndex == 0 &&
                slice.range.start == 0 &&
                isNativeChapterTitleText(fullBlockText, chapterTitle),
        )
    }
}

internal fun buildRichPageRenderBlocks(
    page: List<RichPageSlice>,
    blockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    chapterTitle: String? = null,
): List<RichPageRenderBlock> {
    if (page.isEmpty()) return emptyList()
    return page.mapIndexed { index, slice ->
        val previousBlockIndex = page.getOrNull(index - 1)?.blockIndex
        val startsNewBlock = index > 0 && previousBlockIndex != slice.blockIndex
        val blockText = blockTexts[slice.blockIndex]
        RichPageRenderBlock(
            text = blockText.text.subSequence(TextRange(slice.range.start, slice.range.endExclusive)),
            spacingBeforePx = if (startsNewBlock) paragraphSpacingPx.coerceAtLeast(0) else 0,
            firstLineIndentEm = if (slice.range.start == 0) blockText.firstLineIndentEm else null,
            sourceTextAlign = blockText.sourceTextAlign,
            isChapterTitle = chapterTitle != null &&
                slice.blockIndex == 0 &&
                slice.range.start == 0 &&
                isNativeChapterTitleText(blockText.text.text, chapterTitle),
        )
    }
}

internal fun normalizePageReaderContentPages(
    useRichPageReader: Boolean,
    plainPages: List<List<PlainPageSlice>>,
    richPages: List<List<RichPageSlice>>,
    plainTextBlocks: List<String>,
    richBlockTexts: List<RichPageBlockText>,
    paragraphSpacingPx: Int,
    forceParagraphIndent: Boolean,
    chapterTitle: String? = null,
): List<NovelPageContentPage> {
    return if (useRichPageReader) {
        richPages.map { page ->
            NovelPageContentPage(
                blocks = buildRichPageRenderBlocks(
                    page = page,
                    blockTexts = richBlockTexts,
                    paragraphSpacingPx = paragraphSpacingPx,
                    chapterTitle = chapterTitle,
                ).map { block ->
                    NovelPageContentBlock.Rich(
                        text = block.text,
                        spacingBeforePx = block.spacingBeforePx,
                        firstLineIndentEm = block.firstLineIndentEm,
                        sourceTextAlign = block.sourceTextAlign,
                        isChapterTitle = block.isChapterTitle,
                    )
                },
            )
        }
    } else {
        plainPages.map { page ->
            NovelPageContentPage(
                blocks = buildPlainPageRenderBlocks(
                    page = page,
                    textBlocks = plainTextBlocks,
                    paragraphSpacingPx = paragraphSpacingPx,
                    forceParagraphIndent = forceParagraphIndent,
                    chapterTitle = chapterTitle,
                ).map { block ->
                    NovelPageContentBlock.Plain(
                        text = block.text,
                        spacingBeforePx = block.spacingBeforePx,
                        firstLineIndentEm = block.firstLineIndentEm,
                        isChapterTitle = block.isChapterTitle,
                    )
                },
            )
        }
    }
}

internal fun resolvePageReaderParagraphSeparator(
    paragraphSpacingDp: Int,
): String {
    return if (paragraphSpacingDp.coerceIn(0, 32) >= 20) "\n\n" else "\n"
}

internal fun shouldEnableJavaScriptInReaderWebView(
    pluginRequestsJavaScript: Boolean,
): Boolean {
    return pluginRequestsJavaScript
}

internal enum class RendererSettingDisableReason {
    PAGE_MODE,
    WEBVIEW_ACTIVE,
    BIONIC_READING,
}

internal data class RendererSettingsAvailability(
    val preferWebViewEnabled: Boolean,
    val preferWebViewReason: RendererSettingDisableReason?,
    val richNativeEnabled: Boolean,
    val richNativeReason: RendererSettingDisableReason?,
)

internal fun resolveRendererSettingsAvailability(
    pageReaderEnabled: Boolean,
    showWebView: Boolean,
    bionicReadingEnabled: Boolean,
): RendererSettingsAvailability {
    val preferWebViewReason = if (pageReaderEnabled) RendererSettingDisableReason.PAGE_MODE else null
    val richNativeReason = when {
        showWebView -> RendererSettingDisableReason.WEBVIEW_ACTIVE
        bionicReadingEnabled -> RendererSettingDisableReason.BIONIC_READING
        else -> null
    }
    return RendererSettingsAvailability(
        preferWebViewEnabled = preferWebViewReason == null,
        preferWebViewReason = preferWebViewReason,
        richNativeEnabled = richNativeReason == null,
        richNativeReason = richNativeReason,
    )
}

internal fun areChapterSwipeControlsEnabled(
    swipeGesturesEnabled: Boolean,
    pageReaderEnabled: Boolean,
): Boolean {
    return swipeGesturesEnabled && !pageReaderEnabled
}

internal fun shouldDismissReaderSettingsDialogAfterFamilyChange(
    family: NovelReaderSettingsFamily,
): Boolean {
    return false
}

internal enum class NovelReaderSettingsFamily {
    SOURCE_ALIGNMENT_POLICY,
    CHAPTER_CACHE_POLICY,
    LIVE_TEXT_STYLING,
    RENDERER_TUNING,
}

internal data class NovelReaderSettingsSurfaceStrategy(
    val globalOnlyFamilies: Set<NovelReaderSettingsFamily>,
    val quickDialogOnlyFamilies: Set<NovelReaderSettingsFamily>,
)

internal fun resolveNovelReaderSettingsSurfaceStrategy(): NovelReaderSettingsSurfaceStrategy {
    return NovelReaderSettingsSurfaceStrategy(
        globalOnlyFamilies = setOf(
            NovelReaderSettingsFamily.SOURCE_ALIGNMENT_POLICY,
            NovelReaderSettingsFamily.CHAPTER_CACHE_POLICY,
        ),
        quickDialogOnlyFamilies = setOf(
            NovelReaderSettingsFamily.LIVE_TEXT_STYLING,
            NovelReaderSettingsFamily.RENDERER_TUNING,
        ),
    )
}

internal fun areQuickDialogKindleDependentControlsEnabled(
    showKindleInfoBlock: Boolean,
): Boolean {
    return showKindleInfoBlock
}

internal fun verticalSeekbarLabels(
    readingProgressPercent: Int,
    showScrollPercentage: Boolean,
): Pair<String?, String?> {
    if (!showScrollPercentage) return null to null
    val clamped = readingProgressPercent.coerceIn(0, 100)
    return clamped.toString() to "100"
}

internal data class ReaderBackgroundSelection(
    val source: NovelReaderBackgroundSource,
    val preset: NovelReaderBackgroundPreset,
    val customId: String?,
    val customPath: String?,
    val customIsDarkHint: Boolean?,
)

private const val READER_BACKGROUND_PRESET_URL_PREFIX = "https://reader-background.local/preset/"
private const val READER_BACKGROUND_CUSTOM_URL = "https://reader-background.local/custom"
private const val READER_FONT_USER_URL_PREFIX = "https://reader-font.local/user/"

internal fun resolveReaderBackgroundSelection(
    backgroundSource: NovelReaderBackgroundSource,
    backgroundPresetId: String,
    customBackgroundPath: String,
    customBackgroundExists: Boolean,
    customBackgroundId: String = "",
    customBackgroundItems: List<NovelReaderCustomBackgroundItem> = emptyList(),
): ReaderBackgroundSelection {
    val fallbackPreset = novelReaderBackgroundPresets.first()
    val preset = novelReaderBackgroundPresets
        .firstOrNull { it.id == backgroundPresetId }
        ?: fallbackPreset
    val selectedCustomFromCatalog = customBackgroundItems.firstOrNull { item ->
        item.id == customBackgroundId &&
            item.absolutePath.isNotBlank()
    }
    val hasLegacyCustom = backgroundSource == NovelReaderBackgroundSource.CUSTOM &&
        customBackgroundPath.isNotBlank() &&
        customBackgroundExists
    return if (backgroundSource == NovelReaderBackgroundSource.CUSTOM && selectedCustomFromCatalog != null) {
        ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.CUSTOM,
            preset = preset,
            customId = selectedCustomFromCatalog.id,
            customPath = selectedCustomFromCatalog.absolutePath,
            customIsDarkHint = selectedCustomFromCatalog.isDarkHint,
        )
    } else if (hasLegacyCustom) {
        ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.CUSTOM,
            preset = preset,
            customId = customBackgroundId.ifBlank { customBackgroundPath },
            customPath = customBackgroundPath,
            customIsDarkHint = null,
        )
    } else {
        ReaderBackgroundSelection(
            source = NovelReaderBackgroundSource.PRESET,
            preset = preset,
            customId = null,
            customPath = null,
            customIsDarkHint = null,
        )
    }
}

internal fun resolveReaderTextColorForBackgroundMode(averageLuminance: Float): Color {
    return if (averageLuminance >= 0.55f) {
        Color(0xFF111111)
    } else {
        Color(0xFFEDEDED)
    }
}

internal fun resolveReaderBackgroundWebImageUrl(selection: ReaderBackgroundSelection): String {
    return when (selection.source) {
        NovelReaderBackgroundSource.PRESET -> "$READER_BACKGROUND_PRESET_URL_PREFIX${selection.preset.id}"
        NovelReaderBackgroundSource.CUSTOM -> {
            val selectedId = selection.customId.orEmpty()
            if (selectedId.isBlank()) {
                READER_BACKGROUND_CUSTOM_URL
            } else {
                "$READER_BACKGROUND_CUSTOM_URL?id=${selectedId.encodeUrlParam()}"
            }
        }
    }
}

internal fun resolveReaderBackgroundIdentity(selection: ReaderBackgroundSelection): String {
    return when (selection.source) {
        NovelReaderBackgroundSource.PRESET -> "preset:${selection.preset.id}"
        NovelReaderBackgroundSource.CUSTOM -> "custom:${selection.customId ?: selection.customPath.orEmpty()}"
    }
}

private fun resolveReaderBackgroundWebResourceResponse(
    requestUrl: String,
    context: Context,
    selection: ReaderBackgroundSelection,
): WebResourceResponse? {
    if (requestUrl.startsWith(READER_BACKGROUND_PRESET_URL_PREFIX)) {
        val requestedPresetId = requestUrl
            .removePrefix(READER_BACKGROUND_PRESET_URL_PREFIX)
            .substringBefore('?')
        val requestedPreset = novelReaderBackgroundPresets
            .firstOrNull { it.id == requestedPresetId }
            ?: selection.preset
        return runCatching {
            WebResourceResponse(
                "image/jpeg",
                null,
                context.resources.openRawResource(requestedPreset.imageResId),
            )
        }.getOrNull()
    }
    if (!requestUrl.startsWith(READER_BACKGROUND_CUSTOM_URL)) return null
    val customPath = selection.customPath ?: return null
    val customFile = File(customPath)
    if (!customFile.exists() || !customFile.isFile) return null
    val mimeType = URLConnection.guessContentTypeFromName(customFile.name) ?: "image/*"
    return runCatching {
        WebResourceResponse(
            mimeType,
            null,
            customFile.inputStream(),
        )
    }.getOrNull()
}

internal fun loadNovelReaderTypeface(
    context: Context,
    font: NovelReaderFontOption,
    forceBoldText: Boolean = false,
    forceItalicText: Boolean = false,
): android.graphics.Typeface? {
    val baseTypeface = runCatching {
        when (font.source) {
            NovelReaderFontSource.BUILT_IN -> font.fontResId?.let { ResourcesCompat.getFont(context, it) }
            NovelReaderFontSource.LOCAL_PRIVATE -> {
                if (font.assetPath.isBlank()) {
                    null
                } else {
                    android.graphics.Typeface.createFromAsset(context.assets, font.assetPath)
                }
            }
            NovelReaderFontSource.USER_IMPORTED ->
                font.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { android.graphics.Typeface.createFromFile(it) }
        }
    }.getOrNull()
    return resolveForcedReaderTypeface(
        typeface = baseTypeface,
        forceBoldText = forceBoldText,
        forceItalicText = forceItalicText,
    )
}

internal fun resolveForcedReaderTypeface(
    typeface: android.graphics.Typeface?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): android.graphics.Typeface? {
    if (typeface == null) return null
    val style = resolveForcedReaderTypefaceStyle(forceBoldText, forceItalicText)
    return android.graphics.Typeface.create(typeface, style)
}

internal fun resolveForcedReaderTypefaceStyle(
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): Int {
    return when {
        forceBoldText && forceItalicText -> android.graphics.Typeface.BOLD_ITALIC
        forceBoldText -> android.graphics.Typeface.BOLD
        forceItalicText -> android.graphics.Typeface.ITALIC
        else -> android.graphics.Typeface.NORMAL
    }
}

internal fun resolveNovelReaderComposeFontFamily(
    font: NovelReaderFontOption,
    typeface: android.graphics.Typeface?,
): FontFamily? {
    return when (font.source) {
        NovelReaderFontSource.BUILT_IN -> font.fontResId?.let { FontFamily(Font(it)) }
        NovelReaderFontSource.LOCAL_PRIVATE,
        NovelReaderFontSource.USER_IMPORTED,
        -> typeface?.let { FontFamily(it) }
    }
}

internal fun buildNovelReaderFontFaceCss(font: NovelReaderFontOption): String {
    val fontFamilyName = font.id.takeIf { it.isNotBlank() } ?: return ""
    val fontUrl = when (font.source) {
        NovelReaderFontSource.BUILT_IN,
        NovelReaderFontSource.LOCAL_PRIVATE,
        -> font.assetPath.takeIf {
            it.isNotBlank()
        }?.let { "file:///android_asset/${encodeReaderFontUrlPath(it, preserveSlashes = true)}" }
        NovelReaderFontSource.USER_IMPORTED ->
            font.filePath
                ?.takeIf { it.isNotBlank() }
                ?.let { "$READER_FONT_USER_URL_PREFIX${encodeReaderFontUrlPath(File(it).name)}" }
    } ?: return ""
    return """
        @font-face {
            font-family: '$fontFamilyName';
            src: url('$fontUrl');
        }
    """.trimIndent()
}

private fun resolveReaderFontWebResourceResponse(
    requestUrl: String,
    selectedFont: NovelReaderFontOption,
): WebResourceResponse? {
    if (!requestUrl.startsWith(READER_FONT_USER_URL_PREFIX)) return null
    if (selectedFont.source != NovelReaderFontSource.USER_IMPORTED) return null
    val filePath = selectedFont.filePath ?: return null
    val fontFile = File(filePath)
    if (!fontFile.exists() || !fontFile.isFile) return null
    val requestedFileName = requestUrl
        .removePrefix(READER_FONT_USER_URL_PREFIX)
        .substringBefore('?')
    if (encodeReaderFontUrlPath(fontFile.name) != requestedFileName) return null
    val mimeType = when {
        fontFile.name.endsWith(".otf", ignoreCase = true) -> "font/otf"
        else -> "font/ttf"
    }
    return runCatching {
        WebResourceResponse(
            mimeType,
            null,
            fontFile.inputStream(),
        )
    }.getOrNull()
}

private fun encodeReaderFontUrlPath(
    value: String,
    preserveSlashes: Boolean = false,
): String {
    return if (!preserveSlashes) {
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    } else {
        value.split('/')
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")
            }
    }
}

private fun String.encodeUrlParam(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

internal fun buildWebReaderCssFingerprint(
    chapterId: Long,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    paragraphSpacingPx: Int,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    appearanceMode: NovelReaderAppearanceMode,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    backgroundImageIdentity: String?,
    fontFamilyName: String?,
    customCss: String,
    textShadowCss: String?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
): String {
    return buildString {
        append(chapterId)
        append('|').append(paddingTop)
        append('|').append(paddingBottom)
        append('|').append(paddingHorizontal)
        append('|').append(fontSizePx)
        append('|').append(lineHeightMultiplier)
        append('|').append(paragraphSpacingPx)
        append('|').append(textAlignCss ?: "<site>")
        append('|').append(firstLineIndentCss ?: "<site>")
        append('|').append(textColorHex)
        append('|').append(backgroundHex)
        append('|').append(appearanceMode.name)
        append('|').append(backgroundTexture.name)
        append('|').append(oledEdgeGradient)
        append('|').append(backgroundImageIdentity ?: "<none>")
        append('|').append(fontFamilyName.orEmpty())
        append('|').append(customCss)
        append('|').append(textShadowCss ?: "<none>")
        append('|').append(forceBoldText)
        append('|').append(forceItalicText)
    }
}

internal enum class VerticalChapterSwipeAction {
    NONE,
    NEXT,
    PREVIOUS,
}

internal enum class HorizontalChapterSwipeAction {
    NONE,
    NEXT,
    PREVIOUS,
}

internal fun resolveHorizontalChapterSwipeAction(
    swipeGesturesEnabled: Boolean,
    deltaX: Float,
    deltaY: Float,
    thresholdPx: Float,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): HorizontalChapterSwipeAction {
    if (!swipeGesturesEnabled) return HorizontalChapterSwipeAction.NONE
    if (abs(deltaX) <= abs(deltaY)) return HorizontalChapterSwipeAction.NONE
    if (deltaX > thresholdPx && hasPreviousChapter) return HorizontalChapterSwipeAction.PREVIOUS
    if (deltaX < -thresholdPx && hasNextChapter) return HorizontalChapterSwipeAction.NEXT
    return HorizontalChapterSwipeAction.NONE
}

internal fun resolveVerticalChapterSwipeAction(
    swipeGesturesEnabled: Boolean,
    swipeToNextChapter: Boolean,
    swipeToPrevChapter: Boolean,
    deltaX: Float,
    deltaY: Float,
    minSwipeDistancePx: Float,
    horizontalTolerancePx: Float,
    gestureDurationMillis: Long,
    minHoldDurationMillis: Long,
    wasNearChapterEndAtDown: Boolean,
    wasNearChapterStartAtDown: Boolean,
    isNearChapterEnd: Boolean,
    isNearChapterStart: Boolean,
): VerticalChapterSwipeAction {
    if (!swipeGesturesEnabled) return VerticalChapterSwipeAction.NONE
    if (gestureDurationMillis < minHoldDurationMillis) return VerticalChapterSwipeAction.NONE

    val absX = abs(deltaX)
    val absY = abs(deltaY)
    if (absY < minSwipeDistancePx) return VerticalChapterSwipeAction.NONE
    if (absY <= absX + horizontalTolerancePx) return VerticalChapterSwipeAction.NONE

    if (swipeToNextChapter && deltaY < 0f && wasNearChapterEndAtDown && isNearChapterEnd) {
        return VerticalChapterSwipeAction.NEXT
    }
    if (swipeToPrevChapter && deltaY > 0f && wasNearChapterStartAtDown && isNearChapterStart) {
        return VerticalChapterSwipeAction.PREVIOUS
    }
    return VerticalChapterSwipeAction.NONE
}

internal fun resolveWebViewVerticalChapterSwipeAction(
    swipeGesturesEnabled: Boolean,
    swipeToNextChapter: Boolean,
    swipeToPrevChapter: Boolean,
    deltaX: Float,
    deltaY: Float,
    minSwipeDistancePx: Float,
    horizontalTolerancePx: Float,
    gestureDurationMillis: Long,
    minHoldDurationMillis: Long,
    wasNearChapterEndAtDown: Boolean,
    wasNearChapterStartAtDown: Boolean,
    isNearChapterEnd: Boolean,
    isNearChapterStart: Boolean,
): VerticalChapterSwipeAction {
    if (!swipeGesturesEnabled) return VerticalChapterSwipeAction.NONE
    if (gestureDurationMillis < minHoldDurationMillis) return VerticalChapterSwipeAction.NONE

    val absX = abs(deltaX)
    val absY = abs(deltaY)
    if (absY < minSwipeDistancePx) return VerticalChapterSwipeAction.NONE
    if (absY <= absX + horizontalTolerancePx) return VerticalChapterSwipeAction.NONE

    if (swipeToNextChapter && deltaY < 0f && wasNearChapterEndAtDown && isNearChapterEnd) {
        return VerticalChapterSwipeAction.NEXT
    }
    if (swipeToPrevChapter && deltaY > 0f && wasNearChapterStartAtDown && isNearChapterStart) {
        return VerticalChapterSwipeAction.PREVIOUS
    }
    return VerticalChapterSwipeAction.NONE
}

internal fun resolveReaderContentPaddingPx(
    showReaderUi: Boolean,
    basePaddingPx: Int,
): Int {
    return basePaddingPx
}

internal fun resolveWebViewPaddingTopPx(
    statusBarHeightPx: Int,
    @Suppress("UNUSED_PARAMETER") showReaderUi: Boolean,
    @Suppress("UNUSED_PARAMETER") appBarHeightPx: Int,
    basePaddingPx: Int,
    maxStatusBarInsetPx: Int = Int.MAX_VALUE,
): Int {
    val safeStatusInset = statusBarHeightPx
        .coerceAtLeast(0)
        .coerceAtMost(maxStatusBarInsetPx.coerceAtLeast(0))
    return safeStatusInset + basePaddingPx.coerceAtLeast(0)
}

internal fun resolveWebViewTextAlignCss(
    textAlign: ReaderTextAlign,
): String? {
    return when (textAlign) {
        ReaderTextAlign.SOURCE -> null
        ReaderTextAlign.LEFT -> "left"
        ReaderTextAlign.CENTER -> "center"
        ReaderTextAlign.JUSTIFY -> "justify"
        ReaderTextAlign.RIGHT -> "right"
    }
}

internal fun resolveWebViewFirstLineIndentCss(
    forceParagraphIndent: Boolean,
): String? {
    return if (forceParagraphIndent) {
        "${FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM}em"
    } else {
        null
    }
}

internal fun resolveAutoReaderShadowColor(
    customShadowColor: Color?,
    textColor: Color,
    backgroundColor: Color,
): Color {
    if (customShadowColor != null) return customShadowColor
    val prefersLightShadow = backgroundColor.luminance() < 0.5f || textColor.luminance() > 0.5f
    return if (prefersLightShadow) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }
}

internal fun resolveWebReaderTextShadowCss(
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    textColor: Color,
    backgroundColor: Color,
): String? {
    if (!textShadowEnabled) return null
    val customColor = parseReaderColor(textShadowColor)
    val shadowColor = resolveAutoReaderShadowColor(
        customShadowColor = customColor,
        textColor = textColor,
        backgroundColor = backgroundColor,
    )
    val blur = textShadowBlur.coerceAtLeast(0f)
    return "${formatCssPixel(
        textShadowX,
    )} ${formatCssPixel(textShadowY)} ${formatCssPixel(blur)} ${colorToCssRgba(shadowColor)}"
}

internal fun resolvePageEdgeShadowColor(
    pageEdgeShadowAlpha: Float,
    backgroundColor: Color,
): Color {
    val alpha = pageEdgeShadowAlpha.coerceIn(0.05f, 1f)
    return if (backgroundColor.luminance() < 0.5f) {
        Color.White.copy(alpha = alpha * 0.35f)
    } else {
        Color.Black.copy(alpha = alpha)
    }
}

private fun formatCssPixel(value: Float): String {
    val rounded = ((value * 10f).roundToInt()) / 10f
    return if (abs(rounded - rounded.roundToInt().toFloat()) < 0.001f) {
        "${rounded.roundToInt()}px"
    } else {
        String.format(Locale.US, "%.1fpx", rounded)
    }
}

private fun colorToCssRgba(color: Color): String {
    val red = (color.red * 255f).roundToInt().coerceIn(0, 255)
    val green = (color.green * 255f).roundToInt().coerceIn(0, 255)
    val blue = (color.blue * 255f).roundToInt().coerceIn(0, 255)
    val alpha = color.alpha.coerceIn(0f, 1f)
    return String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", red, green, blue, alpha)
}

@Composable
private fun NovelRichNativeScrollItem(
    block: NovelRichContentBlock,
    index: Int,
    lastIndex: Int,
    chapterTitle: String,
    novelTitle: String,
    sourceId: Long,
    chapterWebUrl: String?,
    novelUrl: String?,
    statusBarTopPadding: androidx.compose.ui.unit.Dp,
    textColor: Color,
    backgroundColor: Color,
    fontSize: Int,
    lineHeight: Float,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    textAlign: ReaderTextAlign,
    forceParagraphIndent: Boolean,
    preserveSourceTextAlignInNative: Boolean,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    textShadow: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
) {
    val context = LocalContext.current
    val textShadowImpl = remember(
        textShadow,
        textShadowColor,
        textShadowBlur,
        textShadowX,
        textShadowY,
        textColor,
        backgroundColor,
    ) {
        if (textShadow) {
            val customColor = parseReaderColor(textShadowColor)
            val shadowColor = resolveAutoReaderShadowColor(
                customShadowColor = customColor,
                textColor = textColor,
                backgroundColor = backgroundColor,
            )
            Shadow(
                color = shadowColor,
                offset = androidx.compose.ui.geometry.Offset(textShadowX, textShadowY),
                blurRadius = textShadowBlur,
            )
        } else {
            null
        }
    }
    val onLinkClick: (String) -> Unit = { rawUrl ->
        val resolvedUrl = resolveNovelReaderLinkUrl(
            rawUrl = rawUrl,
            chapterWebUrl = chapterWebUrl,
            novelUrl = novelUrl,
        )
        if (!resolvedUrl.isNullOrBlank()) {
            context.startActivity(
                WebViewActivity.newIntent(
                    context = context,
                    url = resolvedUrl,
                    sourceId = sourceId,
                    title = novelTitle,
                ),
            )
        }
    }
    when (block) {
        is NovelRichContentBlock.Paragraph -> {
            val text = buildNovelRichAnnotatedString(block.segments)
            val isChapterTitle = index == 0 && isNativeChapterTitleText(text.text, chapterTitle)
            val paragraphStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontSize = if (isChapterTitle) (fontSize * 1.12f).sp else fontSize.sp,
                lineHeight = if (isChapterTitle) (lineHeight * 1.08f).em else lineHeight.em,
                fontFamily = if (isChapterTitle) chapterTitleFontFamily ?: composeFontFamily else composeFontFamily,
                fontWeight = if (isChapterTitle) {
                    FontWeight.SemiBold
                } else if (forceBoldText) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
                shadow = textShadowImpl,
            ).withOptionalTextAlign(
                resolveNativeTextAlign(
                    globalTextAlign = textAlign,
                    preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                    sourceTextAlign = block.textAlign,
                ),
            ).withOptionalFirstLineIndentEm(
                resolveNativeFirstLineIndentEm(
                    forceParagraphIndent = forceParagraphIndent && !isChapterTitle,
                    sourceFirstLineIndentEm = block.firstLineIndentEm,
                ),
            )
            if (isChapterTitle) {
                Column(
                    modifier = Modifier.padding(
                        top = statusBarTopPadding + 10.dp,
                        bottom = if (index == lastIndex) 0.dp else 18.dp,
                    ),
                ) {
                    NovelRichAnnotatedText(
                        text = text,
                        style = paragraphStyle.copy(color = MaterialTheme.colorScheme.primary),
                        onLinkClick = onLinkClick,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(0.72f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    )
                }
            } else {
                NovelRichAnnotatedText(
                    text = text,
                    style = paragraphStyle,
                    modifier = Modifier.padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    ),
                    onLinkClick = onLinkClick,
                )
            }
        }
        is NovelRichContentBlock.Heading -> {
            val headingScale = when (block.level) {
                1 -> 1.24f
                2 -> 1.18f
                3 -> 1.13f
                else -> 1.08f
            }
            NovelRichAnnotatedText(
                text = buildNovelRichAnnotatedString(block.segments),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontSize = (fontSize * headingScale).sp,
                    lineHeight = (lineHeight * 1.1f).em,
                    fontFamily = composeFontFamily,
                    fontWeight = if (forceBoldText) FontWeight.Bold else FontWeight.SemiBold,
                    fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
                    shadow = textShadowImpl,
                ).withOptionalTextAlign(
                    resolveNativeTextAlign(
                        globalTextAlign = textAlign,
                        preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                        sourceTextAlign = block.textAlign,
                    ),
                ),
                modifier = Modifier.padding(
                    top = if (index == 0) statusBarTopPadding else 4.dp,
                    bottom = if (index == lastIndex) 0.dp else paragraphSpacing + 2.dp,
                ),
                onLinkClick = onLinkClick,
            )
        }
        is NovelRichContentBlock.BlockQuote -> {
            NovelRichAnnotatedText(
                text = buildNovelRichAnnotatedString(block.segments),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor.copy(alpha = 0.92f),
                    fontSize = fontSize.sp,
                    lineHeight = lineHeight.em,
                    fontFamily = composeFontFamily,
                    fontWeight = if (forceBoldText) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
                    shadow = textShadowImpl,
                ).withOptionalTextAlign(
                    resolveNativeTextAlign(
                        globalTextAlign = textAlign,
                        preserveSourceTextAlignInNative = preserveSourceTextAlignInNative,
                        sourceTextAlign = block.textAlign,
                    ),
                ),
                modifier = Modifier
                    .padding(
                        top = if (index == 0) statusBarTopPadding else 0.dp,
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    )
                    .padding(start = 12.dp),
                onLinkClick = onLinkClick,
            )
        }
        NovelRichContentBlock.HorizontalRule -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (index == 0) statusBarTopPadding else 4.dp,
                        bottom = if (index == lastIndex) 4.dp else paragraphSpacing + 4.dp,
                    )
                    .height(1.dp)
                    .background(textColor.copy(alpha = 0.22f)),
            )
        }
        is NovelRichContentBlock.Image -> {
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
                        bottom = if (index == lastIndex) 0.dp else paragraphSpacing,
                    ),
            )
        }
    }
}

internal fun resolveReaderTextShadow(
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    textColor: Color,
    backgroundColor: Color,
): Shadow? {
    if (!textShadowEnabled) return null
    val customColor = parseReaderColor(textShadowColor)
    val shadowColor = resolveAutoReaderShadowColor(
        customShadowColor = customColor,
        textColor = textColor,
        backgroundColor = backgroundColor,
    )
    return Shadow(
        color = shadowColor,
        offset = Offset(textShadowX, textShadowY),
        blurRadius = textShadowBlur,
    )
}

internal fun resolvePageReaderBaseTextStyle(
    baseStyle: TextStyle,
    color: Color,
    backgroundColor: Color,
    fontSize: Int,
    lineHeight: Float,
    fontFamily: FontFamily?,
    textAlign: TextAlign?,
    forceBoldText: Boolean,
    forceItalicText: Boolean,
    textShadow: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
): TextStyle {
    return baseStyle.copy(
        color = color,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.em,
        fontFamily = fontFamily,
        fontWeight = if (forceBoldText) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (forceItalicText) FontStyle.Italic else FontStyle.Normal,
        shadow = resolveReaderTextShadow(
            textShadowEnabled = textShadow,
            textShadowColor = textShadowColor,
            textShadowBlur = textShadowBlur,
            textShadowX = textShadowX,
            textShadowY = textShadowY,
            textColor = color,
            backgroundColor = backgroundColor,
        ),
    ).withOptionalTextAlign(textAlign)
}

@Composable
private fun NovelRichAnnotatedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
) {
    val hasLinkAnnotations = remember(text) {
        text.length > 0 && text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty()
    }
    if (!hasLinkAnnotations) {
        Text(
            text = text,
            style = style,
            modifier = modifier,
        )
        return
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            resolveNovelRichLinkAtCharOffset(text, offset)?.let(onLinkClick)
        },
    )
}

private fun novelReaderTextAlign(textAlign: ReaderTextAlign): TextAlign? {
    return when (textAlign) {
        ReaderTextAlign.SOURCE -> null
        ReaderTextAlign.LEFT -> TextAlign.Start
        ReaderTextAlign.CENTER -> TextAlign.Center
        ReaderTextAlign.JUSTIFY -> TextAlign.Justify
        ReaderTextAlign.RIGHT -> TextAlign.End
    }
}

internal fun TextStyle.withOptionalTextAlign(textAlign: TextAlign?): TextStyle {
    return if (textAlign == null) this else copy(textAlign = textAlign)
}

internal fun TextStyle.withOptionalFirstLineIndentEm(firstLineIndentEm: Float?): TextStyle {
    return if (firstLineIndentEm == null) {
        this
    } else {
        copy(textIndent = TextIndent(firstLine = firstLineIndentEm.em))
    }
}

internal fun resolvePageReaderBlockTextStyle(
    baseStyle: TextStyle,
    isChapterTitle: Boolean,
    fontSize: Int,
    lineHeight: Float,
    fontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
): TextStyle {
    if (!isChapterTitle) return baseStyle
    return baseStyle.copy(
        fontSize = (fontSize * 1.12f).sp,
        lineHeight = (lineHeight * 1.08f).em,
        fontFamily = chapterTitleFontFamily ?: fontFamily,
        fontWeight = FontWeight.SemiBold,
    )
}

internal fun resolvePageReaderLayoutTextAlign(
    globalTextAlign: ReaderTextAlign,
    @Suppress("UNUSED_PARAMETER") preserveSourceTextAlignInNative: Boolean,
): ReaderTextAlign {
    return if (globalTextAlign == ReaderTextAlign.SOURCE) {
        // Page mode always needs a deterministic alignment for layout and pagination.
        ReaderTextAlign.LEFT
    } else {
        globalTextAlign
    }
}

internal fun resolvePageReaderRenderTextAlign(
    globalTextAlign: ReaderTextAlign,
): TextAlign {
    return novelReaderTextAlign(textAlign = globalTextAlign) ?: TextAlign.Start
}

internal fun resolveNativeTextAlign(
    globalTextAlign: ReaderTextAlign,
    preserveSourceTextAlignInNative: Boolean,
    sourceTextAlign: NovelRichBlockTextAlign? = null,
): TextAlign? {
    if (!preserveSourceTextAlignInNative) {
        return novelReaderTextAlign(textAlign = globalTextAlign)
    }
    return when (sourceTextAlign) {
        NovelRichBlockTextAlign.LEFT -> TextAlign.Start
        NovelRichBlockTextAlign.CENTER -> TextAlign.Center
        NovelRichBlockTextAlign.JUSTIFY -> TextAlign.Justify
        NovelRichBlockTextAlign.RIGHT -> TextAlign.End
        null -> novelReaderTextAlign(textAlign = globalTextAlign)
    }
}

internal fun resolveNativeFirstLineIndentEm(
    forceParagraphIndent: Boolean,
    sourceFirstLineIndentEm: Float?,
): Float? {
    return if (forceParagraphIndent) {
        FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
    } else {
        sourceFirstLineIndentEm
    }
}

internal fun resolveNovelRichLinkAtCharOffset(
    text: AnnotatedString,
    offset: Int,
): String? {
    if (text.isEmpty()) return null
    val clamped = offset.coerceIn(0, text.length - 1)
    return text.getStringAnnotations(
        tag = "URL",
        start = clamped,
        end = (clamped + 1).coerceAtMost(text.length),
    ).lastOrNull()?.item
}

internal fun resolveNovelReaderLinkUrl(
    rawUrl: String,
    chapterWebUrl: String?,
    novelUrl: String?,
): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    trimmed.toHttpUrlOrNull()?.let { return it.toString() }
    chapterWebUrl?.toHttpUrlOrNull()?.resolve(trimmed)?.let { return it.toString() }
    novelUrl?.toHttpUrlOrNull()?.resolve(trimmed)?.let { return it.toString() }
    return null
}

internal fun isNativeChapterTitleText(
    blockText: String,
    chapterName: String,
): Boolean {
    val normalizedBlock = blockText
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    val normalizedChapter = chapterName
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalizedBlock.isNotBlank() && normalizedBlock == normalizedChapter
}

internal fun resolveWebViewPaddingBottomPx(
    navigationBarHeightPx: Int,
    @Suppress("UNUSED_PARAMETER") showReaderUi: Boolean,
    @Suppress("UNUSED_PARAMETER") bottomBarHeightPx: Int,
    basePaddingPx: Int,
): Int {
    return navigationBarHeightPx.coerceAtLeast(0) + basePaddingPx.coerceAtLeast(0)
}

internal fun shouldTrackWebViewProgress(
    shouldRestoreWebScroll: Boolean,
): Boolean {
    return !shouldRestoreWebScroll
}

internal fun shouldDispatchWebProgressUpdate(
    shouldRestoreWebScroll: Boolean,
    newPercent: Int,
    currentPercent: Int,
): Boolean {
    return shouldTrackWebViewProgress(shouldRestoreWebScroll) && newPercent != currentPercent
}

internal fun resolveWebViewTotalScrollablePx(
    contentHeightPx: Int,
    viewHeightPx: Int,
): Int {
    return (contentHeightPx - viewHeightPx).coerceAtLeast(0)
}

internal fun resolveWebViewScrollProgressPercent(
    scrollY: Int,
    totalScrollable: Int,
): Int {
    if (totalScrollable <= 0) return 0
    val ratio = scrollY.toFloat() / totalScrollable.toFloat()
    return (ratio * 100f).roundToInt().coerceIn(0, 100)
}

internal fun resolveFinalWebViewProgressPercent(
    resolvedPercent: Int?,
    cachedPercent: Int,
): Int {
    val safeCached = cachedPercent.coerceIn(0, 100)
    val safeResolved = resolvedPercent?.coerceIn(0, 100) ?: return safeCached
    if (safeResolved == 0 && safeCached > 0) {
        return safeCached
    }
    return safeResolved
}

internal fun resolveNativeScrollProgressForTracking(
    firstVisibleItemIndex: Int,
    textBlocksCount: Int,
    canScrollForward: Boolean,
): Pair<Int, Int> {
    val normalizedCount = textBlocksCount.coerceAtLeast(0)
    val normalizedIndex = firstVisibleItemIndex.coerceAtLeast(0)
    if (normalizedCount <= 1) {
        return if (canScrollForward) 0 to 2 else 1 to 2
    }
    if (!canScrollForward) {
        return (normalizedCount - 1) to normalizedCount
    }
    return normalizedIndex.coerceAtMost(normalizedCount - 1) to normalizedCount
}

internal fun resolveReaderUiAfterChapterChange(
    currentShowReaderUi: Boolean,
    usePageReader: Boolean,
): Boolean {
    return if (usePageReader) false else currentShowReaderUi
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

private fun readBatteryLevel(
    context: Context,
    batteryIntent: Intent? = null,
): Int {
    val levelFromIntent = resolveBatteryLevelPercent(
        level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
    )
    if (levelFromIntent != null) return levelFromIntent

    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val directLevel = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    if (directLevel in 0..100) return directLevel

    val fallbackIntent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    resolveBatteryLevelPercent(
        level = fallbackIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        scale = fallbackIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
    )?.let { return it }
    return 100
}

internal fun resolveBatteryLevelPercent(
    level: Int,
    scale: Int,
): Int? {
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale.toFloat()).roundToInt().coerceIn(0, 100)
}

private fun currentTimeString(context: Context): String {
    return DateFormat.getTimeFormat(context).format(Date())
}

private fun sampleReaderBackgroundLuminance(path: String): Float? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val maxDimension = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sampleSize = (maxDimension / 96).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeFile(path, options) ?: return@runCatching null
        try {
            val width = sampled.width.coerceAtLeast(1)
            val height = sampled.height.coerceAtLeast(1)
            val stepX = (width / 12).coerceAtLeast(1)
            val stepY = (height / 12).coerceAtLeast(1)
            var luminanceSum = 0f
            var sampledPixels = 0
            var x = 0
            while (x < width) {
                var y = 0
                while (y < height) {
                    val pixel = sampled.getPixel(x, y)
                    val color = Color(pixel)
                    luminanceSum += color.luminance()
                    sampledPixels++
                    y += stepY
                }
                x += stepX
            }
            if (sampledPixels == 0) null else (luminanceSum / sampledPixels).coerceIn(0f, 1f)
        } finally {
            sampled.recycle()
        }
    }.getOrNull()
}

private fun parseReaderColor(value: String?): Color? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null
    val hex = normalized.removePrefix("#")
    return when (hex.length) {
        6 -> runCatching {
            val rgb = hex.toLong(16).toInt()
            val argb = (0xFF shl 24) or rgb
            Color(argb)
        }.getOrNull()
        8 -> {
            // Accept both #AARRGGBB (Android) and #RRGGBBAA (CSS-style).
            runCatching {
                val packed = hex.toLong(16).toInt()
                val argbAlpha = (packed ushr 24) and 0xFF
                val rgbaAlpha = packed and 0xFF
                val shouldTreatAsArgb = when {
                    argbAlpha == 0 && rgbaAlpha > 0 -> false
                    rgbaAlpha == 0 && argbAlpha > 0 -> true
                    else -> true
                }
                if (shouldTreatAsArgb) {
                    Color(packed)
                } else {
                    val rr = (packed shr 24) and 0xFF
                    val gg = (packed shr 16) and 0xFF
                    val bb = (packed shr 8) and 0xFF
                    val aa = packed and 0xFF
                    Color((aa shl 24) or (rr shl 16) or (gg shl 8) or bb)
                }
            }.getOrNull()
        }
        else -> runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
    }
}

internal fun parseReaderColorForTest(value: String?): Color? = parseReaderColor(value)

internal fun colorToCssHex(color: Color): String {
    val argb = color.toArgb()
    val alpha = (argb shr 24) and 0xFF
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return if (alpha == 0xFF) {
        String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    } else {
        String.format(Locale.US, "#%02X%02X%02X%02X", red, green, blue, alpha)
    }
}

internal fun intervalToAutoScrollSpeed(intervalSeconds: Int): Int {
    val clamped = intervalSeconds.coerceIn(1, 60)
    val normalized = (60 - clamped).toFloat() / 59f
    return (1f + normalized * 99f).roundToInt().coerceIn(1, 100)
}

internal fun autoScrollSpeedToInterval(speed: Int): Int {
    val clamped = speed.coerceIn(1, 100)
    val normalized = (clamped - 1).toFloat() / 99f
    return (60f - normalized * 59f).roundToInt().coerceIn(1, 60)
}

internal fun autoScrollPageDelayMs(speed: Int): Long {
    val clamped = speed.coerceIn(1, 100)
    return (10_000 - (clamped - 1) * 80).toLong().coerceIn(2_000L, 10_000L)
}

internal fun autoScrollScrollStepPx(speed: Int): Float {
    val clamped = speed.coerceIn(1, 100)
    return 1.5f + (clamped - 1) * (8.5f / 99f)
}

internal fun autoScrollFrameStepPx(
    speed: Int,
    frameDeltaNanos: Long,
): Float {
    val baseStepPx = autoScrollScrollStepPx(speed)
    val normalizedDelta = frameDeltaNanos.coerceIn(1L, 250_000_000L).toFloat() / 16_000_000f
    return (baseStepPx * normalizedDelta).coerceAtLeast(0.05f)
}

internal data class AutoScrollStepResult(
    val stepPx: Int,
    val remainderPx: Float,
)

internal data class AutoScrollUiState(
    val autoScrollEnabled: Boolean,
    val showReaderUi: Boolean,
    val autoScrollExpanded: Boolean,
)

internal fun resolveAutoScrollUiStateOnToggle(
    currentEnabled: Boolean,
    showReaderUi: Boolean,
    autoScrollExpanded: Boolean,
): AutoScrollUiState {
    val toggledEnabled = !currentEnabled
    return if (toggledEnabled) {
        AutoScrollUiState(
            autoScrollEnabled = true,
            showReaderUi = false,
            autoScrollExpanded = false,
        )
    } else {
        AutoScrollUiState(
            autoScrollEnabled = false,
            showReaderUi = showReaderUi,
            autoScrollExpanded = autoScrollExpanded,
        )
    }
}

internal fun resolveInitialAutoScrollEnabled(
    @Suppress("UNUSED_PARAMETER")
    savedPreferenceEnabled: Boolean,
): Boolean {
    return false
}

internal fun resolveAutoScrollStep(
    frameStepPx: Float,
    previousRemainderPx: Float,
): AutoScrollStepResult {
    val totalStep = frameStepPx.coerceAtLeast(0f) + previousRemainderPx.coerceAtLeast(0f)
    val stepPx = totalStep.toInt().coerceAtLeast(0)
    return AutoScrollStepResult(
        stepPx = stepPx,
        remainderPx = totalStep - stepPx,
    )
}

internal data class TextPageRange(
    val start: Int,
    val endExclusive: Int,
)

internal fun paginateTextIntoPageRanges(
    text: String,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): List<TextPageRange> {
    if (text.isBlank()) return emptyList()

    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = heightPx.coerceAtLeast(1)

    val layout = buildReaderStaticLayout(
        text = text,
        widthPx = safeWidth,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
    ) ?: run {
        val safeTextSize = textSizePx.coerceAtLeast(1f)
        val approxCharsPerLine = (safeWidth / (safeTextSize * 0.55f)).toInt().coerceAtLeast(15)
        val approxLinesPerPage = (safeHeight / (safeTextSize * lineHeightMultiplier.coerceAtLeast(1f)))
            .toInt()
            .coerceAtLeast(8)
        val chunkSize = (approxCharsPerLine * approxLinesPerPage).coerceAtLeast(120)
        val ranges = mutableListOf<TextPageRange>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            val trimmed = trimTextRange(text, start, end)
            if (trimmed != null) ranges += trimmed
            start = end
        }
        return ranges
    }

    if (layout.lineCount <= 0) return listOf(TextPageRange(0, text.length))

    val ranges = mutableListOf<TextPageRange>()
    var startLine = 0
    while (startLine < layout.lineCount) {
        val slice = resolveStaticLayoutSliceForHeight(
            text = text,
            layout = layout,
            startLine = startLine,
            availableHeight = safeHeight,
        ) ?: break
        ranges += slice.range
        startLine = slice.nextStartLine
    }

    return if (ranges.isNotEmpty()) ranges else listOf(TextPageRange(0, text.length))
}

internal fun paginateAnnotatedTextIntoPages(
    text: AnnotatedString,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): List<AnnotatedString> {
    if (text.text.isBlank()) return emptyList()
    val ranges = paginateTextIntoPageRanges(
        text = text.text,
        widthPx = widthPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
    )
    return ranges.map { range ->
        text.subSequence(TextRange(range.start, range.endExclusive))
    }
}

internal fun paginateTextIntoPages(
    text: String,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): List<String> {
    return paginateTextIntoPageRanges(
        text = text,
        widthPx = widthPx,
        heightPx = heightPx,
        textSizePx = textSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        typeface = typeface,
        textAlign = textAlign,
    ).map { range ->
        text.substring(range.start, range.endExclusive)
    }
}

private fun trimTextRange(
    text: String,
    startInclusive: Int,
    endExclusive: Int,
): TextPageRange? {
    var start = startInclusive.coerceIn(0, text.length)
    var end = endExclusive.coerceIn(start, text.length)
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start < end) TextPageRange(start, end) else null
}

private data class StaticLayoutSlice(
    val range: TextPageRange,
    val nextStartLine: Int,
    val heightPx: Int,
)

private data class ApproximateTextMetrics(
    val charsPerLine: Int,
    val lineHeightPx: Int,
)

private fun buildReaderStaticLayout(
    text: String,
    widthPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): StaticLayout? {
    return runCatching {
        val paint = TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSizePx.coerceAtLeast(1f)
            this.typeface = typeface
        }
        StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(textAlign.toLayoutAlignment())
            .setIncludePad(false)
            .setLineSpacing(0f, lineHeightMultiplier.coerceAtLeast(1f))
            .build()
    }.getOrNull()
}

private fun resolveStaticLayoutHeight(
    layout: StaticLayout,
): Int {
    if (layout.lineCount <= 0) return 0
    return layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0)
}

private fun resolveStaticLayoutSliceForHeight(
    text: String,
    layout: StaticLayout,
    startLine: Int,
    availableHeight: Int,
): StaticLayoutSlice? {
    if (startLine >= layout.lineCount || availableHeight <= 0) return null

    val startOffset = layout.getLineStart(startLine)
    var endLineExclusive = startLine
    while (
        endLineExclusive < layout.lineCount &&
        layout.getLineBottom(endLineExclusive) - layout.getLineTop(startLine) <= availableHeight
    ) {
        endLineExclusive++
    }
    val endLine = if (endLineExclusive > startLine) endLineExclusive - 1 else startLine
    val endOffset = layout.getLineEnd(endLine).coerceIn(startOffset, text.length)
    val range = trimTextRange(text, startOffset, endOffset) ?: return null
    return StaticLayoutSlice(
        range = range,
        nextStartLine = endLine + 1,
        heightPx = layout.getLineBottom(endLine) - layout.getLineTop(startLine),
    )
}

private fun measureApproximateTextHeight(
    text: String,
    metrics: ApproximateTextMetrics,
): Int {
    if (text.isBlank()) return metrics.lineHeightPx
    val lineCount = text.split('\n').sumOf { line ->
        ceil(line.length.toDouble() / metrics.charsPerLine.toDouble()).toInt().coerceAtLeast(1)
    }.coerceAtLeast(1)
    return lineCount * metrics.lineHeightPx
}

private fun ReaderTextAlign.toLayoutAlignment(): Layout.Alignment {
    return when (this) {
        ReaderTextAlign.SOURCE -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        ReaderTextAlign.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

internal fun toBionicText(text: String): AnnotatedString {
    if (text.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        Regex("\\S+|\\s+").findAll(text).forEach { match ->
            val token = match.value
            if (token.firstOrNull()?.isWhitespace() == true) {
                append(token)
            } else {
                val emphasizeCount = ceil(token.length * 0.5f).toInt().coerceAtLeast(1)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(token.take(emphasizeCount))
                }
                append(token.drop(emphasizeCount))
            }
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
