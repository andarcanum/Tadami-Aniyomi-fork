package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.EInkProfile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UiPreferencesEInkTest {

    @Test
    fun `e ink profile defaults to off`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.eInkProfile().get() shouldBe EInkProfile.OFF
    }

    @Test
    fun `e ink profile persists monochrome selection`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val eInkProfile = prefs.eInkProfile()

        eInkProfile.set(EInkProfile.MONOCHROME)

        eInkProfile.get() shouldBe EInkProfile.MONOCHROME
    }

    @Test
    fun `e ink profile persists color selection`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val eInkProfile = prefs.eInkProfile()

        eInkProfile.set(EInkProfile.COLOR)

        eInkProfile.get() shouldBe EInkProfile.COLOR
    }
}
