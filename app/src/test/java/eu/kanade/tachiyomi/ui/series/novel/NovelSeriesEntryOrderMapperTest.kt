package eu.kanade.tachiyomi.ui.series.novel

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelSeriesEntryOrderMapperTest {

    @Test
    fun `buildNovelSeriesEntries keeps visible order`() {
        val result = buildNovelSeriesEntries(
            seriesId = 42L,
            novelIds = listOf(30L, 10L, 20L),
            entryIdsByNovelId = mapOf(
                10L to 110L,
                20L to 120L,
                30L to 130L,
            ),
        )

        result.map { it.position } shouldContainExactly listOf(0, 1, 2)
        result.map { it.novelId } shouldContainExactly listOf(30L, 10L, 20L)
    }

    @Test
    fun `buildNovelSeriesEntries skips missing ids`() {
        val result = buildNovelSeriesEntries(
            seriesId = 42L,
            novelIds = listOf(30L, 999L, 10L),
            entryIdsByNovelId = mapOf(
                10L to 110L,
                30L to 130L,
            ),
        )

        result.map { it.novelId } shouldContainExactly listOf(30L, 10L)
        result.map { it.position } shouldContainExactly listOf(0, 1)
        result.first().seriesId shouldBe 42L
    }
}
