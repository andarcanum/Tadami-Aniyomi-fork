package eu.kanade.presentation.theme

import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.domain.ui.model.EInkThemeMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TachiyomiThemeEInkTest {

    @Test
    fun `e ink light mode forces a light palette`() {
        resolveEInkThemeIsDark(
            eInkProfile = EInkProfile.MONOCHROME,
            eInkThemeMode = EInkThemeMode.LIGHT,
            isSystemDarkTheme = true,
        ) shouldBe false
    }

    @Test
    fun `e ink dark mode forces a dark palette`() {
        resolveEInkThemeIsDark(
            eInkProfile = EInkProfile.MONOCHROME,
            eInkThemeMode = EInkThemeMode.DARK,
            isSystemDarkTheme = false,
        ) shouldBe true
    }

    @Test
    fun `e ink system mode follows the system theme`() {
        resolveEInkThemeIsDark(
            eInkProfile = EInkProfile.COLOR,
            eInkThemeMode = EInkThemeMode.SYSTEM,
            isSystemDarkTheme = true,
        ) shouldBe true
    }

    @Test
    fun `e ink color mode also respects theme mode`() {
        resolveEInkThemeIsDark(
            eInkProfile = EInkProfile.COLOR,
            eInkThemeMode = EInkThemeMode.DARK,
            isSystemDarkTheme = false,
        ) shouldBe true
    }

    @Test
    fun `non e ink mode ignores the e ink theme mode`() {
        resolveEInkThemeIsDark(
            eInkProfile = EInkProfile.OFF,
            eInkThemeMode = EInkThemeMode.DARK,
            isSystemDarkTheme = false,
        ) shouldBe false
    }
}
