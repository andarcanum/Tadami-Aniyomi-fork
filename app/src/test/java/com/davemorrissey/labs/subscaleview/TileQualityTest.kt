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
}
