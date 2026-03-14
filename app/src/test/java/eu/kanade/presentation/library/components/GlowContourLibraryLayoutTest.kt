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
                useUnifiedContainer = true,
            )
    }

    @Test
    fun `compact grid shows compact text block below card`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.CompactGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = true,
                titleMaxLines = 1,
                subtitleMaxLines = 1,
                useUnifiedContainer = true,
            )
    }

    @Test
    fun `cover only grid keeps card clean without text block`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.CoverOnlyGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = false,
                titleMaxLines = 0,
                subtitleMaxLines = 0,
                useUnifiedContainer = false,
            )
    }

    @Test
    fun `list mode keeps unified container disabled`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.List) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = false,
                titleMaxLines = 0,
                subtitleMaxLines = 0,
                useUnifiedContainer = false,
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

    @Test
    fun `dark unified blend keeps top card transparent and text block readable`() {
        resolveGlowContourUnifiedBlendSpec(isDark = true) shouldBe GlowContourUnifiedBlendSpec(
            topCardBackgroundAlpha = 0f,
            topCarryGlowAlpha = 0.18f,
            textTopFadeSurfaceAlpha = 0.06f,
            textBaseSurfaceAlpha = 0.18f,
            textTopGlowAlpha = 0.2f,
        )
    }

    @Test
    fun `light unified blend keeps transparent top with softer carry glow`() {
        resolveGlowContourUnifiedBlendSpec(isDark = false) shouldBe GlowContourUnifiedBlendSpec(
            topCardBackgroundAlpha = 0f,
            topCarryGlowAlpha = 0.12f,
            textTopFadeSurfaceAlpha = 0.04f,
            textBaseSurfaceAlpha = 0.12f,
            textTopGlowAlpha = 0.12f,
        )
    }
}
