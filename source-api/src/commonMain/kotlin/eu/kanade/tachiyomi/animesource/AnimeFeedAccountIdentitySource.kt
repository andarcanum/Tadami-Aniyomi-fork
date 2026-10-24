package eu.kanade.tachiyomi.animesource

/**
 * Capability interface (feed contract v26): a feed source that can name the account it is
 * currently serving with a STABLE identifier.
 *
 * The host keys its durable "already watched / deliberately skipped" history by
 * `(source, account scope, item id)`. A display name, an @handle or a session token is not an
 * identity: it changes between logins or leaks a secret, and either way it silently splits or
 * merges someone's history. This capability lets a source supply a real id instead
 * (for example the backend's numeric user id), which survives relogin and process death.
 *
 * Rules:
 * - Return `null` when no stable identity is known (signed out, backend not ready, guest
 *   session). The host then falls back to a source-scoped history — never to a guessed value.
 * - The value must be stable for the same account across sessions and must NOT contain
 *   credentials, session tokens or signed URLs.
 * - Instanceof-detected, like every other capability: no default members are added to
 *   existing interfaces, so plugins built against an older `source-api` keep working.
 */
interface AnimeFeedAccountIdentitySource {

    /**
     * Stable account id for history scoping, or `null` when the source cannot prove one.
     * Must be cheap and synchronous (an in-memory value), since the host calls it when a
     * feed is opened and when an interaction is committed.
     */
    fun accountScopeId(): String?
}
