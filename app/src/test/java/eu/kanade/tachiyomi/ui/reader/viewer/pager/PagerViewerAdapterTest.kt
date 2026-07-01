package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class PagerViewerAdapterTest {

    private fun createPage(index: Int, isWide: Boolean = false): ReaderPage {
        val page = ReaderPage(index, "url-$index", "image-$index")
        page.chapter = ReaderChapter(ChapterImpl().apply { id = 1L })
        // We will mock or set wide image flag on the page if needed, but since we are grouping based on page properties:
        // Let's assume we have a property `isWide` on ReaderPage or we check a flag.
        // Let's add a property or helper to check if a page is wide.
        // Actually, we can check a flag `isWide` on ReaderPage. Let's make sure we can set it.
        page.isWide = isWide
        return page
    }

    @Test
    fun `should not group pages when joinDoublePages is false`() {
        val pages = listOf(createPage(0), createPage(1), createPage(2))
        val result = groupPagesForDoublePage(pages, joinDoublePages = false, isLandscape = true, isR2L = false)

        result.size shouldBe 3
        result[0] shouldBe pages[0]
        result[1] shouldBe pages[1]
        result[2] shouldBe pages[2]
    }

    @Test
    fun `should not group pages when not in landscape`() {
        val pages = listOf(createPage(0), createPage(1), createPage(2))
        val result = groupPagesForDoublePage(pages, joinDoublePages = true, isLandscape = false, isR2L = false)

        result.size shouldBe 3
        result[0] shouldBe pages[0]
    }

    @Test
    fun `should group consecutive single pages in landscape`() {
        val pages = listOf(createPage(0), createPage(1), createPage(2), createPage(3))
        val result = groupPagesForDoublePage(pages, joinDoublePages = true, isLandscape = true, isR2L = false)

        // Page 0 and 1 grouped -> JoinedReaderPage
        // Page 2 and 3 grouped -> JoinedReaderPage
        result.size shouldBe 2

        val firstGroup = result[0]
        firstGroup.shouldBeInstanceOf<JoinedReaderPage>()
        firstGroup.firstPage shouldBe pages[0]
        firstGroup.secondPage shouldBe pages[1]

        val secondGroup = result[1]
        secondGroup.shouldBeInstanceOf<JoinedReaderPage>()
        secondGroup.firstPage shouldBe pages[2]
        secondGroup.secondPage shouldBe pages[3]
    }

    @Test
    fun `should not group wide pages`() {
        val pages = listOf(
            createPage(0),
            createPage(1, isWide = true), // Wide page!
            createPage(2),
            createPage(3),
        )
        val result = groupPagesForDoublePage(pages, joinDoublePages = true, isLandscape = true, isR2L = false)

        // Page 0 cannot be grouped with Page 1 because Page 1 is wide.
        // Page 1 remains single.
        // Page 2 and 3 are grouped.
        result.size shouldBe 3
        result[0] shouldBe pages[0]
        result[1] shouldBe pages[1]

        val third = result[2]
        third.shouldBeInstanceOf<JoinedReaderPage>()
        third.firstPage shouldBe pages[2]
        third.secondPage shouldBe pages[3]
    }

    @Test
    fun `should handle odd number of pages by leaving the last one single`() {
        val pages = listOf(createPage(0), createPage(1), createPage(2))
        val result = groupPagesForDoublePage(pages, joinDoublePages = true, isLandscape = true, isR2L = false)

        result.size shouldBe 2
        result[0].shouldBeInstanceOf<JoinedReaderPage>()
        result[1] shouldBe pages[2]
    }

    @Test
    fun `should reverse page order inside JoinedReaderPage for R2L`() {
        val pages = listOf(createPage(0), createPage(1))
        val result = groupPagesForDoublePage(pages, joinDoublePages = true, isLandscape = true, isR2L = true)

        result.size shouldBe 1
        val group = result[0]
        group.shouldBeInstanceOf<JoinedReaderPage>()
        // In R2L, the first page displayed on the right is page 0, and the second on the left is page 1.
        // So firstPage (left) should be page 1, and secondPage (right) should be page 0.
        group.firstPage shouldBe pages[1]
        group.secondPage shouldBe pages[0]
    }

    @Test
    fun `should shift double pages grouping by leaving the first page single`() {
        val pages = listOf(createPage(0), createPage(1), createPage(2), createPage(3))
        val result = groupPagesForDoublePage(
            pages = pages,
            joinDoublePages = true,
            shiftDoublePages = true,
            isLandscape = true,
            isR2L = false,
        )

        // Page 0 remains single
        // Page 1 and 2 grouped -> JoinedReaderPage
        // Page 3 remains single
        result.size shouldBe 3

        result[0] shouldBe pages[0]

        val secondGroup = result[1]
        secondGroup.shouldBeInstanceOf<JoinedReaderPage>()
        secondGroup.firstPage shouldBe pages[1]
        secondGroup.secondPage shouldBe pages[2]

        result[2] shouldBe pages[3]
    }
}
