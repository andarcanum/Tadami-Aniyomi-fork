package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImagePreloadPlannerTest {

    @Test
    fun `paged reader preloads pages before and after the current page`() {
        val pages = readerPages(10)

        val result = ImagePreloadPlanner.pagesAround(
            currentPage = pages[5],
            pages = pages,
            pagesBefore = 2,
            pagesAfter = 3,
        )

        result.map { it.index } shouldBe listOf(6, 7, 8, 4, 3)
    }

    @Test
    fun `long strip reader keeps preload range inside chapter bounds`() {
        val pages = readerPages(4)

        val result = ImagePreloadPlanner.pagesAround(
            currentPage = pages[1],
            pages = pages,
            pagesBefore = 3,
            pagesAfter = 5,
        )

        result.map { it.index } shouldBe listOf(2, 3, 0)
    }

    @Test
    fun `preload range can be disabled in either direction`() {
        val pages = readerPages(5)

        val result = ImagePreloadPlanner.pagesAround(
            currentPage = pages[2],
            pages = pages,
            pagesBefore = 0,
            pagesAfter = 1,
        )

        result.map { it.index } shouldBe listOf(3)
    }

    private fun readerPages(count: Int): List<ReaderPage> {
        return List(count) { index -> ReaderPage(index = index, url = "page-$index") }
    }
}
