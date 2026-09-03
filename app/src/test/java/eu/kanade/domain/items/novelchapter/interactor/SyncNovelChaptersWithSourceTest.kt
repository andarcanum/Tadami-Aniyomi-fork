package eu.kanade.domain.items.novelchapter.interactor

import eu.kanade.domain.entries.novel.interactor.GetNovelExcludedScanlators
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.interactor.ShouldUpdateDbNovelChapter
import tachiyomi.domain.items.novelchapter.model.NoChaptersException
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences

class SyncNovelChaptersWithSourceTest {

    @Test
    fun `throws when source returns no chapters`() {
        val repository = FakeNovelChapterRepository()
        val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
        val preferences = mockk<LibraryPreferences>()
        val interactor = SyncNovelChaptersWithSource(
            novelChapterRepository = repository,
            shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
            updateNovel = updateNovel,
            libraryPreferences = preferences,
            getNovelExcludedScanlators = noExcludedScanlators(),
        )

        assertThrows<NoChaptersException> {
            runBlocking {
                interactor.await(
                    rawSourceChapters = emptyList(),
                    novel = Novel.create().copy(id = 1L),
                    source = FakeNovelSource(),
                )
            }
        }
    }

    @Test
    fun `does not throw when local source returns no chapters`() = runTest {
        val repository = FakeNovelChapterRepository()
        val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>(relaxed = true)
        val preferences = mockk<LibraryPreferences>(relaxed = true)
        val interactor = SyncNovelChaptersWithSource(
            novelChapterRepository = repository,
            shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
            updateNovel = updateNovel,
            libraryPreferences = preferences,
            getNovelExcludedScanlators = noExcludedScanlators(),
        )

        val localSource = FakeNovelSource(id = 0L)
        val result = interactor.await(
            rawSourceChapters = emptyList(),
            novel = Novel.create().copy(id = 1L, source = 0L),
            source = localSource,
        )
        result shouldBe emptyList()
    }

    @Test
    fun `adds new chapters and updates last update`() {
        runTest {
            val repository = FakeNovelChapterRepository()
            val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
            val preferences = mockk<LibraryPreferences>()
            val duplicatePref = mockk<Preference<Set<String>>>()

            every { duplicatePref.get() } returns emptySet()
            every { preferences.markDuplicateReadChapterAsRead() } returns duplicatePref
            coEvery { updateNovel.await(any()) } returns true
            coEvery { updateNovel.awaitUpdateFetchInterval(any(), any(), any()) } returns true

            val interactor = SyncNovelChaptersWithSource(
                novelChapterRepository = repository,
                shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
                updateNovel = updateNovel,
                libraryPreferences = preferences,
                getNovelExcludedScanlators = noExcludedScanlators(),
            )

            val novel = Novel.create().copy(id = 10L, title = "Novel")
            val sChapter = SNovelChapter.create().apply {
                url = "/chapter-1"
                name = "Chapter 1"
                date_upload = 0L
                chapter_number = 1f
            }

            val result = interactor.await(
                rawSourceChapters = listOf(sChapter),
                novel = novel,
                source = FakeNovelSource(),
            )

            result.size shouldBe 1
            repository.addedChapters.size shouldBe 1
            repository.addedChapters.first().novelId shouldBe novel.id
            repository.addedChapters.first().sourceOrder shouldBe 0L

            coVerify { updateNovel.await(match { it.id == novel.id && it.lastUpdate != null }) }
            coVerify { updateNovel.awaitUpdateFetchInterval(novel, any(), any()) }
        }
    }

    @Test
    fun `keeps raw upload date and does not fabricate parsed upload date`() {
        runTest {
            val repository = FakeNovelChapterRepository()
            val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
            val preferences = mockk<LibraryPreferences>()
            val duplicatePref = mockk<Preference<Set<String>>>()

            every { duplicatePref.get() } returns emptySet()
            every { preferences.markDuplicateReadChapterAsRead() } returns duplicatePref
            coEvery { updateNovel.await(any()) } returns true
            coEvery { updateNovel.awaitUpdateFetchInterval(any(), any(), any()) } returns true

            val interactor = SyncNovelChaptersWithSource(
                novelChapterRepository = repository,
                shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
                updateNovel = updateNovel,
                libraryPreferences = preferences,
                getNovelExcludedScanlators = noExcludedScanlators(),
            )

            val novel = Novel.create().copy(id = 10L, title = "Novel")
            val sChapter = SNovelChapter.create().apply {
                url = "/chapter-raw"
                name = "Chapter Raw"
                date_upload = 0L
                date_upload_raw = "Yesterday at 22:14"
                chapter_number = 1f
            }

            interactor.await(
                rawSourceChapters = listOf(sChapter),
                novel = novel,
                source = FakeNovelSource(),
            )

            repository.addedChapters.first().dateUpload shouldBe 0L
            repository.addedChapters.first().dateUploadRaw shouldBe "Yesterday at 22:14"
        }
    }

    @Test
    fun `does not erase existing raw upload date with blank input`() {
        runTest {
            val existingChapter = NovelChapter.create().copy(
                id = 1L,
                novelId = 10L,
                url = "/chapter-1",
                name = "Chapter 1",
                chapterNumber = 1.0,
                sourceOrder = 0L,
                dateUpload = 0L,
                dateUploadRaw = "Yesterday",
            )
            val repository = FakeNovelChapterRepository().apply {
                chapters = listOf(existingChapter)
            }
            val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
            val preferences = mockk<LibraryPreferences>()
            val duplicatePref = mockk<Preference<Set<String>>>()

            every { duplicatePref.get() } returns emptySet()
            every { preferences.markDuplicateReadChapterAsRead() } returns duplicatePref
            coEvery { updateNovel.await(any()) } returns true
            coEvery { updateNovel.awaitUpdateFetchInterval(any(), any(), any()) } returns true

            val interactor = SyncNovelChaptersWithSource(
                novelChapterRepository = repository,
                shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
                updateNovel = updateNovel,
                libraryPreferences = preferences,
                getNovelExcludedScanlators = noExcludedScanlators(),
            )

            val novel = Novel.create().copy(id = 10L, title = "Novel")
            val sChapter = SNovelChapter.create().apply {
                url = "/chapter-1"
                name = "Chapter 1"
                date_upload = 0L
                date_upload_raw = " "
                chapter_number = 1f
            }

            interactor.await(
                rawSourceChapters = listOf(sChapter),
                novel = novel,
                source = FakeNovelSource(),
            )

            repository.updatedChapters.single().dateUploadRaw shouldBe "Yesterday"
        }
    }

    @Test
    fun `preserves in progress last page read when matching chapter number is re added with a new url`() {
        runTest {
            val existingChapter = NovelChapter.create().copy(
                id = 1L,
                novelId = 10L,
                url = "/chapter-old",
                name = "Chapter 12",
                chapterNumber = 12.0,
                sourceOrder = 0L,
                read = false,
                bookmark = true,
                lastPageRead = 47L,
            )
            val repository = FakeNovelChapterRepository().apply {
                chapters = listOf(existingChapter)
            }
            val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
            val preferences = mockk<LibraryPreferences>()
            val duplicatePref = mockk<Preference<Set<String>>>()

            every { duplicatePref.get() } returns emptySet()
            every { preferences.markDuplicateReadChapterAsRead() } returns duplicatePref
            coEvery { updateNovel.await(any()) } returns true
            coEvery { updateNovel.awaitUpdateFetchInterval(any(), any(), any()) } returns true

            val interactor = SyncNovelChaptersWithSource(
                novelChapterRepository = repository,
                shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
                updateNovel = updateNovel,
                libraryPreferences = preferences,
                getNovelExcludedScanlators = noExcludedScanlators(),
            )

            val novel = Novel.create().copy(id = 10L, title = "Novel")
            val replacementChapter = SNovelChapter.create().apply {
                url = "/chapter-new"
                name = "Chapter 12"
                date_upload = 0L
                chapter_number = 12f
            }

            interactor.await(
                rawSourceChapters = listOf(replacementChapter),
                novel = novel,
                source = FakeNovelSource(),
            )

            repository.removedIds shouldBe listOf(existingChapter.id)
            repository.addedChapters.single().bookmark shouldBe true
            repository.addedChapters.single().lastPageRead shouldBe 47L
            repository.addedChapters.single().read shouldBe false
        }
    }

    @Test
    fun `new chapters from excluded scanlators are filtered from the sync result`() {
        runTest {
            val repository = FakeNovelChapterRepository()
            val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
            val preferences = mockk<LibraryPreferences>()
            val duplicatePref = mockk<Preference<Set<String>>>()
            val excludedScanlators = mockk<GetNovelExcludedScanlators>()

            every { duplicatePref.get() } returns emptySet()
            every { preferences.markDuplicateReadChapterAsRead() } returns duplicatePref
            coEvery { updateNovel.await(any()) } returns true
            coEvery { updateNovel.awaitUpdateFetchInterval(any(), any(), any()) } returns true
            coEvery { excludedScanlators.await(10L) } returns setOf("BadGroup")

            val interactor = SyncNovelChaptersWithSource(
                novelChapterRepository = repository,
                shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
                updateNovel = updateNovel,
                libraryPreferences = preferences,
                getNovelExcludedScanlators = excludedScanlators,
            )

            val novel = Novel.create().copy(id = 10L, title = "Novel")
            val excludedChapter = SNovelChapter.create().apply {
                url = "/chapter-excluded"
                name = "Chapter 1"
                chapter_number = 1f
                scanlator = "BadGroup"
            }
            val keptChapter = SNovelChapter.create().apply {
                url = "/chapter-kept"
                name = "Chapter 2"
                chapter_number = 2f
                scanlator = "GoodGroup"
            }

            val result = interactor.await(
                rawSourceChapters = listOf(excludedChapter, keptChapter),
                novel = novel,
                source = FakeNovelSource(),
            )

            // The excluded chapter still lands in the DB; only the surfaced new-chapter list
            // (update notifications, auto-download) is filtered, matching the manga sync.
            result.map { it.url } shouldBe listOf("/chapter-kept")
            repository.addedChapters.size shouldBe 2
        }
    }

    @Test
    fun `no-change sync refreshes a stale next update when given a real fetch window`() {
        runTest {
            val existingChapter = NovelChapter.create().copy(
                id = 1L,
                novelId = 10L,
                url = "/chapter-1",
                name = "Chapter 1",
                chapterNumber = 1.0,
                sourceOrder = 0L,
            )
            val repository = FakeNovelChapterRepository().apply {
                chapters = listOf(existingChapter)
            }
            val updateNovel = mockk<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
            val preferences = mockk<LibraryPreferences>()
            val duplicatePref = mockk<Preference<Set<String>>>()

            every { duplicatePref.get() } returns emptySet()
            every { preferences.markDuplicateReadChapterAsRead() } returns duplicatePref
            coEvery { updateNovel.await(any()) } returns true
            coEvery { updateNovel.awaitUpdateFetchInterval(any(), any(), any()) } returns true

            val interactor = SyncNovelChaptersWithSource(
                novelChapterRepository = repository,
                shouldUpdateDbNovelChapter = ShouldUpdateDbNovelChapter(),
                updateNovel = updateNovel,
                libraryPreferences = preferences,
                getNovelExcludedScanlators = noExcludedScanlators(),
            )

            // A scheduled novel whose next_update slipped into the past without chapter changes
            // must be rescheduled by the no-change path - this is what the library job's real
            // fetch window enables (the old (0,0) sentinel made the guard dead).
            val novel = Novel.create().copy(id = 10L, title = "Novel", fetchInterval = 7, nextUpdate = 1_000L)
            val sChapter = SNovelChapter.create().apply {
                url = "/chapter-1"
                name = "Chapter 1"
                date_upload = 0L
                chapter_number = 1f
            }

            val result = interactor.await(
                rawSourceChapters = listOf(sChapter),
                novel = novel,
                source = FakeNovelSource(),
                manualFetch = false,
                fetchWindow = Pair(2_000L, 9_000L),
            )

            result shouldBe emptyList()
            coVerify { updateNovel.awaitUpdateFetchInterval(novel, any(), Pair(2_000L, 9_000L)) }
        }
    }

    private fun noExcludedScanlators(): GetNovelExcludedScanlators = mockk {
        coEvery { await(any()) } returns emptySet()
    }

    private class FakeNovelSource(
        override val id: Long = 1L,
    ) : NovelSource {
        override val name = "Test"
    }

    private class FakeNovelChapterRepository : NovelChapterRepository {
        val addedChapters = mutableListOf<NovelChapter>()
        val updatedChapters = mutableListOf<NovelChapterUpdate>()
        val removedIds = mutableListOf<Long>()
        var chapters = emptyList<NovelChapter>()

        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> {
            addedChapters.addAll(chapters)
            return chapters
        }

        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) {
            updatedChapters.add(chapterUpdate)
        }

        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) {
            updatedChapters.addAll(chapterUpdates)
        }

        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
            removedIds.addAll(chapterIds)
        }

        override suspend fun getChapterByNovelId(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): List<NovelChapter> = chapters

        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = chapters.mapNotNull { it.scanlator }

        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())

        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()

        override suspend fun getChapterById(id: Long): NovelChapter? = null

        override fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ) = throw UnsupportedOperationException()

        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null

        override suspend fun syncChapters(
            toAdd: List<NovelChapter>,
            toUpdate: List<NovelChapterUpdate>,
            toDelete: List<Long>,
        ): List<NovelChapter> {
            addedChapters.addAll(toAdd)
            updatedChapters.addAll(toUpdate)
            removedIds.addAll(toDelete)
            return toAdd
        }
    }
}
