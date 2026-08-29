package tachiyomi.source.local.entries.novel

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ArchiveChapterListBuilderTest {

    @Test
    fun `empty entry list yields no chapters`() {
        buildArchiveChapters(
            novelUrl = "Book",
            archiveRelativePath = "book.zip",
            entryNames = emptyList(),
            numberOffset = 0f,
        ).shouldBeEmpty()
    }

    @Test
    fun `unsupported entries and directories are filtered`() {
        val names = listOf("cover.jpg", "ch1/", "notes.txt", "book.fb2")
        selectArchiveChapterEntries(names) shouldBe listOf("book.fb2", "notes.txt")
    }

    @Test
    fun `entries sort naturally case-insensitive`() {
        val names = listOf("Ch 10.txt", "ch 2.txt", "ch 1.txt")
        selectArchiveChapterEntries(names) shouldBe listOf("ch 1.txt", "ch 2.txt", "Ch 10.txt")
    }

    @Test
    fun `chapters carry fragment urls and cleaned names`() {
        val chapters = buildArchiveChapters(
            novelUrl = "My Book",
            archiveRelativePath = "book.zip",
            entryNames = listOf("text/chapter_01.md"),
            numberOffset = 0f,
        )

        chapters.size shouldBe 1
        chapters[0].url shouldBe "My Book/book.zip#text/chapter_01.md"
        chapters[0].name shouldBe "text chapter 01"
        chapters[0].chapter_number shouldBe 1f
    }

    @Test
    fun `numbering continues from offset`() {
        val chapters = buildArchiveChapters(
            novelUrl = "B",
            archiveRelativePath = "b.zip",
            entryNames = listOf("a.txt", "b.txt"),
            numberOffset = 7f,
        )

        chapters.map { it.chapter_number } shouldBe listOf(8f, 9f)
    }

    @Test
    fun `all text extensions supported`() {
        val names = listOf("a.txt", "b.text", "c.md", "d.markdown", "e.html", "f.htm", "g.xhtml", "h.fb2")
        selectArchiveChapterEntries(names).size shouldBe 8
    }
}
