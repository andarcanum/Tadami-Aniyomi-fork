package tachiyomi.presentation.core.components

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxBy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import kotlin.math.roundToInt

/**
 * Draws vertical fast scroller to a lazy list
 *
 * Set key with [STICKY_HEADER_KEY_PREFIX] prefix to any sticky header item in the list.
 */
@OptIn(FlowPreview::class)
@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    onThumbDragStarted: (() -> Unit)? = null,
    onThumbDragStateChanged: ((Boolean) -> Unit)? = null,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount == 0) return@subcompose

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            val liveThumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var resolvedThumbTopPadding by remember { mutableStateOf<Float?>(null) }
            val thumbTopPadding = resolveFastScrollerTrackTopPadding(
                previousTrackTopPadding = resolvedThumbTopPadding,
                liveTrackTopPadding = liveThumbTopPadding,
                isThumbDragged = isThumbDragged,
            )
            SideEffect {
                resolvedThumbTopPadding = thumbTopPadding
            }
            var thumbOffsetY by remember { mutableFloatStateOf(thumbTopPadding) }
            var listEstimateState by remember { mutableStateOf<FastScrollerListEstimateState?>(null) }
            val scrolled = remember {
                MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }

            LaunchedEffect(isThumbDragged) {
                onThumbDragStateChanged?.invoke(isThumbDragged)
                if (isThumbDragged) {
                    onThumbDragStarted?.invoke()
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    onThumbDragStateChanged?.invoke(false)
                }
            }

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackBounds = resolveFastScrollerTrackBounds(
                contentHeightPx = contentHeight.toFloat(),
                thumbTopPadding = thumbTopPadding,
                thumbBottomPadding = thumbBottomPadding,
                afterContentPaddingPx = listState.layoutInfo.afterContentPadding,
                thumbHeightPx = thumbHeightPx,
            )
            val scrollHeightPx = contentHeight.toFloat() -
                listState.layoutInfo.beforeContentPadding -
                listState.layoutInfo.afterContentPadding -
                thumbBottomPadding
            val visibleItems = layoutInfo.visibleItemsInfo.map {
                FastScrollerItemInfo(
                    index = it.index,
                    top = it.offset,
                    size = it.size,
                    isStickyHeader = (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX) == true,
                )
            }
            val scrollProgress = computeFastScrollerListProgress(
                visibleItems = visibleItems,
                totalItemsCount = layoutInfo.totalItemsCount,
                scrollHeightPx = scrollHeightPx,
            ) ?: return@subcompose

            val scrollStateTracker = remember { MutableData(listState.isScrollInProgress) }
            val stableScrollInProgress = scrollStateTracker.value || listState.isScrollInProgress
            scrollStateTracker.value = listState.isScrollInProgress
            val anyScrollInProgress = stableScrollInProgress || isThumbDragged

            val estimateResolution = resolveFastScrollerListEstimateState(
                previousState = listEstimateState,
                progress = scrollProgress,
                anyScrollInProgress = anyScrollInProgress,
            )
            SideEffect {
                listEstimateState = estimateResolution.nextState
            }
            val maxRemainingSections = estimateResolution.maxRemainingSections
            if (maxRemainingSections < 0.5f) return@subcompose

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) {
                    return@LaunchedEffect
                }
                val request = resolveFastScrollerListScrollRequest(
                    thumbOffsetY = thumbOffsetY,
                    trackBounds = trackBounds,
                    maxRemainingSections = maxRemainingSections,
                    totalItemsCount = layoutInfo.totalItemsCount,
                    visibleItems = visibleItems,
                    scrollHeightPx = scrollHeightPx,
                ) ?: return@LaunchedEffect
                listState.scrollToItem(
                    index = request.index,
                    scrollOffset = request.scrollOffset,
                )
                scrolled.tryEmit(Unit)
            }

            // When list scrolled
            LaunchedEffect(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                thumbTopPadding,
                trackBounds.effectiveTrackHeightPx,
                maxRemainingSections,
            ) {
                if (listState.layoutInfo.totalItemsCount == 0 || isThumbDragged) return@LaunchedEffect
                thumbOffsetY = resolveFastScrollerListThumbOffset(
                    progress = scrollProgress,
                    maxRemainingSections = maxRemainingSections,
                    trackBounds = trackBounds,
                )
                if (stableScrollInProgress) {
                    scrolled.tryEmit(Unit)
                }
            }

            // Thumb alpha
            val alpha = remember { Animatable(0f) }
            val isThumbVisible = alpha.value > 0f
            LaunchedEffect(scrolled, alpha) {
                scrolled
                    .sample(100)
                    .collectLatest {
                        if (thumbAllowed()) {
                            alpha.snapTo(1f)
                            alpha.animateTo(0f, animationSpec = FadeOutAnimationSpec)
                        } else {
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (shouldEnableFastScrollerDrag(
                                isThumbVisible = isThumbVisible,
                                isThumbDragged = isThumbDragged,
                                isScrollInProgress = listState.isScrollInProgress,
                            )) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        trackBounds.minThumbOffsetY,
                                        trackBounds.maxThumbOffsetY,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (shouldEnableFastScrollerDrag(
                                isThumbVisible = isThumbVisible,
                                isThumbDragged = isThumbDragged,
                                isScrollInProgress = listState.isScrollInProgress,
                            )) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(horizontal = 8.dp)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness)
                    .alpha(alpha.value)
                    .background(color = thumbColor, shape = ThumbShape),
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

@Composable
private fun rememberColumnWidthSums(
    columns: GridCells,
    horizontalArrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
) = remember<Density.(Constraints) -> List<Int>>(
    columns,
    horizontalArrangement,
    contentPadding,
) {
    { constraints ->
        require(constraints.maxWidth != Constraints.Infinity) {
            "LazyVerticalGrid's width should be bound by parent"
        }
        val horizontalPadding = contentPadding.calculateStartPadding(LayoutDirection.Ltr) +
            contentPadding.calculateEndPadding(LayoutDirection.Ltr)
        val gridWidth = constraints.maxWidth - horizontalPadding.roundToPx()
        with(columns) {
            calculateCrossAxisCellSizes(
                gridWidth,
                horizontalArrangement.spacing.roundToPx(),
            ).toMutableList().apply {
                for (i in 1..<size) {
                    this[i] += this[i - 1]
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun VerticalGridFastScroller(
    state: LazyGridState,
    columns: GridCells,
    arrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    val slotSizesSums = rememberColumnWidthSums(
        columns = columns,
        horizontalArrangement = arrangement,
        contentPadding = contentPadding,
    )

    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = state.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount == 0) return@subcompose

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            val liveThumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var resolvedThumbTopPadding by remember { mutableStateOf<Float?>(null) }
            val thumbTopPadding = resolveFastScrollerTrackTopPadding(
                previousTrackTopPadding = resolvedThumbTopPadding,
                liveTrackTopPadding = liveThumbTopPadding,
                isThumbDragged = isThumbDragged,
            )
            SideEffect {
                resolvedThumbTopPadding = thumbTopPadding
            }
            var thumbOffsetY by remember { mutableFloatStateOf(thumbTopPadding) }
            val scrolled = remember {
                MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackBounds = resolveFastScrollerTrackBounds(
                contentHeightPx = contentHeight.toFloat(),
                thumbTopPadding = thumbTopPadding,
                thumbBottomPadding = thumbBottomPadding,
                afterContentPaddingPx = state.layoutInfo.afterContentPadding,
                thumbHeightPx = thumbHeightPx,
            )

            val scrollStateTracker = remember { MutableData(state.isScrollInProgress) }
            val stableScrollInProgress = scrollStateTracker.value || state.isScrollInProgress
            scrollStateTracker.value = state.isScrollInProgress

            val columnCount = slotSizesSums(constraints).size.coerceAtLeast(1)
            val visibleItems = layoutInfo.visibleItemsInfo.map {
                FastScrollerItemInfo(
                    index = it.index,
                    top = it.offset.y,
                    size = it.size.height,
                )
            }
            val gridProgress = computeFastScrollerGridProgress(
                visibleItems = visibleItems,
                totalItemsCount = layoutInfo.totalItemsCount,
                columnCount = columnCount,
            ) ?: return@subcompose

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) {
                    return@LaunchedEffect
                }
                val request = resolveFastScrollerGridScrollRequest(
                    thumbOffsetY = thumbOffsetY,
                    progress = gridProgress,
                    trackBounds = trackBounds,
                    viewportHeightPx = trackBounds.heightPx,
                ) ?: return@LaunchedEffect
                state.scrollToItem(
                    index = request.index,
                    scrollOffset = request.scrollOffset,
                )
                scrolled.tryEmit(Unit)
            }

            // When list scrolled
            LaunchedEffect(
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                thumbTopPadding,
                trackBounds.effectiveTrackHeightPx,
            ) {
                if (state.layoutInfo.totalItemsCount == 0 || isThumbDragged) return@LaunchedEffect
                thumbOffsetY = resolveFastScrollerGridThumbOffset(
                    progress = gridProgress,
                    trackBounds = trackBounds,
                )
                if (stableScrollInProgress) {
                    scrolled.tryEmit(Unit)
                }
            }

            // Thumb alpha
            val alpha = remember { Animatable(0f) }
            val isThumbVisible = alpha.value > 0f
            LaunchedEffect(scrolled, alpha) {
                scrolled
                    .sample(100)
                    .collectLatest {
                        if (thumbAllowed()) {
                            alpha.snapTo(1f)
                            alpha.animateTo(0f, animationSpec = FadeOutAnimationSpec)
                        } else {
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (shouldEnableFastScrollerDrag(
                                isThumbVisible = isThumbVisible,
                                isThumbDragged = isThumbDragged,
                                isScrollInProgress = state.isScrollInProgress,
                            )) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        trackBounds.minThumbOffsetY,
                                        trackBounds.maxThumbOffsetY,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (shouldEnableFastScrollerDrag(
                                isThumbVisible = isThumbVisible,
                                isThumbDragged = isThumbDragged,
                                isScrollInProgress = state.isScrollInProgress,
                            )) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness)
                    .alpha(alpha.value)
                    .background(color = thumbColor, shape = ThumbShape),
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

private class MutableData<T>(var value: T)

object Scroller {
    const val STICKY_HEADER_KEY_PREFIX = "sticky:"
}

private val ThumbLength = 48.dp
private val ThumbThickness = 12.dp
private val ThumbShape = RoundedCornerShape(ThumbThickness / 2)
private val FadeOutAnimationSpec = tween<Float>(
    durationMillis = ViewConfiguration.getScrollBarFadeDuration(),
    delayMillis = 2000,
)
private val ImmediateFadeOutAnimationSpec = tween<Float>(
    durationMillis = ViewConfiguration.getScrollBarFadeDuration(),
)
