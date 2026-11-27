package eu.kanade.presentation.components

import androidx.compose.ui.geometry.Offset
import io.kotest.matchers.floats.plusOrMinus
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

    @Test
    fun `aurora ping pong oscillates smoothly between endpoints`() {
        auroraPingPong(timeMillis = 0L, durationMillis = 1000L, from = -0.1f, to = 0.2f) shouldBe
            (-0.1f plusOrMinus 1e-5f)
        auroraPingPong(timeMillis = 1000L, durationMillis = 1000L, from = -0.1f, to = 0.2f) shouldBe
            (0.2f plusOrMinus 1e-5f)
        auroraPingPong(timeMillis = 2000L, durationMillis = 1000L, from = -0.1f, to = 0.2f) shouldBe
            (-0.1f plusOrMinus 1e-5f)
        auroraPingPong(timeMillis = 500L, durationMillis = 1000L, from = 0f, to = 1f) shouldBe (0.5f plusOrMinus 1e-5f)
    }

    @Test
    fun `ambient blob circle center aligns with gradient center and transform`() {
        val radius = 240f
        val aspectX = 1.35f
        val aspectY = 0.8f
        val targetCenterX = 300f
        val targetCenterY = 180f

        val circleCenter = resolveAuroraAmbientBlobCircleCenter(radius)
        circleCenter shouldBe Offset(radius, radius)

        // Transform applies translate(targetCenter - radius * aspect) then scale(aspect, pivot = Zero).
        val transformedX = (targetCenterX - radius * aspectX) + circleCenter.x * aspectX
        val transformedY = (targetCenterY - radius * aspectY) + circleCenter.y * aspectY
        transformedX shouldBe (targetCenterX plusOrMinus 1e-4f)
        transformedY shouldBe (targetCenterY plusOrMinus 1e-4f)
    }
}
