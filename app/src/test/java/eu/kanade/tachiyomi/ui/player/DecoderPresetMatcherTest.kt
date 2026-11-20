package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DecoderPresetMatcherTest {
    @Test
    fun `resolveActive returns matching preset and null when customized`() {
        val midSnapshot = DecoderPreset.Mid.expectedSnapshot(supportsGpuNext = true)

        DecoderPreset.resolveActive(DecoderPreset.Mid, midSnapshot, supportsGpuNext = true) shouldBe DecoderPreset.Mid

        val customSnapshot = midSnapshot.copy(anime4kShaderPreset = Anime4KShaderPreset.Off)
        DecoderPreset.resolveActive(DecoderPreset.Mid, customSnapshot, supportsGpuNext = true) shouldBe null
    }
}
