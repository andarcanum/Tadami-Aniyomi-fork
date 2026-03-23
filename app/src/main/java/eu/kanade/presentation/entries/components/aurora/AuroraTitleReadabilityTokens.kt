package eu.kanade.presentation.entries.components.aurora

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor

internal data class AuroraHeroSecondaryButtonPalette(
    val containerColor: Color,
    val borderColor: Color,
    val contentColor: Color,
)

internal fun resolveAuroraHeroOverlayAlphaStops(isDark: Boolean): List<Pair<Float, Float>> {
    if ((isDark)) {
        return listOf(
            0.00f to 0.00f,
            0.38f to 0.06f,
            0.62f to 0.26f,
            0.82f to 0.56f,
            1.00f to 0.82f,
        )
    }
    return listOf(
        0.00f to 0.00f,
        0.28f to 0.00f,
        0.56f to 0.06f,
        0.82f to 0.30f,
        1.00f to 0.60f,
    )
}

internal fun resolveAuroraHeroOverlayAlphaStops(colors: AuroraColors): List<Pair<Float, Float>> {
    if (colors.isEInk) {
        return listOf(
            0.00f to 0.00f,
            0.60f to 0.02f,
            1.00f to 0.08f,
        )
    }
    return resolveAuroraHeroOverlayAlphaStops(colors.isDark)
}

internal fun resolveAuroraHeroOverlayBrush(colors: AuroraColors): Brush {
    if (colors.isEInk) {
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.60f to Color.White.copy(alpha = 0.04f),
                1.00f to Color.White.copy(alpha = 0.14f),
            ),
        )
    }
    val overlayColor = if (colors.isDark) Color.Black else colors.background
    val stops = resolveAuroraHeroOverlayAlphaStops(colors.isDark)
        .map { (stop, alpha) -> stop to overlayColor.copy(alpha = alpha) }
        .toTypedArray()
    return Brush.verticalGradient(colorStops = stops)
}

internal fun resolveAuroraHeroPanelContainerColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFF7F7F7)
    }
    return if (colors.isDark) {
        Color.Transparent
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
    }
}

internal fun resolveAuroraHeroPanelBorderColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFBEBEBE)
    }
    return if (colors.isDark) {
        Color.Transparent
    } else {
        resolveAuroraBorderColor(colors, emphasized = true)
    }
}

internal fun resolveAuroraHeroTitleColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF000000)
    }
    return if (colors.isDark) Color.White else colors.textPrimary
}

internal fun resolveAuroraHeroPrimaryMetaColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF111111)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.85f)
    } else {
        Color(0xFF1E293B)
    }
}

internal fun resolveAuroraHeroSecondaryMetaColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF2F2F2F)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.68f)
    } else {
        colors.textSecondary
    }
}

internal fun resolveAuroraHeroChipContainerColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFEFEFEF)
    }
    return if (colors.isDark) {
        colors.accent.copy(alpha = 0.20f)
    } else {
        colors.accent.copy(alpha = 0.12f)
    }
}

internal fun resolveAuroraHeroChipBorderColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF9F9F9F)
    }
    return if (colors.isDark) {
        Color.Transparent
    } else {
        colors.accent.copy(alpha = 0.18f)
    }
}

internal fun resolveAuroraHeroChipTextColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF000000)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.90f)
    } else {
        colors.accent
    }
}

internal fun resolveAuroraHeroSecondaryButtonPalette(
    colors: AuroraColors,
    isActive: Boolean,
): AuroraHeroSecondaryButtonPalette {
    if (colors.isEInk) {
        return AuroraHeroSecondaryButtonPalette(
            containerColor = if (isActive) Color(0xFFE0E0E0) else Color(0xFFF3F3F3),
            borderColor = if (isActive) Color(0xFF8A8A8A) else Color(0xFFB8B8B8),
            contentColor = Color(0xFF000000),
        )
    }
    return if (colors.isDark) {
        AuroraHeroSecondaryButtonPalette(
            containerColor = if (isActive) {
                colors.accent.copy(alpha = 0.30f)
            } else {
                Color.White.copy(alpha = 0.15f)
            },
            borderColor = if (isActive) {
                colors.accent.copy(alpha = 0.40f)
            } else {
                Color.White.copy(alpha = 0.10f)
            },
            contentColor = if (isActive) {
                Color.White
            } else {
                Color.White.copy(alpha = 0.80f)
            },
        )
    } else {
        AuroraHeroSecondaryButtonPalette(
            containerColor = if (isActive) {
                colors.accent.copy(alpha = 0.14f)
            } else {
                resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
            },
            borderColor = if (isActive) {
                colors.accent.copy(alpha = 0.22f)
            } else {
                resolveAuroraBorderColor(colors, emphasized = false)
            },
            contentColor = if (isActive) {
                colors.accent
            } else {
                colors.textSecondary
            },
        )
    }
}

internal fun resolveAuroraDetailCardBackgroundColors(colors: AuroraColors): List<Color> {
    if (colors.isEInk) {
        return listOf(
            Color(0xFFF9F9F9),
            Color(0xFFF1F1F1),
        )
    }
    return if (colors.isDark) {
        listOf(
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.08f),
        )
    } else {
        listOf(
            Color(0xC3FFFFFF),
            Color(0xEEF2F7FD),
        )
    }
}

internal fun resolveAuroraDetailCardBorderColors(colors: AuroraColors): List<Color> {
    if (colors.isEInk) {
        return listOf(
            Color(0xFF9E9E9E),
            Color(0xFFC9C9C9),
        )
    }
    return if (colors.isDark) {
        listOf(
            Color.White.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.10f),
        )
    } else {
        listOf(
            resolveAuroraBorderColor(colors, emphasized = true),
            resolveAuroraBorderColor(colors, emphasized = false),
        )
    }
}
