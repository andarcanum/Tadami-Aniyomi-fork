package eu.kanade.presentation.entries.components.aurora

import eu.kanade.domain.ui.model.AuroraTitleHeroCtaMode
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
            containerAlpha = 0.46f,
            usesGradient = false,
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
        )
    }
}
