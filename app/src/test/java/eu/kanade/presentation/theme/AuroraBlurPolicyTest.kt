package eu.kanade.presentation.theme

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraBlurPolicyTest {

    @Test
    fun `enables live blur on Android 12+ standard device`() {
        shouldUseLiveBackdropBlur(
            sdkInt = 31,
            isEInkMode = false,
            isLowRamDevice = false,
            isPowerSaveMode = false,
        ) shouldBe true
    }

    @Test
    fun `disables live blur below Android 12`() {
        shouldUseLiveBackdropBlur(
            sdkInt = 30,
            isEInkMode = false,
            isLowRamDevice = false,
            isPowerSaveMode = false,
        ) shouldBe false
    }

    @Test
    fun `disables live blur in e-ink mode`() {
        shouldUseLiveBackdropBlur(
            sdkInt = 34,
            isEInkMode = true,
            isLowRamDevice = false,
            isPowerSaveMode = false,
        ) shouldBe false
    }

    @Test
    fun `disables live blur on low-ram devices`() {
        shouldUseLiveBackdropBlur(
            sdkInt = 34,
            isEInkMode = false,
            isLowRamDevice = true,
            isPowerSaveMode = false,
        ) shouldBe false
    }

    @Test
    fun `disables live blur when battery saver is active`() {
        shouldUseLiveBackdropBlur(
            sdkInt = 34,
            isEInkMode = false,
            isLowRamDevice = false,
            isPowerSaveMode = true,
        ) shouldBe false
    }
}
