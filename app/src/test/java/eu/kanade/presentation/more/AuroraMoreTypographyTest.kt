package eu.kanade.presentation.more

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraMoreTypographyTest {

    @Test
    fun `aurora primary menu title style keeps base font family`() {
        val base = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
        )

        val style = auroraPrimaryMenuTitleTextStyle(base)

        style.fontFamily shouldBe FontFamily.Monospace
        style.fontSize shouldBe 16.sp
        style.fontWeight shouldBe FontWeight.SemiBold
    }
}
