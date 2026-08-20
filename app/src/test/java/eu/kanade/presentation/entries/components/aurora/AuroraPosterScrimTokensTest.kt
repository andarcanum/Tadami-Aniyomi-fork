package eu.kanade.presentation.entries.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraPosterScrimTokensTest {

    @Test
    fun `light aurora poster scrim uses neutral airy overlay`() {
        resolveAuroraPosterScrimAlphaStops(isDark = false) shouldBe listOf(
            0.00f to 0.00f,
            0.45f to 0.00f,
            0.65f to 0.04f,
            0.85f to 0.16f,
            1.00f to 0.35f,
        )
    }
}
