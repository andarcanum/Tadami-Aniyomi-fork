package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.EInkThemeMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UiPreferencesEInkThemeTest {

    @Test
    fun `e ink theme mode defaults to system`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.eInkThemeMode().get() shouldBe EInkThemeMode.SYSTEM
    }

    @Test
    fun `e ink theme mode persists dark selection`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val eInkThemeMode = prefs.eInkThemeMode()

        eInkThemeMode.set(EInkThemeMode.DARK)

        eInkThemeMode.get() shouldBe EInkThemeMode.DARK
    }
}
