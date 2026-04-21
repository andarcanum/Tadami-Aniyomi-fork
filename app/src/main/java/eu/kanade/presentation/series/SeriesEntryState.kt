package eu.kanade.presentation.series

internal fun isSeriesEntryCompleted(
    readCount: Long,
    totalChapters: Long,
): Boolean {
    return totalChapters > 0 && readCount >= totalChapters
}
