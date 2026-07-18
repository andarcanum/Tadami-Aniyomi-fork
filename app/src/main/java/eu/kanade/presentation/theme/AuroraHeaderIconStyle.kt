package eu.kanade.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
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
 */
fun Modifier.auroraHeaderIconSurface(
    colors: AuroraColors,
    shape: Shape = CircleShape,
): Modifier {
    if (colors.eInkProfile != EInkProfile.OFF) {
        return background(resolveAuroraIconSurfaceColor(colors), shape)
    }
    return if (colors.isDark) {
        this
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    1f to Color.White.copy(alpha = 0.07f),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.55f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.05f),
                ),
                shape = shape,
            )
    } else {
        this
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = colors.textPrimary.copy(alpha = 0.30f),
                spotColor = colors.textPrimary.copy(alpha = 0.30f),
            )
            .background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.90f),
                    1f to Color.White.copy(alpha = 0.60f),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.95f),
                    1f to colors.textPrimary.copy(alpha = 0.10f),
                ),
                shape = shape,
            )
    }
}
