package eu.kanade.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.EInkProfile
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
    val eInkProfile: EInkProfile = EInkProfile.OFF,
    val isDark: Boolean,
    val isEInk: Boolean,
    // Aniview Premium specific colors
    val progressCyan: Color,
    val glowEffect: Color,
    val gradientPurple: Color,
    // Semantic colors for achievements and feedback
    val success: Color,
    val warning: Color,
    val error: Color,
    val achievementGold: Color,
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
            eInkProfile: EInkProfile = EInkProfile.OFF,
        ): AuroraColors {
            return when (eInkProfile) {
                EInkProfile.OFF -> {
                    // For AMOLED mode in dark theme, use pure black
                    val effectiveBackground = if (isDark && isAmoled) {
                        Color.Black
                    } else {
                        colorScheme.background
                    }

                    val effectiveSurface = if (isDark && isAmoled) {
                        Color(0xFF0C0C0C)
                    } else {
                        effectiveBackground // Используем тот же цвет, что и background для блендинга
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

                    AuroraColors(
                        accent = colorScheme.primary,
                        accentVariant = colorScheme.primaryContainer,
                        background = effectiveBackground,
                        surface = effectiveSurface,
                        gradientStart = gradientStart,
                        gradientEnd = gradientEnd,
                        glass = if (isDark) {
                            Color.White.copy(alpha = 0.22f)
                        } else {
                            Color(0xE6FFFFFF)
                        },
                        textPrimary = colorScheme.onBackground,
                        textSecondary = colorScheme.onSurfaceVariant,
                        textOnAccent = colorScheme.onPrimary,
                        cardBackground = if (isDark) {
                            Color.White.copy(alpha = 0.12f)
                        } else {
                            Color(0xFFF0F4F8)
                        },
                        divider = if (isDark) colorScheme.outlineVariant else Color(0xFFB8CCE0),
                        eInkProfile = EInkProfile.OFF,
                        isDark = isDark,
                        isEInk = false,
                        // Aniview specific colors
                        progressCyan = colorScheme.secondary,
                        glowEffect = colorScheme.primary,
                        gradientPurple = colorScheme.tertiary,
                        // Semantic colors
                        success = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E),
                        warning = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
                        error = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                        achievementGold = Color(0xFFFFB800),
                    )
                }
                EInkProfile.MONOCHROME -> if (isDark) AuroraColors.EInkDark else AuroraColors.EInk
                EInkProfile.COLOR -> buildEInkColorPalette(
                    colorScheme = colorScheme,
                    isDark = isDark,
                )
            }
        }

        // Legacy static instances for backwards compatibility and previews
        val Dark = AuroraColors(
            accent = AuroraColorScheme.aniviewElectricBlue,
            accentVariant = AuroraColorScheme.aniviewElectricBlue,
            background = AuroraColorScheme.aniviewDarkBg,
            surface = AuroraColorScheme.aniviewDarkBg,
            gradientStart = AuroraColorScheme.auroraDarkGradientStart,
            gradientEnd = AuroraColorScheme.aniviewDarkBg,
            glass = Color.White.copy(alpha = 0.22f),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.7f),
            textOnAccent = Color.White,
            cardBackground = Color.White.copy(alpha = 0.12f),
            divider = Color.White.copy(alpha = 0.1f),
            eInkProfile = EInkProfile.OFF,
            isDark = true,
            isEInk = false,
            progressCyan = AuroraColorScheme.aniviewCyan,
            glowEffect = AuroraColorScheme.aniviewGlow,
            gradientPurple = AuroraColorScheme.aniviewPurple,
            // Semantic colors - dark theme
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            error = Color(0xFFF87171),
            achievementGold = Color(0xFFFFB800),
        )

        val Light = AuroraColors(
            accent = AuroraColorScheme.auroraAccentLight,
            accentVariant = AuroraColorScheme.auroraAccentLight,
            background = AuroraColorScheme.auroraLightBackground,
            surface = AuroraColorScheme.auroraLightSurface,
            gradientStart = AuroraColorScheme.auroraLightGradientStart,
            gradientEnd = AuroraColorScheme.auroraLightGradientEnd,
            glass = Color(0xE6FFFFFF),
            textPrimary = Color(0xFF0f172a),
            textSecondary = Color(0xFF475569),
            textOnAccent = Color.White,
            cardBackground = Color(0xFFF0F4F8),
            divider = Color(0xFFB8CCE0),
            eInkProfile = EInkProfile.OFF,
            isDark = false,
            isEInk = false,
            progressCyan = AuroraColorScheme.aniviewCyan,
            glowEffect = AuroraColorScheme.aniviewElectricBlue,
            gradientPurple = Color(0xFF6366f1),
            // Semantic colors - light theme
            success = Color(0xFF22C55E),
            warning = Color(0xFFF59E0B),
            error = Color(0xFFEF4444),
            achievementGold = Color(0xFFFFB800),
        )

        val EInk = AuroraColors(
            accent = Color(0xFF000000),
            accentVariant = Color(0xFF202020),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF8F8F8),
            gradientStart = Color(0xFFFFFFFF),
            gradientEnd = Color(0xFFFFFFFF),
            glass = Color(0xFFF6F6F6),
            textPrimary = Color(0xFF000000),
            textSecondary = Color(0xFF202020),
            textOnAccent = Color(0xFFFFFFFF),
            cardBackground = Color(0xFFF5F5F5),
            divider = Color(0xFF9A9A9A),
            eInkProfile = EInkProfile.MONOCHROME,
            isDark = false,
            isEInk = true,
            progressCyan = Color(0xFF202020),
            glowEffect = Color(0xFF202020),
            gradientPurple = Color(0xFF4A4A4A),
            success = Color(0xFF2E2E2E),
            warning = Color(0xFF5A5A5A),
            error = Color(0xFF000000),
            achievementGold = Color(0xFF4A4A4A),
        )

        val EInkDark = AuroraColors(
            accent = Color(0xFFFFFFFF),
            accentVariant = Color(0xFFE0E0E0),
            background = Color(0xFF000000),
            surface = Color(0xFF0C0C0C),
            gradientStart = Color(0xFF000000),
            gradientEnd = Color(0xFF000000),
            glass = Color(0xFF383838),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFE0E0E0),
            textOnAccent = Color(0xFF000000),
            cardBackground = Color(0xFF101010),
            divider = Color(0xFFE0E0E0),
            eInkProfile = EInkProfile.MONOCHROME,
            isDark = true,
            isEInk = true,
            progressCyan = Color(0xFFE0E0E0),
            glowEffect = Color(0xFFE0E0E0),
            gradientPurple = Color(0xFFB0B0B0),
            success = Color(0xFFE0E0E0),
            warning = Color(0xFFC0C0C0),
            error = Color(0xFFFFFFFF),
            achievementGold = Color(0xFFB0B0B0),
        )

        private fun buildEInkColorPalette(
            colorScheme: ColorScheme,
            isDark: Boolean,
        ): AuroraColors {
            val base = if (isDark) AuroraColors.EInkDark else AuroraColors.EInk
            val backgroundTint = if (isDark) {
                colorScheme.primary.copy(alpha = 0.16f).compositeOver(base.background)
            } else {
                colorScheme.primary.copy(alpha = 0.08f).compositeOver(base.background)
            }
            val surfaceTint = if (isDark) {
                colorScheme.primaryContainer.copy(alpha = 0.26f).compositeOver(base.surface)
            } else {
                colorScheme.primaryContainer.copy(alpha = 0.12f).compositeOver(base.surface)
            }
            val cardTint = if (isDark) {
                colorScheme.secondaryContainer.copy(alpha = 0.24f).compositeOver(base.cardBackground)
            } else {
                colorScheme.secondaryContainer.copy(alpha = 0.14f).compositeOver(base.cardBackground)
            }
            val glassTint = if (isDark) {
                colorScheme.tertiary.copy(alpha = 0.18f).compositeOver(base.glass)
            } else {
                colorScheme.tertiary.copy(alpha = 0.10f).compositeOver(base.glass)
            }
            return AuroraColors(
                accent = colorScheme.primary,
                accentVariant = colorScheme.primaryContainer,
                background = backgroundTint,
                surface = surfaceTint,
                gradientStart = surfaceTint,
                gradientEnd = backgroundTint,
                glass = glassTint,
                textPrimary = colorScheme.onBackground,
                textSecondary = colorScheme.onSurfaceVariant,
                textOnAccent = colorScheme.onPrimary,
                cardBackground = cardTint,
                divider = colorScheme.outlineVariant,
                eInkProfile = EInkProfile.COLOR,
                isDark = isDark,
                isEInk = true,
                progressCyan = colorScheme.secondary,
                glowEffect = colorScheme.primary,
                gradientPurple = colorScheme.tertiary,
                success = if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E),
                warning = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
                error = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                achievementGold = if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B),
            )
        }
    }
}

val LocalAuroraColors = staticCompositionLocalOf { AuroraColors.Dark }

object AuroraTheme {
    val colors: AuroraColors
        @Composable
        get() = LocalAuroraColors.current

    @Composable
    fun colorsForCurrentTheme(): AuroraColors {
        return when {
            LocalIsEInkMode.current -> LocalAuroraColors.current
            isSystemInDarkTheme() -> AuroraColors.Dark
            else -> AuroraColors.Light
        }
    }
}

// Preview composables for semantic colors
@Preview(name = "Dark Semantic Colors")
@Composable
private fun AuroraSemanticColorsDarkPreview() {
    val colors = AuroraColors.Dark
    AuroraSemanticColorsPreviewContent(colors, "Dark Theme")
}

@Preview(name = "Light Semantic Colors")
@Composable
private fun AuroraSemanticColorsLightPreview() {
    val colors = AuroraColors.Light
    AuroraSemanticColorsPreviewContent(colors, "Light Theme")
}

@Composable
private fun AuroraSemanticColorsPreviewContent(colors: AuroraColors, themeName: String) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "AuroraColors - $themeName",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Semantic Colors",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Success color
            ColorPreviewRow(
                name = "success",
                color = colors.success,
                textColor = colors.textPrimary,
            )

            // Warning color
            ColorPreviewRow(
                name = "warning",
                color = colors.warning,
                textColor = colors.textPrimary,
            )

            // Error color
            ColorPreviewRow(
                name = "error",
                color = colors.error,
                textColor = colors.textPrimary,
            )

            // Achievement Gold color
            ColorPreviewRow(
                name = "achievementGold",
                color = colors.achievementGold,
                textColor = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun ColorPreviewRow(
    name: String,
    color: Color,
    textColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, RoundedCornerShape(8.dp)),
        )
        Text(
            text = name,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = color.toString().takeLast(9),
            color = textColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
