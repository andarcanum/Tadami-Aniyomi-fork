package eu.kanade.presentation.more.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsUiStyleResolverTest {

    @Test
    fun `aurora theme resolves aurora style in single pane`() {
        resolveSettingsUiStyle(
            isAuroraTheme = true,
            paneContext = SettingsPaneContext.SinglePane,
        ) shouldBe SettingsUiStyle.Aurora
    }

    @Test
    fun `aurora theme resolves aurora style in two pane primary`() {
        resolveSettingsUiStyle(
            isAuroraTheme = true,
            paneContext = SettingsPaneContext.TwoPanePrimary,
        ) shouldBe SettingsUiStyle.Aurora
    }

    @Test
    fun `aurora theme resolves aurora style in two pane secondary`() {
        resolveSettingsUiStyle(
            isAuroraTheme = true,
            paneContext = SettingsPaneContext.TwoPaneSecondary,
        ) shouldBe SettingsUiStyle.Aurora
    }

    @Test
    fun `non aurora theme resolves classic style for all pane modes`() {
        SettingsPaneContext.entries.forEach { paneContext ->
            resolveSettingsUiStyle(
                isAuroraTheme = false,
                paneContext = paneContext,
            ) shouldBe SettingsUiStyle.Classic
        }
    }
}
