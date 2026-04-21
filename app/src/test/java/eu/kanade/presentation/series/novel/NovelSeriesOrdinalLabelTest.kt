package eu.kanade.presentation.series.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelSeriesOrdinalLabelTest {

    @Test
    fun `ordinal label is hidden for a single title series`() {
        resolveNovelSeriesOrdinalLabel(index = 0, totalCount = 1) shouldBe null
    }

    @Test
    fun `ordinal label starts at one for the first title`() {
        resolveNovelSeriesOrdinalLabel(index = 0, totalCount = 3) shouldBe "1"
    }

    @Test
    fun `ordinal label follows the visible order after reorder`() {
        resolveNovelSeriesOrdinalLabel(index = 1, totalCount = 4) shouldBe "2"
    }
}
