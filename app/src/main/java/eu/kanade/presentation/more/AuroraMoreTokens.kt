package eu.kanade.presentation.more

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
internal val AURORA_MORE_CARD_VERTICAL_INSET = 4.dp

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
