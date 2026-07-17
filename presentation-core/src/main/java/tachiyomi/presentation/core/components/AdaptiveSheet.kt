package tachiyomi.presentation.core.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

const val ADAPTIVE_SHEET_SCRIM_TEST_TAG = "adaptive_sheet_scrim"
const val ADAPTIVE_SHEET_SURFACE_TEST_TAG = "adaptive_sheet_surface"

private val SHEET_ANIMATION_SPEC = tween<Float>(durationMillis = 350)
private const val PHONE_SCRIM_ALPHA = 0.5f

@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    scrimAlpha: Float = PHONE_SCRIM_ALPHA,
    /**
     * When false, the phone sheet does not apply [statusBarsPadding]. Bottom sheets that already
     * sit below the status bar should set this false to avoid an empty "cap" above the content.
     */
    applyStatusBarsPadding: Boolean = true,
    /**
     * 1f = fully open (settled at top), 0f = fully dismissed.
     * Used by callers that want window blur/dim to track the sheet, not freeze full-screen.
     */
    onRevealChange: (Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE
    val maxWidth = when {
        isTabletUi && isLandscape -> 760.dp
        isTabletUi -> 640.dp
        isLandscape -> 600.dp
        else -> 460.dp
    }

    if (isTabletUi) {
        TabletAdaptiveSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            maxWidth = maxWidth,
            containerColor = containerColor,
            content = content,
        )
    } else {
        PhoneAdaptiveSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            enableSwipeDismiss = enableSwipeDismiss,
            maxWidth = maxWidth,
            containerColor = containerColor,
            scrimAlpha = scrimAlpha,
            applyStatusBarsPadding = applyStatusBarsPadding,
            onRevealChange = onRevealChange,
            content = content,
        )
    }
}

@Composable
private fun TabletAdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    maxWidth: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    var targetAlpha by remember { mutableFloatStateOf(0f) }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = SHEET_ANIMATION_SPEC,
        label = "alpha",
    )
    var dismissRequested by remember { mutableStateOf(false) }

    val internalOnDismissRequest: () -> Unit = {
        if (!dismissRequested) {
            dismissRequested = true
            onDismissRequest()
        }
    }

    Box(
        modifier = Modifier
            .testTag(ADAPTIVE_SHEET_SCRIM_TEST_TAG)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = internalOnDismissRequest,
            )
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .testTag(ADAPTIVE_SHEET_SURFACE_TEST_TAG)
                .requiredWidthIn(max = maxWidth)
                .then(modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(top = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
        ) {
            BackHandler(enabled = !dismissRequested && alpha > 0f, onBack = internalOnDismissRequest)
            content()
        }

        LaunchedEffect(Unit) {
            targetAlpha = 1f
        }
    }
}

@Composable
private fun PhoneAdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    enableSwipeDismiss: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    scrimAlpha: Float,
    applyStatusBarsPadding: Boolean,
    onRevealChange: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.95f
    var scrimTargetAlpha by remember { mutableFloatStateOf(0f) }
    var sheetShown by remember { mutableStateOf(false) }
    var dismissRequested by remember { mutableStateOf(false) }
    var dismissRequestedByDrag by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableStateOf(-1) }
    // 1 when fully open, 0 when fully dragged off-screen. Start at 0 (closed) so callers
    // do not flash full-screen blur/dim before the open animation begins.
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val animatedScrimAlpha by animateFloatAsState(
        targetValue = scrimTargetAlpha,
        animationSpec = SHEET_ANIMATION_SPEC,
        label = "alpha",
    )
    // Visible scrim only over the still-open fraction of the sheet, not a frozen full-screen veil.
    val visibleScrimAlpha = animatedScrimAlpha * sheetReveal

    val anchoredDraggableState = remember { AnchoredDraggableState(initialValue = 1) }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        anchoredDraggableState,
        { with(density) { 56.dp.toPx() } },
        SHEET_ANIMATION_SPEC,
    )

    val internalOnDismissRequest: () -> Unit = {
        if (!dismissRequested) {
            dismissRequested = true
            dismissRequestedByDrag = false
        }
    }

    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow {
            val offset = anchoredDraggableState.offset
            val max = anchoredDraggableState.anchors.maxPosition()
            offset to max
        }
            .map { (offset, max) ->
                when {
                    !max.isFinite() || max <= 0f || !offset.isFinite() -> 1f
                    else -> (1f - offset / max).coerceIn(0f, 1f)
                }
            }
            .distinctUntilChanged()
            .collect { reveal ->
                sheetReveal = reveal
                onRevealChange(reveal)
            }
    }

    Box(
        modifier = Modifier
            .testTag(ADAPTIVE_SHEET_SCRIM_TEST_TAG)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = internalOnDismissRequest,
            )
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = visibleScrimAlpha)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val phoneSheetShape = MaterialTheme.shapes.extraLarge.copy(
            bottomEnd = ZeroCornerSize,
            bottomStart = ZeroCornerSize,
        )
        // CRITICAL modifier order: offset/drag must WRAP border+fill.
        // If border/clip sit outside offset, the top rim stays fixed while the panel
        // slides — exactly the "phantom top edge" ghost during dismiss/open.
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        0,
                        anchoredDraggableState.offset
                            .takeIf { it.isFinite() }
                            ?.roundToInt()
                            ?: 0,
                    )
                }
                .then(
                    if (enableSwipeDismiss) {
                        Modifier
                            .nestedScroll(
                                remember(anchoredDraggableState, flingBehavior) {
                                    anchoredDraggableState.preUpPostDownNestedScrollConnection(
                                        flingBehavior = flingBehavior,
                                        onDragStart = { isDragging = true },
                                        onDragEnd = { isDragging = false },
                                    )
                                },
                            )
                            .anchoredDraggable(
                                state = anchoredDraggableState,
                                orientation = Orientation.Vertical,
                                flingBehavior = flingBehavior,
                            )
                    } else {
                        Modifier
                    },
                )
                .testTag(ADAPTIVE_SHEET_SURFACE_TEST_TAG)
                .sizeIn(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                )
                .onSizeChanged {
                    val newHeight = it.height
                    if (newHeight <= 0 || newHeight == sheetHeight) return@onSizeChanged
                    // Re-anchoring mid-drag or on content-height flicker resets the gesture
                    // ("have to yank several times"). Freeze while dragging; never shrink once open.
                    if (isDragging) return@onSizeChanged
                    if (sheetShown && sheetHeight > 0 && newHeight < sheetHeight) return@onSizeChanged
                    sheetHeight = newHeight
                    anchoredDraggableState.updateAnchors(
                        DraggableAnchors {
                            0 at 0f
                            1 at newHeight.toFloat()
                        },
                    )
                }
                .navigationBarsPadding()
                .then(if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(top = if (applyStatusBarsPadding) 8.dp else 0.dp)
                .clip(phoneSheetShape)
                .then(modifier),
            shape = phoneSheetShape,
            color = containerColor,
            // Avoid elevation shadow trails that look like a ghost top edge while sliding.
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            BackHandler(
                enabled = !dismissRequested,
                onBack = internalOnDismissRequest,
            )
            content()
        }

        LaunchedEffect(dismissRequested) {
            if (dismissRequested) {
                scrimTargetAlpha = 0f
                if (!dismissRequestedByDrag) {
                    anchoredDraggableState.animateTo(1)
                } else {
                    val remainingOffset = anchoredDraggableState.anchors.maxPosition() - anchoredDraggableState.offset
                    if (remainingOffset != 0f) {
                        anchoredDraggableState.dispatchRawDelta(remainingOffset)
                    }
                }
                onDismissRequest()
            } else {
                scrimTargetAlpha = scrimAlpha
                anchoredDraggableState.animateTo(0)
                sheetShown = true
            }
        }

        // Settle-based dismiss: more reliable than sampling offset only while !isDragging
        // (fling can settle closed without a clean isDragging edge).
        LaunchedEffect(anchoredDraggableState, sheetShown) {
            if (!sheetShown) return@LaunchedEffect
            snapshotFlow { anchoredDraggableState.settledValue }
                .distinctUntilChanged()
                .collect { settled ->
                    if (settled == 1 && !dismissRequested) {
                        dismissRequestedByDrag = true
                        dismissRequested = true
                    }
                }
        }
    }
}

private fun AnchoredDraggableState<Int>.preUpPostDownNestedScrollConnection(
    flingBehavior: TargetedFlingBehavior,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
) =
    object : NestedScrollConnection {
        private val scrollScope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float = dispatchRawDelta(pixels)
        }

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.toFloat()
            return if (delta < 0 && source == NestedScrollSource.UserInput) {
                onDragStart()
                dispatchRawDelta(delta).toOffset()
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            return if (source == NestedScrollSource.UserInput) {
                onDragStart()
                dispatchRawDelta(available.toFloat()).toOffset()
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val toFling = available.toFloat()
            return if (toFling < 0 && offset > anchors.minPosition()) {
                onDragStart()
                with(flingBehavior) {
                    scrollScope.performFling(toFling)
                }
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val toFling = available.toFloat()
            return if (toFling > 0) {
                onDragStart()
                with(flingBehavior) {
                    scrollScope.performFling(toFling)
                }
                onDragEnd()
                available
            } else {
                onDragEnd()
                Velocity.Zero
            }
        }

        private fun Float.toOffset(): Offset = Offset(0f, this)

        @JvmName("velocityToFloat")
        private fun Velocity.toFloat() = y

        private fun Offset.toFloat(): Float = y
    }
