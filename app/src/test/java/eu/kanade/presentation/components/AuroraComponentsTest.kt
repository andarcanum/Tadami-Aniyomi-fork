package eu.kanade.presentation.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraComponentsTest {

    @Test
    fun `aurora animation runs only when enabled and resumed`() {
        shouldAnimateAuroraBackground(
            userEnabled = true,
            isLifecycleResumed = true,
            systemAnimationsEnabled = true,
        ) shouldBe true
    }

    @Test
    fun `aurora animation pauses when disabled or backgrounded`() {
        shouldAnimateAuroraBackground(
            userEnabled = false,
            isLifecycleResumed = true,
            systemAnimationsEnabled = true,
        ) shouldBe false

        shouldAnimateAuroraBackground(
            userEnabled = true,
            isLifecycleResumed = false,
            systemAnimationsEnabled = true,
        ) shouldBe false
    }

    @Test
    fun `aurora animation pauses when system animations are disabled`() {
        shouldAnimateAuroraBackground(
            userEnabled = true,
            isLifecycleResumed = true,
            systemAnimationsEnabled = false,
        ) shouldBe false
    }

    @Test
    fun `special background dispatch returns null for none, e-ink, and unknown styles`() {
        resolveAuroraSpecialBackgroundDispatchKey(styleKey = "none", isEInk = false) shouldBe null
        resolveAuroraSpecialBackgroundDispatchKey(styleKey = "ink_water", isEInk = true) shouldBe null
        resolveAuroraSpecialBackgroundDispatchKey(styleKey = "unknown_style", isEInk = false) shouldBe null
    }

    @Test
    fun `special background dispatch resolves every supported special style`() {
        val supported = listOf(
            "void_weeping_red",
            "ink_water",
            "petal_storm",
            "trinity_constellation",
            "event_horizon_library",
            "deep_space_archive",
            "shadow_realm",
            "neon_orbit",
        )
        supported.forEach { key ->
            resolveAuroraSpecialBackgroundDispatchKey(styleKey = key, isEInk = false) shouldBe key
        }
    }

    @Test
    fun `special elapsed seconds returns fallback when static and scaled time when animating`() {
        resolveAuroraSpecialElapsedSeconds(
            timeMillis = 2500L,
            animate = true,
            staticFallbackSeconds = 20f,
        ) shouldBe 2.5f
        resolveAuroraSpecialElapsedSeconds(
            timeMillis = 2500L,
            animate = false,
            staticFallbackSeconds = 20f,
        ) shouldBe 20f
    }
}
