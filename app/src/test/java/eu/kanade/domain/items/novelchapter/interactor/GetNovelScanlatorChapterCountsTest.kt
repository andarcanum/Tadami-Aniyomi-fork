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

class GetNovelScanlatorChapterCountsTest {

    @Test
    fun `await counts chapters per non blank scanlator`() {
        runBlocking {
            val repository = FakeNovelChapterRepository(
                chapters = listOf(
                    chapter(scanlator = "Team A"),
                    chapter(scanlator = "Team A"),
                    chapter(scanlator = "Team B"),
                    chapter(scanlator = ""),
                    chapter(scanlator = null),
                ),
            )
            val interactor = GetNovelScanlatorChapterCounts(repository)

            interactor.await(novelId = 1L) shouldBe mapOf(
                "Team A" to 2,
                "Team B" to 1,
            )
        }
    }

    @Test
    fun `subscribe updates grouped counts`() {
        runBlocking {
            val repository = FakeNovelChapterRepository(
                chapters = listOf(
                    chapter(scanlator = "Team A"),
                    chapter(scanlator = "Team B"),
                ),
            )
            val interactor = GetNovelScanlatorChapterCounts(repository)

            val initial = interactor.subscribe(novelId = 1L).first()
            initial shouldBe mapOf(
                "Team A" to 1,
                "Team B" to 1,
            )
        }
    }

    private fun chapter(scanlator: String?): NovelChapter {
        return NovelChapter.create().copy(
            id = (scanlator?.hashCode() ?: 0).toLong(),
            novelId = 1L,
            scanlator = scanlator,
            url = "/chapter/${scanlator.orEmpty()}",
            name = "Chapter",
            chapterNumber = 1.0,
        )
    }

    private class FakeNovelChapterRepository(
        chapters: List<NovelChapter>,
    ) : NovelChapterRepository {
        private val flow = MutableStateFlow(chapters)

        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) = Unit
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): List<NovelChapter> = flow.value
        override suspend fun getScanlatorsByNovelId(
            novelId: Long,
        ): List<String> = flow.value.mapNotNull { it.scanlator }
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
        override suspend fun getChapterById(id: Long): NovelChapter? = flow.value.firstOrNull { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = flow
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? {
            return flow.value.firstOrNull { it.url == url && it.novelId == novelId }
        }
    }
}
