package com.davemorrissey.labs.subscaleview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileQualityTest {
    @Test
    fun `external scale selects physical source resolution`() {
        assertEquals(8, TileQuality.sampleForScale(0.1f))
        assertEquals(4, TileQuality.sampleForScale(0.2f))
        assertEquals(2, TileQuality.sampleForScale(0.4f))
        assertEquals(1, TileQuality.sampleForScale(0.8f))
        assertEquals(1, TileQuality.sampleForScale(5f))
    }

    @Test
    fun `preview pixel budget includes partial edge pixels`() {
        for ((width, height) in listOf(1 to 1, 4000 to 40000, 1025 to 50001, Int.MAX_VALUE to 1)) {
            val sample = TileQuality.previewSample(width, height)
            val pixels = ((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample)
            assertTrue(pixels <= TileQuality.PREVIEW_PIXELS)
            assertEquals(1, Integer.bitCount(sample))
        }
    }

    @Test
    fun `preview sample skips downsampling when grid fits at most two tiles`() {
        assertEquals(1, TileQuality.previewSample(1000, 1000, 1024, 1024))
        assertEquals(1, TileQuality.previewSample(1000, 1800, 1024, 1024))
        assertEquals(1, TileQuality.previewSample(1800, 1000, 1024, 1024))
        // 2x2 tiles (> 2) or tiny tile limits still downsample to the preview budget.
        assertEquals(
            TileQuality.previewSample(1800, 1800),
            TileQuality.previewSample(1800, 1800, 1024, 1024),
        )
        assertEquals(
            TileQuality.previewSample(4000, 40000),
            TileQuality.previewSample(4000, 40000, 2, 2),
        )
    }
}
