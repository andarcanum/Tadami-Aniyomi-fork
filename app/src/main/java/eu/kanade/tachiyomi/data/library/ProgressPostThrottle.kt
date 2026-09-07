package eu.kanade.tachiyomi.data.library

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate-limits progress notification posts. Library update jobs post progress before and
 * after every entry; on large libraries that means thousands of Binder IPC calls per run.
 * The throttle allows at most one post per [minIntervalMillis]; the first post of a run and
 * any call after the window has elapsed are always allowed.
 */
internal class ProgressPostThrottle(
    private val minIntervalMillis: Long,
    // I18: monotonic clock - a backwards wall-clock jump used to suppress all progress posts
    // for the duration of the skew.
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    // I18: atomic CAS - the plain var was mutated from up to 5 concurrent job coroutines
    // (a JMM race with duplicate/suppressed posts and no visibility guarantee).
    private val lastPostedAtMillis = AtomicLong(NEVER_POSTED)

    fun shouldPostNow(): Boolean {
        val now = clock()
        while (true) {
            val last = lastPostedAtMillis.get()
            if (last != NEVER_POSTED && now - last < minIntervalMillis) {
                return false
            }
            if (lastPostedAtMillis.compareAndSet(last, now)) {
                return true
            }
        }
    }

    companion object {
        private const val NEVER_POSTED = Long.MIN_VALUE

        /** Collapses the before/after posts of long runs to a few updates per second. */
        internal const val DEFAULT_PROGRESS_INTERVAL_MILLIS = 300L
    }
}
