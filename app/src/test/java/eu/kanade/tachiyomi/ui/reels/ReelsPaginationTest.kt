package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsPaginationTest {

    @Test
    fun `page-int pages keep the cursor null and stay loadable`() {
        val step = ReelsPagination().advance(hasNextPage = true, nextCursor = null)

        step.cursorLost shouldBe false
        step.pagination shouldBe ReelsPagination(
            nextPage = 2,
            nextCursor = null,
            cursorMode = false,
            canLoadMore = true,
        )
    }

    @Test
    fun `the first token locks cursor mode for the whole generation`() {
        val locked = ReelsPagination().advance(hasNextPage = true, nextCursor = "t1").pagination
        val next = locked.advance(hasNextPage = true, nextCursor = "t2").pagination

        locked.cursorMode shouldBe true
        locked.nextCursor shouldBe "t1"
        next.cursorMode shouldBe true
        next.nextCursor shouldBe "t2"
        next.canLoadMore shouldBe true
    }

    @Test
    fun `a null cursor in a locked stream is a terminal violation`() {
        val locked = ReelsPagination(cursorMode = true, nextCursor = "t1", nextPage = 2)
        val step = locked.advance(hasNextPage = true, nextCursor = null)

        step.cursorLost shouldBe true
        step.pagination.cursorMode shouldBe true
        step.pagination.canLoadMore shouldBe false
        step.pagination.nextPage shouldBe 3
    }

    @Test
    fun `a terminal cursor page ends pagination without a violation`() {
        val step = ReelsPagination(cursorMode = true, nextCursor = "last", nextPage = 4)
            .advance(hasNextPage = false, nextCursor = null)

        step.cursorLost shouldBe false
        step.pagination.cursorMode shouldBe true
        step.pagination.canLoadMore shouldBe false
    }

    @Test
    fun `a page-int terminal page also ends pagination`() {
        val step = ReelsPagination(nextPage = 3).advance(hasNextPage = false, nextCursor = null)

        step.cursorLost shouldBe false
        step.pagination.cursorMode shouldBe false
        step.pagination.canLoadMore shouldBe false
    }
}
