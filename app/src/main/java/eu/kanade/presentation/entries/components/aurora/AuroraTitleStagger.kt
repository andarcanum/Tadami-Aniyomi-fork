package eu.kanade.presentation.entries.components.aurora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val STAGGER_START_DELAY_MS = 280
private const val STAGGER_STEP_DELAY_MS = 60
private const val STAGGER_ITEM_DURATION_MS = 340
private const val STAGGER_MAX_INDEX = 5
private val STAGGER_EASING = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * One-shot entrance cascade for the title screens (the "stagger" half of the
 * Deep Zoom + Stagger open animation, controlled by the "Title screen open
 * animation" appearance setting). Every wrapped block fades in with a small
 * upward translation and a per-index delay, so the top bar, hero, cards and
 * the chapter header arrive in sequence. When disabled everything snaps
 * visible instantly.
 */
class AuroraTitleStaggerState internal constructor(
    private val enabled: Boolean,
) {
    private val totalDurationMs =
        STAGGER_START_DELAY_MS + STAGGER_STEP_DELAY_MS * STAGGER_MAX_INDEX + STAGGER_ITEM_DURATION_MS
    private val timeline = Animatable(0f)

    suspend fun run() {
        if (enabled) {
            timeline.snapTo(0f)
            timeline.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = totalDurationMs, easing = STAGGER_EASING),
            )
        } else {
            timeline.snapTo(1f)
        }
    }

    fun progressFor(index: Int): Float {
        if (!enabled) return 1f
        val timeMs = timeline.value * totalDurationMs
        val startMs = STAGGER_START_DELAY_MS + index * STAGGER_STEP_DELAY_MS
        val local = ((timeMs - startMs) / STAGGER_ITEM_DURATION_MS).coerceIn(0f, 1f)
        return STAGGER_EASING.transform(local)
    }
}

@Composable
fun rememberTitleScreenStaggerState(enabled: Boolean): AuroraTitleStaggerState {
    val state = remember(enabled) { AuroraTitleStaggerState(enabled) }
    LaunchedEffect(state) { state.run() }
    return state
}

/**
 * Applies the entrance cascade to a block. Pass [index] in the intended order
 * (0 = top bar, 1 = hero, 2 = stats, 3 = info, 4 = actions, 5 = chapters).
 * When [state] is null the modifier is a no-op.
 */
fun Modifier.titleScreenStagger(
    state: AuroraTitleStaggerState?,
    index: Int,
): Modifier {
    if (state == null) return this
    return graphicsLayer {
        val progress = state.progressFor(index)
        alpha = progress
        translationY = (1f - progress) * 12.dp.toPx()
    }
}
