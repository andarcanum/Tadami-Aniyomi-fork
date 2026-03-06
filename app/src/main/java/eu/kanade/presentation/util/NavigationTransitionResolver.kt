package eu.kanade.presentation.util

import eu.kanade.domain.ui.model.NavTransitionMode

internal enum class ResolvedNavigationTransitionMode {
    NONE,
    MODERN,
    LEGACY,
}

internal fun resolveNavigationTransitionMode(
    selectedMode: NavTransitionMode,
    animatorDurationScale: Float,
    isPowerSaveMode: Boolean,
): ResolvedNavigationTransitionMode {
    if (animatorDurationScale <= 0f) {
        return ResolvedNavigationTransitionMode.NONE
    }

    return when (selectedMode) {
        NavTransitionMode.AUTO -> {
            if (isPowerSaveMode) {
                ResolvedNavigationTransitionMode.LEGACY
            } else {
                ResolvedNavigationTransitionMode.MODERN
            }
        }
        NavTransitionMode.MODERN -> ResolvedNavigationTransitionMode.MODERN
        NavTransitionMode.LEGACY -> ResolvedNavigationTransitionMode.LEGACY
    }
}
