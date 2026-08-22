package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelReaderChapterWindowTest {

    @Test
    fun `window centers around current chapter with correct radius`() {
        val allChapters = (1L..2000L).map { fakeChapter(it) }
        val window = NovelReaderChapterWindow.resolveWindow(
            chapters = allChapters,
            currentChapterId = 500L,
            windowRadius = 50,
        )

        window.size shouldBe 101 // ±50 + current
        window.first().id shouldBe 450L
        window.last().id shouldBe 550L
    }

    @Test
    fun `window clamps at start of list`() {
        val allChapters = (1L..2000L).map { fakeChapter(it) }
        val window = NovelReaderChapterWindow.resolveWindow(
            chapters = allChapters,
            currentChapterId = 10L,
            windowRadius = 50,
        )

        window.first().id shouldBe 1L
    }

    @Test
    fun `window clamps at end of list`() {
        val allChapters = (1L..2000L).map { fakeChapter(it) }
        val window = NovelReaderChapterWindow.resolveWindow(
            chapters = allChapters,
            currentChapterId = 1995L,
            windowRadius = 50,
        )

        window.last().id shouldBe 2000L
    }

    @Test
    fun `navigate to previous chapter builds centered window`() {
        val allChapters = (1L..2000L).map { fakeChapter(it) }
        val result = NovelReaderChapterWindow.navigate(
            currentChapterId = 500L,
            allChapters = allChapters,
            direction = -1,
            windowRadius = 50,
        )

        result.newCurrentChapter.id shouldBe 499L
        result.newWindow.first().id shouldBe 449L
    }

    @Test
    fun `navigate forward past last chapter clamps to end`() {
        val allChapters = (1L..2000L).map { fakeChapter(it) }

        val result = NovelReaderChapterWindow.navigate(
            currentChapterId = 2000L,
            allChapters = allChapters,
            direction = 1,
            windowRadius = 50,
        )

        result.newCurrentChapter.id shouldBe 2000L
        result.newWindow.last().id shouldBe 2000L
    }

    @Test
    fun `novel completed requires chapters outside the window to be read`() {
        val allChapters = (1L..300L).map { fakeChapter(it).copy(read = it != 1L) }
        val window = NovelReaderChapterWindow.resolveWindow(
            chapters = allChapters,
            currentChapterId = 300L,
            windowRadius = 50,
        )

        novelReaderNovelCompleted(
            fullChapterList = allChapters,
            visibleWindow = window,
        ) shouldBe false
    }

    @Test
    fun `novel completed when every chapter including outside the window is read`() {
        val allChapters = (1L..300L).map { fakeChapter(it).copy(read = true) }
        val window = NovelReaderChapterWindow.resolveWindow(
            chapters = allChapters,
            currentChapterId = 300L,
            windowRadius = 50,
        )

        novelReaderNovelCompleted(
            fullChapterList = allChapters,
            visibleWindow = window,
        ) shouldBe true
    }

    @Test
    fun `novel completed falls back to visible window without full list`() {
        val window = (1L..101L).map { fakeChapter(it).copy(read = true) }

        novelReaderNovelCompleted(
            fullChapterList = emptyList(),
            visibleWindow = window,
        ) shouldBe true
    }

    companion object {
        private fun fakeChapter(id: Long) = NovelChapter(
            id = id,
            novelId = 1L,
            url = "/ch/$id",
            name = "Chapter $id",
            dateUpload = 0L,
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            chapterNumber = id.toDouble(),
            sourceOrder = id,
            dateFetch = 0L,
            lastModifiedAt = 0L,
            version = 1L,
        )
    }
}
