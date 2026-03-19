package eu.kanade.presentation.more

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal const val AURORA_MORE_DARK_CARD_ALPHA = 0.05f
internal const val AURORA_MORE_DARK_SWITCH_TRACK_ALPHA = 0.4f
internal const val AURORA_MORE_LIGHT_SWITCH_TRACK_ALPHA = 0.5f
internal val AURORA_MORE_CARD_VERTICAL_INSET = 4.dp

internal fun resolveAuroraMoreCardContainerColor(
    glass: Color,
    isDark: Boolean,
): Color {
    return if (isDark) {
        Color.White.copy(alpha = AURORA_MORE_DARK_CARD_ALPHA)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
}

internal fun resolveAuroraMoreCheckedTrackColor(
    accent: Color,
    isDark: Boolean,
): Color {
    return accent.copy(
        alpha = if (isDark) {
            AURORA_MORE_DARK_SWITCH_TRACK_ALPHA
        } else {
            AURORA_MORE_LIGHT_SWITCH_TRACK_ALPHA
        },
    )
}
