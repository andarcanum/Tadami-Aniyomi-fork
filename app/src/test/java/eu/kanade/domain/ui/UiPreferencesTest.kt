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
    fun `special background style defaults to none`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.specialBackgroundStyle().get() shouldBe "none"
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

    @Test
    fun `suggestions UI defaults are correct`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.entrySuggestionsExpandInline().get() shouldBe true
        prefs.entrySuggestionsInOverflow().get() shouldBe false
    }

    @Test
    fun `title screen style defaults to poster immersive and persists glass stack`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val stylePref = prefs.titleScreenStyle()

        stylePref.get() shouldBe eu.kanade.domain.ui.model.TitleScreenStyle.POSTER_IMMERSIVE

        stylePref.set(eu.kanade.domain.ui.model.TitleScreenStyle.GLASS_STACK)
        stylePref.get() shouldBe eu.kanade.domain.ui.model.TitleScreenStyle.GLASS_STACK
    }

    @Test
    fun `title screen open animation is enabled by default and persists disabled`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val animationPref = prefs.titleScreenAnimation()

        animationPref.get() shouldBe true

        animationPref.set(false)
        animationPref.get() shouldBe false
    }
}
