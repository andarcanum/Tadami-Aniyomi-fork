package eu.kanade.presentation.entries.components.aurora

import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
import eu.kanade.presentation.components.AuroraCtaLabelShadowSpec
import eu.kanade.presentation.components.resolveAuroraCtaLabelShadowSpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraTitleHeroActionButtonTest {

    @Test
    fun `title hero cta visual mode switches between aurora glass and classic solid`() {
        resolveAuroraTitleHeroCtaVisualMode(AuroraTitleHeroCtaMode.Aurora) shouldBe
            AuroraTitleHeroCtaVisualMode.AuroraGlass
        resolveAuroraTitleHeroCtaVisualMode(AuroraTitleHeroCtaMode.Classic) shouldBe
            AuroraTitleHeroCtaVisualMode.ClassicSolid
    }

    @Test
    fun `aurora title hero cta surface uses soft accent transparency without gradient`() {
        resolveAuroraTitleHeroCtaSurfaceSpec(
            mode = AuroraTitleHeroCtaMode.Aurora,
            isDark = true,
        ) shouldBe AuroraTitleHeroCtaSurfaceSpec(
            containerAlpha = 0.50f,
            usesGradient = false,
            innerGlowAlpha = 0.55f,
            highlightAlpha = 0f,
            borderAlpha = 0.12f,
        )
    }

    @Test
    fun `classic title hero cta surface stays opaque and non translucent`() {
        resolveAuroraTitleHeroCtaSurfaceSpec(
            mode = AuroraTitleHeroCtaMode.Classic,
            isDark = true,
        ) shouldBe AuroraTitleHeroCtaSurfaceSpec(
            containerAlpha = 1f,
            usesGradient = false,
            innerGlowAlpha = 0f,
            highlightAlpha = 0f,
            borderAlpha = 0f,
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
