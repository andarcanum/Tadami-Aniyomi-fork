package eu.kanade.tachiyomi.ui.libraryUpdateError

import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorRecord
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorRunType
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryUpdateErrorRetryStateTest {

    @Test
    fun `retry state stays active while the original error record is still visible`() {
        val record = record(id = 1L, entryId = 10L)
        val retrying = mutableMapOf(record.key to record.id)

        reconcileRetryingLibraryUpdateErrors(
            errors = listOf(record),
            retryingErrors = retrying,
        )

        assertTrue(record.key in retrying)
    }

    @Test
    fun `retry state is cleared when an entry succeeds and disappears`() {
        val record = record(id = 1L, entryId = 10L)
        val retrying = mutableMapOf(record.key to record.id)

        reconcileRetryingLibraryUpdateErrors(
            errors = emptyList(),
            retryingErrors = retrying,
        )

        assertFalse(record.key in retrying)
    }

    @Test
    fun `retry state is cleared when retry finishes with a new error record`() {
        val oldRecord = record(id = 1L, entryId = 10L)
        val newRecord = record(id = 2L, entryId = 10L)
        val retrying = mutableMapOf(oldRecord.key to oldRecord.id)

        reconcileRetryingLibraryUpdateErrors(
            errors = listOf(newRecord),
            retryingErrors = retrying,
        )

        assertFalse(oldRecord.key in retrying)
    }

    private fun record(
        id: Long,
        entryId: Long,
        media: LibraryUpdateErrorMedia = LibraryUpdateErrorMedia.Manga,
    ): LibraryUpdateErrorRecord {
        return LibraryUpdateErrorRecord(
            id = id,
            media = media,
            entryId = entryId,
            title = "Title $entryId",
            sourceId = 100L,
            sourceName = "Source",
            thumbnailUrl = null,
            message = "Failed",
            runType = LibraryUpdateErrorRunType.Manual,
            occurredAt = id,
        )
    }
}
