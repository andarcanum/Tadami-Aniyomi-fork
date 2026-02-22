package eu.kanade.domain.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UserProfilePreferencesTest {

    @Test
    fun `home header preferences have expected defaults`() {
        val prefs = UserProfilePreferences(InMemoryPreferenceStore())

        prefs.showHomeGreeting().get() shouldBe true
        prefs.showHomeStreak().get() shouldBe true
        prefs.homeHubLastSection().get() shouldBe "anime"
        prefs.greetingFont().get() shouldBe "default"
        prefs.greetingFontSize().get() shouldBe 12
        prefs.greetingColor().get() shouldBe "theme"
        prefs.greetingCustomColorHex().get() shouldBe "#FFFFFF"
        prefs.greetingDecoration().get() shouldBe "none"
        prefs.greetingItalic().get() shouldBe false
    }
}
