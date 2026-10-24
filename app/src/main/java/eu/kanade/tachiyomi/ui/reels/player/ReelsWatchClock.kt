package eu.kanade.tachiyomi.ui.reels.player

/**
 * Monotonic accumulator for time a reel was really playing (or, for a still, really eligible
 * on screen).
 *
 * Only the intervals between [start] and [stop] accrue: pause, buffering and a backgrounded
 * app stop the clock and never count. [consumeSeconds] returns the time since the previous
 * consumption, so a view can be reported on deactivation and again on disposal without
 * double-counting, and partial intervals are preserved instead of being rounded away.
 *
 * Not thread-safe: drive it from the main thread (Compose effects and player callbacks).
 */
internal class ReelsWatchClock {
    private var runningSinceMs: Long? = null
    private var accumulatedMs = 0L
    private var consumedMs = 0L

    val isRunning: Boolean get() = runningSinceMs != null

    /** Idempotent: repeated starts keep the original anchor. */
    fun start(nowMs: Long) {
        if (runningSinceMs == null) runningSinceMs = nowMs
    }

    /** Idempotent: freezes the live interval at [nowMs]. */
    fun stop(nowMs: Long) {
        val since = runningSinceMs ?: return
        accumulatedMs += (nowMs - since).coerceAtLeast(0L)
        runningSinceMs = null
    }

    /** Total accrued time, including the live interval while running. */
    fun elapsedMs(nowMs: Long): Long {
        val since = runningSinceMs ?: return accumulatedMs
        return accumulatedMs + (nowMs - since).coerceAtLeast(0L)
    }

    /** Forgets all accumulation and the consumption baseline (a fresh appearance). */
    fun reset() {
        runningSinceMs = null
        accumulatedMs = 0L
        consumedMs = 0L
    }

    /** Seconds since the previous consumption; repeated calls never report the same time twice. */
    fun consumeSeconds(nowMs: Long): Float {
        val total = elapsedMs(nowMs)
        val delta = (total - consumedMs).coerceAtLeast(0L)
        if (total > consumedMs) consumedMs = total
        return delta / 1000f
    }
}
