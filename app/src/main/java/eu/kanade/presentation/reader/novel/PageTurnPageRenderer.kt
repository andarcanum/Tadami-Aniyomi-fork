@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.PagerState
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal enum class NovelPageTurnDragMode {
    START_END,
    GESTURE,
}

internal data class NovelPageTurnRendererConfig(
    val style: NovelPageTransitionStyle,
    val preset: NovelPageTurnPreset,
    val dragMode: NovelPageTurnDragMode,
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

private enum class PageTurnCustomTapAction {
    NONE,
    TOGGLE_UI,
    OPEN_PREVIOUS_CHAPTER,
    OPEN_NEXT_CHAPTER,
}

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
        -> requestedStyle
    }
}

internal fun resolvePageTurnRendererProgressPageIndex(
    currentPage: Int,
): Int {
    return currentPage.coerceAtLeast(0)
}

internal fun resolveNovelPageTurnRendererConfig(
    style: NovelPageTransitionStyle,
    speed: NovelPageTurnSpeed,
    intensity: NovelPageTurnIntensity,
    shadowIntensity: NovelPageTurnShadowIntensity,
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
    val edgeFraction = if (isCurl) {
        (0.14f + (preset.curlAmount * 0.10f)).coerceIn(0.16f, 0.26f)
    } else {
        (0.08f + (preset.curlAmount * 0.08f)).coerceIn(0.10f, 0.18f)
    }
    val dragTargetReach = if (isCurl) {
        (0.78f + (preset.curlAmount * 0.12f)).coerceIn(0.78f, 0.90f)
    } else {
        (0.54f + (preset.curlAmount * 0.18f)).coerceIn(0.56f, 0.74f)
    }
    // Keep the center band narrow so left-side taps still feel like page turns.
    val centerTapWidth = 0.20f
    val shadowRadiusDp = if (isCurl) {
        18f + (preset.shadowAlpha * 24f)
    } else {
        14f + (preset.shadowAlpha * 18f)
    }
    val shadowOffsetXDp = if (isCurl) {
        4f + (preset.curlAmount * 8f)
    } else {
        3f + (preset.curlAmount * 5f)
    }

    return NovelPageTurnRendererConfig(
        style = style,
        preset = preset,
        dragMode = if (isCurl) NovelPageTurnDragMode.GESTURE else NovelPageTurnDragMode.START_END,
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

private fun resolvePageTurnCustomTapAction(
    tapXFraction: Float,
    currentPage: Int,
    pageCount: Int,
    centerTapWidthFraction: Float,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): PageTurnCustomTapAction {
    if (pageCount <= 0) return PageTurnCustomTapAction.NONE
    val safeTapX = tapXFraction.coerceIn(0f, 1f)
    val centerStart = ((1f - centerTapWidthFraction) / 2f).coerceIn(0.15f, 0.4f)
    val centerEnd = (centerStart + centerTapWidthFraction).coerceIn(0.6f, 0.85f)
    val isFirstPage = currentPage <= 0
    val isLastPage = currentPage >= pageCount - 1
    return when {
        safeTapX in centerStart..centerEnd -> PageTurnCustomTapAction.TOGGLE_UI
        safeTapX < centerStart && isFirstPage && hasPreviousChapter ->
            PageTurnCustomTapAction.OPEN_PREVIOUS_CHAPTER
        safeTapX > centerEnd && isLastPage && hasNextChapter ->
            PageTurnCustomTapAction.OPEN_NEXT_CHAPTER
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
                pointerBehavior = PageCurlConfig.DragInteraction.PointerBehavior.PageEdge,
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
                pointerBehavior = PageCurlConfig.DragInteraction.PointerBehavior.PageEdge,
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
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    backgroundImageModel: Any?,
    backgroundModeIdentity: String,
    isBackgroundMode: Boolean,
    activeBackgroundTexture: NovelReaderBackgroundTexture,
    activeOledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    composeFontFamily: FontFamily?,
    chapterTitleFontFamily: FontFamily?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onToggleUi: () -> Unit,
    requestedPage: Int,
    onRequestedPageConsumed: () -> Unit,
    onCurrentPageChange: (Int) -> Unit,
    onMoveBackward: () -> Unit,
    onMoveForward: () -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
) {
    val safeContentPages = remember(contentPages) {
        contentPages.ifEmpty { listOf(NovelPageContentPage(emptyList())) }
    }
    val pagerCurrentPage = pagerState.currentPage.coerceIn(0, safeContentPages.lastIndex)
    val pageCurlState = rememberPageCurlState(initialCurrent = pagerCurrentPage)
    val currentPage = pageCurlState.current.coerceIn(0, safeContentPages.lastIndex)
    val rendererConfig = remember(
        transitionStyle,
        readerSettings.pageTurnSpeed,
        readerSettings.pageTurnIntensity,
        readerSettings.pageTurnShadowIntensity,
        textBackground,
        currentPage,
        safeContentPages.size,
        hasPreviousChapter,
        hasNextChapter,
    ) {
        resolveNovelPageTurnRendererConfig(
            style = transitionStyle,
            speed = readerSettings.pageTurnSpeed,
            intensity = readerSettings.pageTurnIntensity,
            shadowIntensity = readerSettings.pageTurnShadowIntensity,
            textBackground = textBackground,
            canMoveBackward = currentPage > 0,
            canMoveForward = currentPage < safeContentPages.lastIndex,
        )
    }
    val latestToggleUi by rememberUpdatedState(onToggleUi)
    val latestRequestedPage by rememberUpdatedState(requestedPage)
    val latestRequestedPageConsumed by rememberUpdatedState(onRequestedPageConsumed)
    val latestCurrentPageChange by rememberUpdatedState(onCurrentPageChange)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestRendererConfig by rememberUpdatedState(rendererConfig)
    val latestPageCount by rememberUpdatedState(safeContentPages.size)
    val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapter)
    val latestHasNextChapter by rememberUpdatedState(hasNextChapter)
    val pageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            when (
                resolvePageTurnCustomTapAction(
                    tapXFraction = if (size.width > 0) {
                        offset.x / size.width.toFloat()
                    } else {
                        0.5f
                    },
                    currentPage = pageCurlState.current,
                    pageCount = latestPageCount,
                    centerTapWidthFraction = latestRendererConfig.centerTapWidthFraction,
                    hasPreviousChapter = latestHasPreviousChapter,
                    hasNextChapter = latestHasNextChapter,
                )
            ) {
                PageTurnCustomTapAction.TOGGLE_UI -> {
                    latestToggleUi()
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
        pageCurlConfig.dragBackwardEnabled = rendererConfig.dragBackwardEnabled
        pageCurlConfig.dragForwardEnabled = rendererConfig.dragForwardEnabled
        pageCurlConfig.tapBackwardEnabled = rendererConfig.tapBackwardEnabled
        pageCurlConfig.tapForwardEnabled = rendererConfig.tapForwardEnabled
        pageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
        pageCurlConfig.dragInteraction = createPageTurnDragInteraction(rendererConfig)
        pageCurlConfig.tapInteraction = createPageTurnTapInteraction(rendererConfig)
    }

    LaunchedEffect(pagerCurrentPage, safeContentPages.size) {
        if (pageCurlState.current != pagerCurrentPage) {
            pageCurlState.snapTo(pagerCurrentPage)
        }
    }

    LaunchedEffect(pageCurlState, pagerState, safeContentPages.size) {
        snapshotFlow { pageCurlState.current }
            .map { it.coerceIn(0, safeContentPages.lastIndex) }
            .distinctUntilChanged()
            .collectLatest { targetPage ->
                latestCurrentPageChange(targetPage)
                if (targetPage != pagerState.currentPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }
    }

    LaunchedEffect(latestRequestedPage, safeContentPages.size) {
        val targetPage = latestRequestedPage
        if (targetPage < 0 || safeContentPages.isEmpty()) return@LaunchedEffect
        val clampedTarget = targetPage.coerceIn(0, safeContentPages.lastIndex)
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
            composeFontFamily,
            chapterTitleFontFamily,
            safeContentPages,
            rendererConfig.backPageColor,
        ) {
            NovelPageTurnSnapshotCache<ImageBitmap>(maxSize = 3)
        }

        PageCurl(
            count = safeContentPages.size,
            key = { it },
            state = pageCurlState,
            config = pageCurlConfig,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    pageCurlState.current,
                    safeContentPages.size,
                    hasPreviousChapter,
                    hasNextChapter,
                ) {
                    val thresholdPx = 160.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pageAtGestureStart = pageCurlState.current
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
                                pageCount = safeContentPages.size,
                                deltaX = currentPosition.x - down.position.x,
                                deltaY = currentPosition.y - down.position.y,
                                thresholdPx = thresholdPx,
                                hasPreviousChapter = hasPreviousChapter,
                                hasNextChapter = hasNextChapter,
                            )
                        ) {
                            HorizontalChapterSwipeAction.PREVIOUS -> latestOpenPreviousChapter()
                            HorizontalChapterSwipeAction.NEXT -> latestOpenNextChapter()
                            HorizontalChapterSwipeAction.NONE -> Unit
                        }
                    }
                },
        ) { page ->
            val contentPage = safeContentPages.getOrElse(page) { NovelPageContentPage(emptyList()) }
            val pageTexture = if (isBackgroundMode) activeBackgroundTexture else backgroundTexture
            val pageTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent
            val pageSurfaceColor = if (isBackgroundMode) null else rendererConfig.backPageColor
            val pageSnapshotKey = resolveNovelPageTurnSnapshotKey(
                style = rendererConfig.style,
                pageIndex = page,
                pageCount = safeContentPages.size,
                pageContentHash = contentPage.hashCode(),
                pageSize = pageSize,
                fontFamilyKey = readerSettings.fontFamily,
                chapterTitleFontFamilyKey = chapterTitleFontFamily?.hashCode()?.toString().orEmpty(),
                fontSize = readerSettings.fontSize,
                lineHeight = readerSettings.lineHeight,
                margin = readerSettings.margin,
                contentPaddingPx = contentPaddingPx,
                statusBarTopPaddingPx = statusBarTopPaddingPx,
                textAlign = readerSettings.textAlign,
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
                preferCachedBitmap = !isBackgroundMode,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isBackgroundMode) {
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
                }
                NovelPageReaderPageContent(
                    contentPage = contentPage,
                    readerSettings = readerSettings,
                    textColor = textColor,
                    textBackground = textBackground,
                    pageSurfaceColor = pageSurfaceColor,
                    backgroundTexture = pageTexture,
                    nativeTextureStrengthPercent = pageTextureStrengthPercent,
                    composeFontFamily = composeFontFamily,
                    chapterTitleFontFamily = chapterTitleFontFamily,
                    contentPadding = contentPadding,
                    statusBarTopPadding = statusBarTopPadding,
                )
            }
        }
    }
}
