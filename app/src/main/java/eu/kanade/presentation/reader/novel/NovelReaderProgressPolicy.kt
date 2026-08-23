package eu.kanade.presentation.reader.novel

import kotlin.math.roundToInt

internal fun shouldTrackWebViewProgress(
    shouldRestoreWebScroll: Boolean,
): Boolean {
    return !shouldRestoreWebScroll
}

internal fun shouldDispatchWebProgressUpdate(
    shouldRestoreWebScroll: Boolean,
    newPercent: Int,
    currentPercent: Int,
): Boolean {
    return shouldTrackWebViewProgress(shouldRestoreWebScroll) && newPercent != currentPercent
}

/**
 * Gate value to store when a scroll restore attempt completes.
 *
 * Both outcomes mean the restore is no longer in flight, so the gate must open
 * either way: after a failure the user genuinely starts at the top and live
 * scroll events drive truthful progress reports. Returning `!restored` here
 * used to latch the gate shut for the whole chapter session on failure,
 * disabling progress tracking and auto-scroll end detection.
 */
internal fun resolveWebViewRestoreGate(
    restoreSucceeded: Boolean,
): Boolean {
    return false
}

internal fun resolveWebViewTotalScrollablePx(
    contentHeightPx: Int,
    viewHeightPx: Int,
): Int {
    return (contentHeightPx - viewHeightPx).coerceAtLeast(0)
}

internal fun resolveWebViewScrollProgressPercent(
    scrollY: Int,
    totalScrollable: Int,
): Int {
    if (totalScrollable <= 0) return 0
    val ratio = scrollY.toFloat() / totalScrollable.toFloat()
    return (ratio * 100f).roundToInt().coerceIn(0, 100)
}

internal fun resolveFinalWebViewProgressPercent(
    resolvedPercent: Int?,
    cachedPercent: Int,
): Int {
    val safeCached = cachedPercent.coerceIn(0, 100)
    val safeResolved = resolvedPercent?.coerceIn(0, 100) ?: return safeCached
    if (safeResolved == 0 && safeCached > 0) {
        return safeCached
    }
    return safeResolved
}

internal fun resolveNativeScrollProgressForTracking(
    firstVisibleItemIndex: Int,
    textBlocksCount: Int,
    canScrollForward: Boolean,
): Pair<Int, Int> {
    val normalizedCount = textBlocksCount.coerceAtLeast(0)
    val normalizedIndex = firstVisibleItemIndex.coerceAtLeast(0)
    if (normalizedCount <= 1) {
        return if (canScrollForward) 0 to 2 else 1 to 2
    }
    if (!canScrollForward) {
        return (normalizedCount - 1) to normalizedCount
    }
    return normalizedIndex.coerceAtMost(normalizedCount - 1) to normalizedCount
}

internal fun resolveReaderUiAfterChapterChange(
    currentShowReaderUi: Boolean,
    usePageReader: Boolean,
): Boolean {
    return if (usePageReader) false else currentShowReaderUi
}

internal fun resolveReaderProgressToPersist(
    shouldPersistRead: Boolean,
    currentIndex: Int,
    resolvedPersistedProgress: Long,
    previousProgress: Long?,
    isInitialPositionRestored: Boolean,
    chapterHandoffTarget: NovelReaderPageReaderHandoffTarget =
        NovelReaderPageReaderHandoffTarget.SAVED,
): Long? {
    if (!shouldPersistRead) return resolvedPersistedProgress
    if (previousProgress == null) return resolvedPersistedProgress
    if (chapterHandoffTarget == NovelReaderPageReaderHandoffTarget.START) {
        return resolvedPersistedProgress
    }
    if (resolvedPersistedProgress >= previousProgress) return resolvedPersistedProgress
    if (!isInitialPositionRestored && currentIndex <= 0) return null
    return resolvedPersistedProgress
}
