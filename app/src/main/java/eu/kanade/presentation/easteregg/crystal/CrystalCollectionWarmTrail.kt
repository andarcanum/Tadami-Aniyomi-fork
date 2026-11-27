package eu.kanade.presentation.easteregg.crystal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.isActive

internal fun crystalLocalTrailCanRun(viewActive: Boolean, reduced: Boolean, eInk: Boolean): Boolean =
    viewActive && !reduced && !eInk

internal fun crystalLocalTrailAlpha(seconds: Float, lane: Int, step: Int): Float {
    val phase = ((seconds + lane * 6f + step * .82f) % 18f + 18f) % 18f / 18f
    return when {
        phase < .03f -> 0f
        phase < .07f -> (phase - .03f) / .04f * .13f
        phase < .15f -> .13f - (phase - .07f) / .08f * .03f
        phase < .22f -> (1f - (phase - .15f) / .07f) * .1f
        else -> 0f
    }
}

/** Local decor deliberately has no pawsEnabled parameter or preference mutation. */
@Composable
internal fun CrystalCollectionWarmTrail(reduced: Boolean, occluded: Boolean, modifier: Modifier = Modifier) {
    val colors = AuroraTheme.colors
    val policy = rememberCrystalMotionPolicy()
    val focused by rememberLivingWindowFocus()
    val navigator = LocalNavigator.current
    var visible by remember { mutableStateOf(false) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current
    val clock = remember { CrystalFrameClock() }
    val active = isCrystalLivingViewActive(
        policy.resumed,
        focused,
        visible,
        navigator?.lastItem == CrystalCollectionScreen,
        occluded,
    )
    val running = crystalLocalTrailCanRun(active, reduced || !policy.systemAnimations, colors.isEInk)
    LaunchedEffect(running) {
        clock.pause()
        if (running) {
            var lastEmitNanos = 0L
            try {
                while (isActive) {
                    withFrameNanos { now ->
                        // 18-second paw fade looks identical at ~30fps while cutting draw work by 75%.
                        if (lastEmitNanos != 0L && now - lastEmitNanos < 32_000_000L) return@withFrameNanos
                        lastEmitNanos = now
                        seconds = clock.advance(now, true) / 1000f
                    }
                }
            } finally {
                clock.pause()
            }
        }
    }
    val paw = remember {
        Path().apply {
            moveTo(32f, 29.5f)
            cubicTo(24f, 23.5f, 18.5f, 26.5f, 14.8f, 34.2f)
            cubicTo(11.2f, 41.8f, 13.5f, 50.5f, 22.2f, 51.5f)
            cubicTo(26.2f, 52f, 28.5f, 47.8f, 32f, 47.8f)
            cubicTo(35.5f, 47.8f, 37.8f, 52f, 41.8f, 51.5f)
            cubicTo(50.5f, 50.5f, 52.8f, 41.8f, 49.2f, 34.2f)
            cubicTo(45.5f, 26.5f, 40f, 23.5f, 32f, 29.5f)
            close()
            addOval(Rect(9.3f, 16.1f, 19.7f, 30.9f))
            addOval(Rect(20f, 7.7f, 31f, 23.3f))
            addOval(Rect(33f, 7.7f, 44f, 23.3f))
            addOval(Rect(44.3f, 16.1f, 54.7f, 30.9f))
        }
    }
    Canvas(
        modifier.clearAndSetSemantics {}.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            visible = bounds.width > 0f && bounds.bottom > 0f && bounds.top < view.height
        },
    ) {
        val now = seconds
        val static = reduced || !policy.systemAnimations || colors.isEInk
        val diameter = 16.dp.toPx()
        repeat(3) { lane ->
            repeat(6) { step ->
                val alpha = if (static) .055f else crystalLocalTrailAlpha(now, lane, step)
                if (alpha > 0f) {
                    val side = if (step % 2 == 0) 1f else -1f
                    val x = when (lane) {
                        0 -> 8.dp.toPx() + side * 3.dp.toPx()
                        1 -> size.width * .5f + side * 4.dp.toPx()
                        else -> size.width - 8.dp.toPx() + side * 3.dp.toPx()
                    }
                    val y = 40.dp.toPx() + step * (size.height - 80.dp.toPx()).coerceAtLeast(0f) / 5f
                    translate(x - diameter / 2f, y - diameter / 2f) {
                        rotate(
                            if (lane == 1) {
                                -18f
                            } else if (lane == 0) {
                                12f
                            } else {
                                -12f
                            },
                            Offset(diameter / 2f, diameter / 2f),
                        ) {
                            scale(diameter / 64f, diameter / 64f, Offset.Zero) {
                                drawPath(paw, colors.textPrimary.copy(alpha = alpha))
                            }
                        }
                    }
                }
            }
        }
    }
}
