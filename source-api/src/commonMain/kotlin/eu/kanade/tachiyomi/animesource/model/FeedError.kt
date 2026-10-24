package eu.kanade.tachiyomi.animesource.model

/**
 * Typed feed-failure verdict returned by
 * [eu.kanade.tachiyomi.animesource.AnimeFeedErrorClassifierSource] (contract v25).
 *
 * The host acts on the verdict instead of parsing error text, so a source can express
 * cursor expiry, session death and retry windows in its own wording.
 */
sealed interface FeedError {

    /**
     * The supplied cursor can no longer be resolved (TTL, LRU eviction, account change).
     * On an append the host stops pagination silently instead of retrying the dead cursor;
     * a manual refresh starts a fresh generation from `cursor = null`. On a reset load there
     * is no cursor to expire, so the host surfaces it as a regular terminal error.
     */
    data object ExpiredCursor : FeedError

    /**
     * The source needs a fresh session (expired bearer, Cloudflare challenge, logged-out
     * account). Hosts with a web-login-capable source start their session bootstrap even when
     * the message contains no recognizable status text.
     */
    data object AuthRequired : FeedError

    /**
     * The source rate-limited the request. [retryAtEpochMillis] is the absolute wall-clock
     * time (epoch millis) when a retry is allowed, or null when the source knows no window
     * and the host should use its default backoff. The host caps its automatic retries.
     */
    data class RateLimited(val retryAtEpochMillis: Long? = null) : FeedError

    /**
     * A retryable failure with no auth/rate-limit semantics: the host surfaces it and waits
     * for the user or the next trigger. No automatic backoff is scheduled.
     */
    data object Transient : FeedError

    /**
     * A non-retryable failure. The host neither bootstraps a session nor auto-retries,
     * regardless of status-like numbers in the message text.
     */
    data object Terminal : FeedError
}
