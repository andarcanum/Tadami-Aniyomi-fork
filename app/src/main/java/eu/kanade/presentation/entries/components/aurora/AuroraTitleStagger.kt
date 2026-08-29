package eu.kanade.presentation.entries.components.aurora

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val POSTER_BLUR_DURATION_MS = 650
private const val STAGGER_START_DELAY_MS = 140
private const val STAGGER_STEP_DELAY_MS = 50
private const val STAGGER_ITEM_DURATION_MS = 420
private const val STAGGER_MAX_INDEX = 10
private val STAGGER_EASING = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
private val POSTER_EASING = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

/**
 * Standard index mapping for the tiered title screen entrance cascade.
 */
object AuroraTitleStaggerIndex {
    const val TOP_BAR = 0
    const val HERO_GENRES = 1
    const val HERO_TITLE = 2
    const val HERO_AUTHOR = 3
    const val HERO_STATS = 4
    const val HERO_NOTE = 5
    const val HERO_ACTION_BUTTON = 6
    const val ACTION_CARD = 7
    const val INFO_CARD = 8
    const val STATS_CARD = 9
    const val CHAPTERS_HEADER = 10
}

/**
 * One-shot entrance cascade for the title screens (the "stagger" half of the
 * Deep Zoom + Stagger open animation, controlled by the "Title screen open
 * animation" appearance setting). Every wrapped block fades in with a small
 * upward translation and a per-index delay, so the top bar, hero elements,
 * cards and the chapter header arrive in a smooth tiered sequence. When
 * disabled everything snaps visible instantly.
 */
class AuroraTitleStaggerState internal constructor(
    private val enabled: Boolean,
) {
    private val totalDurationMs = maxOf(
        POSTER_BLUR_DURATION_MS,
        STAGGER_START_DELAY_MS + STAGGER_STEP_DELAY_MS * STAGGER_MAX_INDEX + STAGGER_ITEM_DURATION_MS,
    )
    private val timeline = Animatable(0f)

    suspend fun run() {
        if (enabled) {
            timeline.snapTo(0f)
            timeline.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing),
            )
        } else {
            timeline.snapTo(1f)
        }
    }

    /**
     * Progress for the background poster / backdrop layer (from 0 to 1).
     * Drives the optical Blur Reveal (blur clears from 24dp -> 0dp).
     */
    fun posterProgress(): Float {
        if (!enabled) return 1f
        val timeMs = timeline.value * totalDurationMs
        val local = (timeMs / POSTER_BLUR_DURATION_MS).coerceIn(0f, 1f)
        return POSTER_EASING.transform(local)
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
 * Applies the Deep Zoom Blur Reveal to the background poster / backdrop layer.
 * As the screen opens, the poster smoothly de-blurs from 24dp to 0dp in graphicsLayer phase.
 */
fun Modifier.titleScreenPosterEntrance(
    state: AuroraTitleStaggerState?,
): Modifier {
    if (state == null) return this
    return graphicsLayer {
        val progress = state.posterProgress()
        alpha = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
        if (progress < 0.999f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurPx = (24.dp.toPx() * (1f - progress)).coerceAtLeast(0.1f)
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                blurPx,
                blurPx,
                android.graphics.Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        } else {
            renderEffect = null
        }
    }
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
        translationY = (1f - progress) * 14.dp.toPx()
    }
}
