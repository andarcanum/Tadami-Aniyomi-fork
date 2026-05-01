package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsNavigationParityTest {

    @Test
    fun `main settings navigation includes achievements item`() {
        val keys = mainSettingsNavigationItems().map { it.key }

        keys shouldContain "achievements"
    }

    @Test
    fun `main settings navigation keys remain unique`() {
        val keys = mainSettingsNavigationItems().map { it.key }

        keys.distinct().size shouldBe keys.size
    }

    @Test
    fun `player settings navigation keys remain unique`() {
        val keys = playerSettingsNavigationItems().map { it.key }

        keys.distinct().size shouldBe keys.size
    }

    @Test
    fun `settings search route list without player keeps core settings order`() {
        val routeClasses = settingsSearchRouteScreens(includePlayerSettings = false).map { it::class.simpleName }

        routeClasses shouldContainExactly listOf(
            "SettingsAppearanceScreen",
            "SettingsLibraryScreen",
            "SettingsReaderScreen",
            "SettingsNovelReaderScreen",
            "SettingsDownloadScreen",
            "SettingsTrackingScreen",
            "SettingsBrowseScreen",
            "SettingsDataScreen",
            "SettingsSecurityScreen",
            "SettingsAdvancedScreen",
        )
    }

    @Test
    fun `settings search route list with player appends player routes`() {
        val routeClasses = settingsSearchRouteScreens(includePlayerSettings = true).map { it::class.simpleName }

        routeClasses shouldHaveSize 19
        routeClasses.takeLast(9) shouldContainExactly listOf(
            "PlayerSettingsPlayerScreen",
            "PlayerSettingsLayoutMainScreen",
            "PlayerSettingsLayoutScreen",
            "PlayerSettingsLayoutScreen",
            "PlayerSettingsGesturesScreen",
            "PlayerSettingsDecoderScreen",
            "PlayerSettingsSubtitleScreen",
            "PlayerSettingsAudioScreen",
            "PlayerSettingsAdvancedScreen",
        )
    }
}
