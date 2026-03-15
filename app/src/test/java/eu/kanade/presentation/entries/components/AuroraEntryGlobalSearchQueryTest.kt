package eu.kanade.presentation.entries.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraEntryGlobalSearchQueryTest {

    @Test
    fun `global search query is trimmed when title has extra whitespace`() {
        normalizeAuroraGlobalSearchQuery("  Re Zero  ") shouldBe "Re Zero"
    }

    @Test
    fun `global search query becomes null when title is blank`() {
        normalizeAuroraGlobalSearchQuery("   ") shouldBe null
    }
}
