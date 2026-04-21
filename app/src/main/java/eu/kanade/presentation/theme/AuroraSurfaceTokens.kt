package eu.kanade.presentation.theme

import androidx.compose.ui.graphics.Color
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
            if (emphasized) {
                Color(0xFF9DB4CC)
            } else {
                Color(0xFFB8CCE0)
            }
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
            Color.White.copy(alpha = 0.05f)
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
            Color(0xFFEAF1F8)
        }
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
        when (level) {
            AuroraSurfaceLevel.Subtle -> Color.White.copy(alpha = 0.88f)
            AuroraSurfaceLevel.Glass -> Color(0xE6FFFFFF)
            AuroraSurfaceLevel.Strong -> Color(0xFFF0F4F8)
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
