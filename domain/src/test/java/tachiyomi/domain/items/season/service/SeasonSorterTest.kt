package tachiyomi.domain.items.season.service

import aniyomi.domain.anime.SeasonAnime
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime

class SeasonSorterTest {

    @Test
    fun `invalid season sorting falls back to source order comparator`() {
        val animeForPrefs = Anime.create().copy(seasonFlags = Anime.SEASON_SORT_MASK)
        val first = SeasonAnime(
            anime = Anime.create().copy(id = 1L, title = "B", seasonSourceOrder = 1L),
            totalCount = 0L,
            seenCount = 0L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            fetchedAt = 0L,
            lastSeen = 0L,
        )
        val second = SeasonAnime(
            anime = Anime.create().copy(id = 2L, title = "A", seasonSourceOrder = 2L),
            totalCount = 0L,
            seenCount = 0L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            fetchedAt = 0L,
            lastSeen = 0L,
        )

        val comparator = getSeasonSortComparator(animeForPrefs)

        comparator.compare(first, second) shouldBe -1
        comparator.compare(second, first) shouldBe 1
    }
}
