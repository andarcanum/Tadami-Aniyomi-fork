package eu.kanade.presentation.entries.components.aurora

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.AuroraColors

fun resolveAuroraPosterScrimAlphaStops(isDark: Boolean): List<Pair<Float, Float>> {
    return if (isDark) {
        listOf(
            0.0f to 0.00f,
            0.3f to 0.10f,
            0.5f to 0.40f,
            0.7f to 0.70f,
            1.0f to 0.90f,
        )
    } else {
        listOf(
            0.0f to 0.00f,
            0.3f to 0.00f,
            0.5f to 0.06f,
            0.7f to 0.12f,
            1.0f to 0.18f,
        )
    }
}

fun resolveAuroraPosterScrimBrush(colors: AuroraColors): Brush {
    if (colors.isEInk) {
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.65f to Color.White.copy(alpha = 0.02f),
                1.0f to Color.White.copy(alpha = 0.08f),
            ),
        )
    }
    val overlayColor = if (colors.isDark) Color.Black else colors.background
    val stops = resolveAuroraPosterScrimAlphaStops(isDark = colors.isDark)
        .map { (stop, alpha) -> stop to overlayColor.copy(alpha = alpha) }
        .toTypedArray()
    return Brush.verticalGradient(colorStops = stops)
}
