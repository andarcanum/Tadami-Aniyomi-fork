@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.kanade.tachiyomi.ui.reader.novel.setting.parseNovelReaderTapZoneActions
import eu.kanade.tachiyomi.ui.reader.novel.setting.resolveConfiguredNovelReaderTapAction
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.Edge
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.PageCurlState
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Two-page spread variant of [PageTurnPageRenderer].
 *
 * eu.wewox.pagecurl.page.PageCurl always folds across its own full measured width: there is no
 * config knob to confine the fold to half of a wider container, and a live drag tracks the finger
 * across that whole width regardless of what Rects are configured (verified on-device: a
 * full-width PageCurl behind a two-column spread curls the entire spread as if it were one wide
 * leaf). The only way to get a fold anchored at the spine is to give the fold its own half-width
 * PageCurl instance.
 *
 * So this renderer runs two independent PageCurl surfaces side by side, each exactly half the
 * screen: the left one always shows even real-page indices and only ever turns backward (its fold
 * lives at the left edge, spine on its right); the right one always shows odd real-page indices
 * and only ever turns forward (its fold lives at the right edge, spine on its left). Both track
 * the same spread slot; only the side facing the turn direction plays an animated fold, the other
 * side's content swaps the instant that fold settles, driven off the animating side's own
 * progress via snapshotFlow. This mirrors a real book: only the leaf being turned animates.
 */
@Composable
internal fun SpreadPageTurnPageRenderer(
    pagerState: PagerState,
    chapterId: Long,
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
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    ttsHighlightState: NovelReaderTtsHighlightState? = null,
    ttsHighlightColor: Color = Color.Transparent,
    hasPreviousChapter: Boolean,
    previousChapterName: String?,
    hasNextChapter: Boolean,
    nextChapterName: String?,
    previousChapterLabel: String,
    nextChapterLabel: String,
    boundaryChapterHint: String,
    showBoundaryChapterPages: Boolean = true,
    onToggleUi: () -> Unit,
    requestedPage: Int,
    onRequestedPageConsumed: () -> Unit,
    onCurrentPageChange: (Int) -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    chapterNavigationRequest: PageTurnChapterNavigationRequest? = null,
    onChapterNavigationRequestConsumed: () -> Unit = {},
    onTextTap: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> onToggleUi() },
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
) {
    val hasPreviousChapterNavigation = hasPreviousChapter
    val hasNextChapterNavigation = hasNextChapter

    @Suppress("NAME_SHADOWING")
    val hasPreviousChapter = hasPreviousChapter && showBoundaryChapterPages

    @Suppress("NAME_SHADOWING")
    val hasNextChapter = hasNextChapter && showBoundaryChapterPages

    val safeContentPages = remember(contentPages) {
        contentPages.ifEmpty { listOf(NovelPageContentPage(emptyList())) }
    }
    val spreadSlotCount = resolveSpreadSlotCount(safeContentPages.size, 2)
    val virtualSlotCount = remember(spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        resolvePageTurnRendererVirtualPageCount(
            contentPageCount = spreadSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
    }
    val pagerCurrentSlot = pagerState.currentPage.coerceIn(0, spreadSlotCount - 1)
    val initialVirtualSlot = remember(pagerCurrentSlot, hasPreviousChapter) {
        resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = pagerCurrentSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
    }

    // Both surfaces are seeded at the same virtual slot and only ever move together (see the two
    // LaunchedEffects below), so they never observe each other's page content out of step.
    val leftCurlState = rememberPageCurlState(initialCurrent = (virtualSlotCount - 1 - initialVirtualSlot).coerceAtLeast(0))
    val rightCurlState = rememberPageCurlState(initialCurrent = initialVirtualSlot)

    val currentVirtualSlot = (virtualSlotCount - 1 - leftCurlState.current).coerceIn(0, virtualSlotCount - 1)
    val currentSpreadSlot = resolvePageTurnRendererProgressPageIndex(
        currentPage = currentVirtualSlot,
        contentPageCount = spreadSlotCount,
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
    val tapInteraction = remember(rendererConfig) {
        createPageTurnTapInteraction(rendererConfig)
    }

    val latestRequestedPage by rememberUpdatedState(requestedPage)
    val latestRequestedPageConsumed by rememberUpdatedState(onRequestedPageConsumed)
    val latestCurrentPageChange by rememberUpdatedState(onCurrentPageChange)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestChapterNavigationRequest by rememberUpdatedState(chapterNavigationRequest)
    val latestChapterNavigationRequestConsumed by rememberUpdatedState(onChapterNavigationRequestConsumed)
    val latestRendererConfig by rememberUpdatedState(rendererConfig)
    val latestToggleUi by rememberUpdatedState(onToggleUi)
    val latestTapToScrollEnabled by rememberUpdatedState(readerSettings.tapToScroll)
    val latestCustomTapZonesEnabled by rememberUpdatedState(readerSettings.customTapZones)
    val latestTapZoneActions by rememberUpdatedState(
        parseNovelReaderTapZoneActions(readerSettings.tapZoneActions),
    )
    val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapterNavigation)
    val latestHasNextChapter by rememberUpdatedState(hasNextChapterNavigation)

    // Advances both surfaces together: the one facing the turn direction plays the real fold, the
    // other jumps straight to its new page with no visible animation of its own (snap()), the same
    // way the spine-side page of a real book is simply "already there" once you see it.
    //
    // eu.wewox.pagecurl.page.PageCurl's own drag/animation geometry always references the widget's
    // own size.width (NewEdgeCreator anchors on it, and PageCurlState.setup's forward Animatable
    // always starts at the right edge): using prev()/backward on an unmirrored widget opens the
    // current page from its own left edge outward, not a new page sliding in over it like a book
    // leaf. The left surface is mirrored (see SpreadColumnCurl) so the library's right-referenced
    // geometry lands on the screen's actual left edge — which means the left surface always calls
    // next()/forward internally, for both book directions; only which surface plays the real
    // animation and which one just snaps changes.
    fun turnForward() {
        tapCoroutineScope.launch {
            rightCurlState.next(
                createPageTurnAnimation(
                    animationDurationMillis = latestRendererConfig.preset.animationDurationMillis,
                    forward = true,
                    curlAmount = latestRendererConfig.preset.curlAmount,
                ),
            )
        }
    }
    fun turnBackward() {
        tapCoroutineScope.launch {
            leftCurlState.next(
                createPageTurnAnimation(
                    animationDurationMillis = latestRendererConfig.preset.animationDurationMillis,
                    forward = true,
                    curlAmount = latestRendererConfig.preset.curlAmount,
                ),
            )
        }
    }

    val leftPageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            handleSpreadCustomTap(
                size = size,
                offset = offset,
                isRightSide = false,
                currentSpreadSlot = currentSpreadSlot,
                spreadSlotCount = spreadSlotCount,
                latestCustomTapZonesEnabled = latestCustomTapZonesEnabled,
                latestTapZoneActions = latestTapZoneActions,
                latestTapToScrollEnabled = latestTapToScrollEnabled,
                latestRendererConfig = latestRendererConfig,
                transitionStyle = transitionStyle,
                latestToggleUi = latestToggleUi,
                onTurnForward = ::turnForward,
                onTurnBackward = ::turnBackward,
                latestOpenPreviousChapter = latestOpenPreviousChapter,
                latestOpenNextChapter = latestOpenNextChapter,
            )
        },
    )
    val rightPageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            handleSpreadCustomTap(
                size = size,
                offset = offset,
                isRightSide = true,
                currentSpreadSlot = currentSpreadSlot,
                spreadSlotCount = spreadSlotCount,
                latestCustomTapZonesEnabled = latestCustomTapZonesEnabled,
                latestTapZoneActions = latestTapZoneActions,
                latestTapToScrollEnabled = latestTapToScrollEnabled,
                latestRendererConfig = latestRendererConfig,
                transitionStyle = transitionStyle,
                latestToggleUi = latestToggleUi,
                onTurnForward = ::turnForward,
                onTurnBackward = ::turnBackward,
                latestOpenPreviousChapter = latestOpenPreviousChapter,
                latestOpenNextChapter = latestOpenNextChapter,
            )
        },
    )

    SideEffect {
        leftPageCurlConfig.backPageColor = rendererConfig.backPageColor
        leftPageCurlConfig.backPageContentAlpha = 0f
        leftPageCurlConfig.shadowColor = rendererConfig.shadowColor
        leftPageCurlConfig.shadowAlpha = rendererConfig.preset.shadowAlpha
        leftPageCurlConfig.shadowRadius = rendererConfig.shadowRadiusDp.dp
        leftPageCurlConfig.shadowOffset = DpOffset(rendererConfig.shadowOffsetXDp.dp, 0.dp)
        // The library's own drag-success target (dragTargetReachFraction) is tuned for a
        // full-width single page (it can sit well past the halfway point). Each spread surface is
        // only half that width, so the fold's release target has to be the spine itself — the
        // reach a drag needs to travel to at most doubles compared to a full-width page.
        val spineReach = 0.5f
        // The left surface is mirrored (see SpreadColumnCurl), so the library's own forward
        // mechanism — which always references size.width internally — is what turnBackward() drives
        // here; the fold still lives at the outer (left) edge of the screen once un-mirrored. The
        // library's backward mechanism is unused on this surface.
        leftPageCurlConfig.dragForwardEnabled = currentVirtualSlot > 0
        leftPageCurlConfig.dragBackwardEnabled = false
        leftPageCurlConfig.tapBackwardEnabled = false
        leftPageCurlConfig.tapForwardEnabled = latestTapToScrollEnabled && currentSpreadSlot > 0
        leftPageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
        leftPageCurlConfig.dragInteraction = PageCurlConfig.StartEndDragInteraction(
            pointerBehavior = rendererConfig.dragPointerBehavior,
            backward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(0f, 0f, 0f, 1f),
                end = Rect(0f, 0f, 0f, 1f),
            ),
            forward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(1f - rendererConfig.dragActivationEdgeFraction, 0f, 1f, 1f),
                end = Rect(0f, 0f, spineReach, 1f),
            ),
        )
        leftPageCurlConfig.tapInteraction = tapInteraction

        rightPageCurlConfig.backPageColor = rendererConfig.backPageColor
        rightPageCurlConfig.backPageContentAlpha = 0f
        rightPageCurlConfig.shadowColor = rendererConfig.shadowColor
        rightPageCurlConfig.shadowAlpha = rendererConfig.preset.shadowAlpha
        rightPageCurlConfig.shadowRadius = rendererConfig.shadowRadiusDp.dp
        rightPageCurlConfig.shadowOffset = DpOffset(rendererConfig.shadowOffsetXDp.dp, 0.dp)
        // The right surface only ever turns forward: its fold lives at the outer (right) edge.
        rightPageCurlConfig.dragForwardEnabled = currentVirtualSlot < virtualSlotCount - 1
        rightPageCurlConfig.dragBackwardEnabled = false
        rightPageCurlConfig.tapBackwardEnabled = false
        rightPageCurlConfig.tapForwardEnabled = latestTapToScrollEnabled && currentSpreadSlot < spreadSlotCount - 1
        rightPageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
        rightPageCurlConfig.dragInteraction = PageCurlConfig.StartEndDragInteraction(
            pointerBehavior = rendererConfig.dragPointerBehavior,
            backward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(0f, 0f, 0f, 1f),
                end = Rect(0f, 0f, 0f, 1f),
            ),
            forward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(1f - rendererConfig.dragActivationEdgeFraction, 0f, 1f, 1f),
                end = Rect(0f, 0f, spineReach, 1f),
            ),
        )
        rightPageCurlConfig.tapInteraction = tapInteraction
    }

    // Chapter/content changes reset both surfaces to the same slot, exactly like the single-page
    // renderer's equivalent effect.
    LaunchedEffect(chapterId, pagerCurrentSlot, spreadSlotCount, hasPreviousChapter) {
        val targetVirtualSlot = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = pagerCurrentSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
        val invertedTarget = (virtualSlotCount - 1 - targetVirtualSlot).coerceAtLeast(0)
        if (leftCurlState.current != invertedTarget) leftCurlState.snapTo(invertedTarget)
        if (rightCurlState.current != targetVirtualSlot) rightCurlState.snapTo(targetVirtualSlot)
    }

    var consumedBoundaryNavigation by remember(chapterId, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        mutableStateOf<HorizontalChapterSwipeAction?>(null)
    }
    // Drives boundary detection and the paired-surface snap off the LEFT surface's settled state:
    // both surfaces always share one virtual slot, so either one would do, but exactly one has to
    // own it to avoid double-firing chapter navigation.
    LaunchedEffect(leftCurlState, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) to leftCurlState.progress }
            .distinctUntilChanged()
            .collectLatest { (targetVirtualSlot, progress) ->
                val realTarget = (virtualSlotCount - 1 - targetVirtualSlot).coerceAtLeast(0)
                when (
                    resolvePageTurnRendererSettledBoundaryChapterTarget(
                        currentPage = realTarget,
                        progress = progress,
                        contentPageCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )
                ) {
                    HorizontalChapterSwipeAction.PREVIOUS -> {
                        if (consumedBoundaryNavigation != HorizontalChapterSwipeAction.PREVIOUS) {
                            consumedBoundaryNavigation = HorizontalChapterSwipeAction.PREVIOUS
                            latestOpenPreviousChapter()
                        }
                    }
                    HorizontalChapterSwipeAction.NEXT -> {
                        if (consumedBoundaryNavigation != HorizontalChapterSwipeAction.NEXT) {
                            consumedBoundaryNavigation = HorizontalChapterSwipeAction.NEXT
                            latestOpenNextChapter()
                        }
                    }
                    HorizontalChapterSwipeAction.NONE -> {}
                }
            }
    }

    // A live drag on the right surface (forward turn) is the library's own gesture handling, not
    // the turnForward()/turnBackward() helpers above, so the left surface would never learn the
    // drag happened. Watching progress here keeps it in lockstep regardless of what drove the
    // move: a drag, a tap, or requestedPage.
    LaunchedEffect(rightCurlState, virtualSlotCount) {
        snapshotFlow { rightCurlState.current.coerceIn(0, virtualSlotCount - 1) to rightCurlState.progress }
            .distinctUntilChanged()
            .collectLatest { (target, progress) ->
                val invertedTarget = (virtualSlotCount - 1 - target).coerceAtLeast(0)
                if (progress == 0f && leftCurlState.current != invertedTarget) {
                    leftCurlState.snapTo(invertedTarget)
                }
            }
    }
    LaunchedEffect(leftCurlState, virtualSlotCount) {
        snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) to leftCurlState.progress }
            .distinctUntilChanged()
            .collectLatest { (target, progress) ->
                val realTarget = (virtualSlotCount - 1 - target).coerceAtLeast(0)
                if (progress == 0f && rightCurlState.current != realTarget) {
                    rightCurlState.snapTo(realTarget)
                }
            }
    }

    LaunchedEffect(leftCurlState, pagerState, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) }
            .distinctUntilChanged()
            .collectLatest { targetVirtualSlot ->
                val realTarget = (virtualSlotCount - 1 - targetVirtualSlot).coerceAtLeast(0)
                val boundaryTarget = resolvePageTurnRendererBoundaryChapterTarget(
                    currentPage = realTarget,
                    contentPageCount = spreadSlotCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
                if (boundaryTarget == HorizontalChapterSwipeAction.NONE) {
                    val targetSpreadSlot = resolvePageTurnRendererProgressPageIndex(
                        currentPage = realTarget,
                        contentPageCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                    )
                    latestCurrentPageChange(resolveSpreadSlotFirstPageIndex(targetSpreadSlot, 2))
                    if (targetSpreadSlot != pagerState.currentPage) {
                        pagerState.scrollToPage(targetSpreadSlot)
                    }
                }
            }
    }

    LaunchedEffect(latestRequestedPage, spreadSlotCount, hasPreviousChapter) {
        val targetPage = latestRequestedPage
        if (targetPage < 0 || safeContentPages.isEmpty()) return@LaunchedEffect
        val targetSpreadSlot = resolveSpreadSlotForPageIndex(
            targetPage.coerceIn(0, safeContentPages.lastIndex),
            2,
        )
        val clampedTarget = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = targetSpreadSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
        val invertedTarget = (virtualSlotCount - 1 - clampedTarget).coerceAtLeast(0)
        if (leftCurlState.current != invertedTarget) leftCurlState.snapTo(invertedTarget)
        if (rightCurlState.current != clampedTarget) rightCurlState.snapTo(clampedTarget)
        latestRequestedPageConsumed()
    }

    LaunchedEffect(latestChapterNavigationRequest, transitionStyle) {
        val request = latestChapterNavigationRequest ?: return@LaunchedEffect
        if (transitionStyle != NovelPageTransitionStyle.CURL) {
            latestChapterNavigationRequestConsumed()
            return@LaunchedEffect
        }
        when (request.direction) {
            PageTurnChapterNavigationDirection.PREVIOUS -> turnBackward()
            PageTurnChapterNavigationDirection.NEXT -> turnForward()
        }
        latestChapterNavigationRequestConsumed()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val halfPageSize = IntSize(
            width = with(density) { (maxWidth / 2).roundToPx() }.coerceAtLeast(1),
            height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1),
        )
        val contentPaddingPx = with(density) { contentPadding.roundToPx() }
        val statusBarTopPaddingPx = with(density) { statusBarTopPadding.roundToPx() }
        val snapshotCache = remember(
            halfPageSize,
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
            NovelPageTurnSnapshotCache<ImageBitmap>(maxSize = 6)
        }

        Row(modifier = Modifier.fillMaxSize()) {
            SpreadColumnCurl(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .zIndex(if (leftCurlState.progress > 0f) 1f else 0f),
                mirrored = true,
                invertPage = true,
                columnOffset = 0,
                pageCurlState = leftCurlState,
                pageCurlConfig = leftPageCurlConfig,
                virtualSlotCount = virtualSlotCount,
                spreadSlotCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                showBoundaryChapterPages = showBoundaryChapterPages,
                previousChapterLabel = previousChapterLabel,
                nextChapterLabel = nextChapterLabel,
                previousChapterName = previousChapterName,
                nextChapterName = nextChapterName,
                boundaryChapterHint = boundaryChapterHint,
                safeContentPages = safeContentPages,
                rendererConfig = rendererConfig,
                pageSize = halfPageSize,
                snapshotCache = snapshotCache,
                contentPaddingPx = contentPaddingPx,
                statusBarTopPaddingPx = statusBarTopPaddingPx,
                readerSettings = readerSettings,
                textColor = textColor,
                textBackground = textBackground,
                chapterTitleTextColor = chapterTitleTextColor,
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                backgroundImageModel = backgroundImageModel,
                backgroundModeIdentity = backgroundModeIdentity,
                isBackgroundMode = isBackgroundMode,
                activeBackgroundTexture = activeBackgroundTexture,
                activeOledEdgeGradient = activeOledEdgeGradient,
                isDarkTheme = isDarkTheme,
                pageEdgeShadow = pageEdgeShadow,
                pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                contentPadding = contentPadding,
                statusBarTopPadding = statusBarTopPadding,
                ttsHighlightState = ttsHighlightState,
                ttsHighlightColor = ttsHighlightColor,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onTextTap = onTextTap,
            )
            SpreadColumnCurl(
                modifier = Modifier
                    .fillMaxWidth(1f)
                    .zIndex(if (rightCurlState.progress > 0f) 1f else 0f),
                mirrored = false,
                invertPage = false,
                columnOffset = 1,
                pageCurlState = rightCurlState,
                pageCurlConfig = rightPageCurlConfig,
                virtualSlotCount = virtualSlotCount,
                spreadSlotCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                showBoundaryChapterPages = showBoundaryChapterPages,
                previousChapterLabel = previousChapterLabel,
                nextChapterLabel = nextChapterLabel,
                previousChapterName = previousChapterName,
                nextChapterName = nextChapterName,
                boundaryChapterHint = boundaryChapterHint,
                safeContentPages = safeContentPages,
                rendererConfig = rendererConfig,
                pageSize = halfPageSize,
                snapshotCache = snapshotCache,
                contentPaddingPx = contentPaddingPx,
                statusBarTopPaddingPx = statusBarTopPaddingPx,
                readerSettings = readerSettings,
                textColor = textColor,
                textBackground = textBackground,
                chapterTitleTextColor = chapterTitleTextColor,
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                backgroundImageModel = backgroundImageModel,
                backgroundModeIdentity = backgroundModeIdentity,
                isBackgroundMode = isBackgroundMode,
                activeBackgroundTexture = activeBackgroundTexture,
                activeOledEdgeGradient = activeOledEdgeGradient,
                isDarkTheme = isDarkTheme,
                pageEdgeShadow = pageEdgeShadow,
                pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                contentPadding = contentPadding,
                statusBarTopPadding = statusBarTopPadding,
                ttsHighlightState = ttsHighlightState,
                ttsHighlightColor = ttsHighlightColor,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onTextTap = onTextTap,
            )
        }
    }
}

/** A snap()-driven jump: the paired, non-animating side of a turn lands on its new page instantly. */
private fun instantPageTurnAnimation(): suspend Animatable<Edge, AnimationVector4D>.(Size) -> Unit {
    return { size ->
        animateTo(targetValue = size.startEdge(), animationSpec = snap())
    }
}

private fun handleSpreadCustomTap(
    size: IntSize,
    offset: Offset,
    isRightSide: Boolean,
    currentSpreadSlot: Int,
    spreadSlotCount: Int,
    latestCustomTapZonesEnabled: Boolean,
    latestTapZoneActions: List<NovelReaderTapZoneAction>,
    latestTapToScrollEnabled: Boolean,
    latestRendererConfig: NovelPageTurnRendererConfig,
    transitionStyle: NovelPageTransitionStyle,
    latestToggleUi: () -> Unit,
    onTurnForward: () -> Unit,
    onTurnBackward: () -> Unit,
    latestOpenPreviousChapter: () -> Unit,
    latestOpenNextChapter: () -> Unit,
): Boolean {
    val fullWidth = size.width * 2f
    val absoluteTapX = if (isRightSide) {
        size.width + offset.x
    } else {
        size.width - offset.x
    }

    val customTapAction = if (latestCustomTapZonesEnabled) {
        resolvePageTurnConfiguredTapAction(
            zoneAction = resolveConfiguredNovelReaderTapAction(
                tapX = absoluteTapX,
                tapY = offset.y,
                width = fullWidth,
                height = size.height.toFloat(),
                customTapZonesEnabled = true,
                tapZoneActions = latestTapZoneActions,
                tapToScrollEnabled = latestTapToScrollEnabled,
            ),
            currentPage = currentSpreadSlot,
            pageCount = spreadSlotCount.coerceAtLeast(1),
            hasPreviousChapter = latestRendererConfig.dragBackwardEnabled,
            hasNextChapter = latestRendererConfig.dragForwardEnabled,
            animateBoundaryTransition = transitionStyle == NovelPageTransitionStyle.CURL,
        )
    } else {
        resolvePageTurnCustomTapAction(
            tapXFraction = if (fullWidth > 0) absoluteTapX / fullWidth else 0.5f,
            currentPage = currentSpreadSlot,
            pageCount = spreadSlotCount.coerceAtLeast(1),
            centerTapWidthFraction = latestRendererConfig.centerTapWidthFraction,
            hasPreviousChapter = latestRendererConfig.dragBackwardEnabled,
            hasNextChapter = latestRendererConfig.dragForwardEnabled,
            tapToScrollEnabled = latestTapToScrollEnabled,
            animateBoundaryTransition = transitionStyle == NovelPageTransitionStyle.CURL,
        )
    }
    return when (customTapAction) {
        PageTurnCustomTapAction.TOGGLE_UI -> {
            latestToggleUi()
            true
        }
        PageTurnCustomTapAction.MOVE_PREVIOUS_PAGE -> {
            onTurnBackward()
            true
        }
        PageTurnCustomTapAction.MOVE_NEXT_PAGE -> {
            onTurnForward()
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
}

@Composable
private fun SpreadColumnCurl(
    modifier: Modifier,
    // eu.wewox.pagecurl.page.PageCurl's own drag/animation geometry always references the widget's
    // right edge (NewEdgeCreator anchors on size.width, PageCurlState.setup's forward Animatable
    // always runs right-to-left): using the library's backward mechanism unmirrored opens the
    // current page from its own left edge outward instead of bringing a new page in from the left
    // like a book leaf. Mirroring the whole widget horizontally (and un-mirroring its content) makes
    // the library's own right-referenced geometry land on the screen's actual left edge, so this
    // surface's fold anchors at the spine the same way the unmirrored right surface already does.
    mirrored: Boolean,
    invertPage: Boolean,
    columnOffset: Int,
    pageCurlState: PageCurlState,
    pageCurlConfig: PageCurlConfig,
    virtualSlotCount: Int,
    spreadSlotCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    showBoundaryChapterPages: Boolean,
    previousChapterLabel: String,
    nextChapterLabel: String,
    previousChapterName: String?,
    nextChapterName: String?,
    boundaryChapterHint: String,
    safeContentPages: List<NovelPageContentPage>,
    rendererConfig: NovelPageTurnRendererConfig,
    pageSize: IntSize,
    snapshotCache: NovelPageTurnSnapshotCache<ImageBitmap>,
    contentPaddingPx: Int,
    statusBarTopPaddingPx: Int,
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
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    ttsHighlightState: NovelReaderTtsHighlightState?,
    ttsHighlightColor: Color,
    selectionSessionIdProvider: () -> Long,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    onTextTap: (Float, Float, Float, Float) -> Unit,
) {
    val curlModifier = if (mirrored) {
        modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
    } else {
        modifier.fillMaxSize()
    }
    PageCurl(
        count = virtualSlotCount,
        key = { it },
        state = pageCurlState,
        config = pageCurlConfig,
        modifier = curlModifier,
    ) { page ->
        val actualPage = if (invertPage) (virtualSlotCount - 1 - page).coerceAtLeast(0) else page
        val boundaryPreview = if (!showBoundaryChapterPages) {
            null
        } else {
            when (
                resolvePageTurnRendererBoundaryChapterTarget(
                    currentPage = actualPage,
                    contentPageCount = spreadSlotCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
            ) {
                HorizontalChapterSwipeAction.PREVIOUS,
                HorizontalChapterSwipeAction.NEXT,
                -> {
                    createNovelPageBoundaryPreviewData(
                        chapterLabel = if (actualPage <= 0) previousChapterLabel else nextChapterLabel,
                        chapterName = if (actualPage <= 0) previousChapterName else nextChapterName,
                        chapterHint = boundaryChapterHint,
                    )
                }
                HorizontalChapterSwipeAction.NONE -> null
            }
        }
        val contentPage = if (boundaryPreview == null) {
            val spreadSlot = resolvePageTurnRendererProgressPageIndex(
                currentPage = actualPage,
                contentPageCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
            )
            val firstPage = resolveSpreadSlotFirstPageIndex(spreadSlot, 2)
            safeContentPages.getOrElse(firstPage + columnOffset) { NovelPageContentPage(emptyList()) }
        } else {
            NovelPageContentPage(emptyList())
        }
        val pageTexture = if (isBackgroundMode) activeBackgroundTexture else backgroundTexture
        val pageTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent
        val pageSurfaceColor = if (isBackgroundMode) null else rendererConfig.backPageColor
        val pageContentIdentity = boundaryPreview ?: contentPage
        val pageSnapshotKey = resolveNovelPageTurnSnapshotKey(
            style = rendererConfig.style,
            // The two surfaces address independent PageCurl instances with their own page-index
            // spaces, so columnOffset has to be part of the key or they would collide in the
            // shared cache despite showing different halves of the spread.
            pageIndex = actualPage * 2 + columnOffset,
            pageCount = virtualSlotCount * 2,
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
                TextAlign.CENTER
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
            pageEdgeShadow = pageEdgeShadow,
            pageEdgeShadowAlpha = pageEdgeShadowAlpha,
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
        val contentModifier = if (mirrored) {
            Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
        } else {
            Modifier.fillMaxSize()
        }
        NovelPageTurnSnapshotRenderer(
            snapshotKey = pageSnapshotKey,
            snapshotCache = snapshotCache,
            preferCachedBitmap = false,
            modifier = contentModifier,
        ) {
            NovelAtmosphereBackground(
                backgroundColor = textBackground,
                backgroundTexture = pageTexture,
                nativeTextureStrengthPercent = pageTextureStrengthPercent,
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
                    ttsHighlightState = ttsHighlightState,
                    ttsHighlightColor = ttsHighlightColor,
                    selectionSessionIdProvider = selectionSessionIdProvider,
                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                    onPlainTap = onTextTap,
                    touchHandlingEnabled = false,
                )
            }
        }
    }
}
