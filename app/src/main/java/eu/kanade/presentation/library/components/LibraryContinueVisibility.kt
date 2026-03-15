package eu.kanade.presentation.library.components

internal fun shouldShowContinueViewingAction(
    hasContinueAction: Boolean,
    remainingCount: Long,
): Boolean {
    return hasContinueAction && remainingCount > 0L
}
