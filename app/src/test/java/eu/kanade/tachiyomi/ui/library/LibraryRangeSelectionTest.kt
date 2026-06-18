package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class LibraryRangeSelectionTest {

    @Test
    fun `range selection uses visible grouped category instead of item category`() {
        val item1 = item(id = 1, category = 100)
        val item2 = item(id = 2, category = 200)
        val item3 = item(id = 3, category = 300)

        val additions = resolveLibraryRangeSelectionAdditions(
            selectedItems = listOf(item1),
            targetItem = item3,
            visibleGroups = listOf(listOf(item1, item2, item3)),
            itemId = { it.id },
        )

        additions.map { it.id }.shouldContainExactly(2, 3)
    }

    @Test
    fun `range selection anchors to last selected item in same visible group`() {
        val group1Item = item(id = 1, category = 10)
        val group2Item1 = item(id = 2, category = 10)
        val group2Item2 = item(id = 3, category = 10)
        val group2Item3 = item(id = 4, category = 10)

        val additions = resolveLibraryRangeSelectionAdditions(
            selectedItems = listOf(group1Item, group2Item1),
            targetItem = group2Item3,
            visibleGroups = listOf(
                listOf(group1Item),
                listOf(group2Item1, group2Item2, group2Item3),
            ),
            itemId = { it.id },
        )

        additions.map { it.id }.shouldContainExactly(3, 4)
    }

    @Test
    fun `range selection falls back to adding target when no visible group anchor exists`() {
        val selected = item(id = 1, category = 10)
        val target = item(id = 2, category = 20)

        val additions = resolveLibraryRangeSelectionAdditions(
            selectedItems = listOf(selected),
            targetItem = target,
            visibleGroups = listOf(listOf(target)),
            itemId = { it.id },
        )

        additions.map { it.id }.shouldContainExactly(2)
    }

    private fun item(id: Long, category: Long): RangeItem {
        return RangeItem(id = id, category = category)
    }

    private data class RangeItem(
        val id: Long,
        val category: Long,
    )
}
