package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressHudLogicTest {

    @Test
    fun `progress fraction handles empty, single-page, mid-chapter, and overflow safely`() {
        assertEquals(0f, ReaderProgressHudLogic.computeProgressFraction(0, 0), 0.001f)
        assertEquals(0f, ReaderProgressHudLogic.computeProgressFraction(0, 20), 0.001f)
        assertEquals(1f, ReaderProgressHudLogic.computeProgressFraction(1, 1), 0.001f)
        assertEquals(0.5f, ReaderProgressHudLogic.computeProgressFraction(10, 20), 0.001f)
        assertEquals(1f, ReaderProgressHudLogic.computeProgressFraction(25, 20), 0.001f)
    }

    @Test
    fun `percent rounds cleanly into 0 to 100`() {
        assertEquals(0, ReaderProgressHudLogic.computePercent(0, 40))
        assertEquals(25, ReaderProgressHudLogic.computePercent(10, 40))
        assertEquals(100, ReaderProgressHudLogic.computePercent(40, 40))
    }

    @Test
    fun `scrub preview offset tracks thumb in both LTR and RTL`() {
        assertEquals(0f, ReaderProgressHudLogic.computeScrubPreviewOffsetFraction(1, 10, isRtl = false), 0.001f)
        assertEquals(1f, ReaderProgressHudLogic.computeScrubPreviewOffsetFraction(10, 10, isRtl = false), 0.001f)
        assertEquals(1f, ReaderProgressHudLogic.computeScrubPreviewOffsetFraction(1, 10, isRtl = true), 0.001f)
        assertEquals(0f, ReaderProgressHudLogic.computeScrubPreviewOffsetFraction(10, 10, isRtl = true), 0.001f)
        assertEquals(0.5f, ReaderProgressHudLogic.computeScrubPreviewOffsetFraction(6, 11, isRtl = true), 0.001f)
    }

    @Test
    fun `scrub sample size chooses power of two targeting compact thumbnail`() {
        assertEquals(1, ReaderProgressHudLogic.computeScrubSampleSize(200, 300))
        assertEquals(4, ReaderProgressHudLogic.computeScrubSampleSize(1200, 1800))
        assertEquals(16, ReaderProgressHudLogic.computeScrubSampleSize(800, 4000))
    }
}
