package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsAppearanceGreetingExpansionTest {

    @Test
    fun `toggleGreetingSettingsExpanded switches false to true`() {
        toggleGreetingSettingsExpanded(false) shouldBe true
    }

    @Test
    fun `toggleGreetingSettingsExpanded switches true to false`() {
        toggleGreetingSettingsExpanded(true) shouldBe false
    }
}
