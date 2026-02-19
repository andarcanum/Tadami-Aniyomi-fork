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
}
