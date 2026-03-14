package eu.kanade.presentation.library.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryDisplayMode

class GlowContourLibraryLayoutTest {

    @Test
    fun `comfortable grid shows title and subtitle below card`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.ComfortableGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = true,
                titleMaxLines = 2,
                subtitleMaxLines = 1,
            )
    }

    @Test
    fun `compact grid shows compact text block below card`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.CompactGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = true,
                titleMaxLines = 1,
                subtitleMaxLines = 1,
            )
    }

    @Test
    fun `cover only grid keeps card clean without text block`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.CoverOnlyGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = false,
                titleMaxLines = 0,
                subtitleMaxLines = 0,
            )
    }

    @Test
    fun `footer uses continue action when continue handler is available`() {
        resolveGlowContourFooterContent(
            progressPercent = 73,
            onClickContinueViewing = {},
        ) shouldBe GlowContourFooterContent.ContinueAction
    }

    @Test
    fun `footer falls back to progress percent when continue handler is absent`() {
        resolveGlowContourFooterContent(
            progressPercent = 73,
            onClickContinueViewing = null,
        ) shouldBe GlowContourFooterContent.ProgressPercent(73)
    }
}
