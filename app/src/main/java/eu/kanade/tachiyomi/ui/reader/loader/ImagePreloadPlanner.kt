package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.max
import kotlin.math.min

/**
 * Computes the reader pages that should be warmed around the current page.
 *
 * The caller is responsible for checking page state and doing the actual IO. Keeping this module
 * pure makes the preloading policy easy to test and keeps loader implementations focused on IO.
 */
internal object ImagePreloadPlanner {

    fun pagesAround(
        currentPage: ReaderPage,
        pages: List<ReaderPage>,
        pagesBefore: Int,
        pagesAfter: Int,
    ): List<ReaderPage> {
        if (pages.isEmpty()) return emptyList()

        val pageIndex = currentPage.index
        if (pageIndex !in pages.indices) return emptyList()

        val afterRange = (pageIndex + 1)..min(pageIndex + pagesAfter.coerceAtLeast(0), pages.lastIndex)
        val beforeRange = (max(0, pageIndex - pagesBefore.coerceAtLeast(0)) until pageIndex).reversed()

        return buildList {
            afterRange.forEach { add(pages[it]) }
            beforeRange.forEach { add(pages[it]) }
        }
    }
}
