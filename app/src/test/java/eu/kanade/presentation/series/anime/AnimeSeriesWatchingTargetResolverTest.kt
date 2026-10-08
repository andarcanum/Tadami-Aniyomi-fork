package eu.kanade.presentation.series.anime

import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.anime.model.AnimeSeries
import tachiyomi.domain.series.anime.model.LibraryAnimeSeries
import kotlin.test.assertEquals

class AnimeSeriesWatchingTargetResolverTest {

    @Test
    fun `returns next anime when active anime is fully seen`() {
        val series = LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryAnime(id = 10L, title = "One", totalEpisodes = 100, seenCount = 90),
                libraryAnime(id = 20L, title = "Two", totalEpisodes = 20, seenCount = 0),
            ),
        )

        val episodes = listOf(
            libraryAnimeEpisodes(
                animeId = 10L,
                episodeIds = listOf(1L, 2L, 3L),
                seen = true,
            ),
            libraryAnimeEpisodes(
                animeId = 20L,
                episodeIds = listOf(11L, 12L),
                seen = false,
            ),
        )

        val target = resolveAnimeSeriesWatchingTarget(series, episodes)

        assertEquals(20L, target?.anime?.anime?.id)
        assertEquals(11L, target?.episode?.id)
    }

    @Test
    fun `returns first unseen episode by episode number order`() {
        val series = LibraryAnimeSeries(
            series = AnimeSeries(
                id = 1L,
                title = "Series",
                description = null,
                categoryId = 0L,
                sortOrder = 0L,
                dateAdded = 0L,
                coverLastModified = 0L,
            ),
            entries = listOf(
                libraryAnime(id = 10L, title = "One", totalEpisodes = 100, seenCount = 0),
            ),
        )

        val episodes = listOf(
            libraryAnimeEpisodes(
                animeId = 10L,
                episodeIds = listOf(21L, 11L),
                seen = false,
                episodeNumbers = listOf(2.0, 1.0),
                sourceOrders = listOf(0L, 1L),
            ),
        )

        val target = resolveAnimeSeriesWatchingTarget(series, episodes)

        assertEquals(11L, target?.episode?.id)
    }

    @Test
    fun `history episode id keeps the exact episode even when later unseen exists`() {
        val episode1 = Episode.create().copy(
            id = 1L,
            animeId = 10L,
            seen = false,
            episodeNumber = 1.0,
            sourceOrder = 0L,
            name = "Episode 1",
            url = "https://example.com/anime/10/1",
        )
        val episode2 = Episode.create().copy(
            id = 2L,
            animeId = 10L,
            seen = true,
            episodeNumber = 2.0,
            sourceOrder = 1L,
            name = "Episode 2",
            url = "https://example.com/anime/10/2",
        )
        val episode3 = Episode.create().copy(
            id = 3L,
            animeId = 10L,
            seen = false,
            episodeNumber = 3.0,
            sourceOrder = 2L,
            name = "Episode 3",
            url = "https://example.com/anime/10/3",
        )

        val target = resolveAnimeResumeEpisode(
            listOf(episode1, episode2, episode3),
            fromEpisodeId = episode2.id,
        )

        assertEquals(episode2.id, target?.id)
    }

    private fun libraryAnime(
        id: Long,
        title: String,
        totalEpisodes: Long,
        seenCount: Long,
    ): LibraryAnime {
        return LibraryAnime(
            anime = Anime.create().copy(
                id = id,
                title = title,
                source = 1L,
                url = "https://example.com/$id",
            ),
            category = 0L,
            totalCount = totalEpisodes,
            seenCount = seenCount,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }

    private fun libraryAnimeEpisodes(
        animeId: Long,
        episodeIds: List<Long>,
        seen: Boolean,
        episodeNumbers: List<Double> = episodeIds.mapIndexed { index, _ -> (index + 1).toDouble() },
        sourceOrders: List<Long> = episodeIds.mapIndexed { index, _ -> (index + 1).toLong() },
    ): Pair<LibraryAnime, List<Episode>> {
        val anime = libraryAnime(id = animeId, title = "Anime $animeId", totalEpisodes = 1, seenCount = 0)
        return anime to episodeIds.mapIndexed { index, episodeId ->
            Episode.create().copy(
                id = episodeId,
                animeId = animeId,
                seen = seen,
                episodeNumber = episodeNumbers[index],
                sourceOrder = sourceOrders[index],
                name = "Episode $episodeId",
                url = "https://example.com/anime/$animeId/$episodeId",
            )
        }
    }
}
