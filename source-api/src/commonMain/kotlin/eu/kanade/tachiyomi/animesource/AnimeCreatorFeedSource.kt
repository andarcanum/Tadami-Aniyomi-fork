package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.FeedPage

/**
 * Capability interface (contract v18): a feed source that can also serve the video feed of a
 * single creator.
 *
 * The host detects support with `source is AnimeCreatorFeedSource` (instanceof) — never with
 * default members on [AnimeFeedSource]: plugin classes compiled before this interface exist
 * have no bridge for new default members and would hit AbstractMethodError. Extending
 * capabilities via marker-style interfaces keeps old binaries untouched.
 */
interface AnimeCreatorFeedSource {

    /**
     * Fetches one page of the creator's own feed, newest first.
     *
     * Pagination follows the v17 sticky protocol per stream: pass a null [cursor] until the
     * response carries [FeedPage.nextCursor], then echo the latest token back on every later
     * request of the same generation. [page] is 1-based and authoritative only in page-int
     * mode. Implementations must be replay-safe: the host may retry a failed page with the
     * exact same (page, cursor) pair.
     *
     * @param creator creator identity as previously returned in `ShortVideoItem.author`.
     * @param page 1-based page index (hint in cursor mode).
     * @param cursor continuation token from this creator's previous response, or null.
     */
    suspend fun getCreatorFeed(creator: String, page: Int, cursor: String?): FeedPage
}
