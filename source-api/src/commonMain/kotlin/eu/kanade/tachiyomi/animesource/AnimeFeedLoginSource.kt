package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (contract v19): a feed source that supports account login, so the
 * remote service can tie the personalized feed to an account (and later expose account-only
 * features such as custom feeds).
 *
 * The host detects support with `source is AnimeFeedLoginSource` (instanceof) — the same
 * marker-interface pattern as [AnimeCreatorFeedSource] and [AnimeReelsFeedbackSource]; no
 * default members are added to existing interfaces, so plugins compiled before this interface
 * exists remain untouched.
 */
interface AnimeFeedLoginSource {

    /**
     * Authenticates with the remote service using credentials.
     *
     * @return true on success, false when the credentials are rejected. Ordinary auth failures
     * (wrong credentials, unconfirmed email, blocked account) must return false, not throw;
     * network/transport errors may throw and are surfaced by the host as transient errors.
     */
    suspend fun login(email: String, password: String): Boolean

    /**
     * True when a usable session is persisted (an access token is available). Non-suspend:
     * implementations must answer from local storage only, so the host can call it on demand
     * without a background dispatch.
     */
    fun isLoggedIn(): Boolean

    /**
     * Human-readable account label for the host's login-state UI (e.g. the email used to log
     * in). Non-suspend, local read. Null when logged out.
     */
    fun loggedInAccount(): String?

    /**
     * Clears the persisted session. Fire-and-forget from the host's perspective: failures are
     * swallowed and must never leave the source in a half-logged-out state that cannot be
     * wiped locally.
     */
    suspend fun logout()
}
