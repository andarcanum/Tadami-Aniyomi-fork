package eu.kanade.presentation.more

import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor

internal const val AURORA_MORE_DARK_CARD_ALPHA = 0.05f
internal const val AURORA_MORE_DARK_SWITCH_TRACK_ALPHA = 0.4f
internal const val AURORA_MORE_LIGHT_SWITCH_TRACK_ALPHA = 0.24f
internal val AURORA_MORE_CARD_VERTICAL_INSET = 6.dp

internal fun resolveAuroraMoreCardContainerColor(colors: AuroraColors): Color {
    return when {
        colors.isEInk -> resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong)
        colors.isDark -> Color.White.copy(alpha = AURORA_MORE_DARK_CARD_ALPHA)
        else -> Color.White.copy(alpha = 0.82f)
    }
}

internal fun resolveAuroraMoreCardBorderColor(colors: AuroraColors): Color {
    return resolveAuroraBorderColor(colors, emphasized = colors.isEInk)
}

internal fun resolveAuroraMoreCheckedTrackColor(colors: AuroraColors): Color {
    return when {
        colors.eInkProfile == EInkProfile.MONOCHROME -> Color(0xFFCFCFCF)
        colors.eInkProfile == EInkProfile.COLOR -> colors.accent.copy(alpha = AURORA_MORE_DARK_SWITCH_TRACK_ALPHA)
        colors.isDark -> colors.accent.copy(alpha = AURORA_MORE_DARK_SWITCH_TRACK_ALPHA)
        else -> colors.accent.copy(alpha = AURORA_MORE_LIGHT_SWITCH_TRACK_ALPHA)
    }
}

@Composable
internal fun resolveAuroraMoreSwitchColors(
    colors: AuroraColors,
    accent: Color,
) = SwitchDefaults.colors(
    checkedThumbColor = accent,
    checkedTrackColor = resolveAuroraMoreCheckedTrackColor(colors),
    uncheckedThumbColor = if (colors.isDark || colors.isEInk) {
        accent.copy(alpha = 0.58f)
    } else {
        accent.copy(alpha = 0.58f)
    },
    uncheckedTrackColor = if (colors.isDark || colors.isEInk) {
        colors.accent.copy(alpha = 0.16f)
    } else {
        colors.accent.copy(alpha = 0.12f)
    },
    uncheckedBorderColor = if (colors.isDark || colors.isEInk) {
        colors.accent.copy(alpha = 0.32f)
    } else {
        colors.accent.copy(alpha = 0.24f)
    },
    disabledUncheckedThumbColor = if (colors.isDark || colors.isEInk) {
        accent.copy(alpha = 0.42f)
    } else {
        accent.copy(alpha = 0.42f)
    },
    disabledUncheckedTrackColor = if (colors.isDark || colors.isEInk) {
        colors.accent.copy(alpha = 0.10f)
    } else {
        colors.accent.copy(alpha = 0.08f)
    },
    disabledUncheckedBorderColor = if (colors.isDark || colors.isEInk) {
        colors.accent.copy(alpha = 0.18f)
    } else {
        colors.accent.copy(alpha = 0.14f)
    },
)
