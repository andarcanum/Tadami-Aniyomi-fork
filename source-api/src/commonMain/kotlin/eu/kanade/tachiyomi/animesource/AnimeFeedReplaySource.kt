package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedReplayContext
import eu.kanade.tachiyomi.animesource.model.FeedReplayItemResult
import kotlinx.coroutines.flow.Flow

/**
 * Capability interface (feed contract v27): a feed source that supports session continuity
 * and safe item rehydration.
 *
 * All members are abstract (no default methods).
 */
interface AnimeFeedReplaySource : AnimeFeedAccountIdentitySource {

    /**
     * Cold flow of replay context changes.
     * Emits the proven [FeedReplayContext] when ready, or `null` when signed out / invalidated.
     * Must not perform heavy native/network work on the caller thread upon flow creation.
     */
    val replayContextChanges: Flow<FeedReplayContext?>

    /**
     * Prepares replay capability on IO and returns the current verified context,
     * or `null` if unproven / unavailable.
     */
    suspend fun prepareReplay(): FeedReplayContext?

    /**
     * Rehydrates a single saved candidate item with fresh stream/poster URLs.
     */
    suspend fun resolveReplayItem(
        itemId: String,
        expectedContext: FeedReplayContext,
        filters: AnimeFilterList,
    ): FeedReplayItemResult
}
