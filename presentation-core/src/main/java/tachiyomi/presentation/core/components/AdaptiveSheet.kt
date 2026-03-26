package tachiyomi.presentation.core.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val AdaptiveSheetScrimTestTag = "adaptive_sheet_scrim"
const val AdaptiveSheetSurfaceTestTag = "adaptive_sheet_surface"

private val sheetAnimationSpec = tween<Float>(durationMillis = 350)
private const val phoneScrimAlpha = 0.5f

@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
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
            content = content,
        )
    } else {
        PhoneAdaptiveSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            enableSwipeDismiss = enableSwipeDismiss,
            maxWidth = maxWidth,
            content = content,
        )
    }
}

@Composable
private fun TabletAdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    maxWidth: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    var targetAlpha by remember { mutableFloatStateOf(0f) }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = sheetAnimationSpec,
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
            .testTag(AdaptiveSheetScrimTestTag)
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
                .testTag(AdaptiveSheetSurfaceTestTag)
                .requiredWidthIn(max = maxWidth)
                .then(modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .systemBarsPadding()
                .padding(vertical = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.95f
    var scrimTargetAlpha by remember { mutableFloatStateOf(0f) }
    val scrimAlpha by animateFloatAsState(
        targetValue = scrimTargetAlpha,
        animationSpec = sheetAnimationSpec,
        label = "alpha",
    )

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = 1,
            snapAnimationSpec = sheetAnimationSpec,
            decayAnimationSpec = decayAnimationSpec,
            positionalThreshold = { with(density) { 56.dp.toPx() } },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
        )
    }

    val internalOnDismissRequest: () -> Unit = {
        if (anchoredDraggableState.settledValue == 0) {
            scope.launch {
                scrimTargetAlpha = 0f
                anchoredDraggableState.animateTo(1)
            }
        }
    }

    Box(
        modifier = Modifier
            .testTag(AdaptiveSheetScrimTestTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = internalOnDismissRequest,
            )
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .testTag(AdaptiveSheetSurfaceTestTag)
                .sizeIn(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .then(modifier)
                .systemBarsPadding()
                .padding(vertical = 8.dp)
                .onSizeChanged {
                    val anchors = DraggableAnchors {
                        0 at 0f
                        1 at it.height.toFloat()
                    }
                    anchoredDraggableState.updateAnchors(anchors)
                }
                .offset {
                    IntOffset(
                        0,
                        anchoredDraggableState.offset
                            .takeIf { it.isFinite() }
                            ?.roundToInt()
                            ?: 0,
                    )
                }
                .let { sheetModifier ->
                    if (enableSwipeDismiss) {
                        sheetModifier
                            .nestedScroll(
                                remember(anchoredDraggableState) {
                                    anchoredDraggableState.preUpPostDownNestedScrollConnection()
                                },
                            )
                            .anchoredDraggable(
                                state = anchoredDraggableState,
                                orientation = Orientation.Vertical,
                            )
                    } else {
                        sheetModifier
                    }
                },
            shape = MaterialTheme.shapes.extraLarge.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            BackHandler(
                enabled = anchoredDraggableState.targetValue == 0,
                onBack = internalOnDismissRequest,
            )
            content()
        }

        LaunchedEffect(Unit) {
            scrimTargetAlpha = phoneScrimAlpha
            anchoredDraggableState.animateTo(0)
        }

        LaunchedEffect(anchoredDraggableState) {
            scope.launch { anchoredDraggableState.animateTo(0) }
            snapshotFlow { anchoredDraggableState.settledValue }
                .drop(1)
                .filter { it == 1 }
                .collectLatest {
                    onDismissRequest()
                }
        }
    }
}

private fun AnchoredDraggableState<Int>.preUpPostDownNestedScrollConnection() =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.toFloat()
            return if (delta < 0 && source == NestedScrollSource.UserInput) {
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
                dispatchRawDelta(available.toFloat()).toOffset()
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val toFling = available.toFloat()
            return if (toFling < 0 && offset > anchors.minPosition()) {
                settle(toFling)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val toFling = available.toFloat()
            return if (toFling > 0) {
                settle(toFling)
                available
            } else {
                Velocity.Zero
            }
        }

        private fun Float.toOffset(): Offset = Offset(0f, this)

        @JvmName("velocityToFloat")
        private fun Velocity.toFloat() = y

        private fun Offset.toFloat(): Float = y
    }
