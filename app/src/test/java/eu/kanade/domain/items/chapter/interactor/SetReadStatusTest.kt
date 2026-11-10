package eu.kanade.domain.items.chapter.interactor

import eu.kanade.domain.download.manga.interactor.DeleteChapterDownload
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetReadStatusTest {
    @Test
    fun `mark read preserves position for the preserve reading position setting`() = runTest {
        val fixture = Fixture()
        val chapter = Chapter.create().copy(id = 1, mangaId = 2, lastPageRead = 125)

        assertEquals(SetReadStatus.Result.Success, fixture.interactor.await(true, chapter))

        val update = fixture.updates.captured.single()
        assertEquals(true, update.read)
        assertNull(update.lastPageRead)
        assertNull(update.bookmark)
        verify(exactly = 0) { fixture.preferences.removeLongPageProgressForChapter(any()) }
    }

    @Test
    fun `mark unread resets both database and long page cache`() = runTest {
        val fixture = Fixture()
        val chapter = Chapter.create().copy(id = 1, mangaId = 2, read = true, lastPageRead = 125)

        assertEquals(SetReadStatus.Result.Success, fixture.interactor.await(false, chapter))

        val update = fixture.updates.captured.single()
        assertEquals(false, update.read)
        assertEquals(0L, update.lastPageRead)
        assertNull(update.bookmark)
        verify(exactly = 1) { fixture.preferences.removeLongPageProgressForChapter(1) }
    }

    private class Fixture {
        val preferences = mockk<ReaderPreferences>(relaxed = true)
        val updates = slot<List<ChapterUpdate>>()
        private val repository = mockk<ChapterRepository> {
            coEvery { updateAllChapters(capture(updates)) } returns Unit
            coEvery { getChapterByMangaId(any()) } returns listOf(Chapter.create())
        }
        private val downloadPreferences = mockk<DownloadPreferences> {
            every { removeAfterMarkedAsRead().get() } returns false
        }
        val interactor = SetReadStatus(
            downloadPreferences = downloadPreferences,
            deleteDownload = mockk<DeleteChapterDownload>(),
            mangaRepository = mockk<MangaRepository>(),
            chapterRepository = repository,
            eventBus = mockk<AchievementEventBus>(relaxed = true),
            activityDataRepository = mockk<ActivityDataRepository>(relaxed = true),
            readerPreferences = preferences,
        )
    }
}
