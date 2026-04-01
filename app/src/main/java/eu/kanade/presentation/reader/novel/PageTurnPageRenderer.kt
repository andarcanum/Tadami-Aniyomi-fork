@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnActivationZone
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class NovelPageTurnDragMode {
    START_END,
    GESTURE,
}

internal data class NovelPageTurnRendererConfig(
    val style: NovelPageTransitionStyle,
    val preset: NovelPageTurnPreset,
    val dragMode: NovelPageTurnDragMode,
    val dragPointerBehavior: PageCurlConfig.DragInteraction.PointerBehavior,
    val dragActivationEdgeFraction: Float,
    val dragTargetReachFraction: Float,
    val centerTapWidthFraction: Float,
    val shadowRadiusDp: Float,
    val shadowOffsetXDp: Float,
    val backPageColor: Color,
    val shadowColor: Color,
    val dragBackwardEnabled: Boolean,
    val dragForwardEnabled: Boolean,
    val tapBackwardEnabled: Boolean,
    val tapForwardEnabled: Boolean,
    val tapCustomEnabled: Boolean,
)

internal enum class PageTurnCustomTapAction {
    NONE,
    TOGGLE_UI,
    MOVE_PREVIOUS_PAGE,
    MOVE_NEXT_PAGE,
    OPEN_PREVIOUS_CHAPTER,
    OPEN_NEXT_CHAPTER,
}

internal enum class PageTurnChapterNavigationDirection {
    PREVIOUS,
    NEXT,
}

internal data class PageTurnChapterNavigationRequest(
    val direction: PageTurnChapterNavigationDirection,
    val token: Long,
)

internal fun resolvePageTurnRendererFallbackStyle(
    requestedStyle: NovelPageTransitionStyle,
): NovelPageTransitionStyle {
    return when (requestedStyle) {
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> NovelPageTransitionStyle.SLIDE
        NovelPageTransitionStyle.INSTANT,
        NovelPageTransitionStyle.SLIDE,
        NovelPageTransitionStyle.DEPTH,
        NovelPageTransitionStyle.BOOK_FLIP,
        -> requestedStyle
    }
}

internal fun resolvePageTurnRendererProgressPageIndex(
    currentPage: Int,
    contentPageCount: Int = Int.MAX_VALUE,
    hasPreviousChapter: Boolean = false,
): Int {
    val safeContentPageCount = contentPageCount.coerceAtLeast(1)
    val offset = if (hasPreviousChapter) 1 else 0
    return (currentPage - offset).coerceIn(0, safeContentPageCount - 1)
}

internal fun resolvePageTurnRendererVirtualPageCount(
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): Int {
    return contentPageCount.coerceAtLeast(1) +
        (if (hasPreviousChapter) 1 else 0) +
        (if (hasNextChapter) 1 else 0)
}

internal fun resolvePageTurnRendererVirtualPageIndex(
    actualPageIndex: Int,
    hasPreviousChapter: Boolean,
): Int {
    return actualPageIndex.coerceAtLeast(0) + if (hasPreviousChapter) 1 else 0
}

internal fun resolvePageTurnRendererBoundaryChapterTarget(
    currentPage: Int,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): HorizontalChapterSwipeAction {
    val virtualPageCount = resolvePageTurnRendererVirtualPageCount(
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

internal fun resolvePageTurnRendererSettledBoundaryChapterTarget(
    currentPage: Int,
    progress: Float,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): HorizontalChapterSwipeAction {
    val boundaryTarget = resolvePageTurnRendererBoundaryChapterTarget(
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

internal fun resolveNovelPageTurnRendererConfig(
    style: NovelPageTransitionStyle,
    speed: NovelPageTurnSpeed,
    intensity: NovelPageTurnIntensity,
    shadowIntensity: NovelPageTurnShadowIntensity,
    activationZone: NovelPageTurnActivationZone,
    textBackground: Color,
    canMoveBackward: Boolean,
    canMoveForward: Boolean,
): NovelPageTurnRendererConfig {
    val preset = resolveNovelPageTurnPreset(
        style = style,
        speed = speed,
        intensity = intensity,
        shadowIntensity = shadowIntensity,
    )
    val isCurl = style == NovelPageTransitionStyle.CURL
    val edgeFraction = activationZone.dragActivationEdgeFraction()
    val pointerBehavior = activationZone.pointerBehavior()
    val dragTargetReach = if (isCurl) {
        (edgeFraction + 0.50f).coerceIn(0.76f, 0.94f)
    } else {
        (edgeFraction + 0.32f).coerceIn(0.52f, 0.80f)
    }
    // Keep the center band narrow so left-side taps still feel like page turns.
    val centerTapWidth = 0.20f
    val shadowRadiusDp = when (style) {
        NovelPageTransitionStyle.CURL -> 18f + (preset.shadowAlpha * 24f)
        NovelPageTransitionStyle.BOOK -> 22f + (preset.shadowAlpha * 32f)
        else -> 14f + (preset.shadowAlpha * 18f)
    }
    val shadowOffsetXDp = when (style) {
        NovelPageTransitionStyle.CURL -> 4f + (preset.curlAmount * 8f)
        NovelPageTransitionStyle.BOOK -> 2f + (preset.curlAmount * 4f)
        else -> 3f + (preset.curlAmount * 5f)
    }

    return NovelPageTurnRendererConfig(
        style = style,
        preset = preset,
        // Curl should honor the configured activation zone as a moving drag threshold.
        dragMode = NovelPageTurnDragMode.START_END,
        dragPointerBehavior = if (style == NovelPageTransitionStyle.BOOK) {
            PageCurlConfig.DragInteraction.PointerBehavior.PageEdge
        } else {
            pointerBehavior
        },
        dragActivationEdgeFraction = edgeFraction,
        dragTargetReachFraction = dragTargetReach,
        centerTapWidthFraction = centerTapWidth,
        shadowRadiusDp = shadowRadiusDp,
        shadowOffsetXDp = shadowOffsetXDp,
        backPageColor = resolveNovelPageTurnBackPageColor(textBackground),
        shadowColor = Color.Black,
        dragBackwardEnabled = canMoveBackward,
        dragForwardEnabled = canMoveForward,
        tapBackwardEnabled = canMoveBackward,
        tapForwardEnabled = canMoveForward,
        tapCustomEnabled = true,
    )
}

private fun NovelPageTurnActivationZone.dragActivationEdgeFraction(): Float {
    return when (this) {
        NovelPageTurnActivationZone.NARROWER -> 0.20f
        NovelPageTurnActivationZone.NARROW -> 0.25f
        NovelPageTurnActivationZone.NORMAL -> 0.30f
        NovelPageTurnActivationZone.WIDE -> 0.35f
        NovelPageTurnActivationZone.WIDER -> 0.40f
    }
}

private fun NovelPageTurnActivationZone.pointerBehavior(): PageCurlConfig.DragInteraction.PointerBehavior {
    return when (this) {
        NovelPageTurnActivationZone.NARROWER,
        NovelPageTurnActivationZone.NARROW,
        NovelPageTurnActivationZone.NORMAL,
        -> PageCurlConfig.DragInteraction.PointerBehavior.PageEdge
        NovelPageTurnActivationZone.WIDE,
        NovelPageTurnActivationZone.WIDER,
        -> PageCurlConfig.DragInteraction.PointerBehavior.Default
    }
}

private fun resolveNovelPageTurnBackPageColor(
    textBackground: Color,
): Color {
    val isLightBackground = textBackground.luminance() >= 0.65f
    val parchmentTone = if (isLightBackground) {
        Color(0xFFE6D4AA)
    } else {
        Color(0xFF2F2418)
    }

    return lerp(
        start = textBackground,
        stop = parchmentTone,
        fraction = if (isLightBackground) 0.82f else 0.28f,
    )
}

internal fun resolvePageTurnCustomTapAction(
    tapXFraction: Float,
    currentPage: Int,
    pageCount: Int,
    centerTapWidthFraction: Float,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    tapToScrollEnabled: Boolean,
    animateBoundaryTransition: Boolean,
): PageTurnCustomTapAction {
    if (pageCount <= 0) return PageTurnCustomTapAction.NONE
    val safeTapX = tapXFraction.coerceIn(0f, 1f)
    val centerStart = ((1f - centerTapWidthFraction) / 2f).coerceIn(0.15f, 0.4f)
    val centerEnd = (centerStart + centerTapWidthFraction).coerceIn(0.6f, 0.85f)
    return when {
        safeTapX in centerStart..centerEnd -> PageTurnCustomTapAction.TOGGLE_UI
        tapToScrollEnabled && safeTapX < centerStart -> {
            when {
                currentPage <= 0 && hasPreviousChapter -> {
                    if (animateBoundaryTransition) {
                        PageTurnCustomTapAction.MOVE_PREVIOUS_PAGE
                    } else {
                        PageTurnCustomTapAction.OPEN_PREVIOUS_CHAPTER
                    }
                }
                currentPage > 0 -> PageTurnCustomTapAction.MOVE_PREVIOUS_PAGE
                else -> PageTurnCustomTapAction.NONE
            }
        }
        tapToScrollEnabled && safeTapX > centerEnd -> {
            when {
                currentPage >= pageCount - 1 && hasNextChapter -> {
                    if (animateBoundaryTransition) {
                        PageTurnCustomTapAction.MOVE_NEXT_PAGE
                    } else {
                        PageTurnCustomTapAction.OPEN_NEXT_CHAPTER
                    }
                }
                currentPage < pageCount - 1 -> PageTurnCustomTapAction.MOVE_NEXT_PAGE
                else -> PageTurnCustomTapAction.NONE
            }
        }
        else -> PageTurnCustomTapAction.NONE
    }
}

internal fun createPageTurnTapInteraction(
    config: NovelPageTurnRendererConfig,
): PageCurlConfig.TargetTapInteraction {
    val edgeTapWidth = ((1f - config.centerTapWidthFraction) / 2f).coerceIn(0.18f, 0.4f)
    return PageCurlConfig.TargetTapInteraction(
        backward = PageCurlConfig.TargetTapInteraction.Config(
            Rect(0f, 0f, edgeTapWidth, 1f),
        ),
        forward = PageCurlConfig.TargetTapInteraction.Config(
            Rect(1f - edgeTapWidth, 0f, 1f, 1f),
        ),
    )
}

private fun createPageTurnDragInteraction(
    config: NovelPageTurnRendererConfig,
): PageCurlConfig.DragInteraction {
    return when (config.dragMode) {
        NovelPageTurnDragMode.START_END -> {
            val edge = config.dragActivationEdgeFraction
            val reach = config.dragTargetReachFraction
            PageCurlConfig.StartEndDragInteraction(
                pointerBehavior = config.dragPointerBehavior,
                backward = PageCurlConfig.StartEndDragInteraction.Config(
                    start = Rect(0f, 0f, edge, 1f),
                    end = Rect(1f - reach, 0f, 1f, 1f),
                ),
                forward = PageCurlConfig.StartEndDragInteraction.Config(
                    start = Rect(1f - edge, 0f, 1f, 1f),
                    end = Rect(0f, 0f, reach, 1f),
                ),
            )
        }
        NovelPageTurnDragMode.GESTURE -> {
            val edge = config.dragActivationEdgeFraction
            PageCurlConfig.GestureDragInteraction(
                pointerBehavior = config.dragPointerBehavior,
                backward = PageCurlConfig.GestureDragInteraction.Config(
                    target = Rect(0f, 0f, edge, 1f),
                ),
                forward = PageCurlConfig.GestureDragInteraction.Config(
                    target = Rect(1f - edge, 0f, 1f, 1f),
                ),
            )
        }
    }
}

@Composable
internal fun PageTurnPageRenderer(
    pagerState: PagerState,
    contentPages: List<NovelPageContentPage>,
    transitionStyle: NovelPageTransitionStyle,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    chapterTitleTextColor: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    backgroundImageModel: Any?,
    backgroundModeIdentity: String,
    isBackgroundMode: Boolean,
    activeBackgroundTexture: NovelReaderBackgroundTexture,
    activeOledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    hasPreviousChapter: Boolean,
    previousChapterName: String?,
    hasNextChapter: Boolean,
    nextChapterName: String?,
    previousChapterLabel: String,
    nextChapterLabel: String,
    boundaryChapterHint: String,
    onToggleUi: () -> Unit,
    requestedPage: Int,
    onRequestedPageConsumed: () -> Unit,
    onCurrentPageChange: (Int) -> Unit,
    onMoveBackward: () -> Unit,
    onMoveForward: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    chapterNavigationRequest: PageTurnChapterNavigationRequest? = null,
    onChapterNavigationRequestConsumed: () -> Unit = {},
    onTextTap: (Float, Float) -> Unit = { _, _ -> onToggleUi() },
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
) {
    val safeContentPages = remember(contentPages) {
        contentPages.ifEmpty { listOf(NovelPageContentPage(emptyList())) }
    }
    val actualPageCount = safeContentPages.size
    val virtualPageCount = remember(actualPageCount, hasPreviousChapter, hasNextChapter) {
        resolvePageTurnRendererVirtualPageCount(
            contentPageCount = actualPageCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
    }
    val pagerCurrentPage = pagerState.currentPage.coerceIn(0, safeContentPages.lastIndex)
    val initialVirtualPage = remember(pagerCurrentPage, hasPreviousChapter) {
        resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = pagerCurrentPage,
            hasPreviousChapter = hasPreviousChapter,
        )
    }
    val pageCurlState = rememberPageCurlState(initialCurrent = initialVirtualPage)
    val currentPage = pageCurlState.current.coerceIn(0, virtualPageCount - 1)
    val currentActualPage = resolvePageTurnRendererProgressPageIndex(
        currentPage = currentPage,
        contentPageCount = actualPageCount,
        hasPreviousChapter = hasPreviousChapter,
    )
    val tapCoroutineScope = rememberCoroutineScope()
    val rendererConfig = remember(
        transitionStyle,
        readerSettings.pageTurnSpeed,
        readerSettings.pageTurnIntensity,
        readerSettings.pageTurnShadowIntensity,
        readerSettings.pageTurnActivationZone,
        textBackground,
    ) {
        resolveNovelPageTurnRendererConfig(
            style = transitionStyle,
            speed = readerSettings.pageTurnSpeed,
            intensity = readerSettings.pageTurnIntensity,
            shadowIntensity = readerSettings.pageTurnShadowIntensity,
            activationZone = readerSettings.pageTurnActivationZone,
            textBackground = textBackground,
            canMoveBackward = true,
            canMoveForward = true,
        )
    }
    val dragInteraction = remember(rendererConfig) {
        createPageTurnDragInteraction(rendererConfig)
    }
    val tapInteraction = remember(rendererConfig) {
        createPageTurnTapInteraction(rendererConfig)
    }
    val latestToggleUi by rememberUpdatedState(onToggleUi)
    val latestRequestedPage by rememberUpdatedState(requestedPage)
    val latestRequestedPageConsumed by rememberUpdatedState(onRequestedPageConsumed)
    val latestCurrentPageChange by rememberUpdatedState(onCurrentPageChange)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestChapterNavigationRequest by rememberUpdatedState(chapterNavigationRequest)
    val latestChapterNavigationRequestConsumed by rememberUpdatedState(onChapterNavigationRequestConsumed)
    val latestRendererConfig by rememberUpdatedState(rendererConfig)
    val latestPageCount by rememberUpdatedState(safeContentPages.size)
    val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapter)
    val latestHasNextChapter by rememberUpdatedState(hasNextChapter)
    val latestTapToScrollEnabled by rememberUpdatedState(readerSettings.tapToScroll)
    val pageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            when (
                resolvePageTurnCustomTapAction(
                    tapXFraction = if (size.width > 0) {
                        offset.x / size.width.toFloat()
                    } else {
                        0.5f
                    },
                    currentPage = resolvePageTurnRendererProgressPageIndex(
                        currentPage = pageCurlState.current,
                        contentPageCount = latestPageCount,
                        hasPreviousChapter = latestHasPreviousChapter,
                    ),
                    pageCount = latestPageCount.coerceAtLeast(1),
                    centerTapWidthFraction = latestRendererConfig.centerTapWidthFraction,
                    hasPreviousChapter = latestHasPreviousChapter,
                    hasNextChapter = latestHasNextChapter,
                    tapToScrollEnabled = latestTapToScrollEnabled,
                    animateBoundaryTransition = transitionStyle == NovelPageTransitionStyle.CURL,
                )
            ) {
                PageTurnCustomTapAction.TOGGLE_UI -> {
                    latestToggleUi()
                    true
                }
                PageTurnCustomTapAction.MOVE_PREVIOUS_PAGE -> {
                    tapCoroutineScope.launch {
                        pageCurlState.prev()
                    }
                    true
                }
                PageTurnCustomTapAction.MOVE_NEXT_PAGE -> {
                    tapCoroutineScope.launch {
                        pageCurlState.next()
                    }
                    true
                }
                PageTurnCustomTapAction.OPEN_PREVIOUS_CHAPTER -> {
                    latestOpenPreviousChapter()
                    true
                }
                PageTurnCustomTapAction.OPEN_NEXT_CHAPTER -> {
                    latestOpenNextChapter()
                    true
                }
                PageTurnCustomTapAction.NONE -> false
            }
        },
    )

    SideEffect {
        pageCurlConfig.backPageColor = rendererConfig.backPageColor
        pageCurlConfig.backPageContentAlpha = 0f
        pageCurlConfig.shadowColor = rendererConfig.shadowColor
        pageCurlConfig.shadowAlpha = rendererConfig.preset.shadowAlpha
        pageCurlConfig.shadowRadius = rendererConfig.shadowRadiusDp.dp
        pageCurlConfig.shadowOffset = DpOffset(rendererConfig.shadowOffsetXDp.dp, 0.dp)
        pageCurlConfig.dragBackwardEnabled = currentPage > 0
        pageCurlConfig.dragForwardEnabled = currentPage < virtualPageCount - 1
        pageCurlConfig.tapBackwardEnabled = latestTapToScrollEnabled && currentActualPage > 0
        pageCurlConfig.tapForwardEnabled = latestTapToScrollEnabled && currentActualPage < safeContentPages.lastIndex
        pageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
        pageCurlConfig.dragInteraction = dragInteraction
        pageCurlConfig.tapInteraction = tapInteraction
    }

    LaunchedEffect(pagerCurrentPage, actualPageCount, hasPreviousChapter) {
        val targetVirtualPage = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = pagerCurrentPage,
            hasPreviousChapter = hasPreviousChapter,
        )
        if (pageCurlState.current != targetVirtualPage) {
            pageCurlState.snapTo(targetVirtualPage)
        }
    }

    LaunchedEffect(pageCurlState, pagerState, actualPageCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { pageCurlState.current.coerceIn(0, virtualPageCount - 1) to pageCurlState.progress }
            .distinctUntilChanged()
            .collectLatest { (targetVirtualPage, progress) ->
                val boundaryTarget = resolvePageTurnRendererBoundaryChapterTarget(
                    currentPage = targetVirtualPage,
                    contentPageCount = actualPageCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
                when (
                    resolvePageTurnRendererSettledBoundaryChapterTarget(
                        currentPage = targetVirtualPage,
                        progress = progress,
                        contentPageCount = actualPageCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )
                ) {
                    HorizontalChapterSwipeAction.PREVIOUS -> latestOpenPreviousChapter()
                    HorizontalChapterSwipeAction.NEXT -> latestOpenNextChapter()
                    HorizontalChapterSwipeAction.NONE -> if (boundaryTarget == HorizontalChapterSwipeAction.NONE) {
                        val targetPage = resolvePageTurnRendererProgressPageIndex(
                            currentPage = targetVirtualPage,
                            contentPageCount = actualPageCount,
                            hasPreviousChapter = hasPreviousChapter,
                        )
                        latestCurrentPageChange(targetPage)
                        if (targetPage != pagerState.currentPage) {
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                }
            }
    }

    LaunchedEffect(latestRequestedPage, actualPageCount, hasPreviousChapter) {
        val targetPage = latestRequestedPage
        if (targetPage < 0 || safeContentPages.isEmpty()) return@LaunchedEffect
        val clampedTarget = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = targetPage.coerceIn(0, safeContentPages.lastIndex),
            hasPreviousChapter = hasPreviousChapter,
        )
        if (pageCurlState.current != clampedTarget) {
            pageCurlState.snapTo(clampedTarget)
        }
        latestRequestedPageConsumed()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val density = LocalDensity.current
        val pageSize = IntSize(
            width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1),
            height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1),
        )
        val contentPaddingPx = with(density) { contentPadding.roundToPx() }
        val statusBarTopPaddingPx = with(density) { statusBarTopPadding.roundToPx() }
        val snapshotCache = remember(
            pageSize,
            transitionStyle,
            readerSettings.fontFamily,
            readerSettings.fontSize,
            readerSettings.lineHeight,
            readerSettings.margin,
            readerSettings.textAlign,
            readerSettings.forceBoldText,
            readerSettings.forceItalicText,
            readerSettings.textShadow,
            readerSettings.textShadowColor,
            readerSettings.textShadowBlur,
            readerSettings.textShadowX,
            readerSettings.textShadowY,
            readerSettings.bionicReading,
            textColor,
            textBackground,
            backgroundTexture,
            nativeTextureStrengthPercent,
            backgroundImageModel,
            backgroundModeIdentity,
            isBackgroundMode,
            activeBackgroundTexture,
            activeOledEdgeGradient,
            isDarkTheme,
            chapterTitleTextColor,
            textTypeface,
            chapterTitleTypeface,
            safeContentPages,
            rendererConfig.backPageColor,
        ) {
            NovelPageTurnSnapshotCache<ImageBitmap>(maxSize = 3)
        }

        PageCurl(
            count = virtualPageCount,
            key = { it },
            state = pageCurlState,
            config = pageCurlConfig,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val boundaryPreview = when (
                resolvePageTurnRendererBoundaryChapterTarget(
                    currentPage = page,
                    contentPageCount = actualPageCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
            ) {
                HorizontalChapterSwipeAction.PREVIOUS,
                HorizontalChapterSwipeAction.NEXT,
                -> {
                    createNovelPageBoundaryPreviewData(
                        chapterLabel = if (page <= 0) {
                            previousChapterLabel
                        } else {
                            nextChapterLabel
                        },
                        chapterName = if (page <= 0) {
                            previousChapterName
                        } else {
                            nextChapterName
                        },
                        chapterHint = boundaryChapterHint,
                    )
                }
                HorizontalChapterSwipeAction.NONE -> null
            }
            val contentPage = if (boundaryPreview == null) {
                val actualPage = resolvePageTurnRendererProgressPageIndex(
                    currentPage = page,
                    contentPageCount = actualPageCount,
                    hasPreviousChapter = hasPreviousChapter,
                )
                safeContentPages.getOrElse(actualPage) { NovelPageContentPage(emptyList()) }
            } else {
                NovelPageContentPage(emptyList())
            }
            val pageTexture = if (isBackgroundMode) activeBackgroundTexture else backgroundTexture
            val pageTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent
            val pageSurfaceColor = if (isBackgroundMode) null else rendererConfig.backPageColor
            val pageContentIdentity = boundaryPreview ?: contentPage
            val pageSnapshotKey = resolveNovelPageTurnSnapshotKey(
                style = rendererConfig.style,
                pageIndex = page,
                pageCount = virtualPageCount,
                pageContentHash = pageContentIdentity.hashCode(),
                pageSize = pageSize,
                fontFamilyKey = readerSettings.fontFamily,
                chapterTitleFontFamilyKey = chapterTitleTypeface?.hashCode()?.toString().orEmpty(),
                chapterTitleTextColor = chapterTitleTextColor,
                fontSize = readerSettings.fontSize,
                lineHeight = readerSettings.lineHeight,
                margin = readerSettings.margin,
                contentPaddingPx = contentPaddingPx,
                statusBarTopPaddingPx = statusBarTopPaddingPx,
                textAlign = if (boundaryPreview != null) {
                    eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign.CENTER
                } else {
                    readerSettings.textAlign
                },
                textColor = textColor,
                textBackground = textBackground,
                pageSurfaceColor = pageSurfaceColor ?: Color.Transparent,
                isBackgroundMode = isBackgroundMode,
                backgroundImageIdentity = backgroundModeIdentity,
                backgroundTextureName = pageTexture.name,
                nativeTextureStrengthPercentEffective = pageTextureStrengthPercent,
                oledEdgeGradient = if (isBackgroundMode) activeOledEdgeGradient else false,
                isDarkTheme = isDarkTheme,
                backgroundTexture = pageTexture,
                nativeTextureStrengthPercent = pageTextureStrengthPercent,
                forceBoldText = readerSettings.forceBoldText,
                forceItalicText = readerSettings.forceItalicText,
                textShadow = readerSettings.textShadow,
                textShadowColor = readerSettings.textShadowColor,
                textShadowBlur = readerSettings.textShadowBlur,
                textShadowX = readerSettings.textShadowX,
                textShadowY = readerSettings.textShadowY,
                bionicReading = readerSettings.bionicReading,
            )
            NovelPageTurnSnapshotRenderer(
                snapshotKey = pageSnapshotKey,
                snapshotCache = snapshotCache,
                preferCachedBitmap = false,
                modifier = Modifier.fillMaxSize(),
            ) {
                NovelAtmosphereBackground(
                    backgroundColor = textBackground,
                    backgroundTexture = pageTexture,
                    nativeTextureStrengthPercent = pageTextureStrengthPercent,
                    oledEdgeGradient = activeOledEdgeGradient,
                    isDarkTheme = isDarkTheme,
                    pageEdgeShadow = false,
                    pageEdgeShadowAlpha = 0f,
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
                } else {
                    NovelPageReaderPageContent(
                        contentPage = contentPage,
                        readerSettings = readerSettings,
                        textColor = textColor,
                        textBackground = textBackground,
                        pageSurfaceColor = pageSurfaceColor,
                        backgroundTexture = pageTexture,
                        nativeTextureStrengthPercent = pageTextureStrengthPercent,
                        chapterTitleTextColor = chapterTitleTextColor,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        textShadowEnabled = readerSettings.textShadow,
                        textShadowColor = readerSettings.textShadowColor,
                        textShadowBlur = readerSettings.textShadowBlur,
                        textShadowX = readerSettings.textShadowX,
                        textShadowY = readerSettings.textShadowY,
                        contentPadding = contentPadding,
                        statusBarTopPadding = statusBarTopPadding,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        onPlainTap = onTextTap,
                        touchHandlingEnabled = false,
                    )
                }
            }
        }
    }

    LaunchedEffect(latestChapterNavigationRequest, transitionStyle) {
        val request = latestChapterNavigationRequest ?: return@LaunchedEffect
        if (transitionStyle != NovelPageTransitionStyle.CURL) {
            latestChapterNavigationRequestConsumed()
            return@LaunchedEffect
        }

        when (request.direction) {
            PageTurnChapterNavigationDirection.PREVIOUS -> {
                tapCoroutineScope.launch {
                    pageCurlState.prev()
                }
            }
            PageTurnChapterNavigationDirection.NEXT -> {
                tapCoroutineScope.launch {
                    pageCurlState.next()
                }
            }
        }
        latestChapterNavigationRequestConsumed()
    }
}
