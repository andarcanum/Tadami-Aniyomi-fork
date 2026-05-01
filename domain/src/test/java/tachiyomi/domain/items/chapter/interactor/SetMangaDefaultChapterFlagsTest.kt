package tachiyomi.domain.items.chapter.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences

class SetMangaDefaultChapterFlagsTest {

    @Test
    fun `awaitAll batches manga updates`() = runTest {
        val repository = FakeMangaRepository(
            favorites = listOf(
                Manga.create().copy(id = 1L),
                Manga.create().copy(id = 2L),
            ),
        )
        val interactor = SetMangaDefaultChapterFlags(
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            setMangaChapterFlags = SetMangaChapterFlags(repository),
            getFavorites = GetMangaFavorites(repository),
        )

        interactor.awaitAll()

        repository.updateAllCalls shouldBe 1
        repository.updateCalls shouldBe 0
        repository.receivedUpdates.map { it.id } shouldBe listOf(1L, 2L)
        repository.receivedUpdates.map { it.chapterFlags } shouldBe
            listOf(expectedChapterFlags(), expectedChapterFlags())
    }

    private fun expectedChapterFlags(): Long {
        return 0L
            .setFlag(Manga.SHOW_ALL, Manga.CHAPTER_UNREAD_MASK)
            .setFlag(Manga.SHOW_ALL, Manga.CHAPTER_DOWNLOADED_MASK)
            .setFlag(Manga.SHOW_ALL, Manga.CHAPTER_BOOKMARKED_MASK)
            .setFlag(Manga.CHAPTER_SORTING_SOURCE, Manga.CHAPTER_SORTING_MASK)
            .setFlag(Manga.CHAPTER_SORT_ASC, Manga.CHAPTER_SORT_DIR_MASK)
            .setFlag(Manga.CHAPTER_DISPLAY_NAME, Manga.CHAPTER_DISPLAY_MASK)
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }

    private class FakeMangaRepository(
        private val favorites: List<Manga>,
    ) : MangaRepository {
        var updateCalls = 0
        var updateAllCalls = 0
        var receivedUpdates: List<MangaUpdate> = emptyList()

        override suspend fun getMangaById(id: Long): Manga = error("not used")
        override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> = error("not used")
        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? = error("not used")
        override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> = error("not used")
        override suspend fun getMangaFavorites(): List<Manga> = favorites
        override suspend fun getReadMangaNotInLibrary(): List<Manga> = error("not used")
        override suspend fun getLibraryManga(): List<LibraryManga> = error("not used")
        override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> = error("not used")
        override fun getMangaFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> = error("not used")
        override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Manga> = error("not used")
        override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> = error("not used")
        override suspend fun resetMangaViewerFlags(): Boolean = error("not used")
        override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) = error("not used")
        override suspend fun insertManga(manga: Manga): Long? = error("not used")
        override suspend fun updateManga(update: MangaUpdate): Boolean {
            updateCalls++
            return true
        }
        override suspend fun updateAllManga(mangaUpdates: List<MangaUpdate>): Boolean {
            updateAllCalls++
            receivedUpdates = mangaUpdates
            return true
        }
    }
}
