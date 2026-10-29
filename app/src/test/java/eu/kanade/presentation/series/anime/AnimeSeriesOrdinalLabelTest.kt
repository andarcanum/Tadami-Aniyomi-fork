package eu.kanade.presentation.series.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeSeriesOrdinalLabelTest {

    @Test
    fun `ordinal label is hidden for a single title series`() {
        resolveAnimeSeriesOrdinalLabel(index = 0, totalCount = 1) shouldBe null
    }

    @Test
    fun `ordinal label starts at one for the first title`() {
        resolveAnimeSeriesOrdinalLabel(index = 0, totalCount = 3) shouldBe "1"
    }

    @Test
    fun `ordinal label follows the visible order after reorder`() {
        resolveAnimeSeriesOrdinalLabel(index = 1, totalCount = 4) shouldBe "2"
    }
}
