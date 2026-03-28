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
