package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelReaderChapterProgressUpdaterTest {
    @Test
    fun `updates only matching chapter progress`() {
        val firstChapter = NovelChapter.create().copy(
            id = 1L,
            read = false,
            lastPageRead = 0L,
        )
        val secondChapter = NovelChapter.create().copy(
            id = 2L,
            read = false,
            lastPageRead = 5L,
        )
        val chapters = listOf(firstChapter, secondChapter)

        val updated = updateNovelReaderChapterProgressList(
            chapters = chapters,
            chapterId = 2L,
            read = true,
            progress = 42L,
        )

        assertSame(firstChapter, updated[0])
        updated[1].read shouldBe true
        updated[1].lastPageRead shouldBe 42L
    }

    @Test
    fun `returns original list when chapter is missing`() {
        val chapters = listOf(
            NovelChapter.create().copy(id = 1L, read = false, lastPageRead = 0L),
            NovelChapter.create().copy(id = 2L, read = false, lastPageRead = 5L),
        )

        val updated = updateNovelReaderChapterProgressList(
            chapters = chapters,
            chapterId = 99L,
            read = true,
            progress = 42L,
        )

        assertSame(chapters, updated)
    }
}
