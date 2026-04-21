package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryImmersiveChromePolicyTest {

    @Test
    fun `scrolling down hides chrome when immersive mode is enabled`() {
        val next = resolveLibraryImmersiveChromeState(
            currentState = LibraryImmersiveChromeState(),
            scrollDeltaPx = 64f,
            enabled = true,
            forceVisible = false,
            hideThresholdPx = 48f,
        )

        next.isVisible shouldBe false
        next.accumulatedScrollPx shouldBe 0f
    }

    @Test
    fun `scrolling up shows chrome when immersive mode is enabled`() {
        val next = resolveLibraryImmersiveChromeState(
            currentState = LibraryImmersiveChromeState(
                isVisible = false,
                accumulatedScrollPx = 0f,
            ),
            scrollDeltaPx = -64f,
            enabled = true,
            forceVisible = false,
            hideThresholdPx = 48f,
        )

        next.isVisible shouldBe true
        next.accumulatedScrollPx shouldBe 0f
    }

    @Test
    fun `force visible keeps chrome visible`() {
        val next = resolveLibraryImmersiveChromeState(
            currentState = LibraryImmersiveChromeState(
                isVisible = false,
                accumulatedScrollPx = 24f,
            ),
            scrollDeltaPx = 0f,
            enabled = true,
            forceVisible = true,
            hideThresholdPx = 48f,
        )

        next.isVisible shouldBe true
        next.accumulatedScrollPx shouldBe 0f
    }

    @Test
    fun `disabled keeps chrome visible`() {
        val next = resolveLibraryImmersiveChromeState(
            currentState = LibraryImmersiveChromeState(
                isVisible = false,
                accumulatedScrollPx = 24f,
            ),
            scrollDeltaPx = 64f,
            enabled = false,
            forceVisible = false,
            hideThresholdPx = 48f,
        )

        next.isVisible shouldBe true
        next.accumulatedScrollPx shouldBe 0f
    }
}
