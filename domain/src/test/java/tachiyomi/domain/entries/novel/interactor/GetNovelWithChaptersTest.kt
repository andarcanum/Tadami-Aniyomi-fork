package tachiyomi.domain.entries.novel.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository

class GetNovelWithChaptersTest {

    @Test
    fun `subscribe returns novel and chapters`() = runTest {
        val novelFlow = MutableStateFlow(Novel.create().copy(id = 10L, title = "Title"))
        val chaptersFlow = MutableStateFlow(
            listOf(
                NovelChapter.create().copy(id = 1L, novelId = 10L, name = "Chapter 1"),
            ),
        )
        val repository = FakeNovelRepository(novelFlow)
        val chapterRepository = FakeNovelChapterRepository(chaptersFlow)

        val interactor = GetNovelWithChapters(repository, chapterRepository)

        val result = interactor.subscribe(10L).first()

        result.first.id shouldBe 10L
        result.second.size shouldBe 1
    }

    @Test
    fun `awaitManga returns novel from repository`() = runTest {
        val novel = Novel.create().copy(id = 20L, title = "Result")
        val repository = FakeNovelRepository(MutableStateFlow(novel))
        val chapterRepository = FakeNovelChapterRepository(MutableStateFlow(emptyList()))
        val interactor = GetNovelWithChapters(repository, chapterRepository)

        interactor.awaitNovel(20L) shouldBe novel
    }

    private class FakeNovelRepository(
        private val novelFlow: MutableStateFlow<Novel>,
    ) : NovelRepository {
        override suspend fun getNovelById(id: Long): Novel = novelFlow.value
        override suspend fun getNovelByIdAsFlow(id: Long) = novelFlow
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) =
            MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel() = emptyList<tachiyomi.domain.library.novel.LibraryNovel>()
        override fun getLibraryNovelAsFlow() = MutableStateFlow(
            emptyList<tachiyomi.domain.library.novel.LibraryNovel>(),
        )
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = null
        override suspend fun updateNovel(update: tachiyomi.domain.entries.novel.model.NovelUpdate): Boolean = true
        override suspend fun updateAllNovel(
            novelUpdates: List<tachiyomi.domain.entries.novel.model.NovelUpdate>,
        ): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelChapterRepository(
        private val chaptersFlow: MutableStateFlow<List<NovelChapter>>,
    ) : NovelChapterRepository {
        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        override suspend fun updateChapter(
            chapterUpdate: tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate,
        ) =
            Unit
        override suspend fun updateAllChapters(
            chapterUpdates: List<tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate>,
        ) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): List<NovelChapter> = chaptersFlow.value
        override suspend fun getScanlatorsByNovelId(
            novelId: Long,
        ): List<String> = chaptersFlow.value.mapNotNull { it.scanlator }
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
        override suspend fun getChapterById(id: Long): NovelChapter? = null
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ) = chaptersFlow
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
    }
}
