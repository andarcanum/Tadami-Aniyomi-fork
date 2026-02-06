package tachiyomi.domain.entries.novel.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.library.novel.LibraryNovel

class NovelInteractorsTest {

    @Test
    fun `get novel returns value and handles error`() = runTest {
        val novel = Novel.create().copy(id = 1L, title = "Novel")
        val repository = FakeNovelRepository(novelById = novel)
        val interactor = GetNovel(repository)

        interactor.await(1L) shouldBe novel

        val failing = FakeNovelRepository(throwOnGetById = true)
        GetNovel(failing).await(1L) shouldBe null
    }

    @Test
    fun `get novel subscription returns flow`() = runTest {
        val novel = Novel.create().copy(id = 2L, title = "Flow")
        val repository = FakeNovelRepository(novelById = novel)
        val interactor = GetNovel(repository)

        interactor.subscribe(2L).first() shouldBe novel
    }

    @Test
    fun `get novel by url and source returns value`() = runTest {
        val novel = Novel.create().copy(id = 3L, title = "Url")
        val repository = FakeNovelRepository(novelByUrl = novel)

        val result = GetNovelByUrlAndSourceId(repository).await("/novel", 1L)

        result shouldBe novel
    }

    @Test
    fun `get library novel returns list`() = runTest {
        val novel = Novel.create().copy(id = 4L, title = "Library")
        val library = listOf(
            LibraryNovel(
                novel = novel,
                category = 0L,
                totalChapters = 1L,
                readCount = 0L,
                bookmarkCount = 0L,
                latestUpload = 0L,
                chapterFetchedAt = 0L,
                lastRead = 0L,
            ),
        )
        val repository = FakeNovelRepository(library = library)

        GetLibraryNovel(repository).await() shouldBe library
    }

    @Test
    fun `set novel chapter flags updates repository`() = runTest {
        val novel = Novel.create().copy(id = 5L, chapterFlags = 0L)
        val repository = FakeNovelRepository(novelById = novel)
        val interactor = SetNovelChapterFlags(repository)

        interactor.awaitSetUnreadFilter(novel, Novel.CHAPTER_SHOW_UNREAD) shouldBe true

        repository.lastUpdate?.chapterFlags shouldBe Novel.CHAPTER_SHOW_UNREAD
    }

    @Test
    fun `reset novel viewer flags delegates to repository`() = runTest {
        val repository = FakeNovelRepository()

        ResetNovelViewerFlags(repository).await() shouldBe true
        repository.resetCalled shouldBe true
    }

    private class FakeNovelRepository(
        private val novelById: Novel? = null,
        private val novelByUrl: Novel? = null,
        private val library: List<LibraryNovel> = emptyList(),
        private val throwOnGetById: Boolean = false,
    ) : NovelRepository {
        private val novelFlow = MutableStateFlow(novelById ?: Novel.create())
        private val novelUrlFlow = MutableStateFlow(novelByUrl)
        private val libraryFlow = MutableStateFlow(library)

        var lastUpdate: NovelUpdate? = null
        var resetCalled = false

        override suspend fun getNovelById(id: Long): Novel {
            if (throwOnGetById) error("boom")
            return checkNotNull(novelById)
        }

        override suspend fun getNovelByIdAsFlow(id: Long) = novelFlow

        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long) = novelByUrl

        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = novelUrlFlow

        override suspend fun getNovelFavorites(): List<Novel> = emptyList()

        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()

        override suspend fun getLibraryNovel(): List<LibraryNovel> = library

        override fun getLibraryNovelAsFlow() = libraryFlow

        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())

        override suspend fun insertNovel(novel: Novel): Long? = novel.id

        override suspend fun updateNovel(update: NovelUpdate): Boolean {
            lastUpdate = update
            return true
        }

        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean {
            lastUpdate = novelUpdates.lastOrNull()
            return true
        }

        override suspend fun resetNovelViewerFlags(): Boolean {
            resetCalled = true
            return true
        }
    }
}
