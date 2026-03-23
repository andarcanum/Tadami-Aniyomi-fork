package eu.kanade.presentation.theme

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraLightSurfaceTokensTest {

    @Test
    fun `light aurora theme exposes bright layered surfaces`() {
        resolveAuroraSurfaceColor(AuroraColors.Light, AuroraSurfaceLevel.Glass) shouldBe Color(0xD1FFFFFF)
        resolveAuroraSurfaceColor(AuroraColors.Light, AuroraSurfaceLevel.Strong) shouldBe Color(0xFFF2F7FD)
    }

    @Test
    fun `light aurora theme exposes readable borders and selection fills`() {
        resolveAuroraBorderColor(AuroraColors.Light, emphasized = false) shouldBe Color(0xFFD7E3F1)
        resolveAuroraSelectionContainerColor(AuroraColors.Light) shouldBe AuroraColors.Light.accent.copy(alpha = 0.14f)
        resolveAuroraSelectionBorderColor(AuroraColors.Light) shouldBe AuroraColors.Light.accent.copy(alpha = 0.28f)
    }

    @Test
    fun `light aurora top bar and icon surfaces stay airy`() {
        resolveAuroraTopBarScrimColor(AuroraColors.Light) shouldBe Color(0x14FFFFFF)
        resolveAuroraIconSurfaceColor(AuroraColors.Light) shouldBe Color(0xFFEAF1F8)
    }

    @Test
    fun `e ink aurora theme uses paper like neutral surfaces`() {
        resolveAuroraSurfaceColor(AuroraColors.EInk, AuroraSurfaceLevel.Glass) shouldBe Color(0xFFF5F5F5)
        resolveAuroraSurfaceColor(AuroraColors.EInk, AuroraSurfaceLevel.Strong) shouldBe Color(0xFFECECEC)
        resolveAuroraBorderColor(AuroraColors.EInk, emphasized = true) shouldBe Color(0xFF8F8F8F)
        resolveAuroraSelectionContainerColor(AuroraColors.EInk) shouldBe Color(0xFFE5E5E5)
        resolveAuroraSelectionBorderColor(AuroraColors.EInk) shouldBe Color(0xFF8A8A8A)
        resolveAuroraControlSelectedContentColor(AuroraColors.EInk) shouldBe Color.Black
        resolveAuroraIconSurfaceColor(AuroraColors.EInk) shouldBe Color(0xFFEDEDED)
    }
}
