package eu.kanade.tachiyomi.ui.library

data class LibraryImmersiveChromeState(
    val isVisible: Boolean = true,
    val accumulatedScrollPx: Float = 0f,
)

fun resolveLibraryImmersiveChromeState(
    currentState: LibraryImmersiveChromeState,
    scrollDeltaPx: Float,
    enabled: Boolean,
    forceVisible: Boolean,
    hideThresholdPx: Float,
): LibraryImmersiveChromeState {
    if (!enabled || forceVisible) {
        return LibraryImmersiveChromeState()
    }

    if (scrollDeltaPx == 0f) {
        return currentState
    }

    return if (currentState.isVisible) {
        if (scrollDeltaPx <= 0f) {
            currentState.copy(accumulatedScrollPx = 0f)
        } else {
            val accumulated = (currentState.accumulatedScrollPx + scrollDeltaPx)
                .coerceAtMost(hideThresholdPx)
            if (accumulated >= hideThresholdPx) {
                LibraryImmersiveChromeState(
                    isVisible = false,
                    accumulatedScrollPx = 0f,
                )
            } else {
                currentState.copy(accumulatedScrollPx = accumulated)
            }
        }
    } else {
        if (scrollDeltaPx >= 0f) {
            currentState.copy(accumulatedScrollPx = 0f)
        } else {
            val accumulated = (currentState.accumulatedScrollPx + scrollDeltaPx)
                .coerceAtLeast(-hideThresholdPx)
            if (accumulated <= -hideThresholdPx) {
                LibraryImmersiveChromeState(
                    isVisible = true,
                    accumulatedScrollPx = 0f,
                )
            } else {
                currentState.copy(accumulatedScrollPx = accumulated)
            }
        }
    }
}
