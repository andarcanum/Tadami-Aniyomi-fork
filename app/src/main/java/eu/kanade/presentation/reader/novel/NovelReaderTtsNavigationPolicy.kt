package eu.kanade.presentation.reader.novel

internal fun resolveShouldPauseTtsForManualNavigation(
    isPlaying: Boolean,
    pauseOnManualNavigation: Boolean,
    nowMs: Long,
    suppressUntilMs: Long,
    usePageReader: Boolean,
    currentBlockIndex: Int,
    activeSourceBlockIndex: Int?,
    currentPageIndex: Int?,
    activePageCandidates: Set<Int>?,
): Boolean {
    if (!isPlaying || !pauseOnManualNavigation) return false
    if (nowMs < suppressUntilMs) return false
    val activeBlockIndex = activeSourceBlockIndex ?: return false
    if (usePageReader) {
        val pageIndex = currentPageIndex
        val pageCandidates = activePageCandidates
        if (pageIndex != null && !pageCandidates.isNullOrEmpty()) {
            return pageIndex !in pageCandidates
        }
    }
    return currentBlockIndex != activeBlockIndex
}
