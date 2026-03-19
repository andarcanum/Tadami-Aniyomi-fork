package eu.kanade.presentation.entries

fun resolveEntryAutoJumpTargetIndex(
    enabled: Boolean,
    targetIndex: Int,
    restoredScrollIndex: Int,
): Int? {
    if (!enabled) return null
    if (targetIndex <= 0) return null

    // Saved list position should never win over the explicit next/target entry jump.
    return targetIndex
}
