package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelReadDeleteTargetsTest {

    private fun chapter(id: Long, bookmark: Boolean = false, read: Boolean = true) =
        NovelChapter.create().copy(id = id, novelId = 1L, url = "/c$id", bookmark = bookmark, read = read)

    @Test
    fun `slots zero resolves the completed chapter itself`() {
        val ordered = listOf(chapter(1), chapter(2), chapter(3))
        resolveSlotsDeleteTargetId(ordered, completedChapterId = 3, slots = 0) shouldBe 3L
    }

    @Test
    fun `slots N resolves the chapter N behind in reading order`() {
        val ordered = listOf(chapter(1), chapter(2), chapter(3))
        resolveSlotsDeleteTargetId(ordered, completedChapterId = 3, slots = 1) shouldBe 2L
        resolveSlotsDeleteTargetId(ordered, completedChapterId = 3, slots = 2) shouldBe 1L
    }

    @Test
    fun `negative slots or unknown chapter or out-of-range resolve null`() {
        val ordered = listOf(chapter(1), chapter(2))
        resolveSlotsDeleteTargetId(ordered, 2, -1) shouldBe null
        resolveSlotsDeleteTargetId(ordered, 99, 0) shouldBe null
        resolveSlotsDeleteTargetId(ordered, 1, 1) shouldBe null
    }
}
