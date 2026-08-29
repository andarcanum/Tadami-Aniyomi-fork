package eu.kanade.tachiyomi.extension.manga.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LegacyInstallCancelRegistryTest {

    @Test
    fun `cancel round-trips and clears`() {
        LegacyInstallCancelRegistry.markCancelled(42L)
        LegacyInstallCancelRegistry.isCancelled(42L) shouldBe true

        LegacyInstallCancelRegistry.clear(42L)
        LegacyInstallCancelRegistry.isCancelled(42L) shouldBe false
    }

    @Test
    fun `negative download ids are ignored`() {
        LegacyInstallCancelRegistry.markCancelled(-1L)
        LegacyInstallCancelRegistry.isCancelled(-1L) shouldBe false
    }
}
