package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme

private val ReelsGlassSheen = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
)

/**
 * Shared glass material for the reels chrome (top bars, source pills, action rails).
 *
 * Starts with [clip] so a `clickable()` that follows paints its ripple inside [shape] — the
 * top-bar circles used to show a rounded-square ripple around a circular button. [eInk] wins
 * over [active]: e-ink surfaces stay opaque and never take the accent tint.
 */
@Composable
internal fun Modifier.reelsGlassSurface(
    shape: Shape = CircleShape,
    active: Boolean = false,
    eInk: Boolean = false,
    eInkColor: Color = Color(0xFF1B1725),
): Modifier {
    val accent = AuroraTheme.colors.accent
    return this
        .clip(shape)
        .then(
            when {
                eInk ->
                    Modifier
                        .background(eInkColor, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                active ->
                    Modifier
                        .background(accent.copy(alpha = 0.22f), shape)
                        .border(1.dp, accent.copy(alpha = 0.65f), shape)
                else ->
                    Modifier
                        .background(Color(0xFF0E0C13).copy(alpha = 0.62f), shape)
                        .background(ReelsGlassSheen, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            },
        )
}
