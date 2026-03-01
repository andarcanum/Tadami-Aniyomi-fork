package eu.kanade.presentation.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraTabRowStyleTest {

    @Test
    fun `menu tab row rim light stops match hero style`() {
        auroraMenuRimLightAlphaStops() shouldBe listOf(
            0.00f to 0.10f,
            0.28f to 0.03f,
            0.62f to 0.00f,
            1.00f to 0.00f,
        )
    }
}
