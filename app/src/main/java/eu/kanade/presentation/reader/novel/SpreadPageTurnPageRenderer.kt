@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.parseNovelReaderTapZoneActions
import eu.kanade.tachiyomi.ui.reader.novel.setting.resolveConfiguredNovelReaderTapAction
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.Edge
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.PageCurlState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    // Horizontal (Dp) reserved on the short edges of a spread so text stays clear of a display
    // cutout. Non-zero only when two-page landscape expands to the screen edges.
    spreadCutoutLeftDp: Dp = 0.dp,
    spreadCutoutRightDp: Dp = 0.dp,
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
    seamlessBoundaryPages: Boolean = false,
    previousChapterPreview: SeamlessAdjacentPagesSlice = SeamlessAdjacentPagesSlice(),
    nextChapterPreview: SeamlessAdjacentPagesSlice = SeamlessAdjacentPagesSlice(),
    suppressBoundaryChapterNavigation: Boolean = false,
    syncedContentPageIndex: Int? = null,
    onToggleUi: () -> Unit,
    requestedPage: Int,
    onRequestedPageConsumed: () -> Unit,
    onCurrentPageChange: (Int) -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    chapterNavigationRequest: PageTurnChapterNavigationRequest? = null,
    onChapterNavigationRequestConsumed: () -> Unit = {},
    onTextTap: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> onToggleUi() },
    onImageClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    layoutGeneration: Long = 0L,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hasPreviousChapterNavigation = hasPreviousChapter
        val hasNextChapterNavigation = hasNextChapter
        val hasBoundarySlots = resolvePageTurnBoundarySlotsEnabled(
            showBoundaryChapterPages = showBoundaryChapterPages,
            seamlessBoundaryPages = seamlessBoundaryPages,
        )

        @Suppress("NAME_SHADOWING")
        val hasPreviousChapter = hasPreviousChapterNavigation && hasBoundarySlots

        @Suppress("NAME_SHADOWING")
        val hasNextChapter = hasNextChapterNavigation && hasBoundarySlots

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
        val syncedSlot = resolvePageTurnRendererSyncedSlot(
            syncedContentPageIndex = syncedContentPageIndex,
            contentPageCount = safeContentPages.size,
            spreadColumns = 2,
        )
        val initialVirtualSlot = remember(chapterId) {
            resolvePageTurnRendererVirtualPageIndex(
                actualPageIndex = syncedSlot,
                hasPreviousChapter = hasPreviousChapter,
            )
        }

        // Both surfaces are seeded at the same virtual slot and only ever move together (see the two
        // LaunchedEffects below), so they never observe each other's page content out of step.
        val runtime = rememberSaveable(chapterId, initialVirtualSlot, saver = NovelPageTurnRuntime.Saver) {
            NovelPageTurnRuntime(
                primaryState = PageCurlState(
                    initialCurrent = (virtualSlotCount - 1 - initialVirtualSlot).coerceAtLeast(0),
                ),
                secondaryState = PageCurlState(initialCurrent = initialVirtualSlot),
            )
        }
        val generationSlot = if (requestedPage >= 0) {
            resolveSpreadSlotForPageIndex(requestedPage.coerceIn(0, safeContentPages.lastIndex), 2)
        } else {
            syncedSlot
        }
        val generationVirtualSlot = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = generationSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
        val generation = runtime.generation(
            layoutKey = NovelPageTurnLayoutKey(
                chapterId = chapterId,
                layoutGeneration = layoutGeneration,
                constraints = constraints,
                contentPages = contentPages,
                spreadColumns = 2,
                virtualPageCount = virtualSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                horizontalPadding = DpOffset(spreadCutoutLeftDp, spreadCutoutRightDp),
            ),
            initialPrimaryPage = (virtualSlotCount - 1 - generationVirtualSlot).coerceAtLeast(0),
            initialSecondaryPage = generationVirtualSlot,
            requestedPage = requestedPage,
        )

        key(generation) {
            DisposableEffect(generation) { onDispose { generation.dispose() } }
            val leftCurlState = generation.primaryState
            val rightCurlState = checkNotNull(generation.secondaryState)
            val currentVirtualSlot = (virtualSlotCount - 1 - leftCurlState.current).coerceIn(0, virtualSlotCount - 1)
            val currentSpreadSlot = resolvePageTurnRendererProgressPageIndex(
                currentPage = currentVirtualSlot,
                contentPageCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
            )

            val guardedSelectionChanged by rememberUpdatedState<(NovelSelectedTextSelection?) -> Unit> { selection ->
                generation.publish { onSelectedTextSelectionChanged(selection) }
            }
            val guardedTextTap by rememberUpdatedState<(Float, Float, Float, Float) -> Unit> { x, y, width, height ->
                generation.publish { onTextTap(x, y, width, height) }
            }
            val guardedImageClick by rememberUpdatedState<((String) -> Unit)?> { url ->
                generation.publish { onImageClick?.invoke(url) }
            }
            val guardedImageLongClick by rememberUpdatedState<((String) -> Unit)?> { url ->
                generation.publish { onImageLongClick?.invoke(url) }
            }
            val guardedUrlClick by rememberUpdatedState<((String) -> Unit)?> { url ->
                generation.publish { onUrlClick?.invoke(url) }
            }
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
            val spreadDragInteraction = remember(rendererConfig) {
                // The library's own drag-success target (dragTargetReachFraction) is tuned for a
                // full-width single page (it can sit well past the halfway point). Each spread surface is
                // only half that width, so the fold's release target has to be the spine itself — the
                // reach a drag needs to travel to at most doubles compared to a full-width page.
                val spineReach = 0.5f
                PageCurlConfig.StartEndDragInteraction(
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
            }

            val latestRequestedPage by rememberUpdatedState(requestedPage)
            val latestRequestedPageConsumed by rememberUpdatedState({ generation.publish(onRequestedPageConsumed) })
            val latestCurrentPageChange by rememberUpdatedState({ page: Int ->
                generation.publish { onCurrentPageChange(page) }
            })
            val latestOpenPreviousChapter by rememberUpdatedState({ generation.publish(onOpenPreviousChapter) })
            val latestOpenNextChapter by rememberUpdatedState({ generation.publish(onOpenNextChapter) })
            val latestChapterNavigationRequest by rememberUpdatedState(chapterNavigationRequest)
            val latestChapterNavigationRequestConsumed by rememberUpdatedState({
                generation.publish(onChapterNavigationRequestConsumed)
            })
            val latestRendererConfig by rememberUpdatedState(rendererConfig)
            val latestToggleUi by rememberUpdatedState({ generation.publish(onToggleUi) })
            val latestTapToScrollEnabled by rememberUpdatedState(readerSettings.tapToScroll)
            val latestCustomTapZonesEnabled by rememberUpdatedState(readerSettings.customTapZones)
            val latestTapZoneActions by rememberUpdatedState(
                parseNovelReaderTapZoneActions(readerSettings.tapZoneActions),
            )
            val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapterNavigation)
            val latestHasNextChapter by rememberUpdatedState(hasNextChapterNavigation)
            val latestSuppressBoundaryNavigation by rememberUpdatedState(suppressBoundaryChapterNavigation)
            val latestAnimateBoundaryTransition by rememberUpdatedState(
                resolvePageTurnAnimateBoundaryTransition(
                    transitionStyle = transitionStyle,
                    showBoundaryChapterPages = showBoundaryChapterPages,
                    seamlessBoundaryPages = seamlessBoundaryPages,
                ),
            )

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
                    val liveVirtualSlot = (virtualSlotCount - 1 - leftCurlState.current).coerceIn(
                        0,
                        virtualSlotCount - 1,
                    )
                    val liveSpreadSlot = resolvePageTurnRendererProgressPageIndex(
                        currentPage = liveVirtualSlot,
                        contentPageCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                    )
                    handleSpreadCustomTap(
                        size = size,
                        offset = offset,
                        isRightSide = false,
                        currentSpreadSlot = liveSpreadSlot,
                        spreadSlotCount = spreadSlotCount,
                        latestCustomTapZonesEnabled = latestCustomTapZonesEnabled,
                        latestTapZoneActions = latestTapZoneActions,
                        latestTapToScrollEnabled = latestTapToScrollEnabled,
                        latestRendererConfig = latestRendererConfig,
                        animateBoundaryTransition = latestAnimateBoundaryTransition,
                        latestToggleUi = latestToggleUi,
                        onTurnForward = ::turnForward,
                        onTurnBackward = ::turnBackward,
                        hasPreviousChapter = latestHasPreviousChapter,
                        hasNextChapter = latestHasNextChapter,
                        latestOpenPreviousChapter = latestOpenPreviousChapter,
                        latestOpenNextChapter = latestOpenNextChapter,
                    )
                },
            )
            val rightPageCurlConfig = rememberPageCurlConfig(
                onCustomTap = { size, offset ->
                    val liveVirtualSlot = (virtualSlotCount - 1 - leftCurlState.current).coerceIn(
                        0,
                        virtualSlotCount - 1,
                    )
                    val liveSpreadSlot = resolvePageTurnRendererProgressPageIndex(
                        currentPage = liveVirtualSlot,
                        contentPageCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                    )
                    handleSpreadCustomTap(
                        size = size,
                        offset = offset,
                        isRightSide = true,
                        currentSpreadSlot = liveSpreadSlot,
                        spreadSlotCount = spreadSlotCount,
                        latestCustomTapZonesEnabled = latestCustomTapZonesEnabled,
                        latestTapZoneActions = latestTapZoneActions,
                        latestTapToScrollEnabled = latestTapToScrollEnabled,
                        latestRendererConfig = latestRendererConfig,
                        animateBoundaryTransition = latestAnimateBoundaryTransition,
                        latestToggleUi = latestToggleUi,
                        onTurnForward = ::turnForward,
                        onTurnBackward = ::turnBackward,
                        hasPreviousChapter = latestHasPreviousChapter,
                        hasNextChapter = latestHasNextChapter,
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
                // The left surface is mirrored (see SpreadColumnCurl), so the library's own forward
                // mechanism — which always references size.width internally — is what turnBackward() drives
                // here; the fold still lives at the outer (left) edge of the screen once un-mirrored. The
                // library's backward mechanism is unused on this surface.
                leftPageCurlConfig.dragForwardEnabled = currentVirtualSlot > 0
                leftPageCurlConfig.dragBackwardEnabled = false
                leftPageCurlConfig.tapBackwardEnabled = false
                leftPageCurlConfig.tapForwardEnabled = resolvePageTurnBuiltInTapNavigationEnabled(
                    customTapZonesEnabled = latestCustomTapZonesEnabled,
                    tapToScrollEnabled = latestTapToScrollEnabled,
                    canMoveInDirection = currentSpreadSlot > 0,
                )
                leftPageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
                leftPageCurlConfig.dragInteraction = spreadDragInteraction
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
                rightPageCurlConfig.tapForwardEnabled = resolvePageTurnBuiltInTapNavigationEnabled(
                    customTapZonesEnabled = latestCustomTapZonesEnabled,
                    tapToScrollEnabled = latestTapToScrollEnabled,
                    canMoveInDirection = currentSpreadSlot < spreadSlotCount - 1,
                )
                rightPageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
                rightPageCurlConfig.dragInteraction = spreadDragInteraction
                rightPageCurlConfig.tapInteraction = tapInteraction
            }

            // Chapter/content changes reset both surfaces to the synced slot without consulting
            // pagerState (HorizontalPager is unmounted in this route, so pagerState stays stale at 0).
            LaunchedEffect(chapterId, syncedSlot, spreadSlotCount, hasPreviousChapter) {
                if (!generation.isActive) return@LaunchedEffect
                if (
                    latestSuppressBoundaryNavigation ||
                    latestRequestedPage >= 0 ||
                    leftCurlState.progress > 0f ||
                    rightCurlState.progress > 0f
                ) {
                    return@LaunchedEffect
                }
                val targetVirtualSlot = resolvePageTurnRendererVirtualPageIndex(
                    actualPageIndex = syncedSlot,
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
                if (!generation.isActive) return@LaunchedEffect
                snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) to leftCurlState.progress }
                    .distinctUntilChanged()
                    .collectLatest { (targetVirtualSlot, progress) ->
                        if (!generation.isActive || latestSuppressBoundaryNavigation ||
                            latestRequestedPage >= 0
                        ) {
                            return@collectLatest
                        }
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
                            HorizontalChapterSwipeAction.NONE -> {
                                consumedBoundaryNavigation = null
                            }
                        }
                    }
            }

            // A live drag on the right surface (forward turn) is the library's own gesture handling, not
            // the turnForward()/turnBackward() helpers above, so the left surface would never learn the
            // drag happened. Watching progress here keeps it in lockstep regardless of what drove the
            // move: a drag, a tap, or requestedPage.
            LaunchedEffect(rightCurlState, virtualSlotCount) {
                if (!generation.isActive) return@LaunchedEffect
                snapshotFlow { rightCurlState.current.coerceIn(0, virtualSlotCount - 1) to rightCurlState.progress }
                    .distinctUntilChanged()
                    .collectLatest { (target, progress) ->
                        if (!generation.isActive || latestSuppressBoundaryNavigation ||
                            latestRequestedPage >= 0
                        ) {
                            return@collectLatest
                        }
                        val invertedTarget = (virtualSlotCount - 1 - target).coerceAtLeast(0)
                        if (progress == 0f && leftCurlState.current != invertedTarget) {
                            leftCurlState.snapTo(invertedTarget)
                        }
                    }
            }
            LaunchedEffect(leftCurlState, virtualSlotCount) {
                if (!generation.isActive) return@LaunchedEffect
                snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) to leftCurlState.progress }
                    .distinctUntilChanged()
                    .collectLatest { (target, progress) ->
                        if (!generation.isActive || latestSuppressBoundaryNavigation ||
                            latestRequestedPage >= 0
                        ) {
                            return@collectLatest
                        }
                        val realTarget = (virtualSlotCount - 1 - target).coerceAtLeast(0)
                        if (progress == 0f && rightCurlState.current != realTarget) {
                            rightCurlState.snapTo(realTarget)
                        }
                    }
            }

            LaunchedEffect(leftCurlState, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
                if (!generation.isActive) return@LaunchedEffect
                snapshotFlow {
                    Triple(
                        leftCurlState.current.coerceIn(0, virtualSlotCount - 1),
                        latestSuppressBoundaryNavigation,
                        latestRequestedPage,
                    )
                }
                    .distinctUntilChanged()
                    .collectLatest { (targetVirtualSlot, suppressNavigation, pendingRequestedPage) ->
                        if (!generation.isActive || suppressNavigation ||
                            pendingRequestedPage >= 0
                        ) {
                            return@collectLatest
                        }
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
                        }
                    }
            }

            LaunchedEffect(latestRequestedPage, spreadSlotCount, hasPreviousChapter) {
                if (!generation.isActive) return@LaunchedEffect
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
                latestCurrentPageChange(resolveSpreadSlotFirstPageIndex(targetSpreadSlot, 2))
                latestRequestedPageConsumed()
            }

            LaunchedEffect(latestChapterNavigationRequest, transitionStyle) {
                if (!generation.isActive) return@LaunchedEffect
                val request = latestChapterNavigationRequest ?: return@LaunchedEffect
                if (resolvePageTransitionEngine(transitionStyle) != NovelPageTransitionEngine.PAGE_TURN_RENDERER) {
                    latestChapterNavigationRequestConsumed()
                    return@LaunchedEffect
                }
                val liveVirtualSlot = (virtualSlotCount - 1 - leftCurlState.current).coerceIn(0, virtualSlotCount - 1)
                when (request.direction) {
                    PageTurnChapterNavigationDirection.PREVIOUS -> {
                        if (liveVirtualSlot > 0) {
                            turnBackward()
                        } else if (latestHasPreviousChapter) {
                            latestOpenPreviousChapter()
                        }
                    }
                    PageTurnChapterNavigationDirection.NEXT -> {
                        if (liveVirtualSlot < virtualSlotCount - 1) {
                            turnForward()
                        } else if (latestHasNextChapter) {
                            latestOpenNextChapter()
                        }
                    }
                }
                latestChapterNavigationRequestConsumed()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                NovelAtmosphereBackground(
                    backgroundColor = textBackground,
                    backgroundTexture = if (isBackgroundMode) activeBackgroundTexture else backgroundTexture,
                    nativeTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent,
                    oledEdgeGradient = activeOledEdgeGradient,
                    isDarkTheme = isDarkTheme,
                    pageEdgeShadow = pageEdgeShadow,
                    pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                    backgroundImageModel = backgroundImageModel,
                )
                val isSpreadSettled by remember(leftCurlState, rightCurlState) {
                    derivedStateOf {
                        abs(leftCurlState.progress) < 0.001f &&
                            abs(rightCurlState.progress) < 0.001f &&
                            !latestSuppressBoundaryNavigation
                    }
                }
                val leftZIndex by remember(leftCurlState) {
                    derivedStateOf { if (leftCurlState.progress > 0f) 1f else 0f }
                }
                val rightZIndex by remember(rightCurlState) {
                    derivedStateOf { if (rightCurlState.progress > 0f) 1f else 0f }
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .absolutePadding(left = spreadCutoutLeftDp, right = spreadCutoutRightDp),
                ) {
                    SpreadColumnCurl(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .zIndex(leftZIndex),
                        mirrored = true,
                        invertPage = true,
                        columnOffset = 0,
                        isSpreadSettled = isSpreadSettled,
                        pageCurlState = leftCurlState,
                        pageCurlConfig = leftPageCurlConfig,
                        virtualSlotCount = virtualSlotCount,
                        spreadSlotCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                        showBoundaryChapterPages = showBoundaryChapterPages,
                        previousChapterPreview = previousChapterPreview,
                        nextChapterPreview = nextChapterPreview,
                        previousChapterLabel = previousChapterLabel,
                        nextChapterLabel = nextChapterLabel,
                        previousChapterName = previousChapterName,
                        nextChapterName = nextChapterName,
                        boundaryChapterHint = boundaryChapterHint,
                        safeContentPages = safeContentPages,
                        chapterId = chapterId,
                        rendererConfig = rendererConfig,
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
                        statusBarTopPadding = 0.dp,
                        ttsHighlightState = ttsHighlightState,
                        ttsHighlightColor = ttsHighlightColor,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        onSelectedTextSelectionChanged = guardedSelectionChanged,
                        onTextTap = guardedTextTap,
                        onImageClick = if (onImageClick != null) guardedImageClick else null,
                        onImageLongClick = if (onImageLongClick != null) guardedImageLongClick else null,
                        onUrlClick = if (onUrlClick != null) guardedUrlClick else null,
                    )
                    SpreadColumnCurl(
                        modifier = Modifier
                            .fillMaxWidth(1f)
                            .zIndex(rightZIndex),
                        mirrored = false,
                        invertPage = false,
                        columnOffset = 1,
                        isSpreadSettled = isSpreadSettled,
                        pageCurlState = rightCurlState,
                        pageCurlConfig = rightPageCurlConfig,
                        virtualSlotCount = virtualSlotCount,
                        spreadSlotCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                        showBoundaryChapterPages = showBoundaryChapterPages,
                        previousChapterPreview = previousChapterPreview,
                        nextChapterPreview = nextChapterPreview,
                        previousChapterLabel = previousChapterLabel,
                        nextChapterLabel = nextChapterLabel,
                        previousChapterName = previousChapterName,
                        nextChapterName = nextChapterName,
                        boundaryChapterHint = boundaryChapterHint,
                        safeContentPages = safeContentPages,
                        chapterId = chapterId,
                        rendererConfig = rendererConfig,
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
                        statusBarTopPadding = 0.dp,
                        ttsHighlightState = ttsHighlightState,
                        ttsHighlightColor = ttsHighlightColor,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        onSelectedTextSelectionChanged = guardedSelectionChanged,
                        onTextTap = guardedTextTap,
                        onImageClick = if (onImageClick != null) guardedImageClick else null,
                        onImageLongClick = if (onImageLongClick != null) guardedImageLongClick else null,
                        onUrlClick = if (onUrlClick != null) guardedUrlClick else null,
                    )
                }
            }
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
    animateBoundaryTransition: Boolean,
    latestToggleUi: () -> Unit,
    onTurnForward: () -> Unit,
    onTurnBackward: () -> Unit,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
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
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            animateBoundaryTransition = animateBoundaryTransition,
        )
    } else {
        resolvePageTurnCustomTapAction(
            tapXFraction = if (fullWidth > 0) absoluteTapX / fullWidth else 0.5f,
            currentPage = currentSpreadSlot,
            pageCount = spreadSlotCount.coerceAtLeast(1),
            centerTapWidthFraction = latestRendererConfig.centerTapWidthFraction,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            tapToScrollEnabled = latestTapToScrollEnabled,
            animateBoundaryTransition = animateBoundaryTransition,
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
    isSpreadSettled: Boolean,
    pageCurlState: PageCurlState,
    pageCurlConfig: PageCurlConfig,
    virtualSlotCount: Int,
    spreadSlotCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    showBoundaryChapterPages: Boolean,
    previousChapterPreview: SeamlessAdjacentPagesSlice,
    nextChapterPreview: SeamlessAdjacentPagesSlice,
    previousChapterLabel: String,
    nextChapterLabel: String,
    previousChapterName: String?,
    nextChapterName: String?,
    boundaryChapterHint: String,
    safeContentPages: List<NovelPageContentPage>,
    chapterId: Long,
    rendererConfig: NovelPageTurnRendererConfig,
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
    onImageClick: ((String) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
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
        val boundaryTarget = resolvePageTurnRendererBoundaryChapterTarget(
            currentPage = actualPage,
            contentPageCount = spreadSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
        val boundaryPreview = if (!showBoundaryChapterPages) {
            null
        } else {
            when (boundaryTarget) {
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
        val isSeamlessBoundarySlot =
            boundaryTarget != HorizontalChapterSwipeAction.NONE && !showBoundaryChapterPages
        val (seamlessBoundaryPagesForSlot, seamlessBoundarySlice) = if (isSeamlessBoundarySlot) {
            resolveSeamlessBoundarySpreadPages(
                boundaryTarget = boundaryTarget,
                spreadColumns = 2,
                previousChapterName = previousChapterName,
                nextChapterName = nextChapterName,
                previousChapterPreview = previousChapterPreview,
                nextChapterPreview = nextChapterPreview,
            )
        } else {
            emptyList<NovelPageContentPage>() to SeamlessAdjacentPagesSlice()
        }
        val columnPageIndex = if (boundaryTarget == HorizontalChapterSwipeAction.NONE) {
            val spreadSlot = resolvePageTurnRendererProgressPageIndex(
                currentPage = actualPage,
                contentPageCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
            )
            val firstPage = resolveSpreadSlotFirstPageIndex(spreadSlot, 2)
            firstPage + columnOffset
        } else {
            -1
        }
        val contentPage = when {
            boundaryPreview != null -> NovelPageContentPage(emptyList())
            isSeamlessBoundarySlot -> seamlessBoundaryPagesForSlot.getOrElse(columnOffset) {
                NovelPageContentPage(emptyList())
            }
            else -> safeContentPages.getOrElse(columnPageIndex) { NovelPageContentPage(emptyList()) }
        }
        val pageTexture = if (isBackgroundMode) activeBackgroundTexture else backgroundTexture
        val pageTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent
        val pageSurfaceColor = Color.Transparent
        val spreadHalf = if (columnOffset == 0) NovelSpreadBackgroundHalf.LEFT else NovelSpreadBackgroundHalf.RIGHT
        val contentModifier = if (mirrored) {
            Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
        } else {
            Modifier.fillMaxSize()
        }
        val isInteractiveCurrentPage =
            isSpreadSettled && page == pageCurlState.current && boundaryTarget == HorizontalChapterSwipeAction.NONE
        NovelPageTurnSnapshotRenderer(
            useSnapshotLayer = !isSpreadSettled,
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
                spreadHalf = spreadHalf,
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
                    selectionAnchorChapterId = chapterId,
                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                    onPlainTap = onTextTap,
                    onImageClick = onImageClick,
                    onImageLongClick = onImageLongClick,
                    onUrlClick = onUrlClick,
                    touchHandlingEnabled = isInteractiveCurrentPage,
                    renderAsStaticCanvas = page != pageCurlState.current || isSeamlessBoundarySlot,
                    pageNumber = when {
                        isSeamlessBoundarySlot -> seamlessBoundarySlice.firstPageNumber?.let { firstNum ->
                            val candidate = firstNum + columnOffset
                            if (candidate <= seamlessBoundarySlice.totalPages) candidate else null
                        }
                        columnPageIndex in safeContentPages.indices -> columnPageIndex + 1
                        else -> null
                    },
                    totalPages = if (isSeamlessBoundarySlot) {
                        seamlessBoundarySlice.totalPages
                    } else {
                        safeContentPages.size
                    },
                )
            }
        }
    }
}
