package eu.kanade.domain.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UiPreferencesImmersiveModeTest {

    @Test
    fun `aurora library immersive mode is disabled by default`() {
        val prefs = UiPreferences(InMemoryPreferenceStore())

        prefs.auroraLibraryImmersiveMode().get() shouldBe false
    }
}
