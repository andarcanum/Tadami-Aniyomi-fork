package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme

private val ReelsGlassSheen = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
)

/**
 * Shared glass material for the reels chrome text pills and e-ink icon backplates.
 *
 * Starts with [clip] so a `clickable()` that follows paints its ripple inside [shape].
 * [eInk] wins over [active]: e-ink surfaces stay opaque and never take the accent tint.
 * Pass [subtle] = true for lightweight overlay pills (source switcher, landscape dropdowns).
 */
@Composable
internal fun Modifier.reelsGlassSurface(
    shape: Shape = CircleShape,
    active: Boolean = false,
    eInk: Boolean = false,
    eInkColor: Color = Color(0xFF1B1725),
    subtle: Boolean = false,
): Modifier {
    val accent = AuroraTheme.colors.accent
    val eInkBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    return this
        .clip(shape)
        .then(
            when {
                eInk ->
                    Modifier
                        .background(eInkColor, shape)
                        .border(1.dp, eInkBorder, shape)
                active ->
                    Modifier
                        .background(accent.copy(alpha = 0.22f), shape)
                        .border(1.dp, accent.copy(alpha = 0.65f), shape)
                subtle ->
                    Modifier
                        .background(Color.Black.copy(alpha = 0.36f), shape)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                else ->
                    Modifier
                        .background(Color(0xFF0E0C13).copy(alpha = 0.62f), shape)
                        .background(ReelsGlassSheen, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            },
        )
}

/**
 * Bare video-overlay icon with an optical drop-shadow pass for contrast over bright video frames
 * without wrapping the glyph in a circular box. On e-ink displays the shadow pass is omitted
 * because the caller provides a solid high-contrast backplate.
 */
@Composable
internal fun ReelsChromeIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    eInk: Boolean = AuroraTheme.colors.isEInk,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (!eInk) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.52f),
                modifier = Modifier
                    .offset(y = 1.dp)
                    .size(size),
            )
        }
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
