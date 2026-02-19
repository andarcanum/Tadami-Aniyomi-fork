package eu.kanade.domain.entries.novel.interactor

import eu.kanade.tachiyomi.novelsource.model.SNovel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository

class UpdateNovelTest {

    @Test
    fun `update novel delegates to repository`() {
        runTest {
            val repository = FakeNovelRepository()
            val interactor = UpdateNovel(repository)
            val update = NovelUpdate(id = 1L, title = "New")

            interactor.await(update) shouldBe true

            repository.lastUpdate shouldBe update
        }
    }

    @Test
    fun `update all novels delegates to repository`() {
        runTest {
            val repository = FakeNovelRepository()
            val interactor = UpdateNovel(repository)
            val updates = listOf(
                NovelUpdate(id = 1L, title = "A"),
                NovelUpdate(id = 2L, title = "B"),
            )

            interactor.awaitAll(updates) shouldBe true

            repository.lastUpdate shouldBe updates.last()
        }
    }

    @Test
    fun `awaitUpdateFromSource sanitizes html description and normalizes genres`() {
        runTest {
            val repository = FakeNovelRepository()
            val interactor = UpdateNovel(repository)
            val local = Novel.create().copy(id = 10L, title = "Local", source = 1L)
            val remote = SNovel.create().apply {
                url = "/novel/1"
                title = "Remote"
                author = "Author"
                description = "<p>Hello <b>world</b></p><p>&nbsp;line two</p>"
                genre = "Action;Drama|Fantasy/School,Romance\nMystery"
                status = SNovel.ONGOING
            }

            interactor.awaitUpdateFromSource(local, remote, manualFetch = true) shouldBe true

            repository.lastUpdate?.description shouldBe "Hello world line two"
            repository.lastUpdate?.genre shouldBe listOf(
                "Action",
                "Drama",
                "Fantasy",
                "School",
                "Romance",
                "Mystery",
            )
        }
    }

    private class FakeNovelRepository : NovelRepository {
        var lastUpdate: NovelUpdate? = null
        private val novelFlow = kotlinx.coroutines.flow.MutableStateFlow(Novel.create())
        private val novelUrlFlow = kotlinx.coroutines.flow.MutableStateFlow<Novel?>(null)
        private val libraryFlow = kotlinx.coroutines.flow.MutableStateFlow(
            emptyList<tachiyomi.domain.library.novel.LibraryNovel>(),
        )
        private val favoritesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList<Novel>())

        override suspend fun getNovelById(id: Long): Novel = Novel.create()

        override suspend fun getNovelByIdAsFlow(id: Long) = novelFlow

        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null

        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = novelUrlFlow

        override suspend fun getNovelFavorites(): List<Novel> = emptyList()

        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()

        override suspend fun getLibraryNovel() = emptyList<tachiyomi.domain.library.novel.LibraryNovel>()

        override fun getLibraryNovelAsFlow() = libraryFlow

        override fun getNovelFavoritesBySourceId(sourceId: Long) = favoritesFlow

        override suspend fun insertNovel(novel: Novel): Long? = null

        override suspend fun updateNovel(update: NovelUpdate): Boolean {
            lastUpdate = update
            return true
        }

        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean {
            lastUpdate = novelUpdates.lastOrNull()
            return true
        }

        override suspend fun resetNovelViewerFlags(): Boolean = true
    }
}
