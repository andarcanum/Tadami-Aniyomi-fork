package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderViewModelTest {

    @Test
    fun `unread chapters still restore saved progress`() {
        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = false,
                lastPageRead = 0L,
            ),
            preserveReadingPosition = false,
        ) shouldBe true
    }

    @Test
    fun `read chapters restore when saved progress exists`() {
        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = true,
                lastPageRead = 12L,
            ),
            preserveReadingPosition = false,
        ) shouldBe true
    }

    @Test
    fun `read chapters without saved progress only restore when preserve is enabled`() {
        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = true,
                lastPageRead = 0L,
            ),
            preserveReadingPosition = false,
        ) shouldBe false

        shouldRestoreSavedProgress(
            chapter = readerChapter(
                read = true,
                lastPageRead = 0L,
            ),
            preserveReadingPosition = true,
        ) shouldBe true
    }

    @Test
    fun `adjacent chapter switch flushes before restart`() {
        val events = mutableListOf<String>()

        prepareAdjacentChapterSwitch(
            flushReadTimer = { events += "flush" },
            restartReadTimer = { events += "restart" },
        )

        events shouldBe listOf("flush", "restart")
    }

    private fun readerChapter(
        read: Boolean,
        lastPageRead: Long,
    ): ReaderChapter {
        return ReaderChapter(
            ChapterImpl().apply {
                id = 1L
                manga_id = 1L
                url = "chapter-1"
                name = "Chapter 1"
                this.read = read
                this.last_page_read = lastPageRead
            },
        )
    }
}
