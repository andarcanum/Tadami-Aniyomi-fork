package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.ui.reader.novel.BookLocator
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekReason
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelReaderNativeHardeningTest {
    @Test
    fun `reader ink is not overridden by source colors`() {
        val text = buildNovelRichAnnotatedString(
            listOf(
                NovelRichTextSegment(
                    "A",
                    NovelRichTextStyle(bold = true, colorCss = "#121212", backgroundColorCss = "#ffffff"),
                ),
            ),
        )
        assertEquals("A", text.text)
        assertTrue(
            text.spanStyles.all {
                it.item.color == Color.Unspecified && it.item.background == Color.Unspecified
            },
        )
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `source colors can still be explicitly requested by a fidelity caller`() {
        val text = buildNovelRichAnnotatedString(
            listOf(NovelRichTextSegment("A", NovelRichTextStyle(colorCss = "#121212"))),
            preserveSourceColors = true,
        )
        assertTrue(text.spanStyles.any { it.item.color != Color.Unspecified })
    }

    private fun entries(): List<NovelBookNativeEntry> = buildNovelBookNativeEntries(
        sections = listOf(
            NovelBookNativeSection(
                sectionIndex = 0,
                blocks = listOf(NovelRichContentBlock.Paragraph(listOf(NovelRichTextSegment("x".repeat(1000))))),
            ),
        ),
        failedSectionIndices = emptyList(),
    )

    private fun request(offset: Int) = BookSeekRequest(
        id = 1L,
        locator = BookLocator(chapterId = 10L, blockIndex = 0, charOffset = offset),
        location = NovelBookLocation(sectionIndex = 0, charOffset = offset),
        reason = BookSeekReason.TableOfContents,
    )

    @Test
    fun `zero offset means start regardless of current fraction`() {
        val target = resolveNovelBookNativeSeekTarget(entries(), request(0), 0L, 0.7f)
        assertEquals(0f, requireNotNull(target).fraction)
    }

    @Test
    fun `nonzero seek keeps its requested fraction`() {
        val target = resolveNovelBookNativeSeekTarget(entries(), request(250), 0L, 0.7f)
        assertEquals(0.25f, requireNotNull(target).fraction)
    }

    @Test
    fun `applied seek is not repeated`() {
        assertNull(resolveNovelBookNativeSeekTarget(entries(), request(0), 1L, 0.7f))
    }

    @Test
    fun `CSS coordinates scale with visual viewport width`() {
        assertEquals(3.0, resolveNovelWebSelectionScale(1080, 360.0, 1f))
        assertEquals(6.0, resolveNovelWebSelectionScale(1080, 180.0, 1f))
    }

    @Test
    fun `invalid viewport uses a finite density fallback`() {
        assertEquals(2.0, resolveNovelWebSelectionScale(1080, Double.NaN, 2f))
        assertEquals(1.0, resolveNovelWebSelectionScale(0, 0.0, Float.NaN))
    }
}
