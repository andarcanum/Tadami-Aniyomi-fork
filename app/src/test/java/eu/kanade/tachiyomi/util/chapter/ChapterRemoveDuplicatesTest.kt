package eu.kanade.tachiyomi.util.chapter

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

class ChapterRemoveDuplicatesTest {

    @Test
    fun `removeDuplicateChapters keeps the first chapter when no preferred chapter is supplied`() {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, scanlator = "Team A"),
            chapter(id = 2, chapterNumber = 1.0, scanlator = "Team B"),
            chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"),
        )

        chapters.removeDuplicateChapters { it } shouldBe listOf(
            chapter(id = 1, chapterNumber = 1.0, scanlator = "Team A"),
            chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"),
        )
    }

    @Test
    fun `removeDuplicates prefers the matching chapter and scanlator`() {
        val preferred = chapter(id = 2, chapterNumber = 1.0, scanlator = "Team B")
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, scanlator = "Team A"),
            preferred,
            chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"),
        )

        chapters.removeDuplicates(preferred) shouldBe listOf(
            preferred,
            chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"),
        )
    }

    @Test
    fun `removeDuplicateChapters works with selectors`() {
        val items = listOf(
            ChapterEntry(chapter(id = 1, chapterNumber = 1.0, scanlator = "Team A"), "first"),
            ChapterEntry(chapter(id = 2, chapterNumber = 1.0, scanlator = "Team B"), "second"),
            ChapterEntry(chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"), "third"),
        )

        items.removeDuplicateChapters { it.chapter } shouldBe listOf(
            ChapterEntry(chapter(id = 1, chapterNumber = 1.0, scanlator = "Team A"), "first"),
            ChapterEntry(chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"), "third"),
        )
    }

    private data class ChapterEntry(
        val chapter: Chapter,
        val label: String,
    )

    private fun chapter(
        id: Long,
        chapterNumber: Double,
        scanlator: String?,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            chapterNumber = chapterNumber,
            scanlator = scanlator,
        )
    }
}
