package eu.kanade.presentation.entries.components.aurora

import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.AuroraColors
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraTitleReadabilityTokensTest {

    @Test
    fun `light hero overlay fades into surface instead of dark scrim`() {
        resolveAuroraHeroOverlayAlphaStops(isDark = false) shouldBe listOf(
            0.00f to 0.00f,
            0.28f to 0.00f,
            0.60f to 0.04f,
            0.82f to 0.28f,
            1.00f to 0.58f,
        )
    }

    @Test
    fun `light hero palette switches title and metadata to dark readable text`() {
        resolveAuroraHeroTitleColor(AuroraColors.Light) shouldBe AuroraColors.Light.textPrimary
        resolveAuroraHeroPrimaryMetaColor(AuroraColors.Light) shouldBe Color(0xFF1E293B)
        resolveAuroraHeroSecondaryMetaColor(AuroraColors.Light) shouldBe AuroraColors.Light.textSecondary
    }

    @Test
    fun `light hero secondary button uses bright surface and soft border`() {
        resolveAuroraHeroSecondaryButtonPalette(
            colors = AuroraColors.Light,
            isActive = false,
        ) shouldBe AuroraHeroSecondaryButtonPalette(
            containerColor = Color(0xE6FFFFFF),
            borderColor = Color(0xFFB8CCE0),
            contentColor = AuroraColors.Light.textSecondary,
        )
    }

    @Test
    fun `light detail card colors use bright layered surfaces`() {
        resolveAuroraDetailCardBackgroundColors(AuroraColors.Light) shouldBe listOf(
            Color(0xC3FFFFFF),
            Color(0xEEF2F7FD),
        )
        resolveAuroraDetailCardBorderColors(AuroraColors.Light) shouldBe listOf(
            Color(0xFF9DB4CC),
            Color(0xFFB8CCE0),
        )
    }

    @Test
    fun `e ink hero and detail palettes stay monochrome and paper like`() {
        resolveAuroraHeroTitleColor(AuroraColors.EInk) shouldBe Color.Black
        resolveAuroraHeroPrimaryMetaColor(AuroraColors.EInk) shouldBe Color(0xFF111111)
        resolveAuroraHeroSecondaryMetaColor(AuroraColors.EInk) shouldBe Color(0xFF2F2F2F)
        resolveAuroraHeroPanelContainerColor(AuroraColors.EInk) shouldBe Color(0xFFF7F7F7)
        resolveAuroraHeroPanelBorderColor(AuroraColors.EInk) shouldBe Color(0xFFBEBEBE)
        resolveAuroraHeroChipContainerColor(AuroraColors.EInk) shouldBe Color(0xFFEFEFEF)
        resolveAuroraHeroChipBorderColor(AuroraColors.EInk) shouldBe Color(0xFF9F9F9F)
        resolveAuroraHeroChipTextColor(AuroraColors.EInk) shouldBe Color.Black
        resolveAuroraHeroSecondaryButtonPalette(
            colors = AuroraColors.EInk,
            isActive = true,
        ) shouldBe AuroraHeroSecondaryButtonPalette(
            containerColor = Color(0xFFE0E0E0),
            borderColor = Color(0xFF8A8A8A),
            contentColor = Color.Black,
        )
        resolveAuroraDetailCardBackgroundColors(AuroraColors.EInk) shouldBe listOf(
            Color(0xFFF9F9F9),
            Color(0xFFF1F1F1),
        )
        resolveAuroraDetailCardBorderColors(AuroraColors.EInk) shouldBe listOf(
            Color(0xFF9E9E9E),
            Color(0xFFC9C9C9),
        )
    }
}
