package eu.kanade.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.EInkProfile

enum class AuroraSurfaceLevel {
    Subtle,
    Glass,
    Strong,
}

fun resolveAuroraSurfaceColor(
    colors: AuroraColors,
    level: AuroraSurfaceLevel,
): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> resolveMonochromeEInkSurfaceColor(
            colors = colors,
            level = level,
        )
        EInkProfile.COLOR -> resolveColorEInkSurfaceColor(
            colors = colors,
            level = level,
        )
        EInkProfile.OFF -> resolveStandardSurfaceColor(
            colors = colors,
            level = level,
        )
    }
}

fun resolveAuroraBorderColor(
    colors: AuroraColors,
    emphasized: Boolean,
): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            if (emphasized) {
                Color(0xFF5A5A5A)
            } else {
                Color(0xFF404040)
            }
        } else {
            if (emphasized) {
                Color(0xFF8A8A8A)
            } else {
                Color(0xFFB5B5B5)
            }
        }
        EInkProfile.COLOR -> if (emphasized) {
            colors.accentVariant
        } else {
            colors.divider
        }
        EInkProfile.OFF -> if (colors.isDark) {
            if (emphasized) {
                Color.White.copy(alpha = 0.16f)
            } else {
                Color.White.copy(alpha = 0.08f)
            }
        } else {
            // Light mode: borders are mostly invisible; shadow provides separation
            Color.Transparent
        }
    }
}

fun resolveAuroraSelectionContainerColor(colors: AuroraColors): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            Color(0xFF2A2A2A)
        } else {
            Color(0xFFE3E3E3)
        }
        EInkProfile.COLOR -> colors.accent
        EInkProfile.OFF -> if (colors.isDark) {
            colors.accent.copy(alpha = 0.18f)
        } else {
            colors.accent.copy(alpha = 0.14f)
        }
    }
}

fun resolveAuroraSelectionBorderColor(colors: AuroraColors): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            Color(0xFF5A5A5A)
        } else {
            Color(0xFF8A8A8A)
        }
        EInkProfile.COLOR -> colors.accentVariant
        EInkProfile.OFF -> if (colors.isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            colors.accent.copy(alpha = 0.28f)
        }
    }
}

fun resolveAuroraControlContainerColor(colors: AuroraColors): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            colors.surface
        } else {
            Color(0xFFF5F5F5)
        }
        EInkProfile.COLOR -> resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
        EInkProfile.OFF -> if (colors.isDark) {
            Color.White.copy(alpha = 0.035f)
        } else {
            resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
        }
    }
}

fun resolveAuroraControlSelectedContainerColor(colors: AuroraColors): Color {
    return resolveAuroraSelectionContainerColor(colors)
}

fun resolveAuroraControlSelectedContentColor(colors: AuroraColors): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            Color.White
        } else {
            Color.Black
        }
        EInkProfile.COLOR -> colors.textOnAccent
        EInkProfile.OFF -> if (colors.isDark) {
            colors.textPrimary
        } else {
            colors.accent
        }
    }
}

fun resolveAuroraIconSurfaceColor(colors: AuroraColors): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            colors.cardBackground
        } else {
            Color(0xFFEDEDED)
        }
        EInkProfile.COLOR -> colors.cardBackground
        EInkProfile.OFF -> if (colors.isDark) {
            colors.textPrimary.copy(alpha = 0.10f)
        } else {
            colors.textPrimary.copy(alpha = 0.06f)
        }
    }
}

fun resolveAuroraTopBarIconSurfaceColor(colors: AuroraColors): Color {
    return if (colors.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
}

fun resolveAuroraTopBarScrimColor(colors: AuroraColors): Color {
    return when (colors.eInkProfile) {
        EInkProfile.MONOCHROME -> if (colors.isDark) {
            colors.surface
        } else {
            Color(0x14FFFFFF)
        }
        EInkProfile.COLOR -> colors.surface
        EInkProfile.OFF -> if (colors.isDark) {
            Color.Black.copy(alpha = 0.15f)
        } else {
            Color(0x14FFFFFF)
        }
    }
}

private fun resolveStandardSurfaceColor(
    colors: AuroraColors,
    level: AuroraSurfaceLevel,
): Color {
    return if (colors.isDark) {
        when (level) {
            AuroraSurfaceLevel.Subtle -> Color.White.copy(alpha = 0.05f)
            AuroraSurfaceLevel.Glass -> colors.glass
            AuroraSurfaceLevel.Strong -> colors.cardBackground
        }
    } else {
        // Light mode: opaque white surfaces — elevation shadow provides separation.
        // Semi-transparent values cause "double color" with M3 Card elevation layer.
        when (level) {
            AuroraSurfaceLevel.Subtle -> Color(0xFFF8F9FA)
            AuroraSurfaceLevel.Glass -> Color(0xFFFDFDFD)
            AuroraSurfaceLevel.Strong -> Color.White
        }
    }
}

private fun resolveMonochromeEInkSurfaceColor(
    colors: AuroraColors,
    level: AuroraSurfaceLevel,
): Color {
    return if (colors.isDark) {
        when (level) {
            AuroraSurfaceLevel.Subtle -> colors.background
            AuroraSurfaceLevel.Glass -> colors.surface
            AuroraSurfaceLevel.Strong -> colors.cardBackground
        }
    } else {
        when (level) {
            AuroraSurfaceLevel.Subtle -> Color(0xFFFFFFFF)
            AuroraSurfaceLevel.Glass -> Color(0xFFF6F6F6)
            AuroraSurfaceLevel.Strong -> Color(0xFFEDEDED)
        }
    }
}

private fun resolveColorEInkSurfaceColor(
    colors: AuroraColors,
    level: AuroraSurfaceLevel,
): Color {
    return when (level) {
        AuroraSurfaceLevel.Subtle -> colors.background
        AuroraSurfaceLevel.Glass -> colors.glass
        AuroraSurfaceLevel.Strong -> colors.cardBackground
    }
}

/**
 * Returns the shadow elevation for floating surfaces in light mode.
 * Dark mode and E-Ink return 0.dp (no floating effect).
 */
fun resolveAuroraElevation(
    colors: AuroraColors,
    level: AuroraSurfaceLevel = AuroraSurfaceLevel.Glass,
    isSelected: Boolean = false,
): Dp {
    if (isSelected) return 0.dp
    return when {
        colors.isEInk -> 0.dp
        colors.isDark -> when (level) {
            AuroraSurfaceLevel.Subtle -> 0.5.dp
            AuroraSurfaceLevel.Glass -> 1.dp
            AuroraSurfaceLevel.Strong -> 2.dp
        }
        else -> when (level) {
            AuroraSurfaceLevel.Subtle -> 1.dp
            AuroraSurfaceLevel.Glass -> 2.dp
            AuroraSurfaceLevel.Strong -> 3.dp
        }
    }
}

/**
 * Shadow color for light mode floating surfaces.
 * Returns a soft neutral shadow for depth without color cast.
 */
fun resolveAuroraCardShadowColor(colors: AuroraColors): Color {
    if (colors.isDark || colors.isEInk) return Color.Transparent
    return Color(0xFF0F172A).copy(alpha = 0.12f)
}

/**
 * Modifier that applies the floating surface effect for light mode.
 * Adds elevation shadow and transparent background.
 * No-op for dark mode and E-Ink.
 */
fun Modifier.auroraFloatingSurface(
    colors: AuroraColors,
    level: AuroraSurfaceLevel = AuroraSurfaceLevel.Glass,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier {
    if (colors.isDark || colors.isEInk) return this
    val elevation = resolveAuroraElevation(colors, level)
    val shadowColor = resolveAuroraCardShadowColor(colors)
    return this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
}
