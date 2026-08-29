package eu.kanade.tachiyomi.data.library

/**
 * Rate-limits progress notification posts. Library update jobs post progress before and
 * after every entry; on large libraries that means thousands of Binder IPC calls per run.
 * The throttle allows at most one post per [minIntervalMillis]; the first post of a run and
 * any call after the window has elapsed are always allowed.
 */
internal class ProgressPostThrottle(
    private val minIntervalMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastPostedAtMillis = NEVER_POSTED

    fun shouldPostNow(): Boolean {
        val now = clock()
        if (lastPostedAtMillis != NEVER_POSTED && now - lastPostedAtMillis < minIntervalMillis) {
            return false
        }
        lastPostedAtMillis = now
        return true
    }

    companion object {
        private const val NEVER_POSTED = Long.MIN_VALUE

        /** Collapses the before/after posts of long runs to a few updates per second. */
        internal const val DEFAULT_PROGRESS_INTERVAL_MILLIS = 300L
    }
}
