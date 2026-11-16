package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.i18n.aniyomi.AYMR

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

    @Test
    fun `skeleton style matches configured hero presentation mode`() {
        resolveDiscoveryHeroSkeletonStyle(
            preferredMode = HomeHeroMode.Stage,
            discoveryEnabled = true,
            isFiltering = false,
            keepDiscoveryHeroSlot = true,
        ) shouldBe DiscoveryHeroSkeletonStyle.STAGE

        resolveDiscoveryHeroSkeletonStyle(
            preferredMode = HomeHeroMode.Collage,
            discoveryEnabled = true,
            isFiltering = false,
            keepDiscoveryHeroSlot = true,
        ) shouldBe DiscoveryHeroSkeletonStyle.COLLAGE

        resolveDiscoveryHeroSkeletonStyle(
            preferredMode = HomeHeroMode.Hybrid,
            discoveryEnabled = true,
            isFiltering = false,
            keepDiscoveryHeroSlot = true,
        ) shouldBe DiscoveryHeroSkeletonStyle.HYBRID

        resolveDiscoveryHeroSkeletonStyle(
            preferredMode = HomeHeroMode.Auto,
            discoveryEnabled = true,
            isFiltering = false,
            keepDiscoveryHeroSlot = true,
        ) shouldBe DiscoveryHeroSkeletonStyle.STAGE

        resolveDiscoveryHeroSkeletonStyle(
            preferredMode = HomeHeroMode.Continue,
            discoveryEnabled = true,
            isFiltering = false,
            keepDiscoveryHeroSlot = false,
        ) shouldBe DiscoveryHeroSkeletonStyle.TEASER_ROW

        resolveDiscoveryHeroSkeletonStyle(
            preferredMode = HomeHeroMode.Stage,
            discoveryEnabled = true,
            isFiltering = true,
            keepDiscoveryHeroSlot = true,
        ) shouldBe DiscoveryHeroSkeletonStyle.TEASER_ROW
    }

    @Test
    fun `initial discovery preferences seed cold-start state without frame-zero flicker`() {
        val prefs = DiscoveryPreferences(PersistingPreferenceStore())
        prefs.discoveryEnabled().set(true)
        prefs.lastGenerationAt(DiscoveryMediaType.ANIME).set(123_456L)

        val seeded = HomeHubUiState(
            userName = "Test",
            userAvatar = "",
            greeting = AYMR.strings.aurora_welcome_back,
            greetingReady = true,
            isLoading = false,
            showWelcome = false,
        ).withInitialDiscoveryPreferences(prefs, DiscoveryMediaType.ANIME)

        seeded.discoveryEnabled shouldBe true
        seeded.isDiscoveryLoading shouldBe true
        seeded.discoveryLastUpdated shouldBe 123_456L

        prefs.discoveryEnabled().set(false)
        val disabled = seeded.withInitialDiscoveryPreferences(prefs, DiscoveryMediaType.ANIME)
        disabled.discoveryEnabled shouldBe false
        disabled.isDiscoveryLoading shouldBe false
    }
}
