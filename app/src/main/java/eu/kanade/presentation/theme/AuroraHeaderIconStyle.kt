package eu.kanade.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.domain.ui.model.EInkProfile

/**
 * Single source of truth for Aurora header / top bar icon button surfaces.
 *
 * "Lens" style: frosted fill with a top inner highlight and a soft ambient
 * shadow, so the button reads as a raised glass lens in both dark and light
 * themes. E-ink profiles keep the legacy opaque fill (translucency and
 * gradients hurt those panels).
 *
 * Used by the Titles (library), Updates, Browse, Settings/More scaffolds,
 * History and Dictionary top bars, plus the shared
 * [eu.kanade.presentation.components.AuroraAppBarActions].
 * Tweak the values here to restyle every Aurora header at once.
 *
 * [scrollProgress] (0f..1f, default 0f) densifies the lens as content scrolls
 * under the top bar: at 1f the fill is nearly opaque, so list rows passing
 * underneath no longer blend into the button. [isPosterMode] forces a dense
 * lens matching the current theme (dark in dark theme, dense white in light
 * theme) so the buttons stay readable over fullscreen cover artwork.
 */
fun Modifier.auroraHeaderIconSurface(
    colors: AuroraColors,
    shape: Shape = CircleShape,
    hazeState: HazeState? = null,
    scrollProgress: Float = 0f,
    isPosterMode: Boolean = false,
): Modifier {
    if (colors.eInkProfile != EInkProfile.OFF) {
        return background(resolveAuroraIconSurfaceColor(colors), shape)
    }

    // Fullscreen-poster title screens get a permanently dense lens so the
    // buttons stay readable over uncontrolled cover artwork: dark in the dark
    // theme, a near-opaque white in the light theme (mirrors the light theme's
    // classic white lens, just denser + a soft shadow so it separates from
    // light covers). Otherwise the lens starts at the classic translucent
    // values and densifies as content scrolls underneath
    // (scrollProgress 0f = top, 1f = hero gone).
    val density = scrollProgress.coerceIn(0f, 1f)
    val tintAlpha = if (isPosterMode) {
        if (colors.isDark) 0.84f else 0.90f
    } else {
        val baseAlpha = if (colors.isDark) 0.60f else 0.70f
        val denseAlpha = if (colors.isDark) 0.94f else 0.96f
        baseAlpha + (denseAlpha - baseAlpha) * density
    }
    val useDarkLens = colors.isDark
    val tintColor = if (useDarkLens) {
        if (isPosterMode) Color(0xFF0E1118) else Color(0xFF161922)
    } else {
        Color.White
    }
    val shadowColor = if (useDarkLens) {
        Color.Black.copy(alpha = if (isPosterMode) 0.42f else 0.35f)
    } else {
        if (isPosterMode) {
            Color.Black.copy(alpha = 0.30f)
        } else {
            colors.textPrimary.copy(alpha = 0.30f)
        }
    }

    val shadowModifier = this.shadow(
        elevation = 3.dp,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )

    val hazeModifier = if (hazeState != null) {
        // The effect layer stays slightly translucent so the lens reads as glass
        // (content underneath shows through at ~10-18%) instead of a solid chip.
        // Poster mode keeps a constant density; otherwise the layer densifies
        // together with the tint as content scrolls underneath.
        val effectAlpha = if (isPosterMode) {
            if (colors.isDark) 0.88f else 0.94f
        } else {
            0.82f + 0.14f * density
        }
        shadowModifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    // Required by Haze on SDK 32+: the effect draws the blurred
                    // layer over this opaque base (see HazeEffectNode.drawEffectWithGraphicsLayer),
                    // otherwise it crashes with "backgroundColor not specified".
                    backgroundColor = colors.background,
                    blurRadius = 20.dp,
                    tint = HazeTint(tintColor.copy(alpha = tintAlpha)),
                    noiseFactor = 0f,
                ),
            ) {
                alpha = effectAlpha
            }
    } else {
        shadowModifier
    }

    val bgModifier = if (hazeState != null) {
        // When haze is active, use a lighter gradient overlay to let the blur shine through
        if (useDarkLens) {
            hazeModifier.background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isPosterMode) 0.14f else 0.10f),
                    1f to Color.Transparent,
                ),
                shape = shape,
            )
        } else {
            hazeModifier.background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isPosterMode) 0.55f else 0.35f),
                    1f to Color.Transparent,
                ),
                shape = shape,
            )
        }
    } else {
        if (useDarkLens) {
            hazeModifier.background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    1f to Color.White.copy(alpha = 0.07f),
                ),
                shape = shape,
            )
        } else {
            hazeModifier.background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isPosterMode) 0.95f else 0.90f),
                    1f to Color.White.copy(alpha = if (isPosterMode) 0.85f else 0.60f),
                ),
                shape = shape,
            )
        }
    }

    return if (useDarkLens) {
        bgModifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isPosterMode) 0.35f else 0.30f),
                0.55f to Color.White.copy(alpha = 0.12f),
                1f to Color.White.copy(alpha = 0.05f),
            ),
            shape = shape,
        )
    } else {
        bgModifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.95f),
                1f to colors.textPrimary.copy(alpha = 0.10f),
            ),
            shape = shape,
        )
    }
}
