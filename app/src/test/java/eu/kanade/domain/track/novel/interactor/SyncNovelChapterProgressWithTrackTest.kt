package eu.kanade.domain.track.novel.interactor

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
import tachiyomi.domain.items.novelchapter.interactor.GetNovelChapters
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack
import kotlin.test.assertEquals

class SyncNovelChapterProgressWithTrackTest {

    @Test
    fun `remote ahead marks local chapters to remote`() = runTest {
        val novelId = 42L
        val repository = mockk<NovelChapterRepository>()
        val insertTrack = mockk<InsertNovelTrack>(relaxed = true)
        val getNovelChapters = mockk<GetNovelChapters>()
        val tracker = mockk<MangaTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()
        val chapterUpdatesSlot = slot<List<NovelChapterUpdate>>()

        coEvery { getNovelChapters.await(novelId) } returns listOf(
            chapter(id = 1, novelId = novelId, chapterNumber = 1.0, read = false),
            chapter(id = 2, novelId = novelId, chapterNumber = 2.0, read = false),
            chapter(id = 3, novelId = novelId, chapterNumber = 3.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns true
        coEvery { repository.updateAllChapters(capture(chapterUpdatesSlot)) } returns Unit

        val interactor = SyncNovelChapterProgressWithTrack(
            novelChapterRepository = repository,
            insertTrack = insertTrack,
            getNovelChapters = getNovelChapters,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            novelId = novelId,
            remoteTrack = track(novelId = novelId, lastChapterRead = 2.0),
            tracker = tracker,
        )

        assertEquals(listOf(1L, 2L), chapterUpdatesSlot.captured.map { it.id })
        coVerify(exactly = 1) { repository.updateAllChapters(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
        coVerify(exactly = 0) { insertTrack.await(any()) }
    }

    @Test
    fun `local ahead pushes remote progression`() = runTest {
        val novelId = 42L
        val repository = mockk<NovelChapterRepository>(relaxed = true)
        val insertTrack = mockk<InsertNovelTrack>(relaxed = true)
        val getNovelChapters = mockk<GetNovelChapters>()
        val tracker = mockk<MangaTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()
        val updatedTrackSlot = slot<eu.kanade.tachiyomi.data.database.models.manga.MangaTrack>()

        coEvery { getNovelChapters.await(novelId) } returns listOf(
            chapter(id = 1, novelId = novelId, chapterNumber = 1.0, read = true),
            chapter(id = 2, novelId = novelId, chapterNumber = 2.0, read = true),
            chapter(id = 3, novelId = novelId, chapterNumber = 3.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns true
        coEvery { tracker.update(capture(updatedTrackSlot), any()) } answers { firstArg() }

        val interactor = SyncNovelChapterProgressWithTrack(
            novelChapterRepository = repository,
            insertTrack = insertTrack,
            getNovelChapters = getNovelChapters,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            novelId = novelId,
            remoteTrack = track(novelId = novelId, lastChapterRead = 1.0),
            tracker = tracker,
        )

        assertEquals(2.0, updatedTrackSlot.captured.last_chapter_read)
        coVerify(exactly = 1) { tracker.update(any(), any()) }
        coVerify(exactly = 1) { insertTrack.await(any()) }
    }

    @Test
    fun `equal progress is no-op`() = runTest {
        val novelId = 42L
        val repository = mockk<NovelChapterRepository>(relaxed = true)
        val insertTrack = mockk<InsertNovelTrack>(relaxed = true)
        val getNovelChapters = mockk<GetNovelChapters>()
        val tracker = mockk<MangaTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()

        coEvery { getNovelChapters.await(novelId) } returns listOf(
            chapter(id = 1, novelId = novelId, chapterNumber = 1.0, read = true),
            chapter(id = 2, novelId = novelId, chapterNumber = 2.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns true

        val interactor = SyncNovelChapterProgressWithTrack(
            novelChapterRepository = repository,
            insertTrack = insertTrack,
            getNovelChapters = getNovelChapters,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            novelId = novelId,
            remoteTrack = track(novelId = novelId, lastChapterRead = 1.0),
            tracker = tracker,
        )

        coVerify(exactly = 0) { repository.updateAllChapters(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
        coVerify(exactly = 0) { insertTrack.await(any()) }
    }

    @Test
    fun `toggle off on open suppresses pull action`() = runTest {
        val novelId = 42L
        val repository = mockk<NovelChapterRepository>(relaxed = true)
        val insertTrack = mockk<InsertNovelTrack>(relaxed = true)
        val getNovelChapters = mockk<GetNovelChapters>()
        val tracker = mockk<MangaTracker>(relaxed = true)
        val trackPreferences = mockk<TrackPreferences>()
        val pullPreference = mockk<Preference<Boolean>>()

        coEvery { getNovelChapters.await(novelId) } returns listOf(
            chapter(id = 1, novelId = novelId, chapterNumber = 1.0, read = false),
            chapter(id = 2, novelId = novelId, chapterNumber = 2.0, read = false),
        )
        every { trackPreferences.autoSyncProgressFromTracker() } returns pullPreference
        every { pullPreference.get() } returns false

        val interactor = SyncNovelChapterProgressWithTrack(
            novelChapterRepository = repository,
            insertTrack = insertTrack,
            getNovelChapters = getNovelChapters,
            trackPreferences = trackPreferences,
            resolveTrackProgressSync = ResolveTrackProgressSync(),
        )

        interactor.await(
            novelId = novelId,
            remoteTrack = track(novelId = novelId, lastChapterRead = 2.0),
            tracker = tracker,
        )

        coVerify(exactly = 0) { repository.updateAllChapters(any()) }
        coVerify(exactly = 0) { tracker.update(any(), any()) }
        coVerify(exactly = 0) { insertTrack.await(any()) }
    }

    private fun chapter(
        id: Long,
        novelId: Long,
        chapterNumber: Double,
        read: Boolean,
    ): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = novelId,
            chapterNumber = chapterNumber,
            read = read,
            name = "Chapter $chapterNumber",
            url = "/$chapterNumber",
        )
    }

    private fun track(
        novelId: Long,
        lastChapterRead: Double,
    ): NovelTrack {
        return NovelTrack(
            id = 1L,
            novelId = novelId,
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
