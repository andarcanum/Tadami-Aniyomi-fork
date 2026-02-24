package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MoreSheetLayoutTest {

    @Test
    fun `more sheet filter chips wrap on tablet widths`() {
        shouldWrapMoreSheetFilterChips(480) shouldBe false
        shouldWrapMoreSheetFilterChips(600) shouldBe true
        shouldWrapMoreSheetFilterChips(840) shouldBe true
    }
}
