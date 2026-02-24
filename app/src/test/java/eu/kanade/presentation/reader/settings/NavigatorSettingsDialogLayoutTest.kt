package eu.kanade.presentation.reader.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NavigatorSettingsDialogLayoutTest {

    @Test
    fun `navigator settings uses adaptive color palette layout on tablet widths`() {
        shouldUseAdaptiveNavigatorPaletteLayout(480) shouldBe false
        shouldUseAdaptiveNavigatorPaletteLayout(600) shouldBe true
        shouldUseAdaptiveNavigatorPaletteLayout(840) shouldBe true
    }
}
