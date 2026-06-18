package eu.kanade.presentation.library.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryDerivedStateTest {

    @Test
    fun `contains at least matches short circuits once required count is reached`() {
        var visited = 0
        val hasTwoPinned = listOf(true, false, true, true).containsAtLeastMatches(requiredCount = 2) {
            visited += 1
            it
        }

        hasTwoPinned shouldBe true
        visited shouldBe 3
    }

    @Test
    fun `contains at least matches returns false when there are not enough matches`() {
        listOf(false, true, false).containsAtLeastMatches(requiredCount = 2) { it } shouldBe false
    }

    @Test
    fun `ids to hash set keeps unique ids without intermediate list allocation`() {
        data class Item(val id: Long)

        listOf(Item(1), Item(2), Item(1)).idsToHashSet { it.id } shouldBe setOf(1L, 2L)
    }
}
