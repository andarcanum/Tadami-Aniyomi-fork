package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelEpubExportRangeTest {

    @Test
    fun `export all ignores range and remains valid`() {
        val result = resolveNovelEpubRangeSelection(
            exportAll = true,
            startChapterText = "100",
            endChapterText = "1",
            chapterCount = 10,
        )

        result.isValid shouldBe true
        result.startChapter shouldBe null
        result.endChapter shouldBe null
    }

    @Test
    fun `custom range requires both chapter values`() {
        val result = resolveNovelEpubRangeSelection(
            exportAll = false,
            startChapterText = "3",
            endChapterText = "",
            chapterCount = 20,
        )

        result.isValid shouldBe false
    }

    @Test
    fun `custom range is invalid when start is greater than end`() {
        val result = resolveNovelEpubRangeSelection(
            exportAll = false,
            startChapterText = "8",
            endChapterText = "4",
            chapterCount = 20,
        )

        result.isValid shouldBe false
    }

    @Test
    fun `custom range is invalid when chapter exceeds available count`() {
        val result = resolveNovelEpubRangeSelection(
            exportAll = false,
            startChapterText = "1",
            endChapterText = "999",
            chapterCount = 50,
        )

        result.isValid shouldBe false
    }

    @Test
    fun `custom range returns parsed values when valid`() {
        val result = resolveNovelEpubRangeSelection(
            exportAll = false,
            startChapterText = "2",
            endChapterText = "5",
            chapterCount = 20,
        )

        result.isValid shouldBe true
        result.startChapter shouldBe 2
        result.endChapter shouldBe 5
    }
}
