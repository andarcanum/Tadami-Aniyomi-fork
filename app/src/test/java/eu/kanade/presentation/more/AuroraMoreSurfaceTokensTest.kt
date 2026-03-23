package eu.kanade.presentation.more

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraColors
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraMoreSurfaceTokensTest {

    @Test
    fun `dark aurora more card container returns subtler glass alpha`() {
        resolveAuroraMoreCardContainerColor(AuroraColors.Dark) shouldBe Color.White.copy(alpha = 0.05f)
    }

    @Test
    fun `light aurora more card container returns subtler black alpha`() {
        resolveAuroraMoreCardContainerColor(AuroraColors.Light) shouldBe Color.White.copy(alpha = 0.82f)
    }

    @Test
    fun `dark aurora more switch track uses softer checked alpha`() {
        resolveAuroraMoreCheckedTrackColor(AuroraColors.Dark.copy(accent = Color(0xFF33AAFF))) shouldBe Color(0xFF33AAFF).copy(alpha = 0.4f)
    }

    @Test
    fun `light aurora more switch track keeps current checked alpha`() {
        resolveAuroraMoreCheckedTrackColor(AuroraColors.Light.copy(accent = Color(0xFF33AAFF))) shouldBe Color(0xFF33AAFF).copy(alpha = 0.24f)
    }

    @Test
    fun `e ink aurora more cards and switches stay monochrome`() {
        resolveAuroraMoreCardContainerColor(AuroraColors.EInk) shouldBe Color(0xFFECECEC)
        resolveAuroraMoreCardBorderColor(AuroraColors.EInk) shouldBe Color(0xFF8F8F8F)
        resolveAuroraMoreCheckedTrackColor(AuroraColors.EInk) shouldBe Color(0xFFCFCFCF)
    }

    @Test
    fun `aurora more card vertical inset matches aurora settings spacing`() {
        AURORA_MORE_CARD_VERTICAL_INSET shouldBe 4.dp
    }
}
