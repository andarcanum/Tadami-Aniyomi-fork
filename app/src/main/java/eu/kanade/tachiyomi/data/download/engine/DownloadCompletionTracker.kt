package eu.kanade.tachiyomi.data.download.engine

/**
 * Tracks download completions that are removed from the live queues
 * so the engine snapshot can still report finished work for the current session.
 */
class DownloadCompletionTracker {

    private val completionsBySection = mutableMapOf<DownloadSection, Int>()

    /** Total completions across all sections. */
    val totalCompletions: Int
        @Synchronized
        get() = completionsBySection.values.sum()

    /**
     * Record a single completion event.
     *
     * Call this when a download item finishes successfully and is
     * about to be removed from its backend queue.
     */
    @Synchronized
    fun recordCompletion(section: DownloadSection, count: Int = 1) {
        completionsBySection[section] = (completionsBySection[section] ?: 0) + count
    }

    @Synchronized
    fun reset() {
        completionsBySection.clear()
    }
}
