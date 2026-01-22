package eu.kanade.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import eu.kanade.presentation.theme.colorscheme.AuroraColorScheme

@Immutable
data class AuroraColors(
    val accent: Color,
    val accentVariant: Color,
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
    // Aniview Premium specific colors
    val progressCyan: Color,
    val glowEffect: Color,
    val gradientPurple: Color,
) {
    val backgroundGradient: Brush
        get() = Brush.verticalGradient(listOf(gradientStart, gradientEnd))

    val cardGradient: Brush
        get() = Brush.verticalGradient(
            listOf(
                gradientStart.copy(alpha = 0.85f),
                gradientEnd.copy(alpha = 0.95f),
                gradientEnd,
            ),
        )

    // Aniview gradient: electric blue to purple
    val aniviewGradient: Brush
        get() = Brush.horizontalGradient(
            listOf(
                glowEffect,
                gradientPurple,
            ),
        )

    companion object {
        /**
         * Creates AuroraColors dynamically from the selected ColorScheme.
         * This allows Aurora theme to adapt to user's selected accent color
         * (Sapphire, Nord, Strawberry, etc.) while maintaining Aurora's
         * unique gradient and glass aesthetics.
         */
        fun fromColorScheme(
            colorScheme: ColorScheme,
            isDark: Boolean,
            isAmoled: Boolean = false,
        ): AuroraColors {
            // For AMOLED mode in dark theme, use pure black
            val effectiveBackground = if (isDark && isAmoled) {
                Color.Black
            } else {
                colorScheme.background
            }

            val effectiveSurface = if (isDark && isAmoled) {
                Color(0xFF0C0C0C)
            } else {
                colorScheme.surface
            }

            // Generate gradient colors based on theme's primary color
            val gradientStart = if (isDark) {
                if (isAmoled) {
                    // AMOLED: subtle tint on near-black
                    colorScheme.primary.copy(alpha = 0.08f).compositeOver(Color(0xFF050508))
                } else {
                    // Regular dark: blend primary with background
                    colorScheme.primary.copy(alpha = 0.15f).compositeOver(effectiveBackground)
                }
            } else {
                // Light: gentle tint on light background
                colorScheme.primary.copy(alpha = 0.12f).compositeOver(effectiveBackground)
            }

            val gradientEnd = effectiveBackground

            return AuroraColors(
                accent = colorScheme.primary,
                accentVariant = colorScheme.primaryContainer,
                background = effectiveBackground,
                surface = effectiveSurface,
                gradientStart = gradientStart,
                gradientEnd = gradientEnd,
                glass = if (isDark) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    Color.Black.copy(alpha = 0.05f)
                },
                textPrimary = colorScheme.onBackground,
                textSecondary = colorScheme.onSurfaceVariant,
                textOnAccent = colorScheme.onPrimary,
                cardBackground = if (isDark) {
                    Color.White.copy(alpha = 0.05f)
                } else {
                    Color.Black.copy(alpha = 0.03f)
                },
                divider = colorScheme.outlineVariant,
                isDark = isDark,
                // Aniview specific colors
                progressCyan = colorScheme.secondary,
                glowEffect = colorScheme.primary,
                gradientPurple = colorScheme.tertiary,
            )
        }

        // Legacy static instances for backwards compatibility and previews
        val Dark = AuroraColors(
            accent = AuroraColorScheme.aniviewElectricBlue,
            accentVariant = AuroraColorScheme.aniviewElectricBlue,
            background = AuroraColorScheme.aniviewDarkBg,
            surface = AuroraColorScheme.aniviewSurface,
            gradientStart = AuroraColorScheme.auroraDarkGradientStart,
            gradientEnd = AuroraColorScheme.aniviewDarkBg,
            glass = AuroraColorScheme.auroraGlass,
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.7f),
            textOnAccent = Color.White,
            cardBackground = Color.White.copy(alpha = 0.05f),
            divider = Color.White.copy(alpha = 0.1f),
            isDark = true,
            progressCyan = AuroraColorScheme.aniviewCyan,
            glowEffect = AuroraColorScheme.aniviewGlow,
            gradientPurple = AuroraColorScheme.aniviewPurple,
        )

        val Light = AuroraColors(
            accent = AuroraColorScheme.auroraAccentLight,
            accentVariant = AuroraColorScheme.auroraAccentLight,
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
            progressCyan = AuroraColorScheme.aniviewCyan,
            glowEffect = AuroraColorScheme.aniviewElectricBlue,
            gradientPurple = Color(0xFF6366f1),
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
