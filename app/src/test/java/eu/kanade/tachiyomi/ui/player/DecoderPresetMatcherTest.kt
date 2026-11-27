package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DecoderPresetMatcherTest {
    @Test
    fun `resolveActive returns matching preset and null when customized`() {
        val midSnapshot = DecoderPreset.Mid.expectedSnapshot(supportsGpuNext = true)

        DecoderPreset.resolveActive(DecoderPreset.Mid, midSnapshot, supportsGpuNext = true) shouldBe DecoderPreset.Mid

        val customSnapshot = midSnapshot.copy(useYuv420p = true)
        DecoderPreset.resolveActive(DecoderPreset.Mid, customSnapshot, supportsGpuNext = true) shouldBe null
    }

    @Test
    fun `mid preset keeps motion interpolation off while high keeps it always`() {
        val midSnapshot = DecoderPreset.Mid.expectedSnapshot(supportsGpuNext = true)
        val highSnapshot = DecoderPreset.High.expectedSnapshot(supportsGpuNext = true)

        midSnapshot.motionInterpolationMode shouldBe MotionInterpolationMode.Off
        highSnapshot.motionInterpolationMode shouldBe MotionInterpolationMode.Always
    }
}
