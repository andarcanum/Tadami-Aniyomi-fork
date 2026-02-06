package tachiyomi.domain.items.novelchapter.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class ShouldUpdateDbNovelChapterTest {

    @Test
    fun `returns false when fields match`() {
        val base = NovelChapter.create().copy(
            name = "Chapter 1",
            scanlator = "Team",
            dateUpload = 123L,
            chapterNumber = 1.0,
            sourceOrder = 2,
        )
        val updated = base.copy()

        val result = ShouldUpdateDbNovelChapter().await(base, updated)

        result shouldBe false
    }

    @Test
    fun `returns true when fields differ`() {
        val base = NovelChapter.create().copy(
            name = "Chapter 1",
            scanlator = "Team",
            dateUpload = 123L,
            chapterNumber = 1.0,
            sourceOrder = 2,
        )
        val updated = base.copy(name = "Chapter 2")

        val result = ShouldUpdateDbNovelChapter().await(base, updated)

        result shouldBe true
    }
}
