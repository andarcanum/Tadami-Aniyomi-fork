package eu.kanade.tachiyomi.ui.reader.setting

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaReaderModeRecommendationTest {

    @Test
    fun `webtoon metadata recommends webtoon mode`() {
        recommendReadingModeForMangaFormat(
            genres = listOf("Action", "Webtoon"),
            sourceName = null,
        ) shouldBe ReadingMode.WEBTOON.flagValue
    }

    @Test
    fun `manhwa metadata recommends webtoon mode`() {
        recommendReadingModeForMangaFormat(
            genres = listOf("Манхва"),
            sourceName = null,
        ) shouldBe ReadingMode.WEBTOON.flagValue
    }

    @Test
    fun `source hint recommends webtoon mode`() {
        recommendReadingModeForMangaFormat(
            genres = null,
            sourceName = "Best Manhwa Source",
        ) shouldBe ReadingMode.WEBTOON.flagValue
    }

    @Test
    fun `unknown metadata does not recommend webtoon mode`() {
        recommendReadingModeForMangaFormat(
            genres = listOf("Manga", "Romance"),
            sourceName = "Generic Source",
        ).shouldBeNull()
    }

    @Test
    fun `two tall pages are enough for webtoon dimension detection`() {
        isLikelyWebtoonFromPageDimensions(
            listOf(
                MangaReaderPageDimensions(width = 900, height = 2800),
                MangaReaderPageDimensions(width = 900, height = 3000),
            ),
        ) shouldBe true
    }

    @Test
    fun `one extreme tall page is enough for webtoon dimension detection`() {
        isLikelyWebtoonFromPageDimensions(
            listOf(
                MangaReaderPageDimensions(width = 900, height = 4600),
            ),
        ) shouldBe true
    }

    @Test
    fun `normal pages do not trigger webtoon dimension detection`() {
        isLikelyWebtoonFromPageDimensions(
            listOf(
                MangaReaderPageDimensions(width = 1200, height = 1800),
                MangaReaderPageDimensions(width = 1200, height = 1600),
            ),
        ) shouldBe false
    }
}
