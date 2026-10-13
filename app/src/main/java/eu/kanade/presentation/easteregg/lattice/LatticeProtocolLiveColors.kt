package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Living Lattice Protocol accents: slow deep-cyan breath + amber tertiary glint.
 * Frequencies &lt; 0.4 Hz — safe for epilepsy guidelines.
 *
 * Endpoints mirror [eu.kanade.presentation.theme.colorscheme.LatticeProtocolColorScheme]
 * so the animated palette stays inside the unique theme identity.
 */
object LatticeProtocolLivePalette {
    val cyanCore = Color(0xFF0095AE)
    val cyanDeep = Color(0xFF00718A)
    val amber = Color(0xFFFFA726)
    val amberSoft = Color(0xFFE09A30)
}

data class LatticeProtocolLiveColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

/**
 * Static Lattice Protocol colors without animations.
 * Used in base theme overlay to prevent full-tree recomposition thrashing.
 */
fun getLatticeProtocolStaticColors(): LatticeProtocolLiveColors {
    val cyanCore = LatticeProtocolLivePalette.cyanCore
    val cyanDeep = LatticeProtocolLivePalette.cyanDeep
    val amber = LatticeProtocolLivePalette.amber
    val amberSoft = LatticeProtocolLivePalette.amberSoft
    val breath = 0.5f
    return LatticeProtocolLiveColors(
        primary = lerp(cyanDeep, cyanCore, 0.45f + 0.55f * breath),
        secondary = lerp(cyanDeep, cyanCore, 0.25f + 0.4f * (1f - breath)),
        tertiary = lerp(amberSoft, amber, 0.35f + 0.65f * breath),
    )
}

@Composable
fun rememberLatticeProtocolLiveColors(animated: Boolean = true): LatticeProtocolLiveColors {
    val reduced = rememberLatticeReducedMotion()
    val phase: State<Float> = if (animated && !reduced) {
        val transition = rememberInfiniteTransition(label = "latticeProtocolLive")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "latticeBreath",
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    val t by phase
    val breath = (sin(t * 2f * PI.toFloat()) + 1f) / 2f
    val cyanCore = LatticeProtocolLivePalette.cyanCore
    val cyanDeep = LatticeProtocolLivePalette.cyanDeep
    val amber = LatticeProtocolLivePalette.amber
    val amberSoft = LatticeProtocolLivePalette.amberSoft
    return LatticeProtocolLiveColors(
        primary = lerp(cyanDeep, cyanCore, 0.45f + 0.55f * breath),
        secondary = lerp(cyanDeep, cyanCore, 0.25f + 0.4f * (1f - breath)),
        tertiary = lerp(amberSoft, amber, 0.35f + 0.65f * breath),
    )
}
