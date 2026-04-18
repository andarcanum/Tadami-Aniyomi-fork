package eu.kanade.presentation.series.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaSeriesOrdinalLabelTest {

    @Test
    fun `single-title series hides ordinal label`() {
        resolveMangaSeriesOrdinalLabel(index = 0, totalCount = 1) shouldBe null
    }

    @Test
    fun `multi-title series shows one-based ordinal label`() {
        resolveMangaSeriesOrdinalLabel(index = 0, totalCount = 3) shouldBe "1"
        resolveMangaSeriesOrdinalLabel(index = 1, totalCount = 3) shouldBe "2"
        resolveMangaSeriesOrdinalLabel(index = 2, totalCount = 3) shouldBe "3"
    }
}
