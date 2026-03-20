package eu.kanade.presentation.more.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraSettingsTopBarBackdropTest {

    @Test
    fun `dark aurora settings header backdrop uses stronger opaque scrim`() {
        resolveAuroraSettingsTopBarBackdropSpec(isDark = true) shouldBe
            AuroraSettingsTopBarBackdropSpec(
                topAlpha = 0.96f,
                bottomAlpha = 0.88f,
            )
    }

    @Test
    fun `light aurora settings header backdrop uses slightly denser scrim`() {
        resolveAuroraSettingsTopBarBackdropSpec(isDark = false) shouldBe
            AuroraSettingsTopBarBackdropSpec(
                topAlpha = 0.98f,
                bottomAlpha = 0.92f,
            )
    }
}
