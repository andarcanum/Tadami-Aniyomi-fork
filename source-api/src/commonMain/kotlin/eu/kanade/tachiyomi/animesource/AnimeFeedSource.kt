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
     * @param page Page index to fetch (1-based).
     * @param filters Filters applied to the feed.
     */
    suspend fun getFeed(page: Int, filters: AnimeFilterList = getFilterList()): FeedPage

    /**
     * Searches for video feed items by query/tag.
     *
     * @param page Page index to fetch (1-based).
     * @param query The search query or tag.
     * @param filters Filters applied to the search.
     */
    suspend fun getSearchFeed(page: Int, query: String, filters: AnimeFilterList): FeedPage

    // Unused standard AnimeSource defaults for clean feed sources
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
    override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
}
