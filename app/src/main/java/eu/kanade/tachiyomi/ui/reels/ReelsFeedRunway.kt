package eu.kanade.tachiyomi.ui.reels

/**
 * Adaptive runway for the reels pager.
 *
 * The old rule ("load when two items are left") only reacts after the user has
 * already reached the wall, and it is driven by the settled page, which does
 * not update during a fling. This class predicts whether the next page can be
 * fetched before the current one runs out:
 *
 * `timeToEnd = remaining / pagesPerSecond`
 *
 * and compares it with a conservative estimate of a page load. Idle users are
 * covered by [FLOOR_PAGES], so a slow or stationary scroll still has a small
 * buffer. A fast fling raises the threshold automatically; [MAX_RUNWAY_PAGES]
 * prevents an extreme fling from prefetching the entire history.
 *
 * Pure Kotlin with an injectable monotonic clock: no Compose/Android types,
 * fully unit-testable.
 */
class ReelsFeedRunway(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    data class Decision(
        val shouldLoad: Boolean,
        val pagesRemaining: Int,
        val pagesPerSecond: Double,
        val etaMs: Long,
    )

    private var lastPage = -1
    private var lastPageAtMs = 0L
    private var pagesPerSecond = 0.0

    private var loadPeakMs = INITIAL_LOAD_MS.toDouble()
    private var loadStartedAtMs = 0L
    private var loadInFlight = false

    @Synchronized
    fun reset() {
        lastPage = -1
        lastPageAtMs = 0L
        pagesPerSecond = 0.0
        loadPeakMs = INITIAL_LOAD_MS.toDouble()
        loadStartedAtMs = 0L
        loadInFlight = false
    }

    /** Samples a page transition. Must be called for every pager `currentPage` change. */
    @Synchronized
    fun onPage(page: Int) {
        val now = clock()
        if (lastPage < 0) {
            lastPage = page
            lastPageAtMs = now
            return
        }

        val delta = page - lastPage
        val elapsed = now - lastPageAtMs
        when {
            delta > 0 && elapsed in 1..MAX_SAMPLE_GAP_MS -> {
                val instant = delta * 1_000.0 / elapsed
                pagesPerSecond = if (pagesPerSecond <= 0.0) {
                    instant
                } else {
                    pagesPerSecond * (1.0 - RATE_ALPHA) + instant * RATE_ALPHA
                }.coerceIn(0.0, MAX_RATE)
            }
            delta < 0 || elapsed > MAX_SAMPLE_GAP_MS -> {
                // Backwards or a long pause: the old velocity says nothing about
                // where the user is going next.
                pagesPerSecond = 0.0
            }
        }

        lastPage = page
        lastPageAtMs = now
    }

    @Synchronized
    fun onLoadStarted() {
        loadStartedAtMs = clock()
        loadInFlight = true
    }

    @Synchronized
    fun onLoadFinished() {
        if (!loadInFlight) return
        loadInFlight = false
        val measured = (clock() - loadStartedAtMs).coerceAtLeast(0L).toDouble()
        // A peak, not an average: one cold page must not be erased by ten fast
        // replay hits. The floor keeps later estimates conservative.
        loadPeakMs = maxOf(measured, loadPeakMs * LOAD_DECAY)
            .coerceIn(MIN_LOAD_MS.toDouble(), MAX_LOAD_MS.toDouble())
    }

    @Synchronized
    fun onLoadAborted() {
        loadInFlight = false
    }

    @Synchronized
    fun evaluate(
        page: Int,
        total: Int,
        isLoading: Boolean,
        canLoadMore: Boolean,
    ): Decision {
        val remaining = (total - 1 - page).coerceAtLeast(0)
        if (!canLoadMore || isLoading) {
            return Decision(false, remaining, 0.0, Long.MAX_VALUE)
        }

        val rate = currentRate()
        if (remaining <= FLOOR_PAGES) {
            return Decision(true, remaining, rate, etaMs(remaining, rate))
        }
        if (remaining > MAX_RUNWAY_PAGES || rate < MIN_RATE) {
            return Decision(false, remaining, rate, etaMs(remaining, rate))
        }

        val eta = etaMs(remaining, rate)
        val budget = (loadPeakMs * LOAD_SAFETY + LOAD_MARGIN_MS).toLong()
        return Decision(eta <= budget, remaining, rate, eta)
    }

    private fun currentRate(): Double {
        if (lastPage < 0) return 0.0
        if (clock() - lastPageAtMs >= RATE_IDLE_RESET_MS) return 0.0
        return pagesPerSecond
    }

    private fun etaMs(remaining: Int, rate: Double): Long =
        if (rate <= 0.0) Long.MAX_VALUE else (remaining * 1_000.0 / rate).toLong()

    companion object {
        /** Always keep this many reels of runway for an idle/slow user. */
        const val FLOOR_PAGES = 6

        /** Below this speed the floor is the only sensible trigger. */
        const val MIN_RATE = 0.2

        /** Upper bound for a single fling's predicted runway. */
        const val MAX_RUNWAY_PAGES = 64

        private const val INITIAL_LOAD_MS = 2_500
        private const val MIN_LOAD_MS = 2_000
        private const val MAX_LOAD_MS = 15_000
        private const val LOAD_DECAY = 0.9
        private const val LOAD_SAFETY = 1.15
        private const val LOAD_MARGIN_MS = 700L
        private const val RATE_ALPHA = 0.35
        private const val MAX_RATE = 30.0
        private const val MAX_SAMPLE_GAP_MS = 1_500L
        private const val RATE_IDLE_RESET_MS = 1_000L
    }
}
