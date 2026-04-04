package eu.kanade.tachiyomi.ui.entries.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenMigrationAvailabilityTest {

    @Test
    fun `migration action is available for persisted manga entries`() {
        shouldExposeMangaMigrationAction(mangaId = 42L) shouldBe true
    }

    @Test
    fun `migration action is hidden only when manga id is missing`() {
        shouldExposeMangaMigrationAction(mangaId = 0L) shouldBe false
    }
}
