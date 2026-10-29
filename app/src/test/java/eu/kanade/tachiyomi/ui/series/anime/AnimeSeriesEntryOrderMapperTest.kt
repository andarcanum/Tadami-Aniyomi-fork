package eu.kanade.tachiyomi.ui.series.anime

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeSeriesEntryOrderMapperTest {

    @Test
    fun `buildAnimeSeriesEntries keeps visible order`() {
        val result = buildAnimeSeriesEntries(
            seriesId = 42L,
            animeIds = listOf(30L, 10L, 20L),
            entryIdsByAnimeId = mapOf(
                10L to 110L,
                20L to 120L,
                30L to 130L,
            ),
        )

        result.map { it.position } shouldContainExactly listOf(0, 1, 2)
        result.map { it.animeId } shouldContainExactly listOf(30L, 10L, 20L)
    }

    @Test
    fun `buildAnimeSeriesEntries skips missing ids`() {
        val result = buildAnimeSeriesEntries(
            seriesId = 42L,
            animeIds = listOf(30L, 999L, 10L),
            entryIdsByAnimeId = mapOf(
                10L to 110L,
                30L to 130L,
            ),
        )

        result.map { it.animeId } shouldContainExactly listOf(30L, 10L)
        result.map { it.position } shouldContainExactly listOf(0, 1)
        result.first().seriesId shouldBe 42L
    }
}
