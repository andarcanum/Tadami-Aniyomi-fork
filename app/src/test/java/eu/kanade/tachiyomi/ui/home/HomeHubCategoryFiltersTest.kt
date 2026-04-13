package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubCategoryFiltersTest {

    @Test
    fun `filterHomeHubEntries returns all when no hidden categories`() {
        val entryIds = listOf(1L, 2L, 3L)
        val entryCategoryIds = mapOf(
            1L to listOf(1L),
            2L to listOf(2L),
            3L to listOf(1L, 2L),
        )
        val hiddenCategoryIds = emptySet<Long>()

        val result = filterHomeHubEntries(entryIds, entryCategoryIds, hiddenCategoryIds)

        result shouldBe entryIds
    }

    @Test
    fun `filterHomeHubEntries removes entries whose category is hidden`() {
        val entryIds = listOf(1L, 2L, 3L, 4L)
        val entryCategoryIds = mapOf(
            1L to listOf(1L), // category 1 visible
            2L to listOf(2L), // category 2 HIDDEN
            3L to listOf(1L, 2L), // has hidden category 2
            4L to listOf(3L), // category 3 visible
        )
        val hiddenCategoryIds = setOf(2L)

        val result = filterHomeHubEntries(entryIds, entryCategoryIds, hiddenCategoryIds)

        result shouldBe listOf(1L, 4L)
    }

    @Test
    fun `filterHomeHubEntries keeps entries with system category`() {
        val entryIds = listOf(1L, 2L)
        val entryCategoryIds = mapOf(
            1L to listOf(0L), // system category
            2L to listOf(1L), // visible category
        )
        val hiddenCategoryIds = setOf(2L)

        val result = filterHomeHubEntries(entryIds, entryCategoryIds, hiddenCategoryIds)

        result shouldBe listOf(1L, 2L)
    }

    @Test
    fun `filterHomeHubEntries removes entries that ONLY have hidden categories`() {
        val entryIds = listOf(1L, 2L, 3L)
        val entryCategoryIds = mapOf(
            1L to listOf(1L), // HIDDEN
            2L to listOf(1L, 2L), // has hidden + visible -> hidden wins
            3L to listOf(2L), // visible
        )
        val hiddenCategoryIds = setOf(1L)

        val result = filterHomeHubEntries(entryIds, entryCategoryIds, hiddenCategoryIds)

        result shouldBe listOf(3L)
    }

    @Test
    fun `filterHomeHubEntries returns empty when all hidden`() {
        val entryIds = listOf(1L, 2L)
        val entryCategoryIds = mapOf(
            1L to listOf(1L),
            2L to listOf(2L),
        )
        val hiddenCategoryIds = setOf(1L, 2L)

        val result = filterHomeHubEntries(entryIds, entryCategoryIds, hiddenCategoryIds)

        result shouldBe emptyList()
    }

    @Test
    fun `filterHomeHubEntriesBy filters generic list correctly`() {
        data class Item(val id: Long, val name: String)

        val items = listOf(
            Item(1L, "A"),
            Item(2L, "B"),
            Item(3L, "C"),
        )
        val entryCategoryIds = mapOf(
            1L to listOf(1L),
            2L to listOf(2L),
            3L to listOf(3L),
        )
        val hiddenCategoryIds = setOf(2L)

        val result = filterHomeHubEntriesBy(items, { it.id }, entryCategoryIds, hiddenCategoryIds)

        result.map { it.name } shouldBe listOf("A", "C")
    }
}
