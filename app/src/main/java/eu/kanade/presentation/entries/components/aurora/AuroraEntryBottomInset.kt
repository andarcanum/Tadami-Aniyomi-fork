package eu.kanade.presentation.entries.components.aurora

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val AURORA_ENTRY_FAB_DEFAULT_BOTTOM_OFFSET = 20.dp
internal val AURORA_ENTRY_HERO_DEFAULT_BOTTOM_OFFSET = 0.dp

/**
 * Resolves the bottom offset for the Aurora hero content container, taking into account
 * system navigation bar insets (e.g. 3-button navigation on Android 9/legacy or gesture bars).
 */
internal fun resolveAuroraHeroBottomPadding(
    navigationBarsBottom: Dp,
    baseBottom: Dp = AURORA_ENTRY_HERO_DEFAULT_BOTTOM_OFFSET,
): Dp {
    return baseBottom + navigationBarsBottom
}

/**
 * Resolves the bottom offset for the Aurora floating action button (Play/Resume), taking into
 * account system navigation bar insets.
 */
internal fun resolveAuroraFabBottomPadding(
    navigationBarsBottom: Dp,
    baseBottom: Dp = AURORA_ENTRY_FAB_DEFAULT_BOTTOM_OFFSET,
): Dp {
    return baseBottom + navigationBarsBottom
}
