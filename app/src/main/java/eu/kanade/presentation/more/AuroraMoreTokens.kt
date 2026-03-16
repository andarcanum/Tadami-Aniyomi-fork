package eu.kanade.presentation.more

import androidx.compose.ui.graphics.Color

internal const val AURORA_MORE_DARK_CARD_ALPHA = 0.08f
internal const val AURORA_MORE_DARK_SWITCH_TRACK_ALPHA = 0.4f
internal const val AURORA_MORE_LIGHT_SWITCH_TRACK_ALPHA = 0.5f

internal fun resolveAuroraMoreCardContainerColor(
    glass: Color,
    isDark: Boolean,
): Color {
    return if (isDark) {
        Color.White.copy(alpha = AURORA_MORE_DARK_CARD_ALPHA)
    } else {
        glass
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
