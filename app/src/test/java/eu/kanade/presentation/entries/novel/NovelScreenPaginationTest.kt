package eu.kanade.presentation.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelScreenPaginationTest {

    @Test
    fun `initialVisibleChapterCount returns total when total is smaller than page size`() {
        initialVisibleChapterCount(totalCount = 12, pageSize = 50) shouldBe 12
    }

    @Test
    fun `initialVisibleChapterCount is capped by page size`() {
        initialVisibleChapterCount(totalCount = 120, pageSize = 50) shouldBe 50
    }

    @Test
    fun `nextVisibleChapterCount increases by step when there are more chapters`() {
        nextVisibleChapterCount(currentCount = 50, totalCount = 230, step = 50) shouldBe 100
    }

    @Test
    fun `nextVisibleChapterCount is capped by total chapters`() {
        nextVisibleChapterCount(currentCount = 200, totalCount = 230, step = 50) shouldBe 230
    }

    @Test
    fun `fast scroll expands visible chapters to all locally loaded chapters`() {
        resolveNovelFastScrollVisibleChapterCount(
            currentVisibleCount = 120,
            loadedChapterCount = 360,
        ) shouldBe 360
    }

    @Test
    fun `fast scroll expansion keeps current visible count when all loaded chapters are already shown`() {
        resolveNovelFastScrollVisibleChapterCount(
            currentVisibleCount = 360,
            loadedChapterCount = 360,
        ) shouldBe 360
    }
}
