package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelEntryActionAvailabilityTest {

    @Test
    fun `shows source settings only for configurable sources`() {
        resolveNovelEntryActionAvailability(
            isFavorite = false,
            isSourceConfigurable = true,
        ).showSourceSettings shouldBe true

        resolveNovelEntryActionAvailability(
            isFavorite = false,
            isSourceConfigurable = false,
        ).showSourceSettings shouldBe false
    }

    @Test
    fun `shows migrate action only for favorite novels`() {
        resolveNovelEntryActionAvailability(
            isFavorite = true,
            isSourceConfigurable = false,
        ).showMigrate shouldBe true

        resolveNovelEntryActionAvailability(
            isFavorite = false,
            isSourceConfigurable = false,
        ).showMigrate shouldBe false
    }
}
