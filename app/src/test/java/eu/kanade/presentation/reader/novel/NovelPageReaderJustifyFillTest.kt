package eu.kanade.presentation.reader.novel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vertical justification fill for full paged pages: leftover page height is distributed into
 * inter-block spacers so a full page reads edge-to-edge instead of ending noticeably short.
 *
 * The paginator applies it in flushPage, where the leftover is exactly the unclaimed remainder
 * of the page budget. Fill goes only to real paragraph gaps (spacingBefore > 0): stretching
 * inside a paragraph or between continuation slices would look like a rendering bug. Each gap
 * gains at most half a line, the visual step books use between paragraphs.
 */
class NovelPageReaderJustifyFillTest {

    @Test
    fun `single block page gets no fill because there is no gap to stretch`() {
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0),
            leftoverPx = 300,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0), extra)
    }

    @Test
    fun `small leftover reserves safety buffer and splits remainder evenly across gaps under the cap`() {
        // leftover 40, safety buffer 6 → usable 34, 2 gaps, cap 28 → 17 per gap.
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0, 30, 30),
            leftoverPx = 40,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0, 17, 17), extra)
    }

    @Test
    fun `leftover within safety buffer gets no fill to prevent edge clipping`() {
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0, 30, 30),
            leftoverPx = 5,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0, 0, 0), extra)
    }

    @Test
    fun `large leftover is capped at half a line per gap`() {
        // leftover 570 → 285 per gap, cap 28.
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0, 30),
            leftoverPx = 570,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0, 28), extra)
    }

    @Test
    fun `fill saturates at the cap when gaps are many`() {
        // 3 gaps, leftover 220 → 73 each exceeds the 28 cap → 28,28,28.
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0, 10, 10, 10),
            leftoverPx = 220,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0, 28, 28, 28), extra)
    }

    @Test
    fun `page with only intra-paragraph slices gets no fill`() {
        // Continuation slices carry spacingBefore = 0; stretching inside a paragraph is a bug.
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0, 0, 0),
            leftoverPx = 300,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0, 0, 0), extra)
    }

    @Test
    fun `zero leftover gets no fill`() {
        val extra = resolveNovelPageReaderJustifyFillPx(
            sliceSpacingBeforePx = listOf(0, 30, 30),
            leftoverPx = 0,
            sliceLineHeightPx = 56,
        )

        assertEquals(listOf(0, 0, 0), extra)
    }
}
