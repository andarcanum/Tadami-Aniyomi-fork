package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SubtitleAndFilterPresetsTest {
    @Test
    fun `SubtitleStylePreset resolves matching preset and returns null on manual tweak`() {
        SubtitleStylePreset.resolveActive(SubtitleStylePreset.CinemaBox.snapshot) shouldBe SubtitleStylePreset.CinemaBox
        val custom = SubtitleStylePreset.CinemaBox.snapshot.copy(fontSize = 62)
        SubtitleStylePreset.resolveActive(custom) shouldBe null
    }

    @Test
    fun `VideoFilterPreset resolves matching preset and returns null on custom sliders`() {
        VideoFilterPreset.resolveActive(VideoFilterPreset.AnimeVivid.snapshot) shouldBe VideoFilterPreset.AnimeVivid
        val custom = VideoFilterPreset.AnimeVivid.snapshot.copy(contrast = 25)
        VideoFilterPreset.resolveActive(custom) shouldBe null
    }

    @Test
    fun `DecoderPreset describeFeatureTags summarizes active rendering pipeline`() {
        val midTags = DecoderPreset.describeFeatureTags(DecoderPreset.Mid.expectedSnapshot(supportsGpuNext = true))
        midTags shouldBe listOf("HW", "GPU-Next", "Deband: GPU", "Anime4K: Balanced")

        val highTags = DecoderPreset.describeFeatureTags(DecoderPreset.High.expectedSnapshot(supportsGpuNext = true))
        highTags shouldBe listOf("HW", "GPU-Next", "Deband: GPU", "Anime4K: Quality", "Motion: Always")
    }
}
