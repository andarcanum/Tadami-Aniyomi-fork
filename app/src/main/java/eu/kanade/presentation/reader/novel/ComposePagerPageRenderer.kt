package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import kotlin.math.abs

internal data class ComposePagerTransitionSpec(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
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
        NovelPageTransitionStyle.BOOK,
        NovelPageTransitionStyle.CURL,
        -> ComposePagerTransitionSpec()
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

@Composable
internal fun ComposePagerPageRenderer(
    pagerState: PagerState,
    contentPages: List<NovelPageContentPage>,
    transitionStyle: NovelPageTransitionStyle,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onToggleUi: () -> Unit,
    onMoveBackward: () -> Unit,
    onMoveForward: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
) {
    val density = LocalDensity.current
    val latestToggleUi by rememberUpdatedState(onToggleUi)
    val latestMoveBackward by rememberUpdatedState(onMoveBackward)
    val latestMoveForward by rememberUpdatedState(onMoveForward)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val edgeSwipeThresholdPx = with(density) { 160.dp.toPx() }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                pagerState,
                contentPages.size,
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
                            pageCount = contentPages.size,
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
        val contentPage = contentPages.getOrElse(page) { NovelPageContentPage(emptyList()) }
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        val transitionSpec = resolveComposePagerTransitionSpec(
            style = transitionStyle,
            pageOffset = pageOffset,
        )
        NovelPageReaderPageContent(
            contentPage = contentPage,
            readerSettings = readerSettings,
            textColor = textColor,
            textBackground = textBackground,
            backgroundTexture = backgroundTexture,
            nativeTextureStrengthPercent = nativeTextureStrengthPercent,
            composeFontFamily = composeFontFamily,
            chapterTitleFontFamily = chapterTitleFontFamily,
            contentPadding = contentPadding,
            statusBarTopPadding = statusBarTopPadding,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (transitionSpec.hideOffscreenPages && abs(pageOffset) > 0.5f) {
                        0f
                    } else {
                        transitionSpec.alpha
                    }
                    scaleX = transitionSpec.scale
                    scaleY = transitionSpec.scale
                    translationX = size.width * if (transitionSpec.cancelPagerMotion) {
                        pageOffset
                    } else {
                        transitionSpec.translationXFraction
                    }
                },
        )
    }
}
