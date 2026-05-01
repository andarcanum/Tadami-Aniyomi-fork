package eu.kanade.tachiyomi.ui.player.layout

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlayerLayoutConfigTest {

    @Test
    fun `portrait defaults keep core controls in the bottom left and optional chrome in the bottom right`() {
        val config = PlayerLayoutConfig()

        config.regionFor(
            orientation = PlayerLayoutOrientation.Portrait,
            slot = PlayerLayoutSlot.PlaybackSpeed,
        ) shouldBe PlayerLayoutRegion.BottomLeft
        config.regionFor(
            orientation = PlayerLayoutOrientation.Portrait,
            slot = PlayerLayoutSlot.SkipIntro,
        ) shouldBe PlayerLayoutRegion.BottomRight
    }

    @Test
    fun `landscape defaults shift rotate screen into the bottom right region`() {
        val config = PlayerLayoutConfig()

        config.regionFor(
            orientation = PlayerLayoutOrientation.Landscape,
            slot = PlayerLayoutSlot.RotateScreen,
        ) shouldBe PlayerLayoutRegion.BottomRight
    }

    @Test
    fun `layout config round trips through the preference string`() {
        val config = PlayerLayoutConfig()
            .withRegion(
                orientation = PlayerLayoutOrientation.Landscape,
                slot = PlayerLayoutSlot.CustomButton,
                region = PlayerLayoutRegion.Hidden,
            )

        val encoded = config.toPreferenceValue()
        PlayerLayoutConfig.fromPreferenceValue(encoded) shouldBe config
    }
}
