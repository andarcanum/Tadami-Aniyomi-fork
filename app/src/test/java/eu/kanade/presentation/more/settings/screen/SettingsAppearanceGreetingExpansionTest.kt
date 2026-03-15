package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.ui.UiPreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsAppearanceGreetingExpansionTest {

    @Test
    fun `toggleGreetingSettingsExpanded switches false to true`() {
        toggleGreetingSettingsExpanded(false) shouldBe true
    }

    @Test
    fun `toggleGreetingSettingsExpanded switches true to false`() {
        toggleGreetingSettingsExpanded(true) shouldBe false
    }

    @Test
    fun `font reset action disabled when both appearance fonts use defaults`() {
        shouldEnableAppearanceFontsReset(
            appUiFontId = UiPreferences.DEFAULT_APP_UI_FONT_ID,
            coverTitleFontId = UiPreferences.DEFAULT_COVER_TITLE_FONT_ID,
        ) shouldBe false
    }

    @Test
    fun `font reset action enabled when any appearance font is customized`() {
        shouldEnableAppearanceFontsReset(
            appUiFontId = "custom-ui",
            coverTitleFontId = UiPreferences.DEFAULT_COVER_TITLE_FONT_ID,
        ) shouldBe true
        shouldEnableAppearanceFontsReset(
            appUiFontId = UiPreferences.DEFAULT_APP_UI_FONT_ID,
            coverTitleFontId = "custom-cover",
        ) shouldBe true
    }
}
