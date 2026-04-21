package eu.kanade.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChapterListSheetTest {

    @Test
    fun returnsCurrentChapterIndex() {
        val items = listOf(
            ReaderChapterListItem(id = 1, title = "One"),
            ReaderChapterListItem(id = 2, title = "Two", isCurrent = true),
            ReaderChapterListItem(id = 3, title = "Three"),
        )

        assertEquals(1, resolveReaderChapterListStartIndex(items))
    }

    @Test
    fun returnsFirstCurrentChapterIndexWhenMultipleAreMarked() {
        val items = listOf(
            ReaderChapterListItem(id = 1, title = "One", isCurrent = true),
            ReaderChapterListItem(id = 2, title = "Two", isCurrent = true),
            ReaderChapterListItem(id = 3, title = "Three"),
        )

        assertEquals(0, resolveReaderChapterListStartIndex(items))
    }

    @Test
    fun fallsBackToZeroWhenNoCurrentChapterExists() {
        val items = listOf(
            ReaderChapterListItem(id = 1, title = "One"),
            ReaderChapterListItem(id = 2, title = "Two"),
        )

        assertEquals(0, resolveReaderChapterListStartIndex(items))
    }
}
