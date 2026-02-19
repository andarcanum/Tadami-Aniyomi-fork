package eu.kanade.domain.items.novelchapter.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository

class GetAvailableNovelScanlatorsTest {

    @Test
    fun `await trims and deduplicates scanlator values`() {
        runBlocking {
            val repository = FakeNovelChapterRepository(
                scanlators = listOf(" Team A ", "Team A", " Team B", "", "   "),
            )
            val interactor = GetAvailableNovelScanlators(repository)

            interactor.await(novelId = 1L) shouldBe setOf("Team A", "Team B")
        }
    }

    @Test
    fun `subscribe emits cleaned scanlator set`() {
        runBlocking {
            val repository = FakeNovelChapterRepository(
                scanlators = listOf("Team A ", " Team B"),
            )
            val interactor = GetAvailableNovelScanlators(repository)

            interactor.subscribe(novelId = 1L).first() shouldBe setOf("Team A", "Team B")
        }
    }

    private class FakeNovelChapterRepository(
        scanlators: List<String>,
    ) : NovelChapterRepository {
        private val scanlatorFlow = MutableStateFlow(scanlators)

        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) = Unit
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): List<NovelChapter> = emptyList()
        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = scanlatorFlow.value
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = scanlatorFlow
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
        override suspend fun getChapterById(id: Long): NovelChapter? = null
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
    }
}
