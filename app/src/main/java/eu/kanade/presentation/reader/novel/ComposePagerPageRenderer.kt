package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

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
        NovelPageTransitionStyle.BOOK_FLIP -> {
            if (pageOffset > 0f && pageOffset <= 1f) {
                // Page rotating (around left edge)
                ComposePagerTransitionSpec(
                    rotationY = -180f * pageOffset,
                    pivotXFraction = 0f,
                    cameraDistance = 15f,
                    cancelPagerMotion = true,
                    shadowAlpha = (abs(0.5f - pageOffset) * -0.6f + 0.3f).coerceIn(0f, 0.3f),
                    zIndex = 1f - pageOffset
                )
            } else if (pageOffset <= 0f && pageOffset >= -1f) {
                // Page underneath
                ComposePagerTransitionSpec(
                    cancelPagerMotion = true,
                    zIndex = 0f
                )
            } else {
                ComposePagerTransitionSpec(
                    hideOffscreenPages = true
                )
            }
        }
        else -> ComposePagerTransitionSpec()
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
        else -> "compose-content-${
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
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    previousChapterName: String?,
    nextChapterName: String?,
    previousChapterLabel: String,
    nextChapterLabel: String,
    boundaryChapterHint: String,
    onToggleUi: () -> Unit,
    onMoveBackward: () -> Unit,
    onMoveForward: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
) {
    val density = LocalDensity.current
    val useBoundaryPreview = shouldUseComposePagerBoundaryPreview(transitionStyle)
    val contentPageCount = contentPages.size
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
    val edgeSwipeThresholdPx = with(density) { 160.dp.toPx() }

    val boundarySwipeModifier = if (useBoundaryPreview) {
        Modifier
    } else {
        Modifier.pointerInput(
            pagerState,
            contentPageCount,
            edgeSwipeThresholdPx,
            hasPreviousChapter,
            hasNextChapter,
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
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )
                ) {
                    HorizontalChapterSwipeAction.PREVIOUS -> latestOpenPreviousChapter()
                    HorizontalChapterSwipeAction.NEXT -> latestOpenNextChapter()
                    HorizontalChapterSwipeAction.NONE -> Unit
                }
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
                    if (isScrolling) return@collectLatest
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
            .then(boundarySwipeModifier)
            .pointerInput(readerSettings.tapToScroll) {
                detectTapGestures(
                    onTap = { offset ->
                        when (
                            resolveReaderTapAction(
                                tapX = offset.x,
                                width = size.width.toFloat(),
                                tapToScrollEnabled = readerSettings.tapToScroll,
                            )
                        ) {
                            ReaderTapAction.TOGGLE_UI -> latestToggleUi()
                            ReaderTapAction.BACKWARD -> latestMoveBackward()
                            ReaderTapAction.FORWARD -> latestMoveForward()
                        }
                    },
                )
            },
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
        val boundaryPreview = when (boundaryTarget) {
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
        val contentPage = if (boundaryPreview == null) {
            val actualPage = resolveComposePagerActualPageIndex(
                currentPage = page,
                contentPageCount = contentPageCount,
                hasPreviousChapter = hasPreviousChapter,
            )
            contentPages.getOrElse(actualPage) { NovelPageContentPage(emptyList()) }
        } else {
            NovelPageContentPage(emptyList())
        }
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        val transitionSpec = resolveComposePagerTransitionSpec(
            style = transitionStyle,
            pageOffset = pageOffset,
        )
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
            NovelAtmosphereBackground(
                backgroundColor = textBackground,
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
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
                )
            }

            if (abs(transitionSpec.rotationY) > 90f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    NovelAtmosphereBackground(
                        backgroundColor = textBackground,
                        backgroundTexture = backgroundTexture,
                        nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                        oledEdgeGradient = activeOledEdgeGradient,
                        isDarkTheme = isDarkTheme,
                        pageEdgeShadow = false,
                        pageEdgeShadowAlpha = 0f,
                        backgroundImageModel = backgroundImageModel,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                    )
                }
            }

            if (transitionSpec.shadowAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = transitionSpec.shadowAlpha))
                )
            }
        }
    }
}
