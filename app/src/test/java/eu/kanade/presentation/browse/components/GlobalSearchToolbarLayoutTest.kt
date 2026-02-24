package eu.kanade.presentation.browse.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GlobalSearchToolbarLayoutTest {

    @Test
    fun `global search toolbar filters wrap on tablet widths`() {
        shouldWrapGlobalSearchToolbarFilters(480) shouldBe false
        shouldWrapGlobalSearchToolbarFilters(600) shouldBe true
        shouldWrapGlobalSearchToolbarFilters(840) shouldBe true
    }
}
