package eu.kanade.domain.entries.novel.interactor

import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.ui.browse.novel.migration.NovelMigrationFlags
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import java.util.Date

private const val CHAPTERS = 0b00001
private const val CATEGORIES = 0b00010

class MigrateNovelUseCaseTest {

    @Test
    fun `migrateNovel preserves chapter read progress, bookmark, lastPageRead and history`() = runTest {
        val sourceManager = mockk<NovelSourceManager>()
        val downloadManager = mockk<NovelDownloadManager>(relaxed = true)
        val updateNovel = mockk<UpdateNovel>(relaxed = true)
        val networkToLocalNovel = mockk<NetworkToLocalNovel>()
        val novelChapterRepository = mockk<NovelChapterRepository>()
        val syncNovelChaptersWithSource = mockk<SyncNovelChaptersWithSource>()
        val categoryRepository = mockk<NovelCategoryRepository>(relaxed = true)
        val novelHistoryRepository = mockk<NovelHistoryRepository>(relaxed = true)
        val source = mockk<NovelSource>()

        val oldNovel = Novel.create().copy(id = 1L, source = 10L, favorite = true, title = "Old Novel")
        val newNovel = Novel.create().copy(id = 2L, source = 20L, favorite = false, title = "New Novel")

        every { sourceManager.get(any()) } returns source
        coEvery { networkToLocalNovel.await(newNovel) } returns newNovel
        coEvery { source.getChapterList(any()) } returns listOf(
            SNovelChapter.create().apply {
                url = "/ch1"
                name = "Chapter 1"
                chapter_number = 1f
            },
        )
        coEvery { syncNovelChaptersWithSource.await(any(), any(), any()) } returns emptyList()

        val oldChapter = NovelChapter.create().copy(
            id = 101L,
            novelId = oldNovel.id,
            read = true,
            bookmark = true,
            lastPageRead = 42L,
            dateFetch = 9999L,
            chapterNumber = 1.0,
        )
        val newChapter = NovelChapter.create().copy(
            id = 201L,
            novelId = newNovel.id,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            dateFetch = 0L,
            chapterNumber = 1.0,
        )

        coEvery { novelChapterRepository.getChapterByNovelId(oldNovel.id) } returns listOf(oldChapter)
        coEvery { novelChapterRepository.getChapterByNovelId(newNovel.id) } returns listOf(newChapter)
        coEvery { categoryRepository.getCategoriesByNovelId(oldNovel.id) } returns emptyList()

        val historyDate = Date(1700000000000L)
        coEvery { novelHistoryRepository.getHistoryByNovelId(oldNovel.id) } returns listOf(
            NovelHistory(id = 1L, chapterId = oldChapter.id, readAt = historyDate, readDuration = 300L),
        )

        val chapterUpdatesSlot = slot<List<NovelChapterUpdate>>()
        coEvery { novelChapterRepository.updateAllChapters(capture(chapterUpdatesSlot)) } returns Unit

        val historyUpdateSlot = slot<NovelHistoryUpdate>()
        coEvery { novelHistoryRepository.upsertNovelHistory(capture(historyUpdateSlot)) } returns Unit

        val useCase = MigrateNovelUseCase(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateNovel = updateNovel,
            networkToLocalNovel = networkToLocalNovel,
            novelChapterRepository = novelChapterRepository,
            syncNovelChaptersWithSource = syncNovelChaptersWithSource,
            categoryRepository = categoryRepository,
            novelHistoryRepository = novelHistoryRepository,
        )

        val flags = CHAPTERS or CATEGORIES

        useCase.migrateNovel(
            oldNovel = oldNovel,
            newNovel = newNovel,
            replace = true,
            flags = flags,
        )

        // Verify chapter updates transferred
        val capturedUpdates = chapterUpdatesSlot.captured
        assertEquals(1, capturedUpdates.size)
        val updatedCh = capturedUpdates.first()
        assertEquals(newChapter.id, updatedCh.id)
        assertEquals(true, updatedCh.read)
        assertEquals(true, updatedCh.bookmark)
        assertEquals(42L, updatedCh.lastPageRead)
        assertEquals(9999L, updatedCh.dateFetch)

        // Verify history transferred
        assertEquals(newChapter.id, historyUpdateSlot.captured.chapterId)
        assertEquals(historyDate, historyUpdateSlot.captured.readAt)
        assertEquals(300L, historyUpdateSlot.captured.sessionReadDuration)

        // Verify favorite order: new favorite = true first, then old favorite = false
        coVerifyOrder {
            updateNovel.await(match { it.id == newNovel.id && it.favorite == true })
            updateNovel.await(match { it.id == oldNovel.id && it.favorite == false })
        }
    }
}
