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

class HomeHubCategoryDerivedStateTest {

    @Test
    fun `hiddenHomeHubCategoryIds returns only home hidden categories`() {
        val categories = listOf(
            category(id = 1L, hiddenFromHomeHub = false),
            category(id = 2L, hiddenFromHomeHub = true),
            category(id = 3L, hiddenFromHomeHub = true),
        )

        hiddenHomeHubCategoryIds(
            categories = categories,
            isHiddenFromHomeHub = { it.hiddenFromHomeHub },
            idSelector = { it.id },
        ) shouldBe setOf(2L, 3L)
    }

    @Test
    fun `homeHubCategoryIdsByEntryId groups categories in a single pass`() {
        data class Item(val entryId: Long, val categoryId: Long)

        val items = listOf(
            Item(entryId = 10L, categoryId = 1L),
            Item(entryId = 10L, categoryId = 2L),
            Item(entryId = 11L, categoryId = 3L),
        )

        homeHubCategoryIdsByEntryId(
            items = items,
            entryIdSelector = { it.entryId },
            categoryIdSelector = { it.categoryId },
        ) shouldBe mapOf(
            10L to listOf(1L, 2L),
            11L to listOf(3L),
        )
    }

    @Test
    fun `filterHomeHubEntriesByDistinct filters hidden categories and keeps first visible entry`() {
        data class Item(val entryId: Long, val label: String)

        val items = listOf(
            Item(entryId = 1L, label = "first visible"),
            Item(entryId = 1L, label = "duplicate visible"),
            Item(entryId = 2L, label = "hidden"),
            Item(entryId = 3L, label = "system"),
        )
        val entryCategoryIds = mapOf(
            1L to listOf(1L),
            2L to listOf(2L),
            3L to listOf(0L),
        )

        val result = filterHomeHubEntriesByDistinct(
            items = items,
            keySelector = { it.entryId },
            entryCategoryIds = entryCategoryIds,
            hiddenCategoryIds = setOf(2L),
        )

        result.map { it.label } shouldBe listOf("first visible", "system")
    }

    @Test
    fun `takeHomeHubHistoryExcluding skips excluded entry and stops at limit`() {
        data class Item(val entryId: Long, val label: String)

        val items = listOf(
            Item(entryId = 1L, label = "hero"),
            Item(entryId = 2L, label = "first"),
            Item(entryId = 3L, label = "second"),
            Item(entryId = 4L, label = "third"),
        )

        val result = takeHomeHubHistoryExcluding(
            items = items,
            limit = 2,
            excludedEntryId = 1L,
            entryIdSelector = { it.entryId },
        )

        result.map { it.label } shouldBe listOf("first", "second")
    }

    @Test
    fun `takeHomeHubHistoryExcluding keeps first items when no entry is excluded`() {
        data class Item(val entryId: Long, val label: String)

        val items = listOf(
            Item(entryId = 1L, label = "first"),
            Item(entryId = 2L, label = "second"),
            Item(entryId = 3L, label = "third"),
        )

        val result = takeHomeHubHistoryExcluding(
            items = items,
            limit = 2,
            excludedEntryId = null,
            entryIdSelector = { it.entryId },
        )

        result.map { it.label } shouldBe listOf("first", "second")
    }

    private fun category(
        id: Long,
        hiddenFromHomeHub: Boolean,
    ): tachiyomi.domain.category.model.Category {
        return tachiyomi.domain.category.model.Category(
            id = id,
            name = "Category $id",
            order = id,
            flags = 0L,
            hidden = false,
            hiddenFromHomeHub = hiddenFromHomeHub,
        )
    }
}
