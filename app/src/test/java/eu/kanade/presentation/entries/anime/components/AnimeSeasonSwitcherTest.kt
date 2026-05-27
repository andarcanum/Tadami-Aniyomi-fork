package eu.kanade.presentation.entries.anime.components

import aniyomi.domain.anime.SeasonAnime
import eu.kanade.tachiyomi.animesource.model.FetchType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime

class AnimeSeasonSwitcherTest {

    @Test
    fun `selected season matches current anime id`() {
        val seasons = listOf(
            testSeasonAnime(id = 2L, seasonNumber = 2.0),
            testSeasonAnime(id = 1L, seasonNumber = 1.0),
        )

        val items = resolveAnimeSeasonSwitcherItems(currentAnimeId = 2L, seasons = seasons)

        items.map { it.animeId } shouldBe listOf(1L, 2L)
        items.map { it.selected } shouldBe listOf(false, true)
    }

    private fun testSeasonAnime(id: Long, seasonNumber: Double): SeasonAnime {
        val anime = Anime.create().copy(
            id = id,
            source = 1L,
            url = "/anime/$id",
            title = "Season $id",
            fetchType = FetchType.Seasons,
            initialized = true,
            seasonNumber = seasonNumber,
        )

        return SeasonAnime(
            anime = anime,
            totalCount = 12L,
            seenCount = 0L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            fetchedAt = 0L,
            lastSeen = 0L,
        )
    }
}
