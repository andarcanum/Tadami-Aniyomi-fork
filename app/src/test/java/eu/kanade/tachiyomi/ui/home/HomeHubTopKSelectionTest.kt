package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubTopKSelectionTest {

    private data class Item(val id: Int, val timestamp: Long)

    @Test
    fun `returns top K items in descending order preserving tie stability`() {
        val items = listOf(
            Item(1, 100L),
            Item(2, 500L),
            Item(3, 300L),
            Item(4, 500L),
            Item(5, 200L),
            Item(6, 700L),
            Item(7, 300L),
        )

        val actual = takeTopKDescendingByLong(items, limit = 4) { it.timestamp }
        val expected = items.sortedByDescending { it.timestamp }.take(4)

        actual shouldBe expected
    }

    @Test
    fun `handles empty list, zero limit, and limit exceeding size`() {
        val items = listOf(Item(1, 10L), Item(2, 30L), Item(3, 20L))

        takeTopKDescendingByLong(emptyList<Item>(), limit = 10) { it.timestamp } shouldBe emptyList()
        takeTopKDescendingByLong(items, limit = 0) { it.timestamp } shouldBe emptyList()
        takeTopKDescendingByLong(items, limit = 10) { it.timestamp } shouldBe listOf(
            Item(2, 30L),
            Item(3, 20L),
            Item(1, 10L),
        )
    }
}
