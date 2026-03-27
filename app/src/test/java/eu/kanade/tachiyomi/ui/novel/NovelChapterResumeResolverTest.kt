package eu.kanade.tachiyomi.ui.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelChapterResumeResolverTest {

    @Test
    fun `in-progress unread chapter wins regardless of chapter number order`() {
        val chapter1 = novelChapter(id = 1L, sourceOrder = 2L, chapterNumber = 1.0, read = true)
        val chapter2 = novelChapter(id = 2L, sourceOrder = 0L, chapterNumber = 10.0, read = true)
        val chapter3 = novelChapter(
            id = 3L,
            sourceOrder = 1L,
            chapterNumber = 20.0,
            read = false,
            lastPageRead = 8L,
        )

        resolveNovelResumeChapter(listOf(chapter1, chapter2, chapter3))?.id shouldBe chapter3.id
    }

    @Test
    fun `next unread chapter follows source order instead of chapter number order`() {
        val chapter1 = novelChapter(id = 1L, sourceOrder = 2L, chapterNumber = 1.0, read = true)
        val chapter2 = novelChapter(id = 2L, sourceOrder = 0L, chapterNumber = 10.0, read = true)
        val chapter3 = novelChapter(id = 3L, sourceOrder = 1L, chapterNumber = 20.0, read = false)

        resolveNovelResumeChapter(listOf(chapter1, chapter2, chapter3))?.id shouldBe chapter1.id
    }

    @Test
    fun `fully read novel resumes the last touched chapter`() {
        val chapter1 = novelChapter(id = 1L, sourceOrder = 2L, chapterNumber = 1.0, read = true)
        val chapter2 = novelChapter(id = 2L, sourceOrder = 0L, chapterNumber = 10.0, read = true)
        val chapter3 = novelChapter(id = 3L, sourceOrder = 1L, chapterNumber = 20.0, read = true)

        resolveNovelResumeChapter(listOf(chapter1, chapter2, chapter3))?.id shouldBe chapter1.id
    }

    private fun novelChapter(
        id: Long,
        sourceOrder: Long,
        chapterNumber: Double,
        read: Boolean,
        lastPageRead: Long = 0L,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = 1L,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
            read = read,
            lastPageRead = lastPageRead,
            url = "https://example.org/ch$id",
            name = "Chapter $id",
        )
    }
}
