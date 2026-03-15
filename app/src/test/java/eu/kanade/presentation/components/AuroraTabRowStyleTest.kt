package eu.kanade.presentation.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraTabRowStyleTest {

    @Test
    fun `menu tab row rim light stops match hero style`() {
        auroraMenuRimLightAlphaStops() shouldBe listOf(
            0.00f to 0.10f,
            0.28f to 0.03f,
            0.62f to 0.00f,
            1.00f to 0.00f,
        )
    }

    @Test
    fun `aurora tab text style keeps selected typography defaults and custom font`() {
        val base = TextStyle(
            fontFamily = FontFamily.Cursive,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
        )

        val style = resolveAuroraTabTextStyle(base, isSelected = true)

        style.fontFamily shouldBe FontFamily.Cursive
        style.fontSize shouldBe 14.sp
        style.fontWeight shouldBe FontWeight.Bold
        style.hyphens shouldBe Hyphens.None
        style.letterSpacing shouldBe TextUnit.Unspecified
    }

    @Test
    fun `aurora tab text style keeps unselected typography defaults and custom font`() {
        val base = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )

        val style = resolveAuroraTabTextStyle(base, isSelected = false)

        style.fontFamily shouldBe FontFamily.Serif
        style.fontSize shouldBe 14.sp
        style.fontWeight shouldBe FontWeight.Medium
        style.hyphens shouldBe Hyphens.None
        style.letterSpacing shouldBe TextUnit.Unspecified
    }
}
