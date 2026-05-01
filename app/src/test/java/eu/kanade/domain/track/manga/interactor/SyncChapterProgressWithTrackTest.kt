package eu.kanade.domain.track.manga.interactor

import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.MangaTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
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
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()
        val chapterUpdatesSlot = slot<List<ChapterUpdate>>()

        coEvery { getChaptersByMangaId.await(mangaId, any()) } returns listOf(
            chapter(id = 1, mangaId = mangaId, chapterNumber = 1.0, read = false),
            chapter(id = 2, mangaId = mangaId, chapterNumber = 2.0, read = false),
            chapter(id = 3, mangaId = mangaId, chapterNumber = 3.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns true
        coEvery { updateChapter.awaitAll(capture(chapterUpdatesSlot)) } returns Unit

        val remoteTrack = track(mangaId = mangaId, lastChapterRead = 2.0)
        val interactor = SyncChapterProgressWithTrack(
            updateChapter = updateChapter,
            insertTrack = insertTrack,
            getChaptersByMangaId = getChaptersByMangaId,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
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

    @Test
    fun `toggle off skips pull chapter marking`() = runTest {
        val mangaId = 42L
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val tracker = mockk<MangaTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()

        coEvery { getChaptersByMangaId.await(mangaId, any()) } returns listOf(
            chapter(id = 1, mangaId = mangaId, chapterNumber = 1.0, read = false),
            chapter(id = 2, mangaId = mangaId, chapterNumber = 2.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns false

        val interactor = SyncChapterProgressWithTrack(
            updateChapter = updateChapter,
            insertTrack = insertTrack,
            getChaptersByMangaId = getChaptersByMangaId,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            mangaId = mangaId,
            remoteTrack = track(mangaId = mangaId, lastChapterRead = 2.0),
            tracker = tracker,
        )

        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
        coVerify(exactly = 0) { insertTrack.await(any()) }
    }

    @Test
    fun `local ahead can push remote progression`() = runTest {
        val mangaId = 42L
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val tracker = mockk<MangaTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()
        val updatedTrackSlot = slot<eu.kanade.tachiyomi.data.database.models.manga.MangaTrack>()

        coEvery { getChaptersByMangaId.await(mangaId, any()) } returns listOf(
            chapter(id = 1, mangaId = mangaId, chapterNumber = 1.0, read = true),
            chapter(id = 2, mangaId = mangaId, chapterNumber = 2.0, read = true),
            chapter(id = 3, mangaId = mangaId, chapterNumber = 3.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns true
        coEvery { tracker.update(capture(updatedTrackSlot), any()) } answers { firstArg() }

        val interactor = SyncChapterProgressWithTrack(
            updateChapter = updateChapter,
            insertTrack = insertTrack,
            getChaptersByMangaId = getChaptersByMangaId,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            mangaId = mangaId,
            remoteTrack = track(mangaId = mangaId, lastChapterRead = 1.0),
            tracker = tracker,
        )

        assertEquals(2.0, updatedTrackSlot.captured.last_chapter_read)
        coVerify(exactly = 1) { tracker.update(any(), any()) }
        coVerify(exactly = 1) { insertTrack.await(any()) }
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
