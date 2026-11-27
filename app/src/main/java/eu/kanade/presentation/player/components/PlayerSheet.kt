/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.presentation.player.components

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

private val sheetAnimationSpec = tween<Float>(320)

@Composable
fun PlayerSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 2.dp,
    dismissEvent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == ORIENTATION_LANDSCAPE
    val maxWidth = if (isLandscape) 680.dp else 440.dp
    val maxHeight = configuration.screenHeightDp.dp * if (isLandscape) 0.84f else 0.90f

    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val panelOpacity by playerPreferences.panelOpacity().collectAsState()
    val aurora = AuroraTheme.colors
    val sheetAlpha = ((panelOpacity / 100f).coerceIn(0.45f, 1.0f) * 0.40f + 0.56f).coerceIn(0.76f, 0.96f)
    val containerColor = if (aurora.isEInk) {
        MaterialTheme.colorScheme.surface
    } else if (aurora.isDark) {
        aurora.surface.copy(alpha = sheetAlpha)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = (sheetAlpha + 0.04f).coerceAtMost(0.98f))
    }
    val rimColor = if (aurora.isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    val handleColor = if (aurora.isDark) {
        Color.White.copy(alpha = 0.28f)
    } else {
        Color.Black.copy(alpha = 0.22f)
    }

    var backgroundAlpha by remember { mutableFloatStateOf(0f) }
    val alpha by animateFloatAsState(
        backgroundAlpha,
        animationSpec = sheetAnimationSpec,
        label = "alpha",
    )

    val anchoredDraggableState = remember { AnchoredDraggableState(initialValue = 1) }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        anchoredDraggableState,
        { with(density) { 56.dp.toPx() } },
        sheetAnimationSpec,
    )

    LaunchedEffect(dismissEvent) {
        if (dismissEvent) {
            backgroundAlpha = 0f
            anchoredDraggableState.animateTo(1)
            onDismissRequest()
        }
    }

    val internalOnDismissRequest = {
        if (anchoredDraggableState.currentValue == 0) {
            scope.launch {
                backgroundAlpha = 0f
                anchoredDraggableState.animateTo(1)
            }
        }
    }
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = internalOnDismissRequest,
            )
            .fillMaxSize()
            .background(Color.Black.copy(alpha))
            .onSizeChanged {
                val anchors = DraggableAnchors {
                    0 at 0f
                    1 at it.height.toFloat()
                }
                anchoredDraggableState.updateAnchors(anchors)
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .sizeIn(maxWidth = maxWidth, maxHeight = maxHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .nestedScroll(
                    remember(anchoredDraggableState, flingBehavior) {
                        anchoredDraggableState.preUpPostDownNestedScrollConnection(flingBehavior)
                    },
                )
                .then(modifier)
                .offset {
                    IntOffset(
                        0,
                        anchoredDraggableState.offset
                            .takeIf { it.isFinite() }
                            ?.roundToInt()
                            ?: 0,
                    )
                }
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Vertical,
                    flingBehavior = flingBehavior,
                )
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
            shape = MaterialTheme.shapes.extraLarge.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = containerColor,
            border = BorderStroke(1.dp, rimColor),
            tonalElevation = tonalElevation,
            content = {
                BackHandler(
                    enabled = anchoredDraggableState.targetValue == 0,
                    onBack = internalOnDismissRequest,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(handleColor),
                        )
                    }
                    content()
                }
            },
        )

        LaunchedEffect(true) {
            backgroundAlpha = 0.52f
        }

        LaunchedEffect(anchoredDraggableState) {
            scope.launch { anchoredDraggableState.animateTo(0) }
            snapshotFlow { anchoredDraggableState.currentValue }
                .drop(1)
                .filter { it == 1 }
                .collectLatest { latestOnDismissRequest() }
        }
    }
}

private fun <T> AnchoredDraggableState<T>.preUpPostDownNestedScrollConnection(
    flingBehavior: TargetedFlingBehavior,
) = object : NestedScrollConnection {
    private val scrollScope = object : ScrollScope {
        override fun scrollBy(pixels: Float): Float = dispatchRawDelta(pixels)
    }

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
            with(flingBehavior) {
                scrollScope.performFling(toFling)
            }
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
