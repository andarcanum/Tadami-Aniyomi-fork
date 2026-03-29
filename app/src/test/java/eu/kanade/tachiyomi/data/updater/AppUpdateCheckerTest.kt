package eu.kanade.tachiyomi.data.updater

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppUpdateCheckerTest {

    @Test
    fun `does not prompt when available version matches ignored version`() {
        resolveAppUpdatePrompt(
            availableVersion = "v0.34",
            ignoredVersion = "v0.34",
        ) shouldBe AppUpdatePromptDecision(
            shouldPrompt = false,
            nextIgnoredVersion = "v0.34",
        )
    }

    @Test
    fun `prompts and clears stale ignored version when newer release arrives`() {
        resolveAppUpdatePrompt(
            availableVersion = "v0.35",
            ignoredVersion = "v0.34",
        ) shouldBe AppUpdatePromptDecision(
            shouldPrompt = true,
            nextIgnoredVersion = "",
        )
    }
}
