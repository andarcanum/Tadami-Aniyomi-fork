package eu.kanade.presentation.entries.novel.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelInfoCardToggleTest {

    @Test
    fun `shows description toggle when text overflows`() {
        shouldShowNovelDescriptionToggle(
            hasDescriptionOverflow = true,
            descriptionExpanded = false,
        ) shouldBe true
    }

    @Test
    fun `keeps description toggle visible when expanded`() {
        shouldShowNovelDescriptionToggle(
            hasDescriptionOverflow = false,
            descriptionExpanded = true,
        ) shouldBe true
    }

    @Test
    fun `hides description toggle when text does not overflow`() {
        shouldShowNovelDescriptionToggle(
            hasDescriptionOverflow = false,
            descriptionExpanded = false,
        ) shouldBe false
    }
}
