package tachiyomi.domain.entries.anime.interactor

import aniyomi.domain.anime.SeasonAnime
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.model.DeletableAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class NetworkToLocalAnimeTest {

    @Test
    fun `inserts anime when not found`() {
        runBlocking {
            val repository = FakeAnimeRepository()
            val interactor = NetworkToLocalAnime(repository, mockk<AnimeSourceManager>())

            val anime = Anime.create().copy(url = "/anime/1", title = "Example", source = 1L)
            val result = interactor.await(anime)

            result.id shouldBe 100L
            repository.inserted shouldBe anime
        }
    }

    @Test
    fun `returns persisted anime when insert returns null and second lookup exists`() {
        runBlocking {
            val persisted = Anime.create().copy(id = 42L, url = "/anime/1", title = "Persisted", source = 1L)
            val repository = FakeAnimeRepository(
                lookupResults = listOf(null, persisted),
                insertedId = null,
            )
            val interactor = NetworkToLocalAnime(repository, mockk<AnimeSourceManager>())

            val anime = Anime.create().copy(url = "/anime/1", title = "Remote", source = 1L)
            val result = interactor.await(anime)

            result.id shouldBe 42L
        }
    }

    @Test
    fun `throws when insert returns null and second lookup missing`() {
        runBlocking {
            val repository = FakeAnimeRepository(
                lookupResults = listOf(null, null),
                insertedId = null,
            )
            val interactor = NetworkToLocalAnime(repository, mockk<AnimeSourceManager>())

            val anime = Anime.create().copy(url = "/anime/1", title = "Remote", source = 1L)

            shouldThrow<IllegalStateException> {
                interactor.await(anime)
            }
        }
    }

    @Test
    fun `returns local when favorite`() {
        runBlocking {
            val repository = FakeAnimeRepository(
                existing = Anime.create().copy(
                    id = 10L,
                    url = "/anime/1",
                    title = "Local",
                    source = 1L,
                    favorite = true,
                ),
            )
            val interactor = NetworkToLocalAnime(repository, mockk<AnimeSourceManager>())

            val anime = Anime.create().copy(url = "/anime/1", title = "Remote", source = 1L)
            val result = interactor.await(anime)

            result.title shouldBe "Local"
            result.favorite shouldBe true
        }
    }

    @Test
    fun `updates title for non-favorite local`() {
        runBlocking {
            val repository = FakeAnimeRepository(
                existing = Anime.create().copy(
                    id = 10L,
                    url = "/anime/1",
                    title = "Local",
                    source = 1L,
                    favorite = false,
                ),
            )
            val interactor = NetworkToLocalAnime(repository, mockk<AnimeSourceManager>())

            val anime = Anime.create().copy(url = "/anime/1", title = "Remote", source = 1L)
            val result = interactor.await(anime)

            result.title shouldBe "Remote"
            result.favorite shouldBe false
        }
    }

    private class FakeAnimeRepository(
        private val existing: Anime? = null,
        lookupResults: List<Anime?> = emptyList(),
        private val insertedId: Long? = 100L,
    ) : AnimeRepository {
        private val lookupResults = lookupResults.toMutableList()
        var inserted: Anime? = null

        override suspend fun getAnimeById(id: Long): Anime = error("not used")
        override suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime> = error("not used")
        override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? {
            if (lookupResults.isNotEmpty()) {
                return lookupResults.removeAt(0)
            }
            return existing
        }
        override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> = error("not used")
        override suspend fun getAnimeFavorites(): List<Anime> = error("not used")
        override suspend fun getWatchedAnimeNotInLibrary(): List<Anime> = error("not used")
        override suspend fun getLibraryAnime(): List<LibraryAnime> = error("not used")
        override fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>> = error("not used")
        override fun getRecentLibraryAnime(limit: Long): Flow<List<LibraryAnime>> = error("not used")
        override fun getRecentFavorites(limit: Long): Flow<List<Anime>> = error("not used")
        override fun getAnimeFavoritesBySourceId(sourceId: Long): Flow<List<Anime>> = error("not used")
        override suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<Anime> = error("not used")
        override suspend fun getUpcomingAnime(statuses: Set<Long>): Flow<List<Anime>> = error("not used")
        override suspend fun resetAnimeViewerFlags(): Boolean = error("not used")
        override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) = error("not used")
        override suspend fun insertAnime(anime: Anime): Long? {
            inserted = anime
            return insertedId
        }
        override suspend fun updateAnime(update: tachiyomi.domain.entries.anime.model.AnimeUpdate): Boolean =
            error("not used")
        override suspend fun updateAllAnime(
            animeUpdates: List<tachiyomi.domain.entries.anime.model.AnimeUpdate>,
        ): Boolean = error("not used")
        override suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime> = error("not used")
        override fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>> = error("not used")
        override suspend fun removeParentIdByIds(animeIds: List<Long>) = error("not used")
        override fun getDeletableParentAnime(): Flow<List<DeletableAnime>> = error("not used")
        override suspend fun getChildrenByParentId(parentId: Long): List<Anime> = error("not used")
    }
}
