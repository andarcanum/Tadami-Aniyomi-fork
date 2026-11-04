package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.collections.immutable.ImmutableList

/**
 * Seed data passed from [ReelsAlbumScreen] to [ReelsFeedScreen] when opening an album
 * as a reels feed or tapping a specific item in the album grid.
 *
 * Avoids re-fetching page 1..N over the network when navigating from the album screen,
 * and eliminates deep-link seek failures for large albums (e.g. 5,000 items).
 */
data class ReelsAlbumFeedSeed(
    val albumId: String,
    val items: ImmutableList<ShortVideoItem>,
    val targetVideoId: String? = null,
    val nextPage: Int = 1,
    val nextCursor: String? = null,
    val cursorMode: Boolean = false,
    val canLoadMore: Boolean = true,
)

object ReelsAlbumFeedSeedStore {

    @Volatile
    var pending: ReelsAlbumFeedSeed? = null

    /**
     * Consumes the pending album seed if it matches [albumId].
     * Returns null if no seed is pending or if [albumId] doesn't match.
     */
    fun consume(albumId: String): ReelsAlbumFeedSeed? {
        val seed = pending ?: return null
        return if (seed.albumId == albumId) {
            pending = null
            seed
        } else {
            null
        }
    }

    /** Clears any unconsumed pending seed. */
    fun clear() {
        pending = null
    }
}
