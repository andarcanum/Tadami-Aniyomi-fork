package eu.kanade.tachiyomi.ui.reels

/**
 * Pure watch-qualification state machine: player/UI signals in, qualifying events out.
 *
 * Deliberately free of Android, coroutines and storage so the rules can be tested with a fake
 * clock and a fake player. It only decides "did this visit earn a watch?" — committing to the
 * database, deduplicating across visits and fencing history resets belong to the host model.
 *
 * Signals that must NOT count: paused/READY/buffering ([onSample] with `eligible = false`),
 * background playback (the caller folds visibility into `eligible`), a player that has not
 * presented a frame yet ([onFirstFrame]), and any time attributed to a different card (the
 * [itemId] guard drops late callbacks from the previous binding).
 */
class ReelsInteractionTracker(
    private val watchThresholdMs: Long = ReelsForYou.WATCH_THRESHOLD_MS,
    private val coverageThreshold: Float = ReelsForYou.COVERAGE_THRESHOLD,
) {

    /** A visit that just became "watched"; emitted exactly once per visit. */
    data class WatchEvent(
        val itemId: String,
        val watchedMs: Long,
        val coverage: Float,
        val positionMs: Long,
    )

    /** Read-only view of the current visit, used for the resume position and diagnostics. */
    data class Snapshot(
        val itemId: String,
        val watchedMs: Long,
        val coverage: Float,
        val positionMs: Long,
        val qualified: Boolean,
    )

    private class Visit(
        val itemId: String,
        val durationMs: Long?,
        var lastSampleAtMs: Long,
        var lastPositionMs: Long = 0L,
        var watchedMs: Long = 0L,
        var firstFrameSeen: Boolean = false,
        var watchReported: Boolean = false,
        val coveredBuckets: MutableSet<Int> = HashSet(),
    )

    private var visit: Visit? = null

    /** Starts (or replaces) the active visit. A stale visit is dropped, never merged. */
    fun startVisit(itemId: String, durationMs: Long?, nowMs: Long) {
        visit = Visit(
            itemId = itemId,
            durationMs = durationMs?.takeIf { it > 0L },
            lastSampleAtMs = nowMs,
        )
    }

    /** The first frame of THIS visit/binding was presented: time may start counting. */
    fun onFirstFrame(itemId: String) {
        val current = visit ?: return
        if (current.itemId == itemId) current.firstFrameSeen = true
    }

    /**
     * Feeds one sample of the player state.
     *
     * @param eligible true only while the card is the settled one, the screen is in the
     *   foreground, the player is really playing and a frame has been presented.
     * @return the one-shot [WatchEvent] when this sample crossed the qualification threshold.
     */
    fun onSample(itemId: String, nowMs: Long, positionMs: Long, eligible: Boolean): WatchEvent? {
        val current = visit ?: return null
        // A late callback from the previous binding must not credit the new active item.
        if (current.itemId != itemId) return null

        val delta = (nowMs - current.lastSampleAtMs).coerceIn(0L, MAX_SAMPLE_GAP_MS)
        if (eligible && current.firstFrameSeen) {
            current.watchedMs += delta
            markCoverage(current, positionMs, delta)
        }
        current.lastSampleAtMs = nowMs
        current.lastPositionMs = positionMs

        if (!current.watchReported && qualifies(current)) {
            current.watchReported = true
            return WatchEvent(
                itemId = current.itemId,
                watchedMs = current.watchedMs,
                coverage = coverageOf(current),
                positionMs = positionMs,
            )
        }
        return null
    }

    /** Current values without accruing new time (the caller samples right before ending). */
    fun snapshot(itemId: String): Snapshot? {
        val current = visit ?: return null
        if (current.itemId != itemId) return null
        return Snapshot(
            itemId = current.itemId,
            watchedMs = current.watchedMs,
            coverage = coverageOf(current),
            positionMs = current.lastPositionMs,
            qualified = qualifies(current),
        )
    }

    /** Ends the visit for [itemId] and returns its final values; a no-op for another item. */
    fun endVisit(itemId: String, nowMs: Long, positionMs: Long): Snapshot? {
        val current = visit ?: return null
        if (current.itemId != itemId) return null
        onSample(itemId, nowMs, positionMs, eligible = false)
        visit = null
        return Snapshot(
            itemId = current.itemId,
            watchedMs = current.watchedMs,
            coverage = coverageOf(current),
            positionMs = positionMs,
            qualified = qualifies(current),
        )
    }

    fun clear() {
        visit = null
    }

    private fun markCoverage(current: Visit, positionMs: Long, delta: Long) {
        if (positionMs < 0L) return
        val step = positionMs - current.lastPositionMs
        // Natural playback moves forward by roughly the elapsed time. A seek, a loop wrap or a
        // discontinuity lands on one interval only — it must not fabricate coverage.
        val natural = step >= 0L && step <= MAX_NATURAL_STEP_MS && delta <= MAX_NATURAL_STEP_MS
        if (natural) {
            var bucket = (current.lastPositionMs / BUCKET_MS).toInt()
            val last = (positionMs / BUCKET_MS).toInt()
            while (bucket <= last) {
                current.coveredBuckets += bucket
                bucket++
            }
        } else {
            current.coveredBuckets += (positionMs / BUCKET_MS).toInt()
        }
    }

    private fun qualifies(current: Visit): Boolean {
        if (current.watchedMs >= watchThresholdMs) return true
        val duration = current.durationMs ?: return false
        return coveredMs(current) >= duration * coverageThreshold
    }

    private fun coveredMs(current: Visit): Long = current.coveredBuckets.size.toLong() * BUCKET_MS

    private fun coverageOf(current: Visit): Float {
        val duration = current.durationMs ?: return 0f
        return (coveredMs(current).toFloat() / duration).coerceIn(0f, 1f)
    }

    private companion object {
        /** Coverage resolution: 1 s buckets, enough for an 80% verdict on short clips. */
        const val BUCKET_MS = 1_000L

        /** A single sample may credit at most this much wall time (no background gaps). */
        const val MAX_SAMPLE_GAP_MS = 2_000L

        /** Position advance above this is a seek/discontinuity, not natural playback. */
        const val MAX_NATURAL_STEP_MS = 2_000L
    }
}
