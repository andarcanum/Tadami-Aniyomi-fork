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
 * "Lens" style: clean frosted glass fill with a top specular highlight rim,
 * so the button reads as a raised optical glass lens in both dark and light themes
 * with 100% artifact-free, uniform surface rendering.
 *
 * Used by Titles (library), Updates, Browse, Settings/More scaffolds,
 * History and Dictionary top bars, plus shared [AuroraAppBarActions].
 */
fun Modifier.auroraHeaderIconSurface(
    colors: AuroraColors,
    shape: Shape = CircleShape,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    scrollProgress: Float = 0f,
    isPosterMode: Boolean = false,
): Modifier {
    if (colors.eInkProfile != EInkProfile.OFF) {
        return background(resolveAuroraIconSurfaceColor(colors), shape)
    }

    val density = scrollProgress.coerceIn(0f, 1f)
    val useDarkLens = colors.isDark

    // Glass fill color with scroll densification
    val glassColor = if (useDarkLens) {
        if (isPosterMode) {
            Color(0xFF161922).copy(alpha = 0.85f)
        } else {
            Color(0xFF161922).copy(alpha = 0.70f + 0.18f * density)
        }
    } else {
        if (isPosterMode) {
            Color.White.copy(alpha = 0.85f)
        } else {
            Color.White.copy(alpha = 0.68f + 0.17f * density)
        }
    }

    val bgModifier = this
        .clip(shape)
        .background(color = glassColor, shape = shape)

    return if (useDarkLens) {
        bgModifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isPosterMode) 0.35f else 0.28f),
                0.50f to Color.White.copy(alpha = 0.10f),
                1f to Color.White.copy(alpha = 0.04f),
            ),
            shape = shape,
        )
    } else {
        bgModifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.90f),
                0.50f to Color.White.copy(alpha = 0.40f),
                1f to Color.White.copy(alpha = 0.18f),
            ),
            shape = shape,
        )
    }
}
