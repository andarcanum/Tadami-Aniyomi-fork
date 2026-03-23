package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubLightSurfaceSpecTest {

    @Test
    fun `light aurora recent poster surfaces keep stronger separation`() {
        resolveHomeHubRecentPosterSurfaceSpec(isDark = false) shouldBe HomeHubRecentPosterSurfaceSpec(
            containerAlpha = 0.12f,
            posterAlpha = 0.18f,
        )
    }

    @Test
    fun `light aurora hero button keeps brighter glass treatment`() {
        resolveHomeHubHeroButtonSurfaceSpec(
            mode = eu.kanade.domain.ui.model.HomeHeroCtaMode.Aurora,
            isDark = false,
        ) shouldBe HomeHubHeroButtonSurfaceSpec(
            containerAlpha = 0.78f,
            usesGradient = false,
            borderAlpha = 0.18f,
            innerGlowAlpha = 0.10f,
            highlightAlpha = 0.12f,
        )
    }
}
