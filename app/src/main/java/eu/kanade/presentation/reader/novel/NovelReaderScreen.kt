package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichBlockTextAlign
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderParagraphSpacing
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
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
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
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
) {
    var showSettings by remember { mutableStateOf(false) }
    var showReaderUI by remember { mutableStateOf(false) } // UI скрыт по умолчанию
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
    val readerPreferences = remember { Injekt.get<NovelReaderPreferences>() }
    val sourceId = state.novel.source
    val hasSourceOverride = remember(sourceId) { readerPreferences.getSourceOverride(sourceId) != null }
    var pageViewportSize by remember(state.chapter.id) { mutableStateOf(IntSize.Zero) }
    var autoScrollEnabled by remember(state.chapter.id, state.readerSettings.autoScroll) {
        mutableStateOf(state.readerSettings.autoScroll)
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
    fun updateAutoScrollPreferences(
        enabled: Boolean? = null,
        interval: Int? = null,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScroll = enabled ?: override.autoScroll,
                    autoScrollInterval = interval ?: override.autoScrollInterval,
                )
            }
        } else {
            enabled?.let { readerPreferences.autoScroll().set(it) }
            interval?.let { readerPreferences.autoScrollInterval().set(it) }
        }
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val batteryLevel by rememberBatteryLevel(context)
    val timeText by rememberCurrentTimeText(context)
    val isDarkTheme = when (state.readerSettings.theme) {
        NovelReaderTheme.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < 0.5f
        NovelReaderTheme.DARK -> true
        NovelReaderTheme.LIGHT -> false
    }
    val fallbackTextColor = if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFFEDEDED)
    } else {
        androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    }
    val fallbackBackground = if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFF121212)
    } else {
        androidx.compose.ui.graphics.Color.White
    }
    val textColor = parseReaderColor(state.readerSettings.textColor)
        .takeIf { state.readerSettings.textColor?.isNotBlank() == true }
        ?: fallbackTextColor
    val textBackground = parseReaderColor(state.readerSettings.backgroundColor)
        .takeIf { state.readerSettings.backgroundColor?.isNotBlank() == true }
        ?: fallbackBackground
    val composeFontFamily = remember(state.readerSettings.fontFamily) {
        novelReaderFonts.firstOrNull { it.id == state.readerSettings.fontFamily }
            ?.fontResId
            ?.let { FontFamily(Font(it)) }
    }
    val chapterTitleFontFamily = remember {
        novelReaderFonts.firstOrNull { it.id == "domine" }?.fontResId?.let { FontFamily(Font(it)) }
    }
    val paragraphSpacing = remember(state.readerSettings.paragraphSpacing) {
        resolveParagraphSpacingDp(state.readerSettings.paragraphSpacing)
    }
    val composeTypeface = remember(state.readerSettings.fontFamily, context) {
        novelReaderFonts.firstOrNull { it.id == state.readerSettings.fontFamily }?.fontResId?.let { fontRes ->
            ResourcesCompat.getFont(context, fontRes)
        }
    }
    val textListState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.lastSavedIndex
            .coerceIn(0, (state.contentBlocks.lastIndex).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = state.lastSavedScrollOffsetPx.coerceAtLeast(0),
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
            showReaderUi = showReaderUI,
            basePaddingPx = baseContentPadding.roundToPx(),
        ).toDp()
    }
    val scrollContentBlocks = remember(state.chapter.id, state.contentBlocks) {
        state.contentBlocks.takeIf { it.isNotEmpty() }
            ?: state.textBlocks.map { NovelReaderScreenModel.ContentBlock.Text(it) }
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
    val pageReaderBlocks: List<String> = remember(
        state.chapter.id,
        state.textBlocks,
        shouldPaginateForPageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.forceParagraphIndent,
        composeTypeface,
        pageViewportSize,
        contentPaddingPx,
        statusBarTopPadding,
    ) {
        resolvePageReaderBlocks(
            shouldPaginate = shouldPaginateForPageReader,
            textBlocks = state.textBlocks,
        ) { chapterText ->
            val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
            val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
            val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
            val verticalPaddingPx = topPaddingPx + bottomPaddingPx
            paginateTextIntoPages(
                text = chapterText,
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                textAlign = pageReaderLayoutTextAlign,
            )
        }
    }
    val shouldPaginateRichForPageReader = shouldPaginateForPageReader &&
        state.readerSettings.richNativeRendererExperimental &&
        !state.readerSettings.bionicReading &&
        !state.richContentUnsupportedFeaturesDetected &&
        state.richContentBlocks.isNotEmpty() &&
        state.richContentBlocks.none { it is NovelRichContentBlock.Image }
    val richPageReaderBlocks: List<AnnotatedString> = remember(
        state.chapter.id,
        state.richContentBlocks,
        shouldPaginateRichForPageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        composeTypeface,
        pageViewportSize,
        contentPaddingPx,
        statusBarTopPadding,
    ) {
        if (!shouldPaginateRichForPageReader) {
            emptyList()
        } else {
            val richChapterText = buildRichPageReaderChapterAnnotatedText(
                richBlocks = state.richContentBlocks,
                forcedParagraphFirstLineIndentEm = if (state.readerSettings.forceParagraphIndent) {
                    FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
                } else {
                    null
                },
            )
            if (richChapterText.text.isBlank()) {
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
                paginateAnnotatedTextIntoPages(
                    text = richChapterText,
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
            pageReaderBlocks.isNotEmpty() || richPageReaderBlocks.isNotEmpty()
            )
    val useRichPageReader = usePageReader && richPageReaderBlocks.isNotEmpty()
    val pageReaderItemsCount = if (useRichPageReader) richPageReaderBlocks.size else pageReaderBlocks.size
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
        initialPage = state.lastSavedIndex.coerceIn(0, pageReaderItemsCount.coerceAtLeast(1) - 1),
        pageCount = { pageReaderItemsCount.coerceAtLeast(1) },
    )
    val readingProgressPercent by remember(
        showWebView,
        webProgressPercent,
        nativeScrollItemsCount,
        pageReaderItemsCount,
        pagerState.currentPage,
        textListState.firstVisibleItemIndex,
        textListState.canScrollForward,
        usePageReader,
    ) {
        derivedStateOf {
            when {
                showWebView -> webProgressPercent
                usePageReader -> {
                    (((pagerState.currentPage + 1).toFloat() / pageReaderItemsCount.toFloat()) * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
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
        showReaderUi = showReaderUI,
        showBatteryAndTime = state.readerSettings.showBatteryAndTime,
        showKindleInfoBlock = state.readerSettings.showKindleInfoBlock,
        showTimeToEnd = state.readerSettings.showTimeToEnd,
        showWordCount = state.readerSettings.showWordCount,
    )
    val minVerticalChapterSwipeDistancePx = with(density) { 120.dp.toPx() }
    val verticalChapterSwipeHorizontalTolerancePx = with(density) { 20.dp.toPx() }
    val minVerticalChapterSwipeHoldDurationMillis = 180L

    // Управление System UI для fullscreen режима
    SystemUIController(
        fullScreenMode = state.readerSettings.fullScreenMode,
        keepScreenOn = state.readerSettings.keepScreenOn,
        showReaderUi = showReaderUI,
        defaultLightStatusBars = context.resources.getBoolean(R.bool.lightStatusBar),
    )

    // Volume Buttons Handler
    val coroutineScope = rememberCoroutineScope()
    suspend fun moveBackwardByReaderAction() {
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(-1)) {
                webView.scrollBy(0, -tapScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                onOpenPreviousChapter?.invoke(state.previousChapterId)
            }
        } else if (usePageReader) {
            val currentPage = pagerState.currentPage
            if (currentPage > 0) {
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
            val currentPage = pagerState.currentPage
            if (currentPage < pageReaderBlocks.lastIndex) {
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
        if (showReaderUI) return true
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
        showReaderUI,
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
        showReaderUI,
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
            if (showReaderUI) {
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
                if (showReaderUI || showWebView || !autoScrollEnabled) continue
                val currentPage = pagerState.currentPage
                if (currentPage < pageReaderBlocks.lastIndex) {
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
        ReaderAtmosphereBackground(
            backgroundColor = textBackground,
            backgroundTexture = state.readerSettings.backgroundTexture,
            oledEdgeGradient = state.readerSettings.oledEdgeGradient,
            isDarkTheme = isDarkTheme,
        )
        // Контент (текст главы) - занимает весь экран, padding ВНУТРИ через contentPadding
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!showWebView && scrollContentBlocks.isNotEmpty()) {
                // Отслеживание прогресса в зависимости от режима
                if (usePageReader) {
                    LaunchedEffect(pagerState.currentPage, pageReaderItemsCount) {
                        onReadingProgress(
                            pagerState.currentPage,
                            pageReaderItemsCount,
                            pagerState.currentPage.toLong(),
                        )
                    }
                    DisposableEffect(pagerState, pageReaderItemsCount) {
                        onDispose {
                            onReadingProgress(
                                pagerState.currentPage,
                                pageReaderItemsCount,
                                pagerState.currentPage.toLong(),
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
                if (usePageReader) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(
                                state.readerSettings.swipeToPrevChapter,
                                state.readerSettings.swipeToNextChapter,
                                state.previousChapterId,
                                state.nextChapterId,
                            ) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        when (
                                            resolveReaderTapAction(
                                                tapX = offset.x,
                                                width = size.width.toFloat(),
                                                tapToScrollEnabled = state.readerSettings.tapToScroll,
                                            )
                                        ) {
                                            ReaderTapAction.TOGGLE_UI -> showReaderUI = !showReaderUI
                                            ReaderTapAction.BACKWARD -> coroutineScope.launch {
                                                moveBackwardByReaderAction()
                                            }
                                            ReaderTapAction.FORWARD -> coroutineScope.launch {
                                                moveForwardByReaderAction()
                                            }
                                        }
                                    },
                                )
                            },
                    ) { page ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = contentPaddingPx + statusBarTopPadding,
                                    bottom = contentPaddingPx,
                                    start = state.readerSettings.margin.dp,
                                    end = state.readerSettings.margin.dp,
                                ),
                            contentAlignment = androidx.compose.ui.Alignment.TopStart,
                        ) {
                            Text(
                                text = when {
                                    useRichPageReader -> richPageReaderBlocks[page]
                                    state.readerSettings.bionicReading -> toBionicText(pageReaderBlocks[page])
                                    else -> AnnotatedString(pageReaderBlocks[page])
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = textColor,
                                    fontSize = state.readerSettings.fontSize.sp,
                                    lineHeight = state.readerSettings.lineHeight.em,
                                    fontFamily = composeFontFamily,
                                ).withOptionalTextAlign(
                                    resolveNativeTextAlign(
                                        globalTextAlign = state.readerSettings.textAlign,
                                        preserveSourceTextAlignInNative =
                                        state.readerSettings.preserveSourceTextAlignInNative,
                                    ),
                                ),
                            )
                        }
                    }
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
                                                tapToScrollEnabled = state.readerSettings.tapToScroll,
                                            )
                                        ) {
                                            ReaderTapAction.TOGGLE_UI -> showReaderUI = !showReaderUI
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
                                        showReaderUI,
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

                                            if (showReaderUI || showWebView || usePageReader) {
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
                                    fontSize = state.readerSettings.fontSize,
                                    lineHeight = state.readerSettings.lineHeight,
                                    composeFontFamily = composeFontFamily,
                                    chapterTitleFontFamily = chapterTitleFontFamily,
                                    paragraphSpacing = paragraphSpacing,
                                    textAlign = state.readerSettings.textAlign,
                                    forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                                    preserveSourceTextAlignInNative =
                                    state.readerSettings.preserveSourceTextAlignInNative,
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
                                            fontWeight = if (isChapterTitle) FontWeight.SemiBold else FontWeight.Normal,
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
                    showReaderUi = showReaderUI,
                    appBarHeightPx = appBarHeight,
                    basePaddingPx = initialWebReaderPaddingPx,
                    maxStatusBarInsetPx = initialMaxWebViewStatusInsetPx,
                )
                val initialPaddingBottom = resolveWebViewPaddingBottomPx(
                    navigationBarHeightPx = navigationBarHeight,
                    showReaderUi = showReaderUI,
                    bottomBarHeightPx = bottomBarHeight,
                    basePaddingPx = initialWebReaderPaddingPx,
                )
                val initialPaddingHorizontal = state.readerSettings.margin
                val initialCssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                val initialCssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                    forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                )
                val initialSelectedFontFamily = state.readerSettings.fontFamily.takeIf { it.isNotBlank() }
                val initialFontAssetFile = novelReaderFonts
                    .firstOrNull { it.id == state.readerSettings.fontFamily }
                    ?.assetFileName
                    .orEmpty()
                val initialFontFaceCss = if (initialFontAssetFile.isNotBlank()) {
                    """
                    @font-face {
                        font-family: '${state.readerSettings.fontFamily}';
                        src: url('file:///android_asset/fonts/$initialFontAssetFile');
                    }
                    """.trimIndent()
                } else {
                    ""
                }
                val initialReaderCss = buildWebReaderCssText(
                    fontFaceCss = initialFontFaceCss,
                    paddingTop = initialPaddingTop,
                    paddingBottom = initialPaddingBottom,
                    paddingHorizontal = initialPaddingHorizontal,
                    fontSizePx = state.readerSettings.fontSize,
                    lineHeightMultiplier = state.readerSettings.lineHeight,
                    textAlignCss = initialCssTextAlign,
                    firstLineIndentCss = initialCssFirstLineIndent,
                    textColorHex = colorToCssHex(textColor),
                    backgroundHex = colorToCssHex(textBackground),
                    backgroundTexture = state.readerSettings.backgroundTexture,
                    oledEdgeGradient = state.readerSettings.oledEdgeGradient && isDarkTheme,
                    fontFamilyName = initialSelectedFontFamily,
                    customCss = state.readerSettings.customCSS,
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
                                            tapToScrollEnabled = state.readerSettings.tapToScroll,
                                        )
                                    ) {
                                        ReaderTapAction.TOGGLE_UI -> {
                                            showReaderUI = !showReaderUI
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
                                    if (!showReaderUI && !horizontalSwipeHandled) {
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
                                    if (!showReaderUI && !horizontalSwipeHandled) {
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
                            showReaderUi = showReaderUI,
                            appBarHeightPx = appBarHeight,
                            basePaddingPx = webReaderPaddingPx,
                            maxStatusBarInsetPx = maxWebViewStatusInsetPx,
                        )
                        val paddingBottom = resolveWebViewPaddingBottomPx(
                            navigationBarHeightPx = navigationBarHeight,
                            showReaderUi = showReaderUI,
                            bottomBarHeightPx = bottomBarHeight,
                            basePaddingPx = webReaderPaddingPx,
                        )
                        val paddingHorizontal = state.readerSettings.margin
                        val cssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                        val cssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                            forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                        )
                        val selectedFontFamily = state.readerSettings.fontFamily.takeIf { it.isNotBlank() }
                        val fontAssetFile = novelReaderFonts
                            .firstOrNull { it.id == state.readerSettings.fontFamily }
                            ?.assetFileName
                            .orEmpty()
                        val fontFaceCss = if (fontAssetFile.isNotBlank()) {
                            """
                            @font-face {
                                font-family: '${state.readerSettings.fontFamily}';
                                src: url('file:///android_asset/fonts/$fontAssetFile');
                            }
                            """.trimIndent()
                        } else {
                            ""
                        }
                        val styleFingerprint = buildWebReaderCssFingerprint(
                            chapterId = state.chapter.id,
                            paddingTop = paddingTop,
                            paddingBottom = paddingBottom,
                            paddingHorizontal = paddingHorizontal,
                            fontSizePx = state.readerSettings.fontSize,
                            lineHeightMultiplier = state.readerSettings.lineHeight,
                            textAlignCss = cssTextAlign,
                            firstLineIndentCss = cssFirstLineIndent,
                            textColorHex = colorToCssHex(textColor),
                            backgroundHex = colorToCssHex(textBackground),
                            backgroundTexture = state.readerSettings.backgroundTexture,
                            oledEdgeGradient = state.readerSettings.oledEdgeGradient && isDarkTheme,
                            fontFamilyName = selectedFontFamily,
                            customCss = state.readerSettings.customCSS,
                        )

                        val currentTextColorCss = colorToCssHex(textColor)
                        val currentBackgroundCss = colorToCssHex(textBackground)
                        val currentCustomCss = state.readerSettings.customCSS
                        val currentCustomJs = state.readerSettings.customJS
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
                            textAlignCss = cssTextAlign,
                            firstLineIndentCss = cssFirstLineIndent,
                            textColorHex = currentTextColorCss,
                            backgroundHex = currentBackgroundCss,
                            backgroundTexture = state.readerSettings.backgroundTexture,
                            oledEdgeGradient = state.readerSettings.oledEdgeGradient && isDarkTheme,
                            fontFamilyName = selectedFontFamily,
                            customCss = currentCustomCss,
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
                                    textAlignCss = cssTextAlign,
                                    firstLineIndentCss = cssFirstLineIndent,
                                    textColorHex = currentTextColorCss,
                                    backgroundHex = currentBackgroundCss,
                                    backgroundTexture = state.readerSettings.backgroundTexture,
                                    oledEdgeGradient = state.readerSettings.oledEdgeGradient && isDarkTheme,
                                    fontFamilyName = selectedFontFamily,
                                    customCss = currentCustomCss,
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
                                            val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                                ?: webProgressPercent
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
                                            view?.revealReaderDocumentAndWebView()
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
                                textAlignCss = cssTextAlign,
                                firstLineIndentCss = cssFirstLineIndent,
                                textColorHex = colorToCssHex(textColor),
                                backgroundHex = colorToCssHex(textBackground),
                                backgroundTexture = state.readerSettings.backgroundTexture,
                                oledEdgeGradient = state.readerSettings.oledEdgeGradient && isDarkTheme,
                                fontFamilyName = selectedFontFamily,
                                customCss = state.readerSettings.customCSS,
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
        if (
            shouldShowVerticalSeekbar(
                showReaderUi = showReaderUI,
                verticalSeekbarEnabled = state.readerSettings.verticalSeekbar,
                showWebView = showWebView,
                textBlocksCount = seekbarItemsCount,
            )
        ) {
            val seekbarValue by remember(
                showWebView,
                webProgressPercent,
                usePageReader,
                pagerState.currentPage,
                textListState.firstVisibleItemIndex,
                textListState.canScrollForward,
                seekbarItemsCount,
                readingProgressPercent,
            ) {
                derivedStateOf {
                    if (showWebView) {
                        webProgressPercent.coerceIn(0, 100) / 100f
                    } else if (!usePageReader) {
                        // For long paragraphs/index-based lists, index ratio can lag behind.
                        // Use effective reading progress so thumb reaches the real chapter end.
                        readingProgressPercent.coerceIn(0, 100) / 100f
                    } else {
                        val max = (seekbarItemsCount - 1).coerceAtLeast(1)
                        val current = pagerState.currentPage
                        current.toFloat() / max.toFloat()
                    }
                }
            }
            val (seekbarTopLabel, seekbarBottomLabel) = verticalSeekbarLabels(
                readingProgressPercent = readingProgressPercent,
                showScrollPercentage = state.readerSettings.showScrollPercentage,
            )
            Column(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterEnd)
                    .padding(end = MaterialTheme.padding.small)
                    .size(width = 30.dp, height = 270.dp),
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
                    topLabel = seekbarTopLabel,
                    bottomLabel = seekbarBottomLabel,
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
                            coroutineScope.launch {
                                if (usePageReader) {
                                    pagerState.scrollToPage(target)
                                } else {
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

        if (shouldShowPersistentProgressLine(showReaderUi = showReaderUI)) {
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
            visible = showReaderUI,
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
                                        showReaderUi = showReaderUI,
                                        autoScrollExpanded = autoScrollExpanded,
                                    )
                                    autoScrollEnabled = nextState.autoScrollEnabled
                                    showReaderUI = nextState.showReaderUi
                                    autoScrollExpanded = nextState.autoScrollExpanded
                                    updateAutoScrollPreferences(enabled = nextState.autoScrollEnabled)
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
                                    text = "Auto-scroll speed: $autoScrollSpeed",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    value = autoScrollSpeed.toFloat(),
                                    onValueChange = {
                                        val newSpeed = it.roundToInt().coerceIn(1, 100)
                                        autoScrollSpeed = newSpeed
                                        updateAutoScrollPreferences(
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
            visible = showReaderUI,
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
            "gemini-2.5-flash" to "Gemini 2.5 Flash",
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
            if (readerSettings.geminiModel == "gemini-3-flash") {
                "gemini-3-flash-preview"
            } else {
                readerSettings.geminiModel
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
                id = "nsfw_pulse",
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
                                    onStart()
                                }
                            },
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                listOf(
                                    NovelTranslationProvider.GEMINI to "Gemini",
                                    NovelTranslationProvider.OPENROUTER to "OpenRouter",
                                    NovelTranslationProvider.DEEPSEEK to "DeepSeek",
                                ),
                            ) { option ->
                                val selected = tempProvider == option.first
                                OutlinedButton(
                                    onClick = {
                                        tempProvider = option.first
                                        onSetTranslationProvider(option.first)
                                        onAddLog("?? Provider: ${option.second}")
                                        when (option.first) {
                                            NovelTranslationProvider.GEMINI -> Unit
                                            NovelTranslationProvider.OPENROUTER -> onRefreshOpenRouterModels()
                                            NovelTranslationProvider.DEEPSEEK -> onRefreshDeepSeekModels()
                                            NovelTranslationProvider.AIRFORCE -> Unit
                                        }
                                    },
                                ) {
                                    Text(if (selected) "• ${option.second}" else option.second)
                                }
                            }
                        }
                    }

                    if (page == 0) {
                        when (tempProvider) {
                            NovelTranslationProvider.GEMINI -> {
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
                    }

                    if (page == 0) {
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

                    if (
                        page == 1 &&
                        isGeminiSelected &&
                        (tempModel == "gemini-3-flash-preview" || tempModel == "gemini-3-pro-preview")
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

                    if (page == 1 && isGeminiSelected && tempModel == "gemini-2.5-flash") {
                        Text(
                            "Бюджет токенов (Gemini 2.5)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(-1, 2048, 4096, 8192, 16384)) { value ->
                                OutlinedButton(
                                    onClick = {
                                        tempBudget = value
                                        onSetGeminiBudgetTokens(value)
                                        onAddLog("?? Бюджет: ${if (value == -1) "AUTO" else value}")
                                    },
                                ) {
                                    val title = if (value == -1) "AUTO" else value.toString()
                                    Text(if (tempBudget == value) "• $title" else title)
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
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(if (showAdvanced) "Скрыть доп. настройки" else "Доп. настройки")
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
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    fontFamilyName: String?,
    customCss: String,
): String {
    val escapedFontFamily = fontFamilyName
        ?.replace("\\", "\\\\")
        ?.replace("'", "\\'")
    val fontVariable = escapedFontFamily?.let { "'$it', sans-serif" }.orEmpty()

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
        if (fontVariable.isNotBlank()) {
            append("  --an-reader-font: $fontVariable;\n")
        }
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
        if (fontVariable.isNotBlank()) {
            append("  font-family: var(--an-reader-font) !important;\n")
        }
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
        append(buildWebReaderAtmosphereCss(backgroundTexture, oledEdgeGradient))
        append(customCss)
    }
}

internal fun buildWebReaderAtmosphereCss(
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
): String {
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
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    fontFamilyName: String?,
    customCss: String,
) {
    val css = buildWebReaderCssText(
        fontFaceCss = fontFaceCss,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        paddingHorizontal = paddingHorizontal,
        fontSizePx = fontSizePx,
        lineHeightMultiplier = lineHeightMultiplier,
        textAlignCss = textAlignCss,
        firstLineIndentCss = firstLineIndentCss,
        textColorHex = textColorHex,
        backgroundHex = backgroundHex,
        backgroundTexture = backgroundTexture,
        oledEdgeGradient = oledEdgeGradient,
        fontFamilyName = fontFamilyName,
        customCss = customCss,
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
}

@Composable
private fun LnReaderVerticalSeekbar(
    progress: Float,
    topLabel: String?,
    bottomLabel: String?,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val trackColor = MaterialTheme.colorScheme.outline
    val progressColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)

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
                    onProgressChange(normalizeProgressFromY(offset.y))
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val trackTop = containerHeightPx * 0.125f
                    val trackHeight = (containerHeightPx * 0.75f).coerceAtLeast(1f)
                    val currentY = trackTop + (trackHeight * progress)
                    onProgressChange(normalizeProgressFromY(currentY + delta))
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
                    val trackWidth = 1.dp.toPx()
                    val thumbY = trackTop + ((trackBottom - trackTop) * progress.coerceIn(0f, 1f))
                    val thumbRadius = 4.dp.toPx()

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
                    drawCircle(
                        color = progressColor,
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
    oledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
) {
    val showParchmentGradient = backgroundTexture == NovelReaderBackgroundTexture.PARCHMENT
    val showOledVignette = oledEdgeGradient && isDarkTheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = brush),
            )
        }

        if (showParchmentGradient || showOledVignette) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (showParchmentGradient) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(size.width * 0.22f, size.height * 0.2f),
                            radius = max(size.width, size.height) * 0.62f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(size.width * 0.82f, size.height * 0.78f),
                            radius = max(size.width, size.height) * 0.56f,
                        ),
                    )
                }
                if (showOledVignette) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.36f),
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = max(size.width, size.height) * 0.8f,
                        ),
                    )
                }
            }
        }
    }
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
    spacing: NovelReaderParagraphSpacing,
): androidx.compose.ui.unit.Dp {
    return when (spacing) {
        NovelReaderParagraphSpacing.COMPACT -> 8.dp
        NovelReaderParagraphSpacing.NORMAL -> 12.dp
        NovelReaderParagraphSpacing.SPACIOUS -> 16.dp
    }
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
    textBlocksCount: Int,
): Boolean {
    return showReaderUi && verticalSeekbarEnabled && textBlocksCount > 1
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
    paginate: (String) -> List<String>,
): List<String> {
    val fallbackBlocks = textBlocks.takeIf { it.isNotEmpty() } ?: listOf("")
    if (!shouldPaginate) return fallbackBlocks

    val chapterText = textBlocks.joinToString("\n\n").trim()
    if (chapterText.isBlank()) return fallbackBlocks

    return paginate(chapterText).ifEmpty { fallbackBlocks }
}

internal fun buildRichPageReaderChapterAnnotatedText(
    richBlocks: List<NovelRichContentBlock>,
    forcedParagraphFirstLineIndentEm: Float? = null,
): AnnotatedString {
    if (richBlocks.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var appendedAny = false
        richBlocks.forEach { block ->
            val blockText: AnnotatedString = when (block) {
                is NovelRichContentBlock.Paragraph -> buildNovelRichAnnotatedString(block.segments)
                is NovelRichContentBlock.Heading -> buildNovelRichAnnotatedString(block.segments)
                is NovelRichContentBlock.BlockQuote -> buildNovelRichAnnotatedString(block.segments)
                NovelRichContentBlock.HorizontalRule -> AnnotatedString("* * *")
                is NovelRichContentBlock.Image -> AnnotatedString("")
            }
            if (blockText.text.isBlank()) return@forEach
            if (appendedAny) append("\n\n")
            val start = length
            append(blockText)
            val end = length
            if (block is NovelRichContentBlock.Paragraph) {
                val firstLineIndentEm = forcedParagraphFirstLineIndentEm ?: block.firstLineIndentEm
                firstLineIndentEm?.let { indentEm ->
                    addStyle(
                        style = ParagraphStyle(
                            textIndent = TextIndent(firstLine = indentEm.em),
                        ),
                        start = start,
                        end = end,
                    )
                }
            }
            appendedAny = true
        }
    }
}

internal fun shouldEnableJavaScriptInReaderWebView(
    @Suppress("UNUSED_PARAMETER")
    pluginRequestsJavaScript: Boolean,
): Boolean {
    return true
}

internal fun verticalSeekbarLabels(
    readingProgressPercent: Int,
    showScrollPercentage: Boolean,
): Pair<String?, String?> {
    if (!showScrollPercentage) return null to null
    val clamped = readingProgressPercent.coerceIn(0, 100)
    return clamped.toString() to "100"
}

internal fun buildWebReaderCssFingerprint(
    chapterId: Long,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    textAlignCss: String?,
    firstLineIndentCss: String?,
    textColorHex: String,
    backgroundHex: String,
    backgroundTexture: NovelReaderBackgroundTexture,
    oledEdgeGradient: Boolean,
    fontFamilyName: String?,
    customCss: String,
): String {
    return buildString {
        append(chapterId)
        append('|').append(paddingTop)
        append('|').append(paddingBottom)
        append('|').append(paddingHorizontal)
        append('|').append(fontSizePx)
        append('|').append(lineHeightMultiplier)
        append('|').append(textAlignCss ?: "<site>")
        append('|').append(firstLineIndentCss ?: "<site>")
        append('|').append(textColorHex)
        append('|').append(backgroundHex)
        append('|').append(backgroundTexture.name)
        append('|').append(oledEdgeGradient)
        append('|').append(fontFamilyName.orEmpty())
        append('|').append(customCss)
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
    fontSize: Int,
    lineHeight: Float,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
    paragraphSpacing: androidx.compose.ui.unit.Dp,
    textAlign: ReaderTextAlign,
    forceParagraphIndent: Boolean,
    preserveSourceTextAlignInNative: Boolean,
) {
    val context = LocalContext.current
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
                fontWeight = if (isChapterTitle) FontWeight.SemiBold else FontWeight.Normal,
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
                    fontWeight = FontWeight.SemiBold,
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

private fun TextStyle.withOptionalTextAlign(textAlign: TextAlign?): TextStyle {
    return if (textAlign == null) this else copy(textAlign = textAlign)
}

private fun TextStyle.withOptionalFirstLineIndentEm(firstLineIndentEm: Float?): TextStyle {
    return if (firstLineIndentEm == null) {
        this
    } else {
        copy(textIndent = TextIndent(firstLine = firstLineIndentEm.em))
    }
}

internal fun resolvePageReaderLayoutTextAlign(
    globalTextAlign: ReaderTextAlign,
    preserveSourceTextAlignInNative: Boolean,
): ReaderTextAlign {
    return if (preserveSourceTextAlignInNative) {
        // Page mode uses a flattened text layout, so keep layout stable and avoid forcing justify.
        ReaderTextAlign.LEFT
    } else if (globalTextAlign == ReaderTextAlign.SOURCE) {
        // Page mode always needs a deterministic alignment for layout and pagination.
        ReaderTextAlign.LEFT
    } else {
        globalTextAlign
    }
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
        null -> null
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

internal fun shouldHideSystemBars(
    fullScreenMode: Boolean,
    showReaderUi: Boolean,
): Boolean {
    return fullScreenMode && !showReaderUi
}

internal fun shouldRestoreSystemBarsOnDispose(
    @Suppress("UNUSED_PARAMETER")
    fullScreenMode: Boolean,
): Boolean {
    return true
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
        8 -> runCatching {
            val rgba = hex.toLong(16).toInt()
            val rr = (rgba shr 24) and 0xFF
            val gg = (rgba shr 16) and 0xFF
            val bb = (rgba shr 8) and 0xFF
            val aa = rgba and 0xFF
            val argb = (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
            Color(argb)
        }.getOrNull() ?: runCatching {
            Color(AndroidColor.parseColor(normalized))
        }.getOrNull()
        else -> runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
    }
}

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

    val layout = runCatching {
        val paint = TextPaint().apply {
            isAntiAlias = true
            this.textSize = textSizePx.coerceAtLeast(1f)
            this.typeface = typeface
        }
        StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(textAlign.toLayoutAlignment())
            .setIncludePad(false)
            .setLineSpacing(0f, lineHeightMultiplier.coerceAtLeast(1f))
            .build()
    }.getOrElse {
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
        val startOffset = layout.getLineStart(startLine)
        var endLineExclusive = startLine
        while (
            endLineExclusive < layout.lineCount &&
            layout.getLineBottom(endLineExclusive) - layout.getLineTop(startLine) <= safeHeight
        ) {
            endLineExclusive++
        }
        val endLine = if (endLineExclusive > startLine) endLineExclusive - 1 else startLine
        val endOffset = layout.getLineEnd(endLine).coerceIn(startOffset, text.length)
        trimTextRange(text, startOffset, endOffset)?.let { ranges += it }
        startLine = endLine + 1
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

private fun ReaderTextAlign.toLayoutAlignment(): Layout.Alignment {
    return when (this) {
        ReaderTextAlign.SOURCE -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        ReaderTextAlign.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

private fun toBionicText(text: String): AnnotatedString {
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
private fun SystemUIController(
    fullScreenMode: Boolean,
    keepScreenOn: Boolean,
    showReaderUi: Boolean,
    defaultLightStatusBars: Boolean,
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
            val restoredState = resolveReaderExitSystemBarsState(
                captured = capturedSystemBarsState.value,
                current = insetsController.captureReaderSystemBarsState(),
            )
            if (shouldRestoreSystemBarsOnDispose(fullScreenMode)) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            insetsController.restoreReaderSystemBarsState(restoredState)
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
            defaultLightStatusBars = defaultLightStatusBars,
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

internal data class ReaderSystemBarsState(
    val isLightStatusBars: Boolean,
    val isLightNavigationBars: Boolean,
    val systemBarsBehavior: Int,
)

internal fun resolveReaderExitSystemBarsState(
    captured: ReaderSystemBarsState?,
    current: ReaderSystemBarsState,
): ReaderSystemBarsState {
    return captured ?: current
}

internal fun resolveActiveReaderSystemBarsState(
    showReaderUi: Boolean,
    fullScreenMode: Boolean,
    base: ReaderSystemBarsState,
    defaultLightStatusBars: Boolean,
): ReaderSystemBarsState {
    if (showReaderUi) {
        return base.copy(
            isLightStatusBars = defaultLightStatusBars,
            isLightNavigationBars = defaultLightStatusBars,
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }
    if (!fullScreenMode) {
        return base.copy(
            isLightStatusBars = defaultLightStatusBars,
            isLightNavigationBars = defaultLightStatusBars,
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }
    return base.copy(
        isLightStatusBars = false, // When fullscreen, transient bars should have light icons
        isLightNavigationBars = false,
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
    )
}

private fun WindowInsetsControllerCompat.captureReaderSystemBarsState(): ReaderSystemBarsState {
    return ReaderSystemBarsState(
        isLightStatusBars = isAppearanceLightStatusBars,
        isLightNavigationBars = isAppearanceLightNavigationBars,
        systemBarsBehavior = systemBarsBehavior,
    )
}

private fun WindowInsetsControllerCompat.restoreReaderSystemBarsState(
    state: ReaderSystemBarsState,
) {
    isAppearanceLightStatusBars = state.isLightStatusBars
    isAppearanceLightNavigationBars = state.isLightNavigationBars
    systemBarsBehavior = state.systemBarsBehavior
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
