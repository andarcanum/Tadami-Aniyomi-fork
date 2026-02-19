package eu.kanade.presentation.components

import kotlin.math.abs

internal data class InstantTabSwitchDecision(
    val targetIndex: Int,
    val shouldBounceEdge: Boolean,
)

internal fun resolveInstantTabSwitch(
    currentIndex: Int,
    lastIndex: Int,
    totalDragPx: Float,
    switchThresholdPx: Float,
): InstantTabSwitchDecision {
    if (lastIndex <= 0) {
        return InstantTabSwitchDecision(targetIndex = 0, shouldBounceEdge = false)
    }

    if (abs(totalDragPx) < switchThresholdPx) {
        return InstantTabSwitchDecision(targetIndex = currentIndex, shouldBounceEdge = false)
    }

    return if (totalDragPx > 0f) {
        if (currentIndex <= 0) {
            InstantTabSwitchDecision(targetIndex = 0, shouldBounceEdge = true)
        } else {
            InstantTabSwitchDecision(targetIndex = currentIndex - 1, shouldBounceEdge = false)
        }
    } else {
        if (currentIndex >= lastIndex) {
            InstantTabSwitchDecision(targetIndex = lastIndex, shouldBounceEdge = true)
        } else {
            InstantTabSwitchDecision(targetIndex = currentIndex + 1, shouldBounceEdge = false)
        }
    }
}
