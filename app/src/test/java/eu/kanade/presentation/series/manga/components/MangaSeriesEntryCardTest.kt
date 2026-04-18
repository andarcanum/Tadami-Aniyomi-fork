package eu.kanade.presentation.series.manga.components

import eu.kanade.presentation.series.isSeriesEntryCompleted
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MangaSeriesEntryCardTest {

    @Test
    fun `completed state is based on read count and total chapters`() {
        assertTrue(isSeriesEntryCompleted(100, 100))
        assertFalse(isSeriesEntryCompleted(99, 100))
    }
}
