package eu.kanade.presentation.entries

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EntryAutoJumpBehaviorTest {

    @Test
    fun `disabled auto jump never returns a target`() {
        resolveEntryAutoJumpTargetIndex(
            enabled = false,
            targetIndex = 18,
            restoredScrollIndex = 42,
        ) shouldBe null
    }

    @Test
    fun `enabled auto jump returns target index`() {
        resolveEntryAutoJumpTargetIndex(
            enabled = true,
            targetIndex = 18,
            restoredScrollIndex = 0,
        ) shouldBe 18
    }

    @Test
    fun `enabled auto jump ignores restored scroll index`() {
        resolveEntryAutoJumpTargetIndex(
            enabled = true,
            targetIndex = 18,
            restoredScrollIndex = 42,
        ) shouldBe 18
    }

    @Test
    fun `non positive target index does not trigger auto jump`() {
        resolveEntryAutoJumpTargetIndex(
            enabled = true,
            targetIndex = 0,
            restoredScrollIndex = 42,
        ) shouldBe null
    }
}
