package eu.kanade.presentation.library.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryContinueVisibilityTest {

    @Test
    fun `continue action remains available with positive remaining count`() {
        shouldShowContinueViewingAction(
            hasContinueAction = true,
            remainingCount = 5L,
        ) shouldBe true
    }

    @Test
    fun `continue action is hidden when remaining count is zero`() {
        shouldShowContinueViewingAction(
            hasContinueAction = true,
            remainingCount = 0L,
        ) shouldBe false
    }

    @Test
    fun `continue action is hidden when callback is absent`() {
        shouldShowContinueViewingAction(
            hasContinueAction = false,
            remainingCount = 5L,
        ) shouldBe false
    }
}
