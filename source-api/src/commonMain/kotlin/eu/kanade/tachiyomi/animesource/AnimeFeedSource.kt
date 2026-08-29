package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Source interface for short vertical video feeds (Reels / TikTok-style).
 */
interface AnimeFeedSource : AnimeSource {

    /**
     * Marker flag indicating this is a short video feed source.
     */
    val isFeedSource: Boolean
        get() = true

    /**
     * Whether the source supports filtering/searching by tags.
     */
    val supportsTags: Boolean
        get() = true

    /**
     * Returns the list of filters supported by this feed source.
     */
    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    /**
     * Fetches a page of video feed items.
     *
     * Pagination modes (contract v17): when [cursor] is null the host is in page-int mode and
     * [page] is authoritative. Once a [FeedPage.nextCursor] is returned, the host locks into
     * cursor mode for the whole generation and passes the token here on every later request
     * ([page] keeps counting as a hint only). The returned [FeedPage] must echo a fresh
     * [FeedPage.nextCursor] as long as more pages exist; returning a null cursor with
     * hasNextPage == true in cursor mode stops the host's pagination (protocol violation).
     *
     * @param page 1-based page index (hint in cursor mode).
     * @param cursor continuation token from the previous response, or null.
     * @param filters Filters applied to the feed.
     */
    suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList = getFilterList()): FeedPage

    /**
     * Searches for video feed items by query/tag. Defaults to [getFeed] for sources without
     * search/tag support; the host hides the search UI for those via [supportsTags].
     */
    suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
        getFeed(page, cursor, filters)

    // Unused standard AnimeSource defaults for clean feed sources
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
    override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
}
