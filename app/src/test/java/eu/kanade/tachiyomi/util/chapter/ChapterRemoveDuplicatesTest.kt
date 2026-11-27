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

    @Test
    fun `twelve unrecognized chapters survive regardless of the selected chapter`() {
        val chapters = (1L..12L).map { id ->
            chapter(id = id, chapterNumber = -1.0, scanlator = "Team A")
        }

        chapters.removeDuplicates(chapters[5]) shouldBe chapters
        chapters.removeDuplicateChapters { it } shouldBe chapters
    }

    @Test
    fun `explicitly unnumbered chapters are not duplicates`() {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = -2.0, scanlator = "Team A"),
            chapter(id = 2, chapterNumber = -2.0, scanlator = "Team B"),
            chapter(id = 3, chapterNumber = -2.0, scanlator = "Team A"),
        )

        chapters.removeDuplicates(chapters[1]) shouldBe chapters
    }

    @Test
    fun `unknown chapters keep their positions between recognized duplicate groups`() {
        val unknown1 = chapter(id = 10, chapterNumber = -1.0, scanlator = "Team A")
        val unknown2 = chapter(id = 11, chapterNumber = -1.0, scanlator = "Team B")
        val unnumbered1 = chapter(id = 12, chapterNumber = -2.0, scanlator = "Team B")
        val unnumbered2 = chapter(id = 13, chapterNumber = -2.0, scanlator = "Team A")
        val preferred = chapter(id = 2, chapterNumber = 1.0, scanlator = "Team B")
        val sameScanlator = chapter(id = 4, chapterNumber = 2.0, scanlator = "Team B")
        val chapters = listOf(
            unknown1,
            chapter(id = 1, chapterNumber = 1.0, scanlator = "Team A"),
            unknown2,
            preferred,
            unnumbered1,
            chapter(id = 3, chapterNumber = 2.0, scanlator = "Team A"),
            unnumbered2,
            sameScanlator,
        )

        chapters.removeDuplicates(preferred) shouldBe listOf(
            unknown1,
            preferred,
            unknown2,
            unnumbered1,
            sameScanlator,
            unnumbered2,
        )
    }

    @Test
    fun `selectors preserve separate unknown entries`() {
        val items = listOf(
            ChapterEntry(chapter(id = 1, chapterNumber = -1.0, scanlator = "Team A"), "first"),
            ChapterEntry(chapter(id = 2, chapterNumber = -1.0, scanlator = "Team A"), "second"),
            ChapterEntry(chapter(id = 3, chapterNumber = -2.0, scanlator = "Team A"), "third"),
            ChapterEntry(chapter(id = 4, chapterNumber = -2.0, scanlator = "Team A"), "fourth"),
        )

        items.removeDuplicateChapters { it.chapter } shouldBe items
    }

    @Test
    fun `unknown entries do not use chapter identity as a duplicate key`() {
        val unknown = chapter(id = -1, chapterNumber = -1.0, scanlator = "Team A")
        val items = listOf<ChapterEntry?>(null, ChapterEntry(unknown, "unknown"), null)

        items.removeDuplicateChapters { it?.chapter ?: unknown } shouldBe items
    }

    @Test
    fun `download ahead truncation retains other unknown chapters`() {
        val next = chapter(id = 1, chapterNumber = -1.0, scanlator = "Team A")
        val chapters = listOf(
            next,
            chapter(id = 2, chapterNumber = -1.0, scanlator = "Team A"),
            chapter(id = 3, chapterNumber = -1.0, scanlator = "Team B"),
            chapter(id = 4, chapterNumber = -2.0, scanlator = "Team A"),
        )

        chapters.removeDuplicates(next).take(3) shouldBe chapters.take(3)
    }

    @Test
    fun `zero remains a recognized duplicate number`() {
        val preferred = chapter(id = 2, chapterNumber = 0.0, scanlator = "Team B")
        val unknown = chapter(id = 3, chapterNumber = -1.0, scanlator = "Team B")
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 0.0, scanlator = "Team A"),
            preferred,
            unknown,
        )

        chapters.removeDuplicates(preferred) shouldBe listOf(preferred, unknown)
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
