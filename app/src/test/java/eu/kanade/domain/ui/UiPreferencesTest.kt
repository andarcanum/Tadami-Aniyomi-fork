package eu.kanade.domain.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UiPreferencesTest {

    @Test
    fun `manga scanlator branches are enabled by default`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.showMangaScanlatorBranches().get() shouldBe true
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

    @Test
    fun `entry auto jump preferences are disabled by default`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.entryAutoJumpToNextAnime().get() shouldBe false
        prefs.entryAutoJumpToNextManga().get() shouldBe false
        prefs.entryAutoJumpToNextNovel().get() shouldBe false
    }

    @Test
    fun `entry auto jump preferences persist independently`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val animePref = prefs.entryAutoJumpToNextAnime()
        val mangaPref = prefs.entryAutoJumpToNextManga()
        val novelPref = prefs.entryAutoJumpToNextNovel()

        animePref.set(true)
        mangaPref.set(false)
        novelPref.set(true)

        animePref.get() shouldBe true
        mangaPref.get() shouldBe false
        novelPref.get() shouldBe true
    }
}
