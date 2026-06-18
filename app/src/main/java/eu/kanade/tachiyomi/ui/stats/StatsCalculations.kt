package eu.kanade.tachiyomi.ui.stats

/**
 * Shared statistics rules for the Statistics screen.
 *
 * Keep these rules intentionally small and pure so the screen, achievements,
 * backup statistics and tests can reason about the same business definitions.
 */
object StatsCalculations {

    fun isCompletedStatus(status: Int, completedStatus: Int): Boolean {
        return status == completedStatus
    }

    fun isCompletedByUserConsumption(
        sourceStatus: Int,
        customStatus: Int?,
        completedStatus: Int,
        terminalFallbackStatuses: Set<Int>,
        consumedCount: Long,
        totalCount: Long,
    ): Boolean {
        if (customStatus == completedStatus) return true

        val effectiveStatus = customStatus ?: sourceStatus
        val hasKnownContent = totalCount > 0L
        val hasConsumedContent = consumedCount > 0L
        val fullyConsumed = hasKnownContent && hasConsumedContent && consumedCount >= totalCount
        if (!fullyConsumed) return false

        return effectiveStatus == completedStatus || effectiveStatus in terminalFallbackStatuses
    }

    fun progressFraction(done: Int, total: Int): Float {
        if (total <= 0 || done <= 0) return 0f
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun meanTitleScore(scoresByTitle: Collection<List<Double>>): Double {
        val perTitleScores = scoresByTitle
            .mapNotNull { scores ->
                scores
                    .filter { it > 0.0 && !it.isNaN() }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
            }
        return perTitleScores.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN
    }

    fun watchDurationMillis(episodes: Iterable<WatchProgress>): Long {
        return episodes.sumOf { episode ->
            when {
                episode.seen -> episode.totalMillis.coerceAtLeast(0L)
                else -> episode.lastSeenMillis.coerceIn(0L, episode.totalMillis.coerceAtLeast(0L))
            }
        }
    }
}

data class WatchProgress(
    val seen: Boolean,
    val lastSeenMillis: Long,
    val totalMillis: Long,
)
