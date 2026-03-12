package eu.kanade.presentation.achievement.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementBannerPaletteTest {

    @Test
    fun `unlock banner gradient is muted toward surface`() {
        val accent = Color(0xFF1D9BF0)
        val surface = Color(0xFF0F1116)

        val gradient = mutedUnlockBannerGradient(accent = accent, surface = surface)

        assertEquals(3, gradient.size)

        val accentDistance = colorDistance(accent, surface)
        gradient.forEach { stop ->
            assertTrue(
                actual = colorDistance(stop, surface) < accentDistance,
                message = "Expected muted stop to stay closer to surface than raw accent",
            )
        }
    }

    @Test
    fun `group banner gradient is muted toward surface`() {
        val accent = Color(0xFF8B5CF6)
        val secondary = Color(0xFF06B6D4)
        val surface = Color(0xFF121212)

        val gradient = mutedGroupBannerGradient(
            accent = accent,
            secondary = secondary,
            surface = surface,
        )

        assertEquals(3, gradient.size)

        val accentDistance = colorDistance(accent, surface)
        val secondaryDistance = colorDistance(secondary, surface)

        assertTrue(colorDistance(gradient[0], surface) < accentDistance)
        assertTrue(colorDistance(gradient[1], surface) < secondaryDistance)
        assertTrue(colorDistance(gradient[2], surface) < accentDistance)
    }

    private fun colorDistance(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
