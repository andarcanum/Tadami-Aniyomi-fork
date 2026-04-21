package eu.kanade.domain.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UiPreferencesEInkAutoOptimizationTest {

    @Test
    fun `e ink auto optimization defaults to disabled`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.eInkAutoOptimization().get() shouldBe false
    }

    @Test
    fun `e ink auto optimization persists enabled selection`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())
        val eInkAutoOptimization = prefs.eInkAutoOptimization()

        eInkAutoOptimization.set(true)

        eInkAutoOptimization.get() shouldBe true
    }
}
