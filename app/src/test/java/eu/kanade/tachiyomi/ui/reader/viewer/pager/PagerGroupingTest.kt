package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * A-M1: the double-page grouping must expose the FINAL physical page index on its last item,
 * because the reader marks a chapter complete via `pages.lastIndex == pageIndex`. Before the fix
 * JoinedReaderPage carried firstPage.index, so in L2R/vertical an even page count ended the
 * chapter on lastIndex-1 and completion never fired (no mark-read, no track update, no
 * delete-after-read).
 */
class PagerGroupingTest {

    private val chapter = ReaderChapter(
        ChapterImpl().apply {
            id = 1L
            manga_id = 1L
            url = "chapter-1"
            name = "Chapter 1"
        },
    )

    private fun pages(count: Int, wideAt: Set<Int> = emptySet()): List<ReaderPage> {
        return (0 until count).map { index ->
            ReaderPage(index, "/page/$index").apply {
                isWide = index in wideAt
                chapter = this@PagerGroupingTest.chapter
            }
        }
    }

    @Test
    fun `last spread exposes the final page index in L2R`() {
        val grouped = groupPagesForDoublePage(
            pages = pages(6),
            joinDoublePages = true,
            shiftDoublePages = false,
            isLandscape = true,
            isR2L = false,
        )
        grouped.last().shouldBeInstanceOf<JoinedReaderPage>()
        grouped.last().shouldBeInstanceOf<ReaderPage>().index shouldBe 5
    }

    @Test
    fun `last spread exposes the final page index in R2L`() {
        // R2L pairs first=nextPage and worked before the fix; pinned against regressions.
        val grouped = groupPagesForDoublePage(
            pages = pages(6),
            joinDoublePages = true,
            shiftDoublePages = false,
            isLandscape = true,
            isR2L = true,
        )
        grouped.last().shouldBeInstanceOf<ReaderPage>().index shouldBe 5
    }

    @Test
    fun `shifted and odd counts still end on the final page index`() {
        listOf(
            Triple(5, false, false),
            Triple(5, true, false),
            Triple(6, true, false),
            Triple(6, true, true),
        ).forEach { (count, shift, r2l) ->
            val grouped = groupPagesForDoublePage(
                pages = pages(count),
                joinDoublePages = true,
                shiftDoublePages = shift,
                isLandscape = true,
                isR2L = r2l,
            )
            grouped.last().shouldBeInstanceOf<ReaderPage>().index shouldBe count - 1
        }
    }

    @Test
    fun `wide pages are never paired`() {
        val grouped = groupPagesForDoublePage(
            pages = pages(4, wideAt = setOf(1)),
            joinDoublePages = true,
            shiftDoublePages = false,
            isLandscape = true,
            isR2L = false,
        )
        // 0 alone (next is wide), 1 wide alone, (2,3) paired.
        grouped.size shouldBe 3
        grouped[1].shouldBeInstanceOf<ReaderPage>().isWide shouldBe true
        grouped[2].shouldBeInstanceOf<JoinedReaderPage>()
        grouped.last().shouldBeInstanceOf<ReaderPage>().index shouldBe 3
    }

    @Test
    fun `portrait and disabled join return the plain page list`() {
        val plain = pages(4)
        groupPagesForDoublePage(
            pages = plain,
            joinDoublePages = true,
            shiftDoublePages = false,
            isLandscape = false,
            isR2L = false,
        ) shouldBe plain
        groupPagesForDoublePage(
            pages = plain,
            joinDoublePages = false,
            shiftDoublePages = false,
            isLandscape = true,
            isR2L = false,
        ) shouldBe plain
    }
}
