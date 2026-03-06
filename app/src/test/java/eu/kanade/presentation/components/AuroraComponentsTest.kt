package eu.kanade.presentation.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraComponentsTest {

    @Test
    fun `aurora animation runs only when enabled and resumed`() {
        shouldAnimateAuroraBackground(
            userEnabled = true,
            isLifecycleResumed = true,
        ) shouldBe true
    }

    @Test
    fun `aurora animation pauses when disabled or backgrounded`() {
        shouldAnimateAuroraBackground(
            userEnabled = false,
            isLifecycleResumed = true,
        ) shouldBe false

        shouldAnimateAuroraBackground(
            userEnabled = true,
            isLifecycleResumed = false,
        ) shouldBe false
    }
}
