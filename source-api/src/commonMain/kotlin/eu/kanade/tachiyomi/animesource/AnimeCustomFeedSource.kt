package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.CustomFeedDetail
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import eu.kanade.tachiyomi.animesource.model.FeedPage

/**
 * Capability interface (contract v19): a feed source that can list, play and manage a user's
 * own custom feeds (uploads curated by tag lists, usually behind account login).
 *
 * The host detects support with `source is AnimeCustomFeedSource` (instanceof) — the same
 * marker-interface pattern as [AnimeCreatorFeedSource]/[AnimeReelsFeedbackSource]; no default
 * members are added to existing interfaces, so older plugins remain untouched.
 */
interface AnimeCustomFeedSource {

    /**
     * Returns the user's custom feeds. Result is intentionally unpaginated in v1: a single
     * user's feed list is small. Return an empty list when the user has none or is not
     * logged in. Failures may throw and are surfaced by the host as transient errors.
     */
    suspend fun getCustomFeeds(): List<CustomFeedRef>

    /**
     * Fetches one page of a custom feed's videos. Pagination follows the v17 sticky protocol
     * per stream: pass a null [cursor] until [FeedPage.nextCursor] is set, then echo the token
     * back. [page] is 1-based and authoritative only in page-int mode. Replay-safe: the host
     * may retry a failed page with the exact same (id, page, cursor).
     *
     * @param id custom-feed id as previously returned by [getCustomFeeds].
     */
    suspend fun getCustomFeed(id: String, page: Int, cursor: String?): FeedPage

    /**
     * Available tag names for the custom-feed editor. The host presents these for selection;
     * an implementation may additionally accept free-form tags on create/update.
     */
    suspend fun getCustomFeedTags(): List<String>

    /** Full content (name + tags) of one custom feed, for pre-filling the editor. */
    suspend fun getCustomFeedDetail(id: String): CustomFeedDetail

    /** Creates a custom feed and returns its reference (id + name). */
    suspend fun createCustomFeed(name: String, tags: List<String>): CustomFeedRef

    /** Updates an existing custom feed. Returns true on success. */
    suspend fun updateCustomFeed(id: String, name: String, tags: List<String>): Boolean

    /** Deletes an existing custom feed. Returns true on success. */
    suspend fun deleteCustomFeed(id: String): Boolean
}
