package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaybackSpeedPresetsTest {
    @Test
    fun `removeSpeedPreset deletes both one-decimal and two-decimal representations`() {
        val presets = setOf("0.5", "1.0", "1.50", "2.0")

        removeSpeedPreset(presets, 1.0f) shouldBe setOf("0.5", "1.50", "2.0")
        removeSpeedPreset(presets, 1.5f) shouldBe setOf("0.5", "1.0", "2.0")
    }

    @Test
    fun `addSpeedPreset avoids numeric duplicates and snapPlaybackSpeed steps by 0_05`() {
        val presets = setOf("1.0", "1.5")

        addSpeedPreset(presets, 1.0f) shouldBe presets
        addSpeedPreset(presets, 1.25f) shouldBe setOf("1.0", "1.5", "1.25")
        snapPlaybackSpeed(1.23f) shouldBe 1.25f
        snapPlaybackSpeed(0.01f) shouldBe 0.25f
    }
}
