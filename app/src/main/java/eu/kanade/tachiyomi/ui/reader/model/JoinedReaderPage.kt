package eu.kanade.tachiyomi.ui.reader.model

/**
 * A page model that represents two pages displayed side-by-side.
 */
class JoinedReaderPage(
    val firstPage: ReaderPage,
    val secondPage: ReaderPage,
) : ReaderPage(firstPage.index, firstPage.url, firstPage.imageUrl) {
    init {
        chapter = firstPage.chapter
    }
}
