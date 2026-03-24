package eu.kanade.presentation.entries.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraPosterScrimTokensTest {

    @Test
    fun `light aurora poster scrim uses neutral airy overlay`() {
        resolveAuroraPosterScrimAlphaStops(isDark = false) shouldBe listOf(
            0.0f to 0.00f,
            0.3f to 0.00f,
            0.5f to 0.08f,
            0.7f to 0.18f,
            1.0f to 0.30f,
        )
    }
}
