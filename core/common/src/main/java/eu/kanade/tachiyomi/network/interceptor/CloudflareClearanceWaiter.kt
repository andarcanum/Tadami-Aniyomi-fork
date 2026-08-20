package eu.kanade.tachiyomi.network.interceptor

/**
 * Platform-independent state machine that decides when the Cloudflare challenge resolve
 * loop should stop waiting for a `cf_clearance` cookie.
 *
 * Cloudflare challenges resolve asynchronously: the JS `challenge-platform` payload runs
 * after the first `onPageFinished`, and modern managed challenges may set the clearance
 * cookie in the same document without a navigation/redirect. The cookie can therefore
 * appear well after the last `onPageFinished`, so the resolver must *poll* for it rather
 * than inspect it once. This class isolates that timing logic (no WebView / Handler /
 * Android dependency) so it can be unit-tested on the JVM.
 */
internal class CloudflareClearanceWaiter(
    val pollIntervalMs: Long = 750L,
    val softLimitMs: Long = 7_000L,
    private val maxWaitMs: Long = 30_000L,
) {
    /** True once the clearance cookie has been observed. */
    var bypassed: Boolean = false
        private set

    private var elapsedMs = 0L

    /** True once the caller is allowed to stop waiting (cookie seen or timeout reached). */
    val shouldRelease: Boolean
        get() = bypassed || elapsedMs >= maxWaitMs

    /**
     * True once the optimistic *first* stage has elapsed. The resolver uses this as a chance
     * to check whether the challenge actually needs a human click (an interactive widget that
     * will never self-solve): those can be failed fast instead of waiting out the full
     * [maxWaitMs], while widget-free auto-solves keep polling until [shouldRelease].
     */
    val softLimitReached: Boolean
        get() = elapsedMs >= softLimitMs

    /**
     * Record a page-load event. Returns `true` if the clearance cookie is already present.
     * A `false` result does not release the wait -- the caller should keep polling via [tick].
     */
    fun onPageFinished(cookiePresent: () -> Boolean): Boolean {
        if (cookiePresent()) {
            bypassed = true
            return true
        }
        return false
    }

    /**
     * Advance the polling clock and re-check the cookie. Returns `true` if the cookie is now
     * present. Callers should invoke this on their polling schedule; [shouldRelease] becomes
     * `true` at the configured timeout regardless of the cookie.
     */
    fun tick(cookiePresent: () -> Boolean): Boolean {
        if (cookiePresent()) {
            bypassed = true
            return true
        }
        elapsedMs += pollIntervalMs
        return false
    }
}
