package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable

/**
 * Verified account and continuity key proving replay eligibility (feed contract v27).
 *
 * Rules:
 * - [accountId] matches the v26 [AnimeFeedAccountIdentitySource.accountScopeId].
 * - [continuityKey] is a stable source-owned identifier for the backend session/configuration.
 *   It MUST NOT contain ephemeral values like process port, auth tokens, signed URLs or
 *   process epochs.
 */
data class FeedReplayContext(
    val accountId: String,
    val continuityKey: String,
) : Serializable
