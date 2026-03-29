package eu.kanade.presentation.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelChapterDateTextTest {

    @Test
    fun `returns relative date text when parsed upload date exists`() {
        val chapter = NovelChapter.create().copy(
            dateUpload = 1234L,
            dateUploadRaw = "Yesterday",
        )

        val text = novelChapterDateText(chapter, parsedDateText = "2 days ago")

        text shouldBe "2 days ago"
    }

    @Test
    fun `returns raw date text when parsed upload date is missing`() {
        val chapter = NovelChapter.create().copy(
            dateUpload = 0L,
            dateUploadRaw = "4 hours ago",
        )
        val text = novelChapterDateText(chapter, parsedDateText = null)

        text shouldBe "4 hours ago"
    }

    @Test
    fun `returns null when no upload date is available`() {
        val chapter = NovelChapter.create().copy(
            dateUpload = 0L,
            dateUploadRaw = null,
        )

        val text = novelChapterDateText(chapter, parsedDateText = null)

        text shouldBe null
    }
}
