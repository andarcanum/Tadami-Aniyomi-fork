package eu.kanade.tachiyomi.ui.player.controls

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GestureHandlerTest {

    private val step = 40f

    @Test
    fun `no step is committed while the finger stays within one preset window`() {
        calculateSpeedPresetStep(5, commitX = 0f, currentX = 39.9f, step, presetCount = 10) shouldBe (5 to 0f)
        calculateSpeedPresetStep(5, commitX = 0f, currentX = -39.9f, step, presetCount = 10) shouldBe (5 to 0f)
    }

    @Test
    fun `a full step commits the neighbor preset and re-anchors the commit point`() {
        calculateSpeedPresetStep(5, commitX = 0f, currentX = 40f, step, presetCount = 10) shouldBe (6 to 40f)
        calculateSpeedPresetStep(5, commitX = 0f, currentX = -40f, step, presetCount = 10) shouldBe (4 to -40f)
    }

    @Test
    fun `tremor around the boundary does not flip the preset back`() {
        // Commit 2.5x at x=40, then drift back near the origin.
        var (index, commitX) = calculateSpeedPresetStep(5, 0f, 40f, step, presetCount = 10)
        (index to commitX) shouldBe (6 to 40f)

        // Crossing the original boundary back is not enough: a full step from the
        // new commit point is required, so holding still keeps the chosen speed.
        calculateSpeedPresetStep(index, commitX, 5f, step, presetCount = 10) shouldBe (6 to 40f)
        calculateSpeedPresetStep(index, commitX, 0.1f, step, presetCount = 10) shouldBe (6 to 40f)
    }

    @Test
    fun `traveling a full step back restores the previous preset`() {
        val (index, commitX) = calculateSpeedPresetStep(5, 0f, 40f, step, presetCount = 10)
        calculateSpeedPresetStep(index, commitX, 0f, step, presetCount = 10) shouldBe (5 to 0f)
    }

    @Test
    fun `a fast fling commits multiple presets at once`() {
        calculateSpeedPresetStep(5, commitX = 0f, currentX = 85f, step, presetCount = 10) shouldBe (7 to 85f)
        calculateSpeedPresetStep(5, commitX = 0f, currentX = -85f, step, presetCount = 10) shouldBe (3 to -85f)
    }

    @Test
    fun `presets are clamped at both ends of the list`() {
        calculateSpeedPresetStep(9, commitX = 0f, currentX = 400f, step, presetCount = 10) shouldBe (9 to 0f)
        calculateSpeedPresetStep(0, commitX = 0f, currentX = -400f, step, presetCount = 10) shouldBe (0 to 0f)
    }

    @Test
    fun `clamped commits do not consume the step budget for the reverse direction`() {
        // Sitting at the last preset after a long fling: commit point was never
        // re-anchored while clamped, so stepping back needs a full step from x=85.
        val (index, commitX) = calculateSpeedPresetStep(5, 0f, 400f, step, presetCount = 10)
        index shouldBe 9
        calculateSpeedPresetStep(index, commitX, 361f, step, presetCount = 10) shouldBe (9 to 400f)
        calculateSpeedPresetStep(index, commitX, 360f, step, presetCount = 10) shouldBe (8 to 360f)
    }
}
