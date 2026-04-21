package eu.kanade.presentation.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.ui.model.EInkProfile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraLightSurfaceTokensTest {

    @Test
    fun `light aurora theme exposes bright layered surfaces`() {
        resolveAuroraSurfaceColor(AuroraColors.Light, AuroraSurfaceLevel.Glass) shouldBe Color(0xE6FFFFFF)
        resolveAuroraSurfaceColor(AuroraColors.Light, AuroraSurfaceLevel.Strong) shouldBe Color(0xFFF0F4F8)
    }

    @Test
    fun `light aurora theme exposes readable borders and selection fills`() {
        resolveAuroraBorderColor(AuroraColors.Light, emphasized = false) shouldBe Color(0xFFB8CCE0)
        resolveAuroraSelectionContainerColor(AuroraColors.Light) shouldBe AuroraColors.Light.accent.copy(alpha = 0.14f)
        resolveAuroraSelectionBorderColor(AuroraColors.Light) shouldBe AuroraColors.Light.accent.copy(alpha = 0.28f)
    }

    @Test
    fun `light aurora top bar and icon surfaces stay airy`() {
        resolveAuroraTopBarScrimColor(AuroraColors.Light) shouldBe Color(0x14FFFFFF)
        resolveAuroraIconSurfaceColor(AuroraColors.Light) shouldBe Color(0xFFEAF1F8)
    }

    @Test
    fun `light aurora accent keeps readable contrast on pale poster surfaces`() {
        (
            contrastRatio(
                foreground = AuroraColors.Light.accent,
                background = Color(0xFFF8FAFC),
            ) >= 4.5
            ) shouldBe true
    }

    @Test
    fun `e ink aurora theme uses paper like neutral surfaces`() {
        resolveAuroraSurfaceColor(AuroraColors.EInk, AuroraSurfaceLevel.Glass) shouldBe Color(0xFFF6F6F6)
        resolveAuroraSurfaceColor(AuroraColors.EInk, AuroraSurfaceLevel.Strong) shouldBe Color(0xFFEDEDED)
        resolveAuroraBorderColor(AuroraColors.EInk, emphasized = true) shouldBe Color(0xFF8A8A8A)
        resolveAuroraSelectionContainerColor(AuroraColors.EInk) shouldBe Color(0xFFE3E3E3)
        resolveAuroraSelectionBorderColor(AuroraColors.EInk) shouldBe Color(0xFF8A8A8A)
        resolveAuroraControlSelectedContentColor(AuroraColors.EInk) shouldBe Color.Black
        resolveAuroraIconSurfaceColor(AuroraColors.EInk) shouldBe Color(0xFFEDEDED)
    }

    @Test
    fun `dark e ink aurora theme uses dark neutral surfaces`() {
        resolveAuroraSurfaceColor(AuroraColors.EInkDark, AuroraSurfaceLevel.Glass) shouldBe Color(0xFF0C0C0C)
        resolveAuroraSurfaceColor(AuroraColors.EInkDark, AuroraSurfaceLevel.Strong) shouldBe Color(0xFF101010)
        resolveAuroraBorderColor(AuroraColors.EInkDark, emphasized = true) shouldBe Color(0xFF5A5A5A)
        resolveAuroraSelectionContainerColor(AuroraColors.EInkDark) shouldBe Color(0xFF2A2A2A)
        resolveAuroraSelectionBorderColor(AuroraColors.EInkDark) shouldBe Color(0xFF5A5A5A)
        resolveAuroraControlSelectedContentColor(AuroraColors.EInkDark) shouldBe Color.White
        resolveAuroraIconSurfaceColor(AuroraColors.EInkDark) shouldBe AuroraColors.EInkDark.cardBackground
        resolveAuroraTopBarScrimColor(AuroraColors.EInkDark) shouldBe AuroraColors.EInkDark.surface
    }

    @Test
    fun `color e ink palette keeps accent driven controls`() {
        val colorScheme = lightColorScheme()
        val colors = AuroraColors.fromColorScheme(
            colorScheme = colorScheme,
            isDark = false,
            eInkProfile = EInkProfile.COLOR,
        )

        resolveAuroraBorderColor(colors, emphasized = false) shouldBe colorScheme.outlineVariant
        resolveAuroraSelectionContainerColor(colors) shouldBe colorScheme.primary
        resolveAuroraSelectionBorderColor(colors) shouldBe colorScheme.primaryContainer
        resolveAuroraControlSelectedContentColor(colors) shouldBe colorScheme.onPrimary
        resolveAuroraIconSurfaceColor(colors) shouldBe colors.cardBackground
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val fgLuminance = relativeLuminance(foreground)
        val bgLuminance = relativeLuminance(background)
        val lighter = maxOf(fgLuminance, bgLuminance)
        val darker = minOf(fgLuminance, bgLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        return (0.2126 * srgbToLinear(color.red.toDouble())) +
            (0.7152 * srgbToLinear(color.green.toDouble())) +
            (0.0722 * srgbToLinear(color.blue.toDouble()))
    }

    private fun srgbToLinear(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }
    }
}
