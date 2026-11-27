package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinCatalogResult
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinIdentity
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinReadiness
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanRequest
import eu.kanade.tachiyomi.animesource.model.FeedRoundRobinScanResult

/**
 * Capability interface (feed contract v28): per-chat round-robin scanning for the For You feed.
 *
 * Extends [AnimeFeedReplaySource], so the v26 account scope and the v27 replay context checks
 * stay in force. This capability never replaces the ordinary feed: hosts detect it with
 * `rawSource is AnimeFeedRoundRobinSource` and keep the legacy path for sources that do not
 * implement it. All members are abstract; no default members are added to older interfaces.
 *
 * Implementing this capability requires `extensionLib` 28.0. Older plugins inside
 * `12.0..27.0` are untouched and keep loading.
 */
interface AnimeFeedRoundRobinSource : AnimeFeedReplaySource {

    /**
     * Whether round-robin scanning is enabled for [filters]. Cheap and synchronous (an in-memory
     * decision, not a network call); false keeps the host on the ordinary v27 feed.
     */
    fun isRoundRobinEnabled(filters: AnimeFilterList): Boolean

    /**
     * Prepares round-robin on IO and reports the current readiness.
     *
     * The host calls it again on every `replayContextChanges` emission: the same account and
     * [FeedRoundRobinReadiness.Ready.storageKey] only refresh the context/catalog, while a
     * different account or storage key fences the old scan stream.
     */
    suspend fun prepareRoundRobin(): FeedRoundRobinReadiness

    /**
     * Classifies a stable item id as a round-robin candidate. Cheap and synchronous; null when
     * the id does not belong to a round-robin chat. A non-null identity must use the same
     * [FeedRoundRobinIdentity.sourceKey] that a scan for this chat requests.
     */
    fun identifyRoundRobinItem(itemId: String): FeedRoundRobinIdentity?

    /**
     * Returns the current chat catalog for [expectedContext], optionally bypassing a cache.
     * Returns [FeedRoundRobinCatalogResult.ContextChanged] after a 401 or any context loss
     * instead of declaring every chat unavailable.
     */
    suspend fun getRoundRobinCatalog(
        expectedContext: FeedReplayContext,
        forceRefresh: Boolean,
    ): FeedRoundRobinCatalogResult

    /**
     * Scans one chat for a bounded batch of candidates (at most
     * [FeedRoundRobinScanRequest.maxCandidates], 1..2). The host calls it on IO; the returned
     * page must echo the requested source key and carry a non-empty checkpoint, including when
     * the chat's history is exhausted.
     */
    suspend fun scanRoundRobinSource(request: FeedRoundRobinScanRequest): FeedRoundRobinScanResult
}
