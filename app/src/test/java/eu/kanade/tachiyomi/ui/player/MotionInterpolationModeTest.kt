package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MotionInterpolationModeTest {

    @Test
    fun `off interpolation never applies`() {
        MotionInterpolationMode.Off.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
        ) shouldBe false
    }

    @Test
    fun `auto interpolation requires gpu next`() {
        MotionInterpolationMode.Auto.shouldApply(
            gpuNextEnabled = false,
            deviceSupportsInterpolation = true,
        ) shouldBe false
    }

    @Test
    fun `auto interpolation applies when gpu next and high refresh non low ram device`() {
        MotionInterpolationMode.Auto.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
            isHighRefreshDisplay = true,
            isLowRamDevice = false,
        ) shouldBe true
    }

    @Test
    fun `auto interpolation stays off on standard 60hz display`() {
        MotionInterpolationMode.Auto.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
            isHighRefreshDisplay = false,
            isLowRamDevice = false,
        ) shouldBe false
    }

    @Test
    fun `auto interpolation stays off on low ram devices`() {
        MotionInterpolationMode.Auto.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
            isHighRefreshDisplay = true,
            isLowRamDevice = true,
        ) shouldBe false
    }

    @Test
    fun `always interpolation applies even on 60hz or low ram when gpu next and device support are enabled`() {
        MotionInterpolationMode.Always.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = true,
            isHighRefreshDisplay = false,
            isLowRamDevice = true,
        ) shouldBe true
    }

    @Test
    fun `always interpolation stays off when device support is disabled`() {
        MotionInterpolationMode.Always.shouldApply(
            gpuNextEnabled = true,
            deviceSupportsInterpolation = false,
        ) shouldBe false
    }

    @Test
    fun `always interpolation stays off when gpu next disabled`() {
        MotionInterpolationMode.Always.shouldApply(
            gpuNextEnabled = false,
            deviceSupportsInterpolation = true,
        ) shouldBe false
    }

    @Test
    fun `off interpolation produces no mpv options`() {
        val result: Map<String, String>? = MotionInterpolationMode.Off.mpvOptions()
        result shouldBe null
    }

    @Test
    fun `interpolation produces mpv options for video-sync tscale and interpolation`() {
        val options = MotionInterpolationMode.Auto.mpvOptions()
        options shouldBe mapOf(
            "video-sync" to "display-resample",
            "tscale" to "oversample",
            "interpolation" to "yes",
        )
    }
}
