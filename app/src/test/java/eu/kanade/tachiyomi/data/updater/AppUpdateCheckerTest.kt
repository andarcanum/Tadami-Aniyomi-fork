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

    @Test
    fun `does not show updated changelog on first install and stores current version`() {
        resolveUpdatedChangelogPrompt(
            currentVersionCode = 120,
            lastSeenVersionCode = 0,
            pendingPreviousVersionCode = 0,
            isDebug = false,
        ) shouldBe UpdatedChangelogPromptDecision(
            shouldPrompt = false,
            nextSeenVersionCode = 120,
            nextPendingPreviousVersionCode = 0,
        )
    }

    @Test
    fun `shows updated changelog when version code increases`() {
        resolveUpdatedChangelogPrompt(
            currentVersionCode = 121,
            lastSeenVersionCode = 120,
            pendingPreviousVersionCode = 0,
            isDebug = false,
        ) shouldBe UpdatedChangelogPromptDecision(
            shouldPrompt = true,
            nextSeenVersionCode = 121,
            nextPendingPreviousVersionCode = 0,
        )
    }

    @Test
    fun `does not show updated changelog when version code is unchanged`() {
        resolveUpdatedChangelogPrompt(
            currentVersionCode = 121,
            lastSeenVersionCode = 121,
            pendingPreviousVersionCode = 0,
            isDebug = false,
        ) shouldBe UpdatedChangelogPromptDecision(
            shouldPrompt = false,
            nextSeenVersionCode = 121,
            nextPendingPreviousVersionCode = 0,
        )
    }

    @Test
    fun `does not show updated changelog for debug builds but still stores current version`() {
        resolveUpdatedChangelogPrompt(
            currentVersionCode = 121,
            lastSeenVersionCode = 120,
            pendingPreviousVersionCode = 0,
            isDebug = true,
        ) shouldBe UpdatedChangelogPromptDecision(
            shouldPrompt = false,
            nextSeenVersionCode = 121,
            nextPendingPreviousVersionCode = 0,
        )
    }

    @Test
    fun `shows updated changelog for existing installs when dedicated seen version is not seeded yet`() {
        resolveUpdatedChangelogPrompt(
            currentVersionCode = 121,
            lastSeenVersionCode = 0,
            pendingPreviousVersionCode = 120,
            isDebug = false,
        ) shouldBe UpdatedChangelogPromptDecision(
            shouldPrompt = true,
            nextSeenVersionCode = 121,
            nextPendingPreviousVersionCode = 0,
        )
    }
}
