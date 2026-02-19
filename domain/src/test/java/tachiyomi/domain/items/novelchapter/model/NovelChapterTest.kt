package tachiyomi.domain.items.novelchapter.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class NovelChapterTest {

    @Test
    fun `create returns default novel chapter`() {
        val chapter = NovelChapter.create()

        chapter.id shouldBe -1L
        chapter.novelId shouldBe -1L
        chapter.read shouldBe false
        chapter.url shouldBe ""
        chapter.name shouldBe ""
    }

    @Test
    fun `copyFrom copies expected fields`() {
        val chapter = NovelChapter.create().copy(
            name = "Old",
            url = "old",
            dateUpload = 1,
            chapterNumber = 3.0,
            scanlator = "old",
        )
        val other = NovelChapter.create().copy(
            name = "New",
            url = "new",
            dateUpload = 5,
            chapterNumber = 7.0,
            scanlator = "new",
        )

        val updated = chapter.copyFrom(other)

        updated.name shouldBe "New"
        updated.url shouldBe "new"
        updated.dateUpload shouldBe 5
        updated.chapterNumber shouldBe 7.0
        updated.scanlator shouldBe "new"
    }
}
