package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

internal enum class BookFlipHalf {
    LEFT,
    RIGHT,
}

internal data class BookFlipSplitSpec(
    val active: Boolean = false,
    val visible: Boolean = true,
    val zIndex: Float = 0f,
    val staticHalf: BookFlipHalf = BookFlipHalf.LEFT,
    val staticShadowAlpha: Float = 0f,
    val flapVisible: Boolean = false,
    val flapHalf: BookFlipHalf = BookFlipHalf.RIGHT,
    val flapRotationY: Float = 0f,
    val flapShadowAlpha: Float = 0f,
)

internal fun resolveBookFlipSplitSpec(pageOffset: Float): BookFlipSplitSpec {
    val absOffset = abs(pageOffset)
    if (absOffset < 0.001f) {
        return BookFlipSplitSpec(active = false, visible = true, zIndex = 2f)
    }
    if (absOffset >= 1f) {
        return BookFlipSplitSpec(active = true, visible = false, zIndex = -1f)
    }
    val isLeftOwner = pageOffset > 0f
    val staticHalf = if (isLeftOwner) BookFlipHalf.LEFT else BookFlipHalf.RIGHT
    val flapHalf = if (isLeftOwner) BookFlipHalf.RIGHT else BookFlipHalf.LEFT
    val hasFlap = absOffset < 0.5f
    val staticShadowAlpha = if (hasFlap) {
        0f
    } else {
        ((absOffset - 0.5f) * 2f * 0.24f).coerceIn(0f, 0.24f)
    }
    val flapShadowAlpha = if (hasFlap) {
        (absOffset * 2f * 0.22f).coerceIn(0f, 0.22f)
    } else {
        0f
    }
    return BookFlipSplitSpec(
        active = true,
        visible = true,
        zIndex = if (hasFlap) 2f else 0f,
        staticHalf = staticHalf,
        staticShadowAlpha = staticShadowAlpha,
        flapVisible = hasFlap,
        flapHalf = flapHalf,
        flapRotationY = if (hasFlap) -180f * pageOffset else 0f,
        flapShadowAlpha = flapShadowAlpha,
    )
}

private fun DrawScope.drawBookFlipHalfFromLayer(
    pageLayer: GraphicsLayer,
    half: BookFlipHalf,
    shadowAlpha: Float,
    spineShadowWidthPx: Float,
) {
    val halfWidth = size.width * 0.5f
    val isLeft = half == BookFlipHalf.LEFT
    val left = if (isLeft) 0f else halfWidth
    val right = if (isLeft) halfWidth else size.width
    clipRect(left = left, top = 0f, right = right, bottom = size.height) {
        drawLayer(pageLayer)
        if (shadowAlpha > 0f) {
            drawRect(Color.Black.copy(alpha = shadowAlpha))
        }
        val creaseStartX = if (isLeft) {
            (halfWidth - spineShadowWidthPx).coerceAtLeast(0f)
        } else {
            halfWidth
        }
        val creaseEndX = if (isLeft) {
            halfWidth
        } else {
            (halfWidth + spineShadowWidthPx).coerceAtMost(size.width)
        }
        val creaseBrush = if (isLeft) {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                startX = creaseStartX,
                endX = creaseEndX,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent),
                startX = creaseStartX,
                endX = creaseEndX,
            )
        }
        drawRect(brush = creaseBrush)
    }
}

internal data class ComposePagerTransitionSpec(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
    val rotationY: Float = 0f,
    val pivotXFraction: Float = 0.5f,
    val cameraDistance: Float = 8f,
    val shadowAlpha: Float = 0f,
    val zIndex: Float = 0f,
    val cancelPagerMotion: Boolean = false,
    val hideOffscreenPages: Boolean = false,
)

internal fun resolveComposePagerTransitionSpec(
    style: NovelPageTransitionStyle,
    pageOffset: Float,
): ComposePagerTransitionSpec {
    val clampedAbsOffset = abs(pageOffset).coerceIn(0f, 1f)
    return when (style) {
        NovelPageTransitionStyle.INSTANT -> ComposePagerTransitionSpec(
            cancelPagerMotion = true,
            hideOffscreenPages = true,
        )
        NovelPageTransitionStyle.SLIDE -> ComposePagerTransitionSpec()
        NovelPageTransitionStyle.DEPTH -> ComposePagerTransitionSpec(
            alpha = (1f - (clampedAbsOffset * 0.35f)).coerceIn(0.65f, 1f),
            scale = (1f - (clampedAbsOffset * 0.08f)).coerceIn(0.92f, 1f),
            translationXFraction = (-pageOffset * 0.12f).coerceIn(-0.12f, 0.12f),
        )
        NovelPageTransitionStyle.BOOK -> {
            if (pageOffset > 0f && pageOffset <= 1f) {
                ComposePagerTransitionSpec(
                    rotationY = -180f * pageOffset,
                    pivotXFraction = 0f,
                    cameraDistance = 15f,
                    cancelPagerMotion = true,
                    shadowAlpha = (abs(0.5f - pageOffset) * -0.6f + 0.3f).coerceIn(0f, 0.3f),
                    zIndex = 1f - pageOffset,
                )
            } else if (pageOffset <= 0f && pageOffset >= -1f) {
                ComposePagerTransitionSpec(
                    cancelPagerMotion = true,
                    zIndex = 0f,
                )
            } else {
                ComposePagerTransitionSpec(
                    hideOffscreenPages = true,
                )
            }
        }
        NovelPageTransitionStyle.CURL -> {
            if (pageOffset > 0f && pageOffset <= 1f) {
                ComposePagerTransitionSpec(
                    rotationY = -180f * pageOffset,
                    pivotXFraction = 1f,
                    cameraDistance = 15f,
                    cancelPagerMotion = true,
                    shadowAlpha = (abs(0.5f - pageOffset) * -0.6f + 0.3f).coerceIn(0f, 0.3f),
                    zIndex = 1f - pageOffset,
                )
            } else if (pageOffset <= 0f && pageOffset >= -1f) {
                ComposePagerTransitionSpec(
                    cancelPagerMotion = true,
                    zIndex = 0f,
                )
            } else {
                ComposePagerTransitionSpec(
                    hideOffscreenPages = true,
                )
            }
        }
        NovelPageTransitionStyle.BOOK_FLIP -> {
            if (abs(pageOffset) < 0.5f) {
                // Symmetrical centered 3D flip across [-90deg, +90deg]: outgoing flips to edge-on at 0.5,
                // incoming unfolds from edge-on over [-0.5, 0], avoiding blank backface occlusion.
                ComposePagerTransitionSpec(
                    rotationY = -180f * pageOffset,
                    pivotXFraction = 0.5f,
                    cameraDistance = 15f,
                    cancelPagerMotion = true,
                    shadowAlpha = (abs(pageOffset) * 0.6f).coerceIn(0f, 0.3f),
                    zIndex = 1f - abs(pageOffset),
                )
            } else {
                ComposePagerTransitionSpec(
                    alpha = 0f,
                    cancelPagerMotion = true,
                    hideOffscreenPages = true,
                    zIndex = 0f,
                )
            }
        }
    }
}

internal fun resolvePageReaderBoundaryChapterSwipeAction(
    currentPage: Int,
    pageCount: Int,
    deltaX: Float,
    deltaY: Float,
    thresholdPx: Float,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): HorizontalChapterSwipeAction {
    if (pageCount <= 0) return HorizontalChapterSwipeAction.NONE
    if (abs(deltaX) <= abs(deltaY)) return HorizontalChapterSwipeAction.NONE
    val isAtFirstPage = currentPage <= 0
    val isAtLastPage = currentPage >= pageCount - 1
    return when {
        isAtFirstPage && deltaX > thresholdPx && hasPreviousChapter ->
            HorizontalChapterSwipeAction.PREVIOUS
        isAtLastPage && deltaX < -thresholdPx && hasNextChapter ->
            HorizontalChapterSwipeAction.NEXT
        else -> HorizontalChapterSwipeAction.NONE
    }
}

private fun resolveComposePagerPageKey(
    page: Int,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    useBoundaryPreview: Boolean,
): Any {
    if (!useBoundaryPreview) return "compose-content-$page"
    val virtualPageCount = resolveComposePagerVirtualPageCount(
        contentPageCount = contentPageCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    )
    return when {
        hasPreviousChapter && page == 0 -> "compose-boundary-previous"
        hasNextChapter && page == virtualPageCount - 1 -> "compose-boundary-next"
        else ->
            "compose-content-${
                resolveComposePagerActualPageIndex(
                    currentPage = page,
                    contentPageCount = contentPageCount,
                    hasPreviousChapter = hasPreviousChapter,
                )
            }"
    }
}

@Composable
internal fun ComposePagerPageRenderer(
    pagerState: PagerState,
    contentPages: List<NovelPageContentPage>,
    chapterId: Long,
    // How many content pages one pager slot shows side by side. 1 is the ordinary single-page
    // reader; 2 is a landscape two-page spread. contentPageCount below (and therefore the pager's
    // own slot count) is expressed in slots, not raw content pages, so a slot always addresses
    // pages [slot * spreadColumns, slot * spreadColumns + spreadColumns - 1].
    spreadColumns: Int = 1,
    transitionStyle: NovelPageTransitionStyle,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    chapterTitleTextColor: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    backgroundImageModel: Any?,
    activeOledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    // Horizontal (Dp) reserved on the short edges of a spread so text stays clear of a display
    // cutout. Non-zero only when two-page landscape expands to the screen edges.
    spreadCutoutLeftDp: Dp = 0.dp,
    spreadCutoutRightDp: Dp = 0.dp,
    ttsHighlightState: NovelReaderTtsHighlightState? = null,
    ttsHighlightColor: Color = Color.Transparent,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    previousChapterName: String?,
    nextChapterName: String?,
    previousChapterLabel: String,
    nextChapterLabel: String,
    boundaryChapterHint: String,
    // When false, the pager never renders the intermediate "next/previous chapter" placeholder page:
    // the edge page shows real chapter content and the chapter switch happens directly.
    showBoundaryChapterPages: Boolean = true,
    suppressBoundaryChapterNavigation: Boolean = false,
    onToggleUi: () -> Unit,
    onMoveBackward: () -> Unit,
    onMoveForward: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    onTextTap: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> onToggleUi() },
    onImageClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    // Chapter neighbour availability drives two different things: the extra boundary placeholder
    // page (pager geometry) and whether an edge swipe may switch chapters (navigation). When the
    // placeholder pages are disabled the extra slots have to disappear, otherwise the edge page
    // renders clamped content and the last page of the chapter shows up twice.
    val hasPreviousChapterNavigation = hasPreviousChapter
    val hasNextChapterNavigation = hasNextChapter

    @Suppress("NAME_SHADOWING")
    val hasPreviousChapter = hasPreviousChapter && showBoundaryChapterPages

    @Suppress("NAME_SHADOWING")
    val hasNextChapter = hasNextChapter && showBoundaryChapterPages
    val useBoundaryPreview = shouldUseComposePagerBoundaryPreview(transitionStyle) && showBoundaryChapterPages
    // The pager's own index space is expressed in slots (see spreadColumns above), so every
    // boundary/key computation below has to work against the slot count, not the raw page count.
    val contentPageCount = resolveSpreadSlotCount(contentPages.size, spreadColumns)
    val latestToggleUi by rememberUpdatedState(onToggleUi)
    val latestMoveBackward by rememberUpdatedState(onMoveBackward)
    val latestMoveForward by rememberUpdatedState(onMoveForward)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestPreviousChapterName by rememberUpdatedState(previousChapterName)
    val latestNextChapterName by rememberUpdatedState(nextChapterName)
    val latestPreviousChapterLabel by rememberUpdatedState(previousChapterLabel)
    val latestNextChapterLabel by rememberUpdatedState(nextChapterLabel)
    val latestBoundaryChapterHint by rememberUpdatedState(boundaryChapterHint)
    val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapter)
    val latestHasNextChapter by rememberUpdatedState(hasNextChapter)
    val latestSuppressBoundaryNavigation by rememberUpdatedState(suppressBoundaryChapterNavigation)
    val edgeSwipeThresholdPx = with(density) { 160.dp.toPx() }

    val boundarySwipeModifier = if (useBoundaryPreview) {
        Modifier
    } else {
        Modifier.pointerInput(
            pagerState,
            contentPageCount,
            edgeSwipeThresholdPx,
            hasPreviousChapterNavigation,
            hasNextChapterNavigation,
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pageAtGestureStart = pagerState.currentPage
                var currentPosition = down.position

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    currentPosition = change.position
                    if (!change.pressed) break
                }

                when (
                    resolvePageReaderBoundaryChapterSwipeAction(
                        currentPage = pageAtGestureStart,
                        pageCount = contentPageCount,
                        deltaX = currentPosition.x - down.position.x,
                        deltaY = currentPosition.y - down.position.y,
                        thresholdPx = edgeSwipeThresholdPx,
                        hasPreviousChapter = hasPreviousChapterNavigation,
                        hasNextChapter = hasNextChapterNavigation,
                    )
                ) {
                    HorizontalChapterSwipeAction.PREVIOUS -> latestOpenPreviousChapter()
                    HorizontalChapterSwipeAction.NEXT -> latestOpenNextChapter()
                    HorizontalChapterSwipeAction.NONE -> Unit
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) {
                    clearFocusedNovelReaderTextView(hostView)
                }
            }
    }

    if (useBoundaryPreview) {
        LaunchedEffect(
            pagerState,
            contentPageCount,
            hasPreviousChapter,
            hasNextChapter,
            transitionStyle,
        ) {
            snapshotFlow {
                Triple(
                    pagerState.currentPage,
                    pagerState.currentPageOffsetFraction,
                    pagerState.isScrollInProgress,
                )
            }
                .distinctUntilChanged()
                .collectLatest { (page, progress, isScrolling) ->
                    if (isScrolling || latestSuppressBoundaryNavigation) return@collectLatest
                    when (
                        resolveComposePagerSettledBoundaryChapterTarget(
                            currentPage = page,
                            progress = progress,
                            contentPageCount = contentPageCount,
                            hasPreviousChapter = latestHasPreviousChapter,
                            hasNextChapter = latestHasNextChapter,
                        )
                    ) {
                        HorizontalChapterSwipeAction.PREVIOUS -> latestOpenPreviousChapter()
                        HorizontalChapterSwipeAction.NEXT -> latestOpenNextChapter()
                        HorizontalChapterSwipeAction.NONE -> Unit
                    }
                }
        }
    }

    HorizontalPager(
        state = pagerState,
        key = { page ->
            resolveComposePagerPageKey(
                page = page,
                contentPageCount = contentPageCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                useBoundaryPreview = useBoundaryPreview,
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .then(boundarySwipeModifier),
    ) { page ->
        val boundaryTarget = if (useBoundaryPreview) {
            resolveComposePagerBoundaryChapterTarget(
                currentPage = page,
                contentPageCount = contentPageCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
            )
        } else {
            HorizontalChapterSwipeAction.NONE
        }
        val boundaryPreview = if (!showBoundaryChapterPages) {
            null
        } else {
            when (boundaryTarget) {
                HorizontalChapterSwipeAction.PREVIOUS -> createNovelPageBoundaryPreviewData(
                    chapterLabel = latestPreviousChapterLabel,
                    chapterName = latestPreviousChapterName,
                    chapterHint = latestBoundaryChapterHint,
                )
                HorizontalChapterSwipeAction.NEXT -> createNovelPageBoundaryPreviewData(
                    chapterLabel = latestNextChapterLabel,
                    chapterName = latestNextChapterName,
                    chapterHint = latestBoundaryChapterHint,
                )
                HorizontalChapterSwipeAction.NONE -> null
            }
        }
        val spreadFirstPage = if (boundaryPreview == null) {
            val spreadSlot = resolveComposePagerActualPageIndex(
                currentPage = page,
                contentPageCount = contentPageCount,
                hasPreviousChapter = hasPreviousChapter,
            )
            resolveSpreadSlotFirstPageIndex(spreadSlot, spreadColumns)
        } else {
            -1
        }
        val spreadPages = if (boundaryPreview == null) {
            (spreadFirstPage until spreadFirstPage + spreadColumns).map { index ->
                contentPages.getOrElse(index) { NovelPageContentPage(emptyList()) }
            }
        } else {
            listOf(NovelPageContentPage(emptyList()))
        }
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        val isSplitBookFlipMode = transitionStyle == NovelPageTransitionStyle.BOOK_FLIP
        val splitSpec = if (isSplitBookFlipMode) resolveBookFlipSplitSpec(pageOffset) else null
        val pageLayer = if (isSplitBookFlipMode) rememberGraphicsLayer() else null
        val isPageSettledCurrent = page == pagerState.currentPage && abs(pageOffset) < 0.001f
        val transitionSpec = if (splitSpec != null) {
            ComposePagerTransitionSpec(
                alpha = if (splitSpec.visible) 1f else 0f,
                cancelPagerMotion = true,
                zIndex = splitSpec.zIndex,
            )
        } else {
            resolveComposePagerTransitionSpec(
                style = transitionStyle,
                pageOffset = pageOffset,
            )
        }
        val spineShadowWidthPx = with(LocalDensity.current) { 14.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(transitionSpec.zIndex)
                .graphicsLayer {
                    val densityLayer = this
                    alpha = if (transitionSpec.hideOffscreenPages && abs(pageOffset) > 0.5f) {
                        0f
                    } else {
                        transitionSpec.alpha
                    }
                    scaleX = transitionSpec.scale
                    scaleY = transitionSpec.scale
                    rotationY = transitionSpec.rotationY
                    cameraDistance = transitionSpec.cameraDistance * densityLayer.density
                    transformOrigin = TransformOrigin(transitionSpec.pivotXFraction, 0.5f)
                    translationX = size.width * if (transitionSpec.cancelPagerMotion) {
                        pageOffset
                    } else {
                        transitionSpec.translationXFraction
                    }
                },
        ) {
            val baseLayerModifier = if (splitSpec != null && splitSpec.active && pageLayer != null) {
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        pageLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        if (splitSpec.visible) {
                            drawBookFlipHalfFromLayer(
                                pageLayer = pageLayer,
                                half = splitSpec.staticHalf,
                                shadowAlpha = splitSpec.staticShadowAlpha,
                                spineShadowWidthPx = spineShadowWidthPx,
                            )
                        }
                    }
            } else {
                Modifier.fillMaxSize()
            }
            Box(modifier = baseLayerModifier) {
                NovelAtmosphereBackground(
                    backgroundColor = textBackground,
                    backgroundTexture = backgroundTexture,
                    nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                    oledEdgeGradient = activeOledEdgeGradient,
                    isDarkTheme = isDarkTheme,
                    pageEdgeShadow = pageEdgeShadow,
                    pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                    backgroundImageModel = backgroundImageModel,
                )

                if (boundaryPreview != null) {
                    NovelPageBoundaryPreviewContent(
                        preview = boundaryPreview,
                        textColor = textColor,
                        chapterTitleTextColor = chapterTitleTextColor,
                        textBackground = textBackground,
                        contentPadding = contentPadding,
                        statusBarTopPadding = statusBarTopPadding,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                    )
                } else if (spreadPages.size <= 1) {
                    NovelPageReaderPageContent(
                        contentPage = spreadPages.first(),
                        readerSettings = readerSettings,
                        textColor = textColor,
                        textBackground = textBackground,
                        backgroundTexture = backgroundTexture,
                        nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        chapterTitleTextColor = chapterTitleTextColor,
                        textShadowEnabled = readerSettings.textShadow,
                        textShadowColor = readerSettings.textShadowColor,
                        textShadowBlur = readerSettings.textShadowBlur,
                        textShadowX = readerSettings.textShadowX,
                        textShadowY = readerSettings.textShadowY,
                        contentPadding = contentPadding,
                        statusBarTopPadding = statusBarTopPadding,
                        ttsHighlightState = ttsHighlightState,
                        ttsHighlightColor = ttsHighlightColor,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        selectionAnchorChapterId = chapterId,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        onPlainTap = onTextTap,
                        onImageClick = onImageClick,
                        onImageLongClick = onImageLongClick,
                        onUrlClick = onUrlClick,
                        touchHandlingEnabled = !isSplitBookFlipMode || isPageSettledCurrent,
                        pageNumber = if (spreadFirstPage in contentPages.indices) spreadFirstPage + 1 else null,
                        totalPages = contentPages.size,
                        modifier = Modifier.absolutePadding(left = spreadCutoutLeftDp, right = spreadCutoutRightDp),
                    )
                } else {
                    // Two columns of one spread. Each gets a shared reader-content composable inside
                    // its own half-width slot; the pagination step (NovelReaderContentHost) already
                    // measured text against that half width, so no extra layout math is needed here.
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .absolutePadding(left = spreadCutoutLeftDp, right = spreadCutoutRightDp),
                    ) {
                        spreadPages.forEachIndexed { columnOffset, spreadPage ->
                            val pageIdx = spreadFirstPage + columnOffset
                            NovelPageReaderPageContent(
                                contentPage = spreadPage,
                                readerSettings = readerSettings,
                                textColor = textColor,
                                textBackground = textBackground,
                                backgroundTexture = backgroundTexture,
                                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                                textTypeface = textTypeface,
                                chapterTitleTypeface = chapterTitleTypeface,
                                chapterTitleTextColor = chapterTitleTextColor,
                                textShadowEnabled = readerSettings.textShadow,
                                textShadowColor = readerSettings.textShadowColor,
                                textShadowBlur = readerSettings.textShadowBlur,
                                textShadowX = readerSettings.textShadowX,
                                textShadowY = readerSettings.textShadowY,
                                contentPadding = contentPadding,
                                // Compact spread: landscape has no status bar on the text, so skip its
                                // top inset to win back vertical space.
                                statusBarTopPadding = if (spreadPages.size > 1) 0.dp else statusBarTopPadding,
                                ttsHighlightState = ttsHighlightState,
                                ttsHighlightColor = ttsHighlightColor,
                                selectionSessionIdProvider = selectionSessionIdProvider,
                                selectionAnchorChapterId = chapterId,
                                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                onPlainTap = onTextTap,
                                onImageClick = onImageClick,
                                onImageLongClick = onImageLongClick,
                                onUrlClick = onUrlClick,
                                touchHandlingEnabled = !isSplitBookFlipMode || isPageSettledCurrent,
                                pageNumber = if (pageIdx in contentPages.indices) pageIdx + 1 else null,
                                totalPages = contentPages.size,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (splitSpec != null && splitSpec.active && splitSpec.flapVisible && pageLayer != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val densityLayer = this
                            rotationY = splitSpec.flapRotationY
                            cameraDistance = 18f * densityLayer.density
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .drawWithContent {
                            drawBookFlipHalfFromLayer(
                                pageLayer = pageLayer,
                                half = splitSpec.flapHalf,
                                shadowAlpha = splitSpec.flapShadowAlpha,
                                spineShadowWidthPx = spineShadowWidthPx,
                            )
                        },
                )
            }

            if (abs(transitionSpec.rotationY) > 90f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f },
                ) {
                    NovelAtmosphereBackground(
                        backgroundColor = textBackground,
                        backgroundTexture = backgroundTexture,
                        nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                        oledEdgeGradient = activeOledEdgeGradient,
                        isDarkTheme = isDarkTheme,
                        pageEdgeShadow = pageEdgeShadow,
                        pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                        backgroundImageModel = backgroundImageModel,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f)),
                    )
                }
            }

            if (transitionSpec.shadowAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = transitionSpec.shadowAlpha)),
                )
            }
        }
    }
}
