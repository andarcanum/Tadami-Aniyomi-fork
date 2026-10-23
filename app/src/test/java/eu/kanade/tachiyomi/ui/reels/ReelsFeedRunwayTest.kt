package eu.kanade.tachiyomi.ui.reels

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReelsFeedRunwayTest {

    private var now = 0L

    private fun runway() = ReelsFeedRunway { now }

    @Test
    fun `idle user keeps the floor and loads at six pages remaining`() {
        val runway = runway()

        val sevenLeft = runway.evaluate(page = 12, total = 20, isLoading = false, canLoadMore = true)
        assertFalse(sevenLeft.shouldLoad)
        assertEquals(7, sevenLeft.pagesRemaining)

        val sixLeft = runway.evaluate(page = 13, total = 20, isLoading = false, canLoadMore = true)
        assertTrue(sixLeft.shouldLoad)
        assertEquals(6, sixLeft.pagesRemaining)
    }

    @Test
    fun `ten page fling opens the runway early`() {
        val runway = runway()
        runway.onPage(0)
        now += 1_000
        runway.onPage(10)

        val decision = runway.evaluate(page = 10, total = 40, isLoading = false, canLoadMore = true)

        assertTrue(decision.shouldLoad)
        assertEquals(29, decision.pagesRemaining)
        assertTrue(decision.pagesPerSecond >= 9.0)
    }

    @Test
    fun `long pause resets the measured velocity`() {
        val runway = runway()
        runway.onPage(0)
        now += 1_000
        runway.onPage(10)
        now += 2_000

        val decision = runway.evaluate(page = 10, total = 30, isLoading = false, canLoadMore = true)

        assertFalse(decision.shouldLoad)
        assertEquals(19, decision.pagesRemaining)
        assertEquals(0.0, decision.pagesPerSecond)
    }

    @Test
    fun `backwards movement resets the measured velocity`() {
        val runway = runway()
        runway.onPage(0)
        now += 1_000
        runway.onPage(10)
        runway.onPage(8)

        val decision = runway.evaluate(page = 8, total = 30, isLoading = false, canLoadMore = true)

        assertEquals(0.0, decision.pagesPerSecond)
        assertFalse(decision.shouldLoad)
    }

    @Test
    fun `cold load raises the budget and is not erased by one warm hit`() {
        val runway = runway()
        runway.onLoadStarted()
        now += 6_000
        runway.onLoadFinished()

        runway.onLoadStarted()
        now += 100
        runway.onLoadFinished()

        runway.onPage(0)
        now += 1_000
        runway.onPage(10)

        // remaining = 60 at 10 pages/s -> 6 s; a plain average would have
        // collapsed the 6 s cold load to ~3 s, the decayed peak keeps it.
        val decision = runway.evaluate(page = 10, total = 71, isLoading = false, canLoadMore = true)

        assertTrue(decision.shouldLoad)
        assertEquals(60, decision.pagesRemaining)
    }

    @Test
    fun `extreme fling is capped at sixty four pages`() {
        val runway = runway()
        runway.onPage(0)
        now += 1_000
        runway.onPage(30)

        val beyondCap = runway.evaluate(page = 134, total = 200, isLoading = false, canLoadMore = true)
        assertFalse(beyondCap.shouldLoad)
        assertEquals(65, beyondCap.pagesRemaining)

        val atCap = runway.evaluate(page = 135, total = 200, isLoading = false, canLoadMore = true)
        assertTrue(atCap.shouldLoad)
        assertEquals(64, atCap.pagesRemaining)
    }

    @Test
    fun `loading or terminal feed never triggers`() {
        val runway = runway()
        runway.onPage(0)
        now += 100
        runway.onPage(20)

        assertFalse(runway.evaluate(page = 20, total = 21, isLoading = true, canLoadMore = true).shouldLoad)
        assertFalse(runway.evaluate(page = 20, total = 21, isLoading = false, canLoadMore = false).shouldLoad)
    }

    @Test
    fun `reset clears position and velocity`() {
        val runway = runway()
        runway.onPage(0)
        now += 1_000
        runway.onPage(10)

        runway.reset()
        now += 100
        runway.onPage(0)

        val decision = runway.evaluate(page = 0, total = 100, isLoading = false, canLoadMore = true)
        assertEquals(0.0, decision.pagesPerSecond)
        assertFalse(decision.shouldLoad)
    }
}
