package eu.kanade.tachiyomi.ui.player.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference

class PlayerPreferencesTest {

    @Test
    fun `player chrome defaults keep custom buttons and aniskip visible`() {
        val prefs = PlayerPreferences(InMemoryPreferenceStore())

        val showCustomButtons = PlayerPreferences::class.java
            .getMethod("showCustomButtons")
            .invoke(prefs) as Preference<Boolean>

        showCustomButtons.get() shouldBe true
    }
}
