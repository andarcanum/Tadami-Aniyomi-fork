package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import tachiyomi.presentation.core.components.material.padding
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

@Composable
fun NovelReaderScreen(
    state: NovelReaderScreenModel.State.Success,
    onBack: () -> Unit,
    onReadingProgress: (currentIndex: Int, totalItems: Int, persistedProgress: Long?) -> Unit,
    onToggleBookmark: () -> Unit = {},
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
                contentBlocksCount = state.contentBlocks.size,
            ),
        )
    }
    val readerPreferences = remember { Injekt.get<NovelReaderPreferences>() }
    val sourceId = state.novel.source
    val hasSourceOverride = remember(sourceId) { readerPreferences.getSourceOverride(sourceId) != null }
    var pageViewportSize by remember(state.chapter.id) { mutableStateOf(IntSize.Zero) }
    var autoScrollEnabled by remember(state.chapter.id) { mutableStateOf(state.readerSettings.autoScroll) }
    var autoScrollSpeed by remember(state.chapter.id) {
        mutableIntStateOf(intervalToAutoScrollSpeed(state.readerSettings.autoScrollInterval))
    }
    var autoScrollExpanded by remember(state.chapter.id) { mutableStateOf(false) }
    var webViewInstance by remember(state.chapter.id) { mutableStateOf<WebView?>(null) }
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
    val timeText by produceState(initialValue = currentTimeString(context), context) {
        while (isActive) {
            value = currentTimeString(context)
            delay(60_000)
        }
    }
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
    val shouldPaginateForPageReader = state.readerSettings.pageReader &&
        scrollContentBlocks.none { it is NovelReaderScreenModel.ContentBlock.Image }
    val pageReaderBlocks: List<String> = remember(
        state.chapter.id,
        state.textBlocks,
        shouldPaginateForPageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
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
                textAlign = state.readerSettings.textAlign,
            )
        }
    }
    val usePageReader = shouldPaginateForPageReader && pageReaderBlocks.isNotEmpty()
    val pagerState = rememberPagerState(
        initialPage = state.lastSavedIndex.coerceIn(0, pageReaderBlocks.lastIndex.coerceAtLeast(0)),
        pageCount = { pageReaderBlocks.size.coerceAtLeast(1) },
    )
    val readingProgressPercent by remember(
        showWebView,
        webProgressPercent,
        scrollContentBlocks.size,
        pageReaderBlocks.size,
        pagerState.currentPage,
        textListState.firstVisibleItemIndex,
        usePageReader,
    ) {
        derivedStateOf {
            when {
                showWebView -> webProgressPercent
                usePageReader -> {
                    (((pagerState.currentPage + 1).toFloat() / pageReaderBlocks.size.toFloat()) * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
                }
                scrollContentBlocks.isEmpty() -> 0
                else -> {
                    (((textListState.firstVisibleItemIndex + 1).toFloat() / scrollContentBlocks.size.toFloat()) * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
                }
            }
        }
    }
    val showBottomInfoOverlay = shouldShowBottomInfoOverlay(
        showReaderUi = showReaderUI,
        showBatteryAndTime = state.readerSettings.showBatteryAndTime,
    )
    val minVerticalChapterSwipeDistancePx = with(density) { 120.dp.toPx() }
    val verticalChapterSwipeHorizontalTolerancePx = with(density) { 20.dp.toPx() }
    val minVerticalChapterSwipeHoldDurationMillis = 180L

    // Управление System UI для fullscreen режима
    SystemUIController(
        fullScreenMode = state.readerSettings.fullScreenMode,
        keepScreenOn = state.readerSettings.keepScreenOn,
        showReaderUi = showReaderUI,
    )

    // Volume Buttons Handler
    val coroutineScope = rememberCoroutineScope()
    suspend fun moveBackwardByReaderAction() {
        if (usePageReader) {
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
        if (usePageReader) {
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
        if (showWebView || showReaderUI) return true
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
        pageReaderBlocks.size,
        scrollContentBlocks.size,
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
        state.nextChapterId,
        state.readerSettings.swipeToNextChapter,
        pageReaderBlocks.size,
    ) {
        if (!autoScrollEnabled || showWebView) return@LaunchedEffect
        while (isActive && autoScrollEnabled) {
            if (showWebView) {
                delay(120)
                continue
            }
            if (showReaderUI) {
                delay(120)
                continue
            }
            if (usePageReader) {
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
                delay(16)
                val scrollOffset = autoScrollScrollStepPx(autoScrollSpeed)
                val consumed = textListState.scrollBy(scrollOffset)
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
        // Контент (текст главы) - занимает весь экран, padding ВНУТРИ через contentPadding
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!showWebView && scrollContentBlocks.isNotEmpty()) {
                // Отслеживание прогресса в зависимости от режима
                if (usePageReader) {
                    LaunchedEffect(pagerState.currentPage, pageReaderBlocks.size) {
                        onReadingProgress(
                            pagerState.currentPage,
                            pageReaderBlocks.size,
                            pagerState.currentPage.toLong(),
                        )
                    }
                    DisposableEffect(pagerState, pageReaderBlocks.size) {
                        onDispose {
                            onReadingProgress(
                                pagerState.currentPage,
                                pageReaderBlocks.size,
                                pagerState.currentPage.toLong(),
                            )
                        }
                    }
                } else {
                    LaunchedEffect(
                        textListState.firstVisibleItemIndex,
                        textListState.canScrollForward,
                        scrollContentBlocks.size,
                    ) {
                        val (progressIndex, progressTotal) = resolveNativeScrollProgressForTracking(
                            firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                            textBlocksCount = scrollContentBlocks.size,
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
                    DisposableEffect(textListState, textListState.canScrollForward, scrollContentBlocks.size) {
                        onDispose {
                            val (progressIndex, progressTotal) = resolveNativeScrollProgressForTracking(
                                firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                                textBlocksCount = scrollContentBlocks.size,
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
                            .background(textBackground)
                            .pointerInput(
                                state.readerSettings.swipeToPrevChapter,
                                state.readerSettings.swipeToNextChapter,
                                state.previousChapterId,
                                state.nextChapterId,
                            ) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val tapX = offset.x
                                        if (tapX > size.width * 0.3f && tapX < size.width * 0.7f) {
                                            showReaderUI = !showReaderUI
                                        } else {
                                            coroutineScope.launch {
                                                if (tapX <= size.width * 0.3f) {
                                                    moveBackwardByReaderAction()
                                                } else if (tapX >= size.width * 0.7f) {
                                                    moveForwardByReaderAction()
                                                }
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
                                text = if (state.readerSettings.bionicReading) {
                                    toBionicText(pageReaderBlocks[page])
                                } else {
                                    AnnotatedString(pageReaderBlocks[page])
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = textColor,
                                    fontSize = state.readerSettings.fontSize.sp,
                                    lineHeight = state.readerSettings.lineHeight.em,
                                    fontFamily = composeFontFamily,
                                    textAlign = when (state.readerSettings.textAlign) {
                                        ReaderTextAlign.LEFT -> TextAlign.Start
                                        ReaderTextAlign.CENTER -> TextAlign.Center
                                        ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                                        ReaderTextAlign.RIGHT -> TextAlign.End
                                    },
                                ),
                            )
                        }
                    }
                } else {
                    // Scroll Mode (режим прокрутки, по умолчанию)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(textBackground)
                            .pointerInput(
                                state.readerSettings.swipeToPrevChapter,
                                state.readerSettings.swipeToNextChapter,
                                state.previousChapterId,
                                state.nextChapterId,
                                scrollContentBlocks.size,
                            ) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        // Тап в центральной зоне (30-70% ширины) - переключить UI
                                        val tapX = offset.x
                                        if (tapX > size.width * 0.3f && tapX < size.width * 0.7f) {
                                            showReaderUI = !showReaderUI
                                        } else {
                                            coroutineScope.launch {
                                                if (tapX <= size.width * 0.3f) {
                                                    moveBackwardByReaderAction()
                                                } else if (tapX >= size.width * 0.7f) {
                                                    moveForwardByReaderAction()
                                                }
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
                        itemsIndexed(scrollContentBlocks) { index, block ->
                            when (block) {
                                is NovelReaderScreenModel.ContentBlock.Text -> {
                                    val isChapterTitle = index == 0 &&
                                        isNativeChapterTitleText(block.text, state.chapter.name)
                                    Text(
                                        text = if (state.readerSettings.bionicReading) {
                                            toBionicText(block.text)
                                        } else {
                                            AnnotatedString(block.text)
                                        },
                                        style = MaterialTheme.typography.bodyLarge.copy(
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
                                            fontFamily = composeFontFamily,
                                            fontWeight = if (isChapterTitle) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = when (state.readerSettings.textAlign) {
                                                ReaderTextAlign.LEFT -> TextAlign.Start
                                                ReaderTextAlign.CENTER -> TextAlign.Center
                                                ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                                                ReaderTextAlign.RIGHT -> TextAlign.End
                                            },
                                        ),
                                        modifier = Modifier.padding(
                                            top = if (index == 0) statusBarTopPadding else 0.dp,
                                            bottom = if (index == scrollContentBlocks.lastIndex) {
                                                0.dp
                                            } else if (isChapterTitle) {
                                                16.dp
                                            } else {
                                                12.dp
                                            },
                                        ),
                                    )
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
                                                bottom = if (index == scrollContentBlocks.lastIndex) 0.dp else 12.dp,
                                            ),
                                    )
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

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            setBackgroundColor(backgroundColor)
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
                            loadDataWithBaseURL(baseUrl, state.html, "text/html", "utf-8", null)
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
                        val gestureDetector = GestureDetector(
                            webView.context,
                            object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    val viewWidth = webView.width.takeIf { it > 0 } ?: return false
                                    val tapX = e.x
                                    if (tapX > viewWidth * 0.3f && tapX < viewWidth * 0.7f) {
                                        showReaderUI = !showReaderUI
                                    }
                                    return false
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
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (!showReaderUI) {
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
                            gestureDetector.onTouchEvent(event)
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
                            textColorHex = colorToCssHex(textColor),
                            backgroundHex = colorToCssHex(textBackground),
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
                        webView.webViewClient = object : WebViewClient() {
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
                                    textColorHex = currentTextColorCss,
                                    backgroundHex = currentBackgroundCss,
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
                                }
                            }
                        }

                        if (webView.tag != state.html) {
                            shouldRestoreWebScroll = true
                            appliedWebCssFingerprint = null
                            webView.loadDataWithBaseURL(baseUrl, state.html, "text/html", "utf-8", null)
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
                                textColorHex = colorToCssHex(textColor),
                                backgroundHex = colorToCssHex(textBackground),
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
                }
            }
        }

        val seekbarItemsCount = if (showWebView) {
            101
        } else if (usePageReader) {
            pageReaderBlocks.size
        } else {
            scrollContentBlocks.size
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
                seekbarItemsCount,
            ) {
                derivedStateOf {
                    if (showWebView) {
                        webProgressPercent.coerceIn(0, 100) / 100f
                    } else {
                        val max = (seekbarItemsCount - 1).coerceAtLeast(1)
                        val current = if (usePageReader) {
                            pagerState.currentPage
                        } else {
                            textListState.firstVisibleItemIndex
                        }
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

        // Top AppBar с учетом statusBarHeight
        AnimatedVisibility(
            visible = showReaderUI,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                        val newValue = !autoScrollEnabled
                                        autoScrollEnabled = newValue
                                        updateAutoScrollPreferences(enabled = newValue)
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
        }

        // Bottom navigation in LNReader-like style
        AnimatedVisibility(
            visible = showReaderUI,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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

private fun WebView.applyReaderCss(
    fontFaceCss: String,
    paddingTop: Int,
    paddingBottom: Int,
    paddingHorizontal: Int,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    textAlignCss: String?,
    textColorHex: String,
    backgroundHex: String,
    fontFamilyName: String?,
    customCss: String,
) {
    val escapedFontFamily = fontFamilyName
        ?.replace("\\", "\\\\")
        ?.replace("'", "\\'")
    val shouldForceFontFamily = escapedFontFamily != null
    val fontVariable = escapedFontFamily?.let { "'$it', sans-serif" }.orEmpty()

    val css = buildString {
        append(fontFaceCss)
        append('\n')
        append(":root {\n")
        append("  --an-reader-bg: $backgroundHex;\n")
        append("  --an-reader-fg: $textColorHex;\n")
        if (!textAlignCss.isNullOrBlank()) {
            append("  --an-reader-align: $textAlignCss;\n")
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
        append(customCss)
    }
    val quotedCss = JSONObject.quote(css)
    val fontFlag = if (shouldForceFontFamily) "true" else "false"
    val alignFlag = if (textAlignCss.isNullOrBlank()) "false" else "true"
    evaluateJavascript(
        """
        (function() {
            const styleId = '__an_reader_style__';
            let style = document.getElementById(styleId);
            if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
            }
            style.textContent = $quotedCss;

            const shouldForceFont = $fontFlag;
            const shouldForceAlign = $alignFlag;
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

internal fun shouldShowBottomInfoOverlay(
    showReaderUi: Boolean,
    showBatteryAndTime: Boolean,
): Boolean {
    return showReaderUi && showBatteryAndTime
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
    contentBlocksCount: Int,
): Boolean {
    return preferWebViewRenderer || contentBlocksCount <= 0
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
    textColorHex: String,
    backgroundHex: String,
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
        append('|').append(textColorHex)
        append('|').append(backgroundHex)
        append('|').append(fontFamilyName.orEmpty())
        append('|').append(customCss)
    }
}

internal enum class VerticalChapterSwipeAction {
    NONE,
    NEXT,
    PREVIOUS,
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
        ReaderTextAlign.LEFT -> null
        ReaderTextAlign.CENTER -> "center"
        ReaderTextAlign.JUSTIFY -> "justify"
        ReaderTextAlign.RIGHT -> "right"
    }
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
    return produceState(initialValue = readBatteryLevel(context), context) {
        while (isActive) {
            value = readBatteryLevel(context)
            delay(60_000)
        }
    }
}

private fun readBatteryLevel(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val directLevel = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    if (directLevel in 0..100) return directLevel

    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    if (level >= 0 && scale > 0) {
        return ((level * 100f) / scale.toFloat()).roundToInt().coerceIn(0, 100)
    }
    return 100
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

internal fun paginateTextIntoPages(
    text: String,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Float,
    lineHeightMultiplier: Float,
    typeface: android.graphics.Typeface?,
    textAlign: ReaderTextAlign,
): List<String> {
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
        return text
            .chunked(chunkSize)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    if (layout.lineCount <= 0) return listOf(text)

    val pages = mutableListOf<String>()
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
        val endLine = if (endLineExclusive > startLine) {
            endLineExclusive - 1
        } else {
            startLine
        }
        val endOffset = layout.getLineEnd(endLine).coerceIn(startOffset, text.length)
        val pageText = text.substring(startOffset, endOffset).trim()
        if (pageText.isNotBlank()) {
            pages += pageText
        }
        startLine = endLine + 1
    }

    return if (pages.isNotEmpty()) pages else listOf(text)
}

private fun ReaderTextAlign.toLayoutAlignment(): Layout.Alignment {
    return when (this) {
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
) {
    val view = LocalView.current
    DisposableEffect(view, fullScreenMode, showReaderUi) {
        onDispose {
            val activity = view.context.findActivity() ?: return@onDispose
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (shouldRestoreSystemBarsOnDispose(fullScreenMode)) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(fullScreenMode, keepScreenOn, showReaderUi) {
        val activity = view.context.findActivity() ?: return@LaunchedEffect
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)

        // Keep Screen On
        if (keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Fullscreen Mode
        if (shouldHideSystemBars(fullScreenMode = fullScreenMode, showReaderUi = showReaderUi)) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
