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
}
