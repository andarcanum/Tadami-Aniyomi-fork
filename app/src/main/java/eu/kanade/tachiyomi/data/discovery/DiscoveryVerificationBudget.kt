package eu.kanade.tachiyomi.data.discovery

import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hierarchical budget for one media run: a wall-clock deadline plus a shared
 * allowance of source-details requests.
 *
 * Rows do not own independent budgets: TASTE and SOURCE consume the same
 * deadline and the same details allowance, so their combined cost stays inside
 * the worker budget instead of multiplying per row.
 */
class DiscoveryVerificationBudget(
    budgetMs: Long = DEFAULT_MEDIA_BUDGET_MS,
    maxDetails: Int = DEFAULT_MAX_DETAILS,
    maxConcurrentDetails: Int = DEFAULT_MAX_CONCURRENT_DETAILS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    init {
        require(budgetMs > 0L) { "budgetMs must be > 0" }
        require(maxDetails >= 0) { "maxDetails must be >= 0" }
        require(maxConcurrentDetails > 0) { "maxConcurrentDetails must be > 0" }
    }

    private val deadlineAt = clock() + budgetMs
    private val detailsLeft = AtomicInteger(maxDetails)

    /** Shared concurrency limit for details requests across all rows. */
    val detailPermits = Semaphore(maxConcurrentDetails)

    fun remainingMs(): Long = (deadlineAt - clock()).coerceAtLeast(0L)

    fun isExpired(): Boolean = remainingMs() == 0L

    /**
     * Reserves one details request. Returns false when the shared allowance is
     * exhausted; callers must treat that as an unattempted card, not as a
     * failed verification.
     */
    fun tryAcquireDetail(): Boolean {
        while (true) {
            val current = detailsLeft.get()
            if (current <= 0) return false
            if (detailsLeft.compareAndSet(current, current - 1)) return true
        }
    }

    val detailsExhausted: Boolean
        get() = detailsLeft.get() <= 0

    companion object {
        /** Fits three media types into the 120 s worker deadline with margin. */
        const val DEFAULT_MEDIA_BUDGET_MS = 36_000L
        const val DEFAULT_MAX_DETAILS = 48
        const val DEFAULT_MAX_CONCURRENT_DETAILS = 4
    }
}
