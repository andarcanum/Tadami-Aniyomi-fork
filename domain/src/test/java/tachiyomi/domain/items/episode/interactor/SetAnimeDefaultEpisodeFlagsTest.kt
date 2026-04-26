package tachiyomi.domain.items.episode.interactor

import aniyomi.domain.anime.SeasonAnime
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.model.DeletableAnime

class SetAnimeDefaultEpisodeFlagsTest {

    @Test
    fun `awaitAll batches anime updates`() = runTest {
        val repository = FakeAnimeRepository(
            favorites = listOf(
                Anime.create().copy(id = 1L),
                Anime.create().copy(id = 2L),
            ),
        )
        val interactor = SetAnimeDefaultEpisodeFlags(
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            setAnimeEpisodeFlags = SetAnimeEpisodeFlags(repository),
            getFavorites = GetAnimeFavorites(repository),
        )

        interactor.awaitAll()

        repository.updateAllCalls shouldBe 1
        repository.updateCalls shouldBe 0
        repository.receivedUpdates.map { it.id } shouldBe listOf(1L, 2L)
        repository.receivedUpdates.map { it.episodeFlags } shouldBe
            listOf(expectedEpisodeFlags(), expectedEpisodeFlags())
    }

    private fun expectedEpisodeFlags(): Long {
        return 0L
            .setFlag(Anime.SHOW_ALL, Anime.EPISODE_UNSEEN_MASK)
            .setFlag(Anime.SHOW_ALL, Anime.EPISODE_DOWNLOADED_MASK)
            .setFlag(Anime.SHOW_ALL, Anime.EPISODE_BOOKMARKED_MASK)
            .setFlag(Anime.SHOW_ALL, Anime.EPISODE_FILLERMARKED_MASK)
            .setFlag(Anime.EPISODE_SORTING_NUMBER, Anime.EPISODE_SORTING_MASK)
            .setFlag(Anime.EPISODE_SORT_DESC, Anime.EPISODE_SORT_DIR_MASK)
            .setFlag(Anime.EPISODE_DISPLAY_NAME, Anime.EPISODE_DISPLAY_MASK)
            .setFlag(Anime.EPISODE_SHOW_PREVIEWS, Anime.EPISODE_PREVIEWS_MASK)
            .setFlag(Anime.EPISODE_SHOW_SUMMARIES, Anime.EPISODE_SUMMARIES_MASK)
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }

    private class FakeAnimeRepository(
        private val favorites: List<Anime>,
    ) : AnimeRepository {
        var updateCalls = 0
        var updateAllCalls = 0
        var receivedUpdates: List<AnimeUpdate> = emptyList()

        override suspend fun getAnimeById(id: Long): Anime = error("not used")
        override suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime> = error("not used")
        override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? = error("not used")
        override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> = error("not used")
        override suspend fun getAnimeFavorites(): List<Anime> = favorites
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
        override suspend fun insertAnime(anime: Anime): Long? = error("not used")
        override suspend fun updateAnime(update: AnimeUpdate): Boolean {
            updateCalls++
            return true
        }
        override suspend fun updateAllAnime(animeUpdates: List<AnimeUpdate>): Boolean {
            updateAllCalls++
            receivedUpdates = animeUpdates
            return true
        }
        override suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime> = error("not used")
        override fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>> = error("not used")
        override suspend fun removeParentIdByIds(animeIds: List<Long>) = error("not used")
        override fun getDeletableParentAnime(): Flow<List<DeletableAnime>> = error("not used")
        override suspend fun getChildrenByParentId(parentId: Long): List<Anime> = error("not used")
    }
}
