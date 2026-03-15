package eu.kanade.domain.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UiPreferencesTest {

    @Test
    fun `manga scanlator branches are disabled by default`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.showMangaScanlatorBranches().get() shouldBe false
    }

    @Test
    fun `animated aurora background is enabled by default`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.animatedAuroraBackground().get() shouldBe true
    }

    @Test
    fun `app ui font defaults to system font`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.appUiFontId().get() shouldBe UiPreferences.DEFAULT_APP_UI_FONT_ID
    }

    @Test
    fun `cover title font defaults to system font`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.coverTitleFontId().get() shouldBe UiPreferences.DEFAULT_COVER_TITLE_FONT_ID
    }
}
