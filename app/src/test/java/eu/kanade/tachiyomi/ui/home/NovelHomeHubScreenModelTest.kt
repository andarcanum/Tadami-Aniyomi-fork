package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelHomeHubScreenModelTest {

    @Test
    fun `fully read series falls back to the last touched chapter when hero chapter is missing`() {
        val chapters = listOf(
            chapter(id = 101L, read = true),
            chapter(id = 102L, read = true),
            chapter(id = 103L, read = true),
        )

        resolveNovelHomeHeroChapterId(chapters, fromChapterId = 999L) shouldBe 103L
    }

    @Test
    fun `hero chapter of a book-mode title resolves from the book state`() {
        val chapters = listOf(
            chapter(id = 101L, read = true),
            chapter(id = 102L, read = true),
            chapter(id = 103L, read = true),
        )
        val bookState = tachiyomi.domain.book.novel.model.NovelBookState
            .create(novelId = 1L, sourceId = 10L)
            .copy(enabled = true, lastChapterId = 102L)

        // Without the book state the fully-read fallback picks the last touched chapter...
        resolveNovelHomeHeroChapterId(chapters, fromChapterId = 999L) shouldBe 103L
        // ...the hero card of a compiled book must resume at the stored book position instead.
        resolveNovelHomeHeroChapterId(chapters, fromChapterId = 999L, bookState = bookState) shouldBe 102L
    }

    @Test
    fun `hero chapter id reloads when the hero chapter changes inside the same novel`() {
        shouldReloadNovelHomeHeroChapterId(
            previousHeroNovelId = 1L,
            previousHeroChapterId = 30L,
            currentHeroNovelId = 1L,
            currentHeroChapterId = 33L,
        ) shouldBe true
    }

    private fun chapter(
        id: Long,
        read: Boolean,
        lastPageRead: Long = 0L,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = 1L,
            read = read,
            bookmark = false,
            lastPageRead = lastPageRead,
            dateFetch = 0L,
            sourceOrder = id,
            url = "https://example.org/ch$id",
            name = "Chapter $id",
            dateUpload = 0L,
            chapterNumber = id.toDouble(),
            scanlator = null,
            lastModifiedAt = 0L,
            version = 1L,
        )
    }
}
