package tachiyomi.domain.entries.manga.interactor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga

class NetworkToLocalMangaTest {

    @Test
    fun `inserts manga when not found`() {
        runBlocking {
            val repository = FakeMangaRepository()
            val interactor = NetworkToLocalManga(repository)

            val manga = Manga.create().copy(url = "/manga/1", title = "Example", source = 1L)
            val result = interactor.await(manga)

            result.id shouldBe 100L
            repository.inserted shouldBe manga
        }
    }

    @Test
    fun `returns persisted manga when insert returns null and second lookup exists`() {
        runBlocking {
            val persisted = Manga.create().copy(id = 42L, url = "/manga/1", title = "Persisted", source = 1L)
            val repository = FakeMangaRepository(
                lookupResults = listOf(null, persisted),
                insertedId = null,
            )
            val interactor = NetworkToLocalManga(repository)

            val manga = Manga.create().copy(url = "/manga/1", title = "Remote", source = 1L)
            val result = interactor.await(manga)

            result.id shouldBe 42L
        }
    }

    @Test
    fun `throws when insert returns null and second lookup missing`() {
        runBlocking {
            val repository = FakeMangaRepository(
                lookupResults = listOf(null, null),
                insertedId = null,
            )
            val interactor = NetworkToLocalManga(repository)

            val manga = Manga.create().copy(url = "/manga/1", title = "Remote", source = 1L)

            shouldThrow<IllegalStateException> {
                interactor.await(manga)
            }
        }
    }

    @Test
    fun `returns local when favorite`() {
        runBlocking {
            val repository = FakeMangaRepository(
                existing = Manga.create().copy(
                    id = 10L,
                    url = "/manga/1",
                    title = "Local",
                    source = 1L,
                    favorite = true,
                ),
            )
            val interactor = NetworkToLocalManga(repository)

            val manga = Manga.create().copy(url = "/manga/1", title = "Remote", source = 1L)
            val result = interactor.await(manga)

            result.title shouldBe "Local"
            result.favorite shouldBe true
        }
    }

    @Test
    fun `updates title for non-favorite local`() {
        runBlocking {
            val repository = FakeMangaRepository(
                existing = Manga.create().copy(
                    id = 10L,
                    url = "/manga/1",
                    title = "Local",
                    source = 1L,
                    favorite = false,
                ),
            )
            val interactor = NetworkToLocalManga(repository)

            val manga = Manga.create().copy(url = "/manga/1", title = "Remote", source = 1L)
            val result = interactor.await(manga)

            result.title shouldBe "Remote"
            result.favorite shouldBe false
        }
    }

    private class FakeMangaRepository(
        private val existing: Manga? = null,
        lookupResults: List<Manga?> = emptyList(),
        private val insertedId: Long? = 100L,
    ) : MangaRepository {
        private val lookupResults = lookupResults.toMutableList()
        var inserted: Manga? = null

        override suspend fun getMangaById(id: Long): Manga = error("not used")
        override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> = error("not used")
        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
            if (lookupResults.isNotEmpty()) {
                return lookupResults.removeAt(0)
            }
            return existing
        }
        override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> = error("not used")
        override suspend fun getMangaFavorites(): List<Manga> = error("not used")
        override suspend fun getReadMangaNotInLibrary(): List<Manga> = error("not used")
        override suspend fun getLibraryManga(): List<LibraryManga> = error("not used")
        override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> = error("not used")
        override fun getMangaFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> = error("not used")
        override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Manga> = error("not used")
        override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> = error("not used")
        override suspend fun resetMangaViewerFlags(): Boolean = error("not used")
        override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) = error("not used")
        override suspend fun insertManga(manga: Manga): Long? {
            inserted = manga
            return insertedId
        }
        override suspend fun updateManga(update: tachiyomi.domain.entries.manga.model.MangaUpdate): Boolean =
            error("not used")
        override suspend fun updateAllManga(
            mangaUpdates: List<tachiyomi.domain.entries.manga.model.MangaUpdate>,
        ): Boolean = error("not used")
    }
}
