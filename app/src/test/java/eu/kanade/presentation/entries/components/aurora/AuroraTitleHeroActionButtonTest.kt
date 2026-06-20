package eu.kanade.presentation.entries.components.aurora

import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
import eu.kanade.presentation.components.AuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraTitleHeroActionButtonTest {

    @Test
    fun `title hero cta mode maps to presentation mode`() {
        AuroraTitleHeroCtaMode.Aurora.toPresentationMode() shouldBe AuroraHeroCtaMode.Aurora
        AuroraTitleHeroCtaMode.Classic.toPresentationMode() shouldBe AuroraHeroCtaMode.Classic
    }

    @Test
    fun `aurora title hero cta surface on details screen uses correct specifications`() {
        // Dark Details Screen
        resolveAuroraHeroCtaSurfaceSpec(
            mode = AuroraHeroCtaMode.Aurora,
            isDark = true,
            isHome = false,
        ) shouldBe AuroraHeroCtaSurfaceSpec(
            containerAlpha = 0.28f,
            usesGradient = true,
            innerGlowAlpha = 0.55f,
            highlightAlpha = 0.42f,
            borderAlpha = 0.12f,
        )

        // Light Details Screen
        resolveAuroraHeroCtaSurfaceSpec(
            mode = AuroraHeroCtaMode.Aurora,
            isDark = false,
            isHome = false,
        ) shouldBe AuroraHeroCtaSurfaceSpec(
            containerAlpha = 0.88f,
            usesGradient = false,
            innerGlowAlpha = 0.08f,
            highlightAlpha = 0.12f,
            borderAlpha = 0.10f,
        )
    }

    @Test
    fun `aurora title hero cta surface on home screen uses correct specifications`() {
        // Dark Home Screen
        resolveAuroraHeroCtaSurfaceSpec(
            mode = AuroraHeroCtaMode.Aurora,
            isDark = true,
            isHome = true,
        ) shouldBe AuroraHeroCtaSurfaceSpec(
            containerAlpha = 0.50f,
            usesGradient = false,
            innerGlowAlpha = 0.55f,
            highlightAlpha = 0f,
            borderAlpha = 0.12f,
        )

        // Light Home Screen
        resolveAuroraHeroCtaSurfaceSpec(
            mode = AuroraHeroCtaMode.Aurora,
            isDark = false,
            isHome = true,
        ) shouldBe AuroraHeroCtaSurfaceSpec(
            containerAlpha = 0.78f,
            usesGradient = false,
            innerGlowAlpha = 0.10f,
            highlightAlpha = 0.12f,
            borderAlpha = 0.18f,
        )
    }

    @Test
    fun `classic title hero cta surface specs`() {
        // Details Screen
        resolveAuroraHeroCtaSurfaceSpec(
            mode = AuroraHeroCtaMode.Classic,
            isDark = true,
            isHome = false,
        ) shouldBe AuroraHeroCtaSurfaceSpec(
            containerAlpha = 1f,
            usesGradient = false,
            innerGlowAlpha = 0f,
            highlightAlpha = 0f,
            borderAlpha = 0f,
        )

        // Home Screen
        resolveAuroraHeroCtaSurfaceSpec(
            mode = AuroraHeroCtaMode.Classic,
            isDark = true,
            isHome = true,
        ) shouldBe AuroraHeroCtaSurfaceSpec(
            containerAlpha = 1f,
            usesGradient = true,
            innerGlowAlpha = 0f,
            highlightAlpha = 0f,
            borderAlpha = 0.12f,
        )
    }

    @Test
    fun `title hero cta label uses readability shadow only in aurora mode`() {
        resolveAuroraCtaLabelShadowSpec(enabled = true) shouldBe AuroraCtaLabelShadowSpec(
            alpha = 0.26f,
            offsetY = 1.5f,
            blurRadius = 3.5f,
        )

        resolveAuroraCtaLabelShadowSpec(enabled = false) shouldBe AuroraCtaLabelShadowSpec(
            alpha = 0f,
            offsetY = 0f,
            blurRadius = 0f,
        )
    }
}
