package eu.kanade.tachiyomi.ui.series.manga

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaSeriesEntryOrderMapperTest {

    @Test
    fun `buildMangaSeriesEntries keeps visible order`() {
        val result = buildMangaSeriesEntries(
            seriesId = 42L,
            mangaIds = listOf(30L, 10L, 20L),
            entryIdsByMangaId = mapOf(
                10L to 110L,
                20L to 120L,
                30L to 130L,
            ),
        )

        result.map { it.position } shouldContainExactly listOf(0, 1, 2)
        result.map { it.mangaId } shouldContainExactly listOf(30L, 10L, 20L)
    }

    @Test
    fun `buildMangaSeriesEntries skips missing ids`() {
        val result = buildMangaSeriesEntries(
            seriesId = 42L,
            mangaIds = listOf(30L, 999L, 10L),
            entryIdsByMangaId = mapOf(
                10L to 110L,
                30L to 130L,
            ),
        )

        result.map { it.mangaId } shouldContainExactly listOf(30L, 10L)
        result.map { it.position } shouldContainExactly listOf(0, 1)
        result.first().seriesId shouldBe 42L
    }
}
