package eu.kanade.presentation.more

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraMoreSurfaceTokensTest {

    @Test
    fun `dark aurora more card container returns subtler glass alpha`() {
        resolveAuroraMoreCardContainerColor(
            glass = Color.White.copy(alpha = 0.22f),
            isDark = true,
        ) shouldBe Color.White.copy(alpha = 0.08f)
    }

    @Test
    fun `light aurora more card container keeps incoming glass color`() {
        val lightGlass = Color.Black.copy(alpha = 0.05f)

        resolveAuroraMoreCardContainerColor(
            glass = lightGlass,
            isDark = false,
        ) shouldBe lightGlass
    }

    @Test
    fun `dark aurora more switch track uses softer checked alpha`() {
        resolveAuroraMoreCheckedTrackColor(
            accent = Color(0xFF33AAFF),
            isDark = true,
        ) shouldBe Color(0xFF33AAFF).copy(alpha = 0.4f)
    }

    @Test
    fun `light aurora more switch track keeps current checked alpha`() {
        resolveAuroraMoreCheckedTrackColor(
            accent = Color(0xFF33AAFF),
            isDark = false,
        ) shouldBe Color(0xFF33AAFF).copy(alpha = 0.5f)
    }

    @Test
    fun `aurora more card vertical inset matches aurora settings spacing`() {
        AURORA_MORE_CARD_VERTICAL_INSET shouldBe 4.dp
    }
}
