package tachiyomi.domain.entries.novel.interactor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.library.novel.LibraryNovel

class NetworkToLocalNovelTest {

    @Test
    fun `inserts novel when not found`() {
        runBlocking {
            val repository = FakeNovelRepository()
            val interactor = NetworkToLocalNovel(repository)

            val novel = Novel.create().copy(url = "/novel/1", title = "Example", source = 1L)
            val result = interactor.await(novel)

            result.id shouldBe 100L
            repository.inserted shouldBe novel
        }
    }

    @Test
    fun `returns persisted novel when insert returns null and second lookup exists`() {
        runBlocking {
            val persisted = Novel.create().copy(id = 42L, url = "/novel/1", title = "Persisted", source = 1L)
            val repository = FakeNovelRepository(
                lookupResults = listOf(null, persisted),
                insertedId = null,
            )
            val interactor = NetworkToLocalNovel(repository)

            val novel = Novel.create().copy(url = "/novel/1", title = "Remote", source = 1L)
            val result = interactor.await(novel)

            result.id shouldBe 42L
        }
    }

    @Test
    fun `throws when insert returns null and second lookup missing`() {
        runBlocking {
            val repository = FakeNovelRepository(
                lookupResults = listOf(null, null),
                insertedId = null,
            )
            val interactor = NetworkToLocalNovel(repository)

            val novel = Novel.create().copy(url = "/novel/1", title = "Remote", source = 1L)

            shouldThrow<IllegalStateException> {
                interactor.await(novel)
            }
        }
    }

    @Test
    fun `returns local when favorite`() {
        runBlocking {
            val repository = FakeNovelRepository(
                existing = Novel.create().copy(id = 10L, url = "/novel/1", title = "Local", source = 1L, favorite = true),
            )
            val interactor = NetworkToLocalNovel(repository)

            val novel = Novel.create().copy(url = "/novel/1", title = "Remote", source = 1L)
            val result = interactor.await(novel)

            result.title shouldBe "Local"
            result.favorite shouldBe true
        }
    }

    @Test
    fun `updates title for non-favorite local`() {
        runBlocking {
            val repository = FakeNovelRepository(
                existing = Novel.create().copy(id = 10L, url = "/novel/1", title = "Local", source = 1L, favorite = false),
            )
            val interactor = NetworkToLocalNovel(repository)

            val novel = Novel.create().copy(url = "/novel/1", title = "Remote", source = 1L)
            val result = interactor.await(novel)

            result.title shouldBe "Remote"
            result.favorite shouldBe false
        }
    }

    private class FakeNovelRepository(
        private val existing: Novel? = null,
        lookupResults: List<Novel?> = emptyList(),
        private val insertedId: Long? = 100L,
    ) : NovelRepository {
        private val lookupResults = lookupResults.toMutableList()
        var inserted: Novel? = null

        override suspend fun getNovelById(id: Long): Novel = error("not used")
        override suspend fun getNovelByIdAsFlow(id: Long) = error("not used")
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? {
            if (lookupResults.isNotEmpty()) {
                return lookupResults.removeAt(0)
            }
            return existing
        }
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = error("not used")
        override suspend fun getNovelFavorites(): List<Novel> = error("not used")
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = error("not used")
        override suspend fun getLibraryNovel(): List<LibraryNovel> = error("not used")
        override fun getLibraryNovelAsFlow() = error("not used")
        override fun getNovelFavoritesBySourceId(sourceId: Long) = error("not used")
        override suspend fun insertNovel(novel: Novel): Long? {
            inserted = novel
            return insertedId
        }
        override suspend fun updateNovel(update: tachiyomi.domain.entries.novel.model.NovelUpdate): Boolean =
            error("not used")
        override suspend fun updateAllNovel(
            novelUpdates: List<tachiyomi.domain.entries.novel.model.NovelUpdate>,
        ): Boolean = error("not used")
        override suspend fun resetNovelViewerFlags(): Boolean = error("not used")
    }
}
