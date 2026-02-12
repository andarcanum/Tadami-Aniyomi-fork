package eu.kanade.domain.items.chapter.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository

class GetScanlatorChapterCountsTest {

    @Test
    fun `await counts chapters per non blank scanlator`() {
        runBlocking {
            val repository = FakeChapterRepository(
                chapters = listOf(
                    chapter(scanlator = "Team A"),
                    chapter(scanlator = "Team A"),
                    chapter(scanlator = "Team B"),
                    chapter(scanlator = ""),
                    chapter(scanlator = null),
                ),
            )
            val interactor = GetScanlatorChapterCounts(repository)

            interactor.await(mangaId = 1L) shouldBe mapOf(
                "Team A" to 2,
                "Team B" to 1,
            )
        }
    }

    @Test
    fun `subscribe updates grouped counts`() {
        runBlocking {
            val repository = FakeChapterRepository(
                chapters = listOf(
                    chapter(scanlator = "Team A"),
                    chapter(scanlator = "Team B"),
                ),
            )
            val interactor = GetScanlatorChapterCounts(repository)

            val initial = interactor.subscribe(mangaId = 1L).first()
            initial shouldBe mapOf(
                "Team A" to 1,
                "Team B" to 1,
            )
        }
    }

    private fun chapter(scanlator: String?): Chapter {
        return Chapter.create().copy(
            id = (scanlator?.hashCode() ?: 0).toLong(),
            mangaId = 1L,
            scanlator = scanlator,
            url = "/chapter/${scanlator.orEmpty()}",
            name = "Chapter",
            chapterNumber = 1.0,
        )
    }

    private class FakeChapterRepository(
        chapters: List<Chapter>,
    ) : ChapterRepository {
        private val flow = MutableStateFlow(chapters)

        override suspend fun addAllChapters(chapters: List<Chapter>): List<Chapter> = chapters
        override suspend fun updateChapter(chapterUpdate: ChapterUpdate) = Unit
        override suspend fun updateAllChapters(chapterUpdates: List<ChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> = flow.value
        override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> = flow.value.mapNotNull { it.scanlator }
        override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> = emptyList()
        override suspend fun getChapterById(id: Long): Chapter? = flow.value.firstOrNull { it.id == id }
        override suspend fun getChapterByMangaIdAsFlow(
            mangaId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<Chapter>> = flow
        override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
            return flow.value.firstOrNull { it.url == url && it.mangaId == mangaId }
        }
    }
}
