package eu.kanade.domain.entries.manga.interactor

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryUpdate
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import java.util.Date
import kotlin.test.assertEquals

private const val CHAPTERS = 0b00001
private const val TRACKING = 0b00100
private const val DELETE_DOWNLOADED = 0b10000
private const val EXTRA = 0b100000
private const val NOTES = 0b1000000

class MigrateMangaUseCaseTest {

    @Test
    fun `tracking and extra data stay untouched when flags are off`() = runTest {
        val sourceManager = mockk<MangaSourceManager>()
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>()
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val syncChaptersWithSource = mockk<SyncChaptersWithSource>()
        val updateChapter = mockk<UpdateChapter>()
        val getCategories = mockk<GetMangaCategories>()
        val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
        val getTracks = mockk<GetMangaTracks>()
        val insertTrack = mockk<InsertMangaTrack>()
        val coverCache = mockk<MangaCoverCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val source = mockk<MangaSource>()
        val oldManga = manga(1L, 10L, viewerFlags = 77L, chapterFlags = 55L)
        val newManga = manga(2L, 20L, viewerFlags = 7L, chapterFlags = 9L)
        val updateSlot = slot<MangaUpdate>()

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { source.getChapterList(any()) } returns listOf(
            sChapter(1.0f),
        )
        coEvery { syncChaptersWithSource.await(any(), any(), any()) } returns emptyList()
        coEvery { getChaptersByMangaId.await(oldManga.id) } returns listOf(
            chapter(1L, oldManga.id, 1.0, read = true),
        )
        coEvery { getChaptersByMangaId.await(newManga.id) } returns listOf(
            chapter(2L, newManga.id, 1.0, read = false),
        )
        coEvery { updateChapter.awaitAll(any()) } returns Unit
        coEvery { getCategories.await(oldManga.id) } returns emptyList()
        coEvery { updateManga.await(capture(updateSlot)) } returns true

        val interactor = migrateUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            trackerManager = trackerManager,
        )

        interactor.migrateManga(
            oldManga = oldManga,
            newManga = newManga,
            replace = false,
            flags = CHAPTERS,
        )

        coVerify(exactly = 0) { getTracks.await(any()) }
        coVerify(exactly = 0) { insertTrack.awaitAll(any()) }
        assertEquals(null, updateSlot.captured.viewerFlags)
        assertEquals(null, updateSlot.captured.chapterFlags)
        assertEquals(true, updateSlot.captured.favorite)
    }

    @Test
    fun `tracking and extra data migrate when flags are on`() = runTest {
        val sourceManager = mockk<MangaSourceManager>()
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>()
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val syncChaptersWithSource = mockk<SyncChaptersWithSource>()
        val updateChapter = mockk<UpdateChapter>()
        val getCategories = mockk<GetMangaCategories>()
        val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
        val getTracks = mockk<GetMangaTracks>()
        val insertTrack = mockk<InsertMangaTrack>()
        val coverCache = mockk<MangaCoverCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val source = mockk<MangaSource>()
        val oldManga = manga(1L, 10L, viewerFlags = 77L, chapterFlags = 55L)
        val newManga = manga(2L, 20L, viewerFlags = 7L, chapterFlags = 9L)
        val updateSlot = slot<MangaUpdate>()
        val trackSlot = slot<List<MangaTrack>>()

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { source.getChapterList(any()) } returns listOf(
            sChapter(1.0f),
        )
        coEvery { syncChaptersWithSource.await(any(), any(), any()) } returns emptyList()
        coEvery { getChaptersByMangaId.await(oldManga.id) } returns listOf(
            chapter(1L, oldManga.id, 1.0, read = true),
        )
        coEvery { getChaptersByMangaId.await(newManga.id) } returns listOf(
            chapter(2L, newManga.id, 1.0, read = false),
        )
        coEvery { updateChapter.awaitAll(any()) } returns Unit
        coEvery { getCategories.await(oldManga.id) } returns emptyList()
        coEvery { getTracks.await(oldManga.id) } returns listOf(
            track(mangaId = oldManga.id),
        )
        coEvery { insertTrack.awaitAll(capture(trackSlot)) } returns Unit
        coEvery { updateManga.await(capture(updateSlot)) } returns true

        val interactor = migrateUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            trackerManager = trackerManager,
        )

        interactor.migrateManga(
            oldManga = oldManga,
            newManga = newManga,
            replace = false,
            flags = CHAPTERS or TRACKING or EXTRA,
        )

        coVerify(exactly = 1) { getTracks.await(oldManga.id) }
        coVerify(exactly = 1) { insertTrack.awaitAll(any()) }
        assertEquals(oldManga.viewerFlags, updateSlot.captured.viewerFlags)
        assertEquals(oldManga.chapterFlags, updateSlot.captured.chapterFlags)
        assertEquals(newManga.id, trackSlot.captured.single().mangaId)
    }

    @Test
    fun `chapter migration preserves exact read state last page and history`() = runTest {
        val sourceManager = mockk<MangaSourceManager>()
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>(relaxed = true)
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val syncChaptersWithSource = mockk<SyncChaptersWithSource>()
        val updateChapter = mockk<UpdateChapter>()
        val getCategories = mockk<GetMangaCategories>(relaxed = true)
        val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
        val getTracks = mockk<GetMangaTracks>()
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val coverCache = mockk<MangaCoverCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val getHistory = mockk<GetMangaHistory>()
        val upsertHistory = mockk<UpsertMangaHistory>(relaxed = true)
        val source = mockk<MangaSource>()
        val oldManga = manga(1L, 10L)
        val newManga = manga(2L, 20L)
        val chapterUpdates = slot<List<ChapterUpdate>>()
        val historyUpdate = slot<MangaHistoryUpdate>()
        val readAt = Date(123_000L)

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { source.getChapterList(any()) } returns listOf(sChapter(1.0f), sChapter(2.0f))
        coEvery { syncChaptersWithSource.await(any(), any(), any()) } returns emptyList()
        coEvery { getChaptersByMangaId.await(oldManga.id) } returns listOf(
            chapter(1L, oldManga.id, 1.0, read = true, lastPageRead = 12L),
            chapter(2L, oldManga.id, 2.0, read = false, lastPageRead = 4L),
        )
        coEvery { getChaptersByMangaId.await(newManga.id) } returns listOf(
            chapter(10L, newManga.id, 1.0, read = false),
            chapter(20L, newManga.id, 2.0, read = false),
        )
        coEvery { getHistory.await(oldManga.id) } returns listOf(
            MangaHistory(id = 100L, chapterId = 1L, readAt = readAt, readDuration = 42L),
        )
        coEvery { updateChapter.awaitAll(capture(chapterUpdates)) } returns Unit
        coEvery { upsertHistory.await(capture(historyUpdate)) } returns Unit

        val interactor = migrateUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            trackerManager = trackerManager,
            getHistory = getHistory,
            upsertHistory = upsertHistory,
        )

        interactor.migrateManga(
            oldManga = oldManga,
            newManga = newManga,
            replace = false,
            flags = CHAPTERS,
        )

        val updatedById = chapterUpdates.captured.associateBy { it.id }
        assertEquals(true, updatedById.getValue(10L).read)
        assertEquals(12L, updatedById.getValue(10L).lastPageRead)
        assertEquals(false, updatedById.getValue(20L).read)
        assertEquals(4L, updatedById.getValue(20L).lastPageRead)
        assertEquals(10L, historyUpdate.captured.chapterId)
        assertEquals(readAt, historyUpdate.captured.readAt)
        assertEquals(42L, historyUpdate.captured.sessionReadDuration)
    }

    @Test
    fun `notes migrate only when notes flag is enabled`() = runTest {
        val sourceManager = mockk<MangaSourceManager>()
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>()
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true)
        val syncChaptersWithSource = mockk<SyncChaptersWithSource>()
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val getCategories = mockk<GetMangaCategories>(relaxed = true)
        val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
        val getTracks = mockk<GetMangaTracks>(relaxed = true)
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val coverCache = mockk<MangaCoverCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val source = mockk<MangaSource>()
        val oldManga = manga(1L, 10L).copy(notes = "Keep this")
        val newManga = manga(2L, 20L)
        val updateSlot = slot<MangaUpdate>()

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { source.getChapterList(any()) } returns emptyList()
        coEvery { syncChaptersWithSource.await(any(), any(), any()) } returns emptyList()
        coEvery { updateManga.await(capture(updateSlot)) } returns true

        val interactor = migrateUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            trackerManager = trackerManager,
        )

        interactor.migrateManga(
            oldManga = oldManga,
            newManga = newManga,
            replace = false,
            flags = NOTES,
        )

        assertEquals(oldManga.notes, updateSlot.captured.notes)
    }

    @Test
    fun `downloaded chapters are removed when delete flag is enabled`() = runTest {
        val sourceManager = mockk<MangaSourceManager>()
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>(relaxed = true)
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val syncChaptersWithSource = mockk<SyncChaptersWithSource>()
        val updateChapter = mockk<UpdateChapter>()
        val getCategories = mockk<GetMangaCategories>()
        val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
        val getTracks = mockk<GetMangaTracks>()
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val coverCache = mockk<MangaCoverCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val source = mockk<MangaSource>()
        val oldManga = manga(1L, 10L)
        val newManga = manga(2L, 20L)

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { source.getChapterList(any()) } returns listOf(
            sChapter(2.0f),
        )
        coEvery { syncChaptersWithSource.await(any(), any(), any()) } returns emptyList()
        coEvery { getChaptersByMangaId.await(oldManga.id) } returns listOf(
            chapter(1L, oldManga.id, 1.0, read = true),
        )
        coEvery { getChaptersByMangaId.await(newManga.id) } returns listOf(
            chapter(2L, newManga.id, 2.0, read = false),
        )
        coEvery { updateChapter.awaitAll(any()) } returns Unit
        coEvery { getCategories.await(oldManga.id) } returns emptyList()

        val interactor = migrateUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            trackerManager = trackerManager,
        )

        interactor.migrateManga(
            oldManga = oldManga,
            newManga = newManga,
            replace = false,
            flags = CHAPTERS or DELETE_DOWNLOADED,
        )

        verify(exactly = 1) { downloadManager.deleteManga(oldManga, source) }
    }

    @Test
    fun `network manga target is localized before replacing old favorite`() = runTest {
        val sourceManager = mockk<MangaSourceManager>()
        val downloadManager = mockk<MangaDownloadManager>(relaxed = true)
        val updateManga = mockk<UpdateManga>(relaxed = true)
        val networkToLocalManga = mockk<NetworkToLocalManga>()
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true)
        val syncChaptersWithSource = mockk<SyncChaptersWithSource>()
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val getCategories = mockk<GetMangaCategories>(relaxed = true)
        val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
        val getTracks = mockk<GetMangaTracks>(relaxed = true)
        val insertTrack = mockk<InsertMangaTrack>(relaxed = true)
        val coverCache = mockk<MangaCoverCache>(relaxed = true)
        val trackerManager = mockk<TrackerManager>()
        val source = mockk<MangaSource>()
        val oldManga = manga(1L, 10L)
        val networkNewManga = manga(0L, 20L).copy(favorite = false, url = "/target")
        val localNewManga = networkNewManga.copy(id = 2L)
        val updateSlot = slot<MangaUpdate>()

        every { sourceManager.get(any()) } returns source
        every { trackerManager.trackers } returns emptyList()
        coEvery { networkToLocalManga.await(networkNewManga) } returns localNewManga
        coEvery { source.getChapterList(any()) } returns emptyList()
        coEvery { syncChaptersWithSource.await(any(), localNewManga, source) } returns emptyList()
        coEvery { updateManga.await(capture(updateSlot)) } returns true

        val interactor = migrateUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            trackerManager = trackerManager,
            networkToLocalManga = networkToLocalManga,
        )

        interactor.migrateManga(
            oldManga = oldManga,
            newManga = networkNewManga,
            replace = true,
            flags = 0,
        )

        assertEquals(localNewManga.id, updateSlot.captured.id)
        assertEquals(true, updateSlot.captured.favorite)
        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(oldManga.id, favorite = false) }
    }

    private fun migrateUseCase(
        sourceManager: MangaSourceManager,
        downloadManager: MangaDownloadManager,
        updateManga: UpdateManga,
        getChaptersByMangaId: GetChaptersByMangaId,
        syncChaptersWithSource: SyncChaptersWithSource,
        updateChapter: UpdateChapter,
        getCategories: GetMangaCategories,
        setMangaCategories: SetMangaCategories,
        getTracks: GetMangaTracks,
        insertTrack: InsertMangaTrack,
        coverCache: MangaCoverCache,
        trackerManager: TrackerManager,
        networkToLocalManga: NetworkToLocalManga = mockk<NetworkToLocalManga>().also {
            coEvery { it.await(any<Manga>()) } answers { firstArg<Manga>() }
        },
        getHistory: GetMangaHistory = mockk<GetMangaHistory>().also {
            coEvery { it.await(any()) } returns emptyList()
        },
        upsertHistory: UpsertMangaHistory = mockk(relaxed = true),
    ) = MigrateMangaUseCase(
        sourceManager = sourceManager,
        downloadManager = downloadManager,
        updateManga = updateManga,
        networkToLocalManga = networkToLocalManga,
        getChaptersByMangaId = getChaptersByMangaId,
        syncChaptersWithSource = syncChaptersWithSource,
        updateChapter = updateChapter,
        getCategories = getCategories,
        setMangaCategories = setMangaCategories,
        getTracks = getTracks,
        insertTrack = insertTrack,
        coverCache = coverCache,
        trackerManager = trackerManager,
        getHistory = getHistory,
        upsertHistory = upsertHistory,
    )

    private fun manga(
        id: Long,
        source: Long,
        viewerFlags: Long = 0L,
        chapterFlags: Long = 0L,
        title: String = "Title $id",
    ): Manga {
        return Manga.create().copy(
            id = id,
            source = source,
            favorite = true,
            url = "/$id",
            title = title,
            viewerFlags = viewerFlags,
            chapterFlags = chapterFlags,
            initialized = true,
        )
    }

    private fun chapter(
        id: Long,
        mangaId: Long,
        chapterNumber: Double,
        read: Boolean,
        lastPageRead: Long = 0L,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            chapterNumber = chapterNumber,
            read = read,
            lastPageRead = lastPageRead,
            name = "Chapter $chapterNumber",
            url = "/$chapterNumber",
        )
    }

    private fun sChapter(chapterNumber: Float): SChapter {
        return SChapter.create().apply {
            url = "/$chapterNumber"
            name = "Chapter $chapterNumber"
            date_upload = 0L
            this.chapter_number = chapterNumber
            scanlator = null
        }
    }

    private fun track(
        mangaId: Long,
    ): MangaTrack {
        return MangaTrack(
            id = 1L,
            mangaId = mangaId,
            trackerId = 2L,
            remoteId = 3L,
            libraryId = null,
            title = "Test",
            lastChapterRead = 1.0,
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
