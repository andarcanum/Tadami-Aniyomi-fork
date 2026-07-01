package eu.kanade.tachiyomi.ui.reader

/**
 * Tracks page transitions to calculate the average reading speed.
 */
class ReadingSpeedTracker(
    private val maxHistorySize: Int = 5,
    private val maxGapMs: Long = 120_000, // 2 minutes
) {
    private val history = mutableListOf<Long>()

    /**
     * Records a page transition timestamp.
     */
    fun addPageTransition(timestamp: Long) {
        history.add(timestamp)
        // Keep at most maxHistorySize + 1 timestamps to measure maxHistorySize transitions
        if (history.size > maxHistorySize + 1) {
            history.removeAt(0)
        }
    }

    /**
     * Calculates the average reading speed in seconds.
     * Returns null if there are not enough valid transitions.
     */
    fun getAverageSpeedSeconds(): Double? {
        if (history.size < 2) return null

        val transitionDurations = mutableListOf<Long>()
        for (i in 1 until history.size) {
            val duration = history[i] - history[i - 1]
            if (duration in 0..maxGapMs) {
                transitionDurations.add(duration)
            }
        }

        if (transitionDurations.isEmpty()) return null

        val averageMs = transitionDurations.average()
        return averageMs / 1000.0
    }
}
