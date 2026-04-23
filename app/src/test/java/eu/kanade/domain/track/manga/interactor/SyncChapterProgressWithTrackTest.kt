package eu.kanade.domain.track.manga.interactor

import eu.kanade.tachiyomi.data.track.MangaTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncChapterProgressWithTrackTest {

    @Test
    fun `marks local chapters as read for non enhanced trackers`() = runTest {
        val mangaId = 42L
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val updateChapter = mockk<UpdateChapter>()
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val tracker = mockk<MangaTracker>(relaxed = true)
        val chapterUpdatesSlot = slot<List<ChapterUpdate>>()

        coEvery { getChaptersByMangaId.await(mangaId, any()) } returns listOf(
            chapter(id = 1, mangaId = mangaId, chapterNumber = 1.0, read = false),
            chapter(id = 2, mangaId = mangaId, chapterNumber = 2.0, read = false),
            chapter(id = 3, mangaId = mangaId, chapterNumber = 3.0, read = false),
        )
        coEvery { updateChapter.awaitAll(capture(chapterUpdatesSlot)) } returns Unit

        val remoteTrack = track(mangaId = mangaId, lastChapterRead = 2.0)
        val interactor = SyncChapterProgressWithTrack(
            updateChapter = updateChapter,
            insertTrack = insertTrack,
            getChaptersByMangaId = getChaptersByMangaId,
        )

        interactor.await(
            mangaId = mangaId,
            remoteTrack = remoteTrack,
            tracker = tracker,
        )

        assertEquals(listOf(1L, 2L), chapterUpdatesSlot.captured.map { it.id })
        assertTrue(chapterUpdatesSlot.captured.all { it.read == true })
        coVerify(exactly = 1) { updateChapter.awaitAll(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
        coVerify(exactly = 0) { insertTrack.await(any()) }
    }

    private fun chapter(
        id: Long,
        mangaId: Long,
        chapterNumber: Double,
        read: Boolean,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            chapterNumber = chapterNumber,
            read = read,
            name = "Chapter $chapterNumber",
            url = "/$chapterNumber",
        )
    }

    private fun track(
        mangaId: Long,
        lastChapterRead: Double,
    ): MangaTrack {
        return MangaTrack(
            id = 1L,
            mangaId = mangaId,
            trackerId = 2L,
            remoteId = 3L,
            libraryId = null,
            title = "Test",
            lastChapterRead = lastChapterRead,
            totalChapters = 10L,
            status = 1L,
            score = 0.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )
    }
}
