package eu.kanade.tachiyomi.ui.entries

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NewItemIdsTest {

    @Test
    fun `keeps existing ids and adds newly added ids`() {
        mergeNewItemIds(
            existingNewItemIds = setOf(1L, 2L),
            addedItemIds = listOf(3L, 4L),
        ) shouldBe setOf(1L, 2L, 3L, 4L)
    }

    @Test
    fun `removes cleared ids from the highlight set`() {
        mergeNewItemIds(
            existingNewItemIds = setOf(1L, 2L, 3L),
            addedItemIds = listOf(4L),
            clearedItemIds = listOf(2L, 3L),
        ) shouldBe setOf(1L, 4L)
    }
}
