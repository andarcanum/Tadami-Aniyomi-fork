package eu.kanade.tachiyomi.ui.reader.viewer

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderScaleUtilTest {

    @Test
    fun `portrait screen with portrait image should resolve to FIT_WIDTH`() {
        val result = ReaderScaleUtil.resolveSmartScaleType(
            viewWidth = 1080,
            viewHeight = 2400,
            imageWidth = 1000,
            imageHeight = 1500,
        )
        result shouldBe SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
    }

    @Test
    fun `portrait screen with landscape spread image should resolve to CENTER_INSIDE`() {
        val result = ReaderScaleUtil.resolveSmartScaleType(
            viewWidth = 1080,
            viewHeight = 2400,
            imageWidth = 2000,
            imageHeight = 1400,
        )
        result shouldBe SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }

    @Test
    fun `landscape screen with portrait image should resolve to CENTER_INSIDE`() {
        // This is the core fix: user rotates phone to landscape while reading portrait manga.
        // It must scale to fit the screen height completely rather than blowing up across 2400px width.
        val result = ReaderScaleUtil.resolveSmartScaleType(
            viewWidth = 2400,
            viewHeight = 1080,
            imageWidth = 1000,
            imageHeight = 1500,
        )
        result shouldBe SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }

    @Test
    fun `landscape screen with landscape spread image should resolve to CENTER_INSIDE`() {
        val result = ReaderScaleUtil.resolveSmartScaleType(
            viewWidth = 2400,
            viewHeight = 1080,
            imageWidth = 2000,
            imageHeight = 1400,
        )
        result shouldBe SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }

    @Test
    fun `invalid or zero dimensions fallback to safe CENTER_INSIDE`() {
        ReaderScaleUtil.resolveSmartScaleType(0, 1080, 1000, 1500) shouldBe
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
        ReaderScaleUtil.resolveSmartScaleType(1080, 0, 1000, 1500) shouldBe
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
        ReaderScaleUtil.resolveSmartScaleType(1080, 2400, 0, 1500) shouldBe
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
        ReaderScaleUtil.resolveSmartScaleType(1080, 2400, 1000, 0) shouldBe
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    }

    @Test
    fun `webtoon margin in portrait should only apply user side padding`() {
        val margin = ReaderScaleUtil.computeWebtoonAdaptiveSideMargin(
            viewportWidth = 1080,
            viewportHeight = 2400,
            imageWidth = 800,
            imageHeight = 1600,
            basePaddingPercent = 5,
            webtoonSmartFit = true,
        )
        // 5% of 1080 = 54px
        margin shouldBe 54
    }

    @Test
    fun `webtoon margin in landscape when smart fit is disabled should only apply user side padding`() {
        val margin = ReaderScaleUtil.computeWebtoonAdaptiveSideMargin(
            viewportWidth = 2400,
            viewportHeight = 1080,
            imageWidth = 800,
            imageHeight = 1600,
            basePaddingPercent = 0,
            webtoonSmartFit = false,
        )
        margin shouldBe 0
    }

    @Test
    fun `webtoon margin in landscape when smart fit is enabled should apply adaptive pillarbox margin`() {
        val margin = ReaderScaleUtil.computeWebtoonAdaptiveSideMargin(
            viewportWidth = 2400,
            viewportHeight = 1080,
            imageWidth = 800,
            imageHeight = 1600,
            basePaddingPercent = 0,
            webtoonSmartFit = true,
        )
        // In landscape (2400x1080), standard vertical strip is capped to screen height (1080px).
        // Side margin = (2400 - 1080) / 2 = 660px
        margin shouldBe 660
    }

    @Test
    fun `webtoon margin in landscape for wide splash panel should not add pillarbox margin`() {
        val margin = ReaderScaleUtil.computeWebtoonAdaptiveSideMargin(
            viewportWidth = 2400,
            viewportHeight = 1080,
            imageWidth = 2400,
            imageHeight = 1200, // Wide image!
            basePaddingPercent = 0,
            webtoonSmartFit = true,
        )
        // Wide image can take full width
        margin shouldBe 0
    }
}
