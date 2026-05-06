package eu.kanade.presentation.library.components

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

class GlowContourLibraryLayoutTest {

    @Test
    fun `comfortable grid shows title and subtitle below card`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.ComfortableGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = true,
                showTextOverlay = false,
                titleMaxLines = 2,
                subtitleMaxLines = 1,
                useUnifiedContainer = false,
            )
    }

    @Test
    fun `compact grid shows compact text block below card`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.CompactGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = false,
                showTextOverlay = true,
                titleMaxLines = 2,
                subtitleMaxLines = 1,
                useUnifiedContainer = false,
            )
    }

    @Test
    fun `cover only grid keeps card clean without text block`() {
        resolveGlowContourLibraryTextSpec(LibraryDisplayMode.CoverOnlyGrid) shouldBe
            GlowContourLibraryTextSpec(
                showTextBlock = false,
                showTextOverlay = false,
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
                showTextOverlay = false,
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
    fun `footer stays empty when continue handler is absent`() {
        resolveGlowContourFooterContent(
            progressPercent = 73,
            onClickContinueViewing = null,
        ) shouldBe GlowContourFooterContent.None
    }

    @Test
    fun `text block render spec uses proximity spacing on screen background`() {
        resolveGlowContourTextBlockRenderSpec() shouldBe GlowContourTextBlockRenderSpec(
            topSpacing = 8.dp,
            titleSubtitleSpacing = 2.dp,
            horizontalPadding = 6.dp,
            verticalPadding = 0.dp,
            useSurfaceBlend = false,
            titleMinLines = 1,
            minTextBlockHeight = 34.dp,
        )
    }

    @Test
    fun `anime indicator prefers continue action when handler is available and unseen remains`() {
        resolveGlowContourCornerIndicatorState(
            hasContinueAction = true,
            remainingCount = 4L,
            isFinished = true,
        ) shouldBe GlowContourCornerIndicatorState.ContinueAction
    }

    @Test
    fun `anime indicator uses check jewel when completed and caught up`() {
        resolveGlowContourCornerIndicatorState(
            hasContinueAction = false,
            remainingCount = 0L,
            isFinished = true,
        ) shouldBe GlowContourCornerIndicatorState.CompletedJewel
    }

    @Test
    fun `manga indicator uses update jewel when unread remains and continue is unavailable`() {
        resolveGlowContourCornerIndicatorState(
            hasContinueAction = false,
            remainingCount = 7L,
            isFinished = false,
        ) shouldBe GlowContourCornerIndicatorState.NewContentJewel
    }

    @Test
    fun `indicator falls back to neutral jewel when entry is neither completed nor new`() {
        resolveGlowContourCornerIndicatorState(
            hasContinueAction = false,
            remainingCount = 0L,
            isFinished = false,
        ) shouldBe GlowContourCornerIndicatorState.NeutralJewel
    }

    @Test
    fun `indicator content descriptions stay mapped for accessibility`() {
        listOf(
            GlowContourCornerIndicatorState.ContinueAction to MR.strings.action_resume,
            GlowContourCornerIndicatorState.CompletedJewel to MR.strings.completed,
            GlowContourCornerIndicatorState.NewContentJewel to AYMR.strings.aurora_new_badge,
            GlowContourCornerIndicatorState.NeutralJewel to MR.strings.status,
        ).forEach { (state, expectedRes) ->
            resolveGlowContourCornerIndicatorContentDescriptionRes(state) shouldBe expectedRes
        }
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

    @Test
    fun `poster surface spec keeps background clipped to shell`() {
        resolveGlowContourPosterSurfaceSpec(isDark = true) shouldBe GlowContourPosterSurfaceSpec(
            backgroundAlpha = 0.18f,
            clipBackgroundToShape = true,
        )
    }

    @Test
    fun `dark bottom mask keeps contour shape but uses airy aurora fade`() {
        resolveGlowContourBottomMaskRenderSpec(
            isDark = true,
            hasActionPocket = true,
        ) shouldBe GlowContourBottomMaskRenderSpec(
            accentTopAlpha = 0f,
            accentMidAlpha = 0.08f,
            accentBottomAlpha = 0.24f,
            pocketTopAlpha = 0.04f,
            pocketMidAlpha = 0.12f,
            pocketBottomAlpha = 0.26f,
            pocketBloomAlpha = 0.12f,
        )
    }

    @Test
    fun `action button render spec uses glass accent instead of solid fill`() {
        resolveGlowContourActionButtonRenderSpec(isDark = true) shouldBe GlowContourActionButtonRenderSpec(
            containerTopAlpha = 0.22f,
            containerBottomAlpha = 0.08f,
            borderTopAlpha = 0.42f,
            borderBottomAlpha = 0.14f,
            glowAlpha = 0.58f,
            glowElevation = 18.dp,
        )
    }

    @Test
    fun `progress line render spec uses aurora bloom instead of harsh neon`() {
        resolveGlowContourProgressLineRenderSpec() shouldBe GlowContourProgressLineRenderSpec(
            lineHeight = 2.5.dp,
            trackAlpha = 0.15f,
            glowAlpha = 0.56f,
            glowElevation = 14.dp,
        )
    }

    @Test
    fun `progress render state hides track when progress is absent`() {
        resolveGlowContourProgressRenderState(progressPercent = null) shouldBe
            GlowContourProgressRenderState(
                showTrack = false,
                fillFraction = null,
                showGlow = false,
            )
    }

    @Test
    fun `progress render state shows frosted track at zero percent`() {
        resolveGlowContourProgressRenderState(progressPercent = 0) shouldBe
            GlowContourProgressRenderState(
                showTrack = true,
                fillFraction = null,
                showGlow = false,
            )
    }

    @Test
    fun `progress render state shows fill and glow after reading starts`() {
        resolveGlowContourProgressRenderState(progressPercent = 64) shouldBe
            GlowContourProgressRenderState(
                showTrack = true,
                fillFraction = 0.64f,
                showGlow = true,
            )
    }

    @Test
    fun `divider render spec keeps only the poster seam and disables bottom frame glow`() {
        resolveGlowContourDividerRenderSpec() shouldBe GlowContourDividerRenderSpec(
            glowStrokeWidths = listOf(3.dp),
            coreStrokeWidth = 1.25.dp,
            glowAlphaBase = 0.14f,
            coreAlpha = 0.78f,
            clipBottomToProgressTop = true,
            showBottomFrameGlow = false,
        )
    }
}
