package eu.kanade.presentation.reader.novel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.PageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelBookFlipAnimationSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

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

internal fun shouldPaginateForPageReader(
    pageReaderEnabled: Boolean,
    contentBlocksCount: Int,
): Boolean {
    return pageReaderEnabled && contentBlocksCount > 0
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
    chapterHandoffTarget: NovelReaderPageReaderHandoffTarget = NovelReaderPageReaderHandoffTarget.SAVED,
): Int {
    val safePageCount = pageCount.coerceAtLeast(1)
    val lastPageIndex = safePageCount - 1
    when (chapterHandoffTarget) {
        NovelReaderPageReaderHandoffTarget.START -> return 0
        NovelReaderPageReaderHandoffTarget.END -> return lastPageIndex
        NovelReaderPageReaderHandoffTarget.SAVED -> Unit
    }
    if (!shouldRestoreSavedPageReaderProgress(chapterHandoffTarget)) return 0
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
    return richContentBlocks.isNotEmpty()
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
        NovelPageTransitionStyle.BOOK_FLIP,
        -> NovelPageTransitionEngine.COMPOSE_PAGER
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> NovelPageTransitionEngine.PAGE_TURN_RENDERER
    }
}

internal fun shouldUseComposePagerBoundaryPreview(
    style: NovelPageTransitionStyle,
): Boolean {
    return when (style) {
        NovelPageTransitionStyle.SLIDE,
        NovelPageTransitionStyle.DEPTH,
        NovelPageTransitionStyle.BOOK_FLIP,
        -> true
        NovelPageTransitionStyle.INSTANT,
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> false
    }
}

internal fun resolveComposePagerVirtualPageCount(
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): Int {
    return contentPageCount.coerceAtLeast(1) +
        (if (hasPreviousChapter) 1 else 0) +
        (if (hasNextChapter) 1 else 0)
}

internal fun resolveComposePagerVirtualPageIndex(
    actualPageIndex: Int,
    hasPreviousChapter: Boolean,
): Int {
    return actualPageIndex.coerceAtLeast(0) + if (hasPreviousChapter) 1 else 0
}

internal fun resolveComposePagerActualPageIndex(
    currentPage: Int,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
): Int {
    val safeContentPageCount = contentPageCount.coerceAtLeast(1)
    val offset = if (hasPreviousChapter) 1 else 0
    return (currentPage - offset).coerceIn(0, safeContentPageCount - 1)
}

internal fun resolveComposePagerBoundaryChapterTarget(
    currentPage: Int,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): HorizontalChapterSwipeAction {
    val virtualPageCount = resolveComposePagerVirtualPageCount(
        contentPageCount = contentPageCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    )
    return when {
        hasPreviousChapter && currentPage <= 0 -> HorizontalChapterSwipeAction.PREVIOUS
        hasNextChapter && currentPage >= virtualPageCount - 1 -> HorizontalChapterSwipeAction.NEXT
        else -> HorizontalChapterSwipeAction.NONE
    }
}

internal fun resolveComposePagerSettledBoundaryChapterTarget(
    currentPage: Int,
    progress: Float,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): HorizontalChapterSwipeAction {
    val boundaryTarget = resolveComposePagerBoundaryChapterTarget(
        currentPage = currentPage,
        contentPageCount = contentPageCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    )
    return if (boundaryTarget != HorizontalChapterSwipeAction.NONE && abs(progress) <= 0.001f) {
        boundaryTarget
    } else {
        HorizontalChapterSwipeAction.NONE
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

internal fun shouldShowNovelAtmosphereBackground(
    usePageReader: Boolean,
    activePageTransitionStyle: NovelPageTransitionStyle,
): Boolean {
    return !usePageReader || activePageTransitionStyle != NovelPageTransitionStyle.BOOK_FLIP
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
    composePagerContentPageCount: Int,
    composePagerHasPreviousChapter: Boolean,
): Int {
    return when (pageReaderRendererRoute) {
        NovelPageReaderRendererRoute.PAGE_TURN_RENDERER ->
            resolvePageTurnRendererProgressPageIndex(pageTurnCurrentPage)
        NovelPageReaderRendererRoute.COMPOSE_PAGER ->
            resolveComposePagerActualPageIndex(
                currentPage = pagerCurrentPage,
                contentPageCount = composePagerContentPageCount,
                hasPreviousChapter = composePagerHasPreviousChapter,
            )
        null -> pagerCurrentPage.coerceAtLeast(0)
    }
}

internal fun resolveReaderVerticalSeekbarValue(
    showWebView: Boolean,
    webProgressPercent: Int,
    usePageReader: Boolean,
    pageReaderRendererRoute: NovelPageReaderRendererRoute?,
    pagerCurrentPage: Int,
    pageTurnCurrentPage: Int,
    composePagerContentPageCount: Int,
    composePagerHasPreviousChapter: Boolean,
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
                composePagerContentPageCount = composePagerContentPageCount,
                composePagerHasPreviousChapter = composePagerHasPreviousChapter,
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

private const val BOOK_FLIP_EDGE_TAP_ANIMATION_DURATION_SLOW_MILLIS = 1500
private const val BOOK_FLIP_EDGE_TAP_ANIMATION_DURATION_NORMAL_MILLIS = 1000
private const val BOOK_FLIP_EDGE_TAP_ANIMATION_DURATION_FAST_MILLIS = 500

internal fun resolveBookFlipPageAnimationDurationMillis(
    transitionStyle: NovelPageTransitionStyle,
    animationSpeed: NovelBookFlipAnimationSpeed,
): Int? {
    if (transitionStyle != NovelPageTransitionStyle.BOOK_FLIP) return null
    return when (animationSpeed) {
        NovelBookFlipAnimationSpeed.SLOW -> BOOK_FLIP_EDGE_TAP_ANIMATION_DURATION_SLOW_MILLIS
        NovelBookFlipAnimationSpeed.NORMAL -> BOOK_FLIP_EDGE_TAP_ANIMATION_DURATION_NORMAL_MILLIS
        NovelBookFlipAnimationSpeed.FAST -> BOOK_FLIP_EDGE_TAP_ANIMATION_DURATION_FAST_MILLIS
    }
}

internal fun resolveBookFlipEdgeTapAnimationDurationMillis(
    transitionStyle: NovelPageTransitionStyle,
    animationSpeed: NovelBookFlipAnimationSpeed,
    tapToScrollEnabled: Boolean,
): Int? {
    if (!tapToScrollEnabled) return null
    return resolveBookFlipPageAnimationDurationMillis(
        transitionStyle = transitionStyle,
        animationSpeed = animationSpeed,
    )
}
