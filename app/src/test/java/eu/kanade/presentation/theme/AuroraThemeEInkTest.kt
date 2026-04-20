package eu.kanade.presentation.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import eu.kanade.domain.ui.model.EInkProfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class AuroraThemeEInkTest {

    @Test
    fun `e ink profile enables the e ink branch`() {
        EInkProfile.OFF.isEnabled shouldBe false
        EInkProfile.MONOCHROME.isEnabled shouldBe true
        EInkProfile.COLOR.isEnabled shouldBe true
    }

    @Test
    fun `aurora colors use the e ink palette when requested`() {
        val colors = AuroraColors.fromColorScheme(
            colorScheme = lightColorScheme(),
            isDark = false,
            eInkProfile = EInkProfile.MONOCHROME,
        )

        colors shouldBe AuroraColors.EInk
    }

    @Test
    fun `aurora colors use the dark e ink palette when requested`() {
        val colors = AuroraColors.fromColorScheme(
            colorScheme = darkColorScheme(),
            isDark = true,
            eInkProfile = EInkProfile.MONOCHROME,
        )

        colors shouldBe AuroraColors.EInkDark
    }

    @Test
    fun `aurora colors use the color e ink palette when requested`() {
        val colorScheme = lightColorScheme()
        val colors = AuroraColors.fromColorScheme(
            colorScheme = colorScheme,
            isDark = false,
            eInkProfile = EInkProfile.COLOR,
        )

        colors.eInkProfile shouldBe EInkProfile.COLOR
        colors.isEInk shouldBe true
        colors.accent shouldBe colorScheme.primary
        colors.textOnAccent shouldBe colorScheme.onPrimary
        colors.divider shouldBe colorScheme.outlineVariant
        colors.background shouldNotBe AuroraColors.EInk.background
        colors.cardBackground shouldNotBe AuroraColors.EInk.cardBackground
    }
}
