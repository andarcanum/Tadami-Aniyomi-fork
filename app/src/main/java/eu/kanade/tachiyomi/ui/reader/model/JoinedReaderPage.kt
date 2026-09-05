package eu.kanade.tachiyomi.ui.reader.model

/**
 * A page model that represents two pages displayed side-by-side.
 */
class JoinedReaderPage(
    val firstPage: ReaderPage,
    val secondPage: ReaderPage,
) : ReaderPage(
    // A-M1: the spread represents BOTH pages, so its index is the higher one. With firstPage's
    // index, the L2R/vertical pairing of an even page count ended on lastIndex-1 and the chapter
    // completion check (pages.lastIndex == pageIndex) never fired: the chapter was never marked
    // read, never tracked, never deleted-after-read. R2L pairs first=nextPage, where max keeps
    // the pre-existing behavior.
    maxOf(firstPage.index, secondPage.index),
    firstPage.url,
    firstPage.imageUrl,
) {
    init {
        chapter = firstPage.chapter
    }
}
