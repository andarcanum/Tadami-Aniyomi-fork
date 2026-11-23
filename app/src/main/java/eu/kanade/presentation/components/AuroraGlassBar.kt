package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.kanade.presentation.theme.AuroraColors

/**
 * Shared Aurora floating glass bar surface: elevation + clip + live Haze blur
 * (or adaptive solid-glass fallback) + rim-light border.
 *
 * Used by both the Aurora floating bottom navigation / rail in `HomeScreen` and
 * the floating multi-selection action bar (`LibraryBottomActionMenu`).
 */
internal fun Modifier.auroraGlassBar(
    colors: AuroraColors,
    shape: Shape,
    hazeState: HazeState?,
): Modifier {
    if (colors.isEInk) {
        return this
            .clip(shape)
            .background(colors.surface, shape)
            .border(
                BorderStroke(width = 1.dp, color = colors.textPrimary),
                shape = shape,
            )
    }

    val hazeModifier = if (hazeState != null) {
        val hazeStyle = HazeBlurStyle {
            backgroundColor(colors.background)
            colorEffects(listOf(HazeColorEffect.tint(colors.surface.copy(alpha = 0.65f))))
            blurRadius(24.dp)
            noiseFactor(0.12f)
        }
        Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
    } else {
        // Adaptive non-blur glass fallback for API < 31 / low-RAM / Battery Saver.
        Modifier.background(
            colors.surface.copy(alpha = if (colors.isDark) 0.88f else 0.92f),
            shape,
        )
    }

    return if (colors.isDark) {
        this
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.12f),
                spotColor = Color.White.copy(alpha = 0.08f),
            )
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.18f),
                spotColor = Color.White.copy(alpha = 0.12f),
            )
            .clip(shape)
            // Opaque base under the frost: on first frames after navigation
            // (e.g. popping back to home) the haze layer has not captured
            // content yet, so without this the bar flashes transparent.
            .background(colors.background, shape)
            .then(hazeModifier)
            .border(
                BorderStroke(width = 1.dp, brush = auroraMenuRimLightBrush(colors)),
                shape = shape,
            )
    } else {
        this
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            // Opaque base under the frost (see dark branch comment).
            .background(colors.background, shape)
            .then(hazeModifier)
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.80f),
                            Color.White.copy(alpha = 0.20f),
                        ),
                    ),
                ),
                shape = shape,
            )
    }
}
