package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class LibraryUpdateFailureNotificationFormatterTest {

    @Test
    fun `build shows first failure and more count`() {
        val failures = listOf(
            LibraryUpdateFailure(
                title = "First title",
                sourceName = "Source A",
                reason = "Network error",
            ),
            LibraryUpdateFailure(
                title = "Second title",
                sourceName = "Source B",
                reason = "Deleted",
            ),
            LibraryUpdateFailure(
                title = "Third title",
                sourceName = "Source C",
                reason = "Unknown error",
            ),
            LibraryUpdateFailure(
                title = "Fourth title",
                sourceName = "Source D",
                reason = "Timeout",
            ),
        )

        val text = LibraryUpdateFailureNotificationFormatter.buildText(
            failures = failures,
            hideContent = false,
            actionShowErrorsText = "Tap to see details",
            unknownErrorText = "Unknown error",
            moreText = { count -> "+$count more" },
        )

        text.contentText.toString() shouldContain "First title"
        text.bigText.toString() shouldContain "Second title"
        text.bigText.toString() shouldContain "+1 more"
    }

    @Test
    fun `build hides content when requested`() {
        val text = LibraryUpdateFailureNotificationFormatter.buildText(
            failures = listOf(
                LibraryUpdateFailure(
                    title = "Secret title",
                    sourceName = "Source A",
                    reason = "Network error",
                ),
            ),
            hideContent = true,
            actionShowErrorsText = "Tap to see details",
            unknownErrorText = "Unknown error",
            moreText = { count -> "+$count more" },
        )

        text.contentText.toString() shouldBe "Tap to see details"
        text.bigText shouldBe null
    }
}
