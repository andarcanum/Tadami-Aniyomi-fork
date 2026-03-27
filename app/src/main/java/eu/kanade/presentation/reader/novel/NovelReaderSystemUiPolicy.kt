package eu.kanade.presentation.reader.novel

import androidx.core.view.WindowInsetsControllerCompat

internal data class ReaderSystemBarsState(
    val isLightStatusBars: Boolean,
    val isLightNavigationBars: Boolean,
    val systemBarsBehavior: Int,
)

internal fun resolveReaderExitSystemBarsState(
    captured: ReaderSystemBarsState?,
    current: ReaderSystemBarsState,
): ReaderSystemBarsState {
    return captured ?: current
}

internal fun resolveActiveReaderSystemBarsState(
    showReaderUi: Boolean,
    fullScreenMode: Boolean,
    base: ReaderSystemBarsState,
): ReaderSystemBarsState {
    if (showReaderUi) {
        return base.copy(
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }
    if (!fullScreenMode) {
        return base.copy(
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
    }
    return base.copy(
        isLightStatusBars = false,
        isLightNavigationBars = false,
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
    )
}

internal fun shouldHideSystemBars(
    fullScreenMode: Boolean,
    showReaderUi: Boolean,
): Boolean {
    return fullScreenMode && !showReaderUi
}

internal fun shouldRestoreSystemBarsOnDispose(
    @Suppress("UNUSED_PARAMETER")
    fullScreenMode: Boolean,
): Boolean {
    return true
}

internal fun WindowInsetsControllerCompat.captureReaderSystemBarsState(): ReaderSystemBarsState {
    return ReaderSystemBarsState(
        isLightStatusBars = isAppearanceLightStatusBars,
        isLightNavigationBars = isAppearanceLightNavigationBars,
        systemBarsBehavior = systemBarsBehavior,
    )
}

internal fun WindowInsetsControllerCompat.restoreReaderSystemBarsState(
    state: ReaderSystemBarsState,
) {
    isAppearanceLightStatusBars = state.isLightStatusBars
    isAppearanceLightNavigationBars = state.isLightNavigationBars
    systemBarsBehavior = state.systemBarsBehavior
}
