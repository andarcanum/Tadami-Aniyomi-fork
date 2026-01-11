package eu.kanade.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.colorscheme.AuroraColorScheme

@Immutable
data class AuroraColors(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val glass: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnAccent: Color,
    val cardBackground: Color,
    val divider: Color,
    val isDark: Boolean,
) {
    val backgroundGradient: Brush
        get() = Brush.verticalGradient(listOf(gradientStart, gradientEnd))
    
    val cardGradient: Brush
        get() = Brush.verticalGradient(
            listOf(
                gradientStart.copy(alpha = 0.85f),
                gradientEnd.copy(alpha = 0.95f),
                gradientEnd
            )
        )

    companion object {
        val Dark = AuroraColors(
            accent = AuroraColorScheme.auroraAccent,
            background = AuroraColorScheme.auroraDarkBackground,
            surface = AuroraColorScheme.auroraDarkSurface,
            gradientStart = AuroraColorScheme.auroraDarkGradientStart,
            gradientEnd = AuroraColorScheme.auroraDarkGradientEnd,
            glass = AuroraColorScheme.auroraGlass,
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.7f),
            textOnAccent = Color.White,
            cardBackground = Color.White.copy(alpha = 0.05f),
            divider = Color.White.copy(alpha = 0.1f),
            isDark = true,
        )

        val Light = AuroraColors(
            accent = AuroraColorScheme.auroraAccentLight,
            background = AuroraColorScheme.auroraLightBackground,
            surface = AuroraColorScheme.auroraLightSurface,
            gradientStart = AuroraColorScheme.auroraLightGradientStart,
            gradientEnd = AuroraColorScheme.auroraLightGradientEnd,
            glass = AuroraColorScheme.auroraGlassLight,
            textPrimary = Color(0xFF0f172a),
            textSecondary = Color(0xFF475569),
            textOnAccent = Color.White,
            cardBackground = Color.Black.copy(alpha = 0.03f),
            divider = Color.Black.copy(alpha = 0.1f),
            isDark = false,
        )
    }
}

val LocalAuroraColors = staticCompositionLocalOf { AuroraColors.Dark }

object AuroraTheme {
    val colors: AuroraColors
        @Composable
        get() = LocalAuroraColors.current
    
    @Composable
    fun colorsForCurrentTheme(): AuroraColors {
        return if (isSystemInDarkTheme()) AuroraColors.Dark else AuroraColors.Light
    }
}
