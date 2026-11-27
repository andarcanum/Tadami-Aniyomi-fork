package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HdrToneMappingModeTest {

    @Test
    fun `spline uses spline on gpu-next and falls back to bt2446a on legacy gpu`() {
        HdrToneMappingMode.Spline.mpvOptions(gpuNextEnabled = true) shouldBe mapOf(
            "tone-mapping" to "spline",
            "hdr-compute-peak" to "auto",
        )
        HdrToneMappingMode.Spline.mpvOptions(gpuNextEnabled = false) shouldBe mapOf(
            "tone-mapping" to "bt.2446a",
            "hdr-compute-peak" to "auto",
        )
    }

    @Test
    fun `reinhard and clip disable hdr-compute-peak for lightweight rendering`() {
        HdrToneMappingMode.Reinhard.mpvOptions(gpuNextEnabled = true) shouldBe mapOf(
            "tone-mapping" to "reinhard",
            "hdr-compute-peak" to "no",
        )
        HdrToneMappingMode.Clip.mpvOptions(gpuNextEnabled = false) shouldBe mapOf(
            "tone-mapping" to "clip",
            "hdr-compute-peak" to "no",
        )
    }
}
