package eu.kanade.domain.items.novelchapter.model

import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelChapterMappingTest {

    @Test
    fun `toSNovelChapter maps fields`() {
        val chapter = NovelChapter.create().copy(
            url = "/chapter/1",
            name = "Chapter 1",
            dateUpload = 1234L,
            chapterNumber = 1.0,
            scanlator = "Team",
        )

        val sChapter = chapter.toSNovelChapter()

        sChapter.url shouldBe "/chapter/1"
        sChapter.name shouldBe "Chapter 1"
        sChapter.date_upload shouldBe 1234L
        sChapter.chapter_number shouldBe 1.0f
        sChapter.scanlator shouldBe "Team"
    }

    @Test
    fun `copyFromSNovelChapter updates fields`() {
        val base = NovelChapter.create().copy(
            url = "/old",
            name = "Old",
            dateUpload = 1L,
            chapterNumber = 0.0,
            scanlator = "Old",
        )
        val sChapter = SNovelChapter.create().apply {
            url = "/new"
            name = "New"
            date_upload = 999L
            chapter_number = 2.5f
            scanlator = ""
        }

        val updated = base.copyFromSNovelChapter(sChapter)

        updated.url shouldBe "/new"
        updated.name shouldBe "New"
        updated.dateUpload shouldBe 999L
        updated.chapterNumber shouldBe 2.5
        updated.scanlator shouldBe null
    }
}
