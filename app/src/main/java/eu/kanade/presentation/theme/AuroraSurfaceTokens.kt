package eu.kanade.presentation.theme

import androidx.compose.ui.graphics.Color

enum class AuroraSurfaceLevel {
    Subtle,
    Glass,
    Strong,
}

fun resolveAuroraSurfaceColor(
    colors: AuroraColors,
    level: AuroraSurfaceLevel,
): Color {
    if (colors.isEInk) {
        return when (level) {
            AuroraSurfaceLevel.Subtle -> Color(0xFFFFFFFF)
            AuroraSurfaceLevel.Glass -> Color(0xFFF5F5F5)
            AuroraSurfaceLevel.Strong -> Color(0xFFECECEC)
        }
    }
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

fun resolveAuroraBorderColor(
    colors: AuroraColors,
    emphasized: Boolean,
): Color {
    if (colors.isEInk) {
        return if (emphasized) {
            Color(0xFF8F8F8F)
        } else {
            Color(0xFFCCCCCC)
        }
    }
    return if (colors.isDark) {
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

fun resolveAuroraSelectionContainerColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFE5E5E5)
    }
    return if (colors.isDark) {
        colors.accent.copy(alpha = 0.18f)
    } else {
        colors.accent.copy(alpha = 0.14f)
    }
}

fun resolveAuroraSelectionBorderColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF8A8A8A)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        colors.accent.copy(alpha = 0.28f)
    }
}

fun resolveAuroraControlContainerColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFF4F4F4)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    }
}

fun resolveAuroraControlSelectedContainerColor(colors: AuroraColors): Color {
    return resolveAuroraSelectionContainerColor(colors)
}

fun resolveAuroraControlSelectedContentColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color.Black
    }
    return if (colors.isDark) {
        colors.textPrimary
    } else {
        colors.accent
    }
}

fun resolveAuroraIconSurfaceColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFEDEDED)
    }
    return if (colors.isDark) {
        colors.textPrimary.copy(alpha = 0.10f)
    } else {
        Color(0xFFEAF1F8)
    }
}

fun resolveAuroraTopBarScrimColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color.Black.copy(alpha = 0.04f)
    }
    return if (colors.isDark) {
        Color.Black.copy(alpha = 0.15f)
    } else {
        Color(0x14FFFFFF)
    }
}
