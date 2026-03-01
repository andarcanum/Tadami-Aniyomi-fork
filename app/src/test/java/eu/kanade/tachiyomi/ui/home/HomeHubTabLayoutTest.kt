package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubTabLayoutTest {

    @Test
    fun `home hub rim light stops match hero border style`() {
        homeHubRimLightAlphaStops() shouldBe listOf(
            0.00f to 0.15f,
            0.28f to 0.05f,
            0.62f to 0.00f,
            1.00f to 0.00f,
        )
    }

    @Test
    fun `wrapped homehub section layout is enabled only on tablet classes`() {
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.Phone) shouldBe false
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.TabletCompact) shouldBe true
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.TabletExpanded) shouldBe true
    }

    @Test
    fun `home header editor visible elements omit hidden greeting and streak`() {
        homeHeaderLayoutEditorVisibleElements(
            showGreeting = false,
            showStreak = false,
        ) shouldBe listOf(
            HomeHeaderLayoutElement.Nickname,
            HomeHeaderLayoutElement.Avatar,
        )
    }

    @Test
    fun `reserve hero slot while loading prevents top item jump`() {
        shouldReserveHomeHubHeroSlot(
            hasHero = false,
            isLoading = true,
            showWelcome = false,
            isFiltering = false,
        ) shouldBe true
    }

    @Test
    fun `do not reserve hero slot once loading is finished`() {
        shouldReserveHomeHubHeroSlot(
            hasHero = false,
            isLoading = false,
            showWelcome = false,
            isFiltering = false,
        ) shouldBe false
    }
}
