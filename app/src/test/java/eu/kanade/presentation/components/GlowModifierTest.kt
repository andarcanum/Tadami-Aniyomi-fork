package eu.kanade.presentation.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GlowModifierTest {

    @Test
    fun `gradient border glow generates three cached glow passes`() {
        val passes = gradientBorderGlowPasses(
            alpha = 0.8f,
            glowRadiusPx = 16f,
        )

        passes.size shouldBe 3
        passes[0].alpha shouldBe 0.53333336f
        passes[0].expansionPx shouldBe 5.3333335f
        passes[0].offsetPx shouldBe 2.6666667f
        passes[2].alpha shouldBe 0.17777778f
        passes[2].expansionPx shouldBe 16f
        passes[2].offsetPx shouldBe 8f
    }
}
