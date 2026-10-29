package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeDiscoverySlotPolicyTest {
    @Test
    fun `cold auto keeps the continue hero`() {
        shouldKeepDiscoveryHeroSlot(
            preferredMode = HomeHeroMode.Auto,
            discoveryEnabled = true,
            isFiltering = false,
            hasPreviousDiscoveryContent = false,
        ) shouldBe false
    }

    @Test
    fun `auto keeps the slot after discovery produced content once`() {
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Auto, true, false, hasPreviousDiscoveryContent = true) shouldBe true
    }

    @Test
    fun `explicit discovery modes own the slot even on a cold start`() {
        for (mode in listOf(HomeHeroMode.Stage, HomeHeroMode.Collage, HomeHeroMode.Hybrid)) {
            shouldKeepDiscoveryHeroSlot(mode, true, false, hasPreviousDiscoveryContent = false) shouldBe true
        }
    }

    @Test
    fun `explicit continue disabled discovery and filtering preserve the previous policy`() {
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Continue, true, false, true) shouldBe false
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Stage, false, false, true) shouldBe false
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Stage, true, true, true) shouldBe false
    }

    @Test
    fun `slot survives a re-check that returns no cards`() {
        // cold Auto -> Continue
        var previousContent = false
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Auto, true, false, previousContent) shouldBe false
        // discovery appeared
        previousContent = true
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Auto, true, false, previousContent) shouldBe true
        // required genres changed, then the re-check returned nothing: the slot stays
        // because discovery already occupied it once.
        shouldKeepDiscoveryHeroSlot(HomeHeroMode.Auto, true, false, previousContent) shouldBe true
    }
}
