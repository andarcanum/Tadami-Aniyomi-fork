package tachiyomi.domain.book.novel.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.book.novel.model.NovelHighlight
import tachiyomi.domain.book.novel.model.NovelHighlightWithChapter
import tachiyomi.domain.book.novel.repository.NovelHighlightRepository

private fun highlight(id: Long = 0L, note: String = "", color: Long = 4294688813L) = NovelHighlight(
    id = id, novelId = 1L, chapterId = 2L, blockIndex = 3, charStart = 10, charEndExclusive = 20,
    normalizedText = "test", colorArgb = color, note = note, createdAt = 100L, updatedAt = 100L,
)

private class FakeNovelHighlightRepository : NovelHighlightRepository {
    val items = mutableListOf<NovelHighlight>()
    private val flow = MutableStateFlow<List<NovelHighlight>>(emptyList())
    private var nextId = 1L

    override fun subscribeForNovel(novelId: Long) = flow
    override fun subscribeForChapter(chapterId: Long) = flow
    override fun subscribeAll(): Flow<List<NovelHighlightWithChapter>> = flowOf(emptyList())
    override suspend fun getForNovel(novelId: Long) = items.filter { it.novelId == novelId }
    override suspend fun add(highlight: NovelHighlight): Long {
        val stored = highlight.copy(id = nextId++)
        items += stored
        flow.value = items.toList()
        return stored.id
    }

    override suspend fun updateNoteAndColor(highlightId: Long, note: String, colorArgb: Long, updatedAt: Long) {
        items.replaceAll {
            if (it.id ==
                highlightId
            ) {
                it.copy(note = note, colorArgb = colorArgb, updatedAt = updatedAt)
            } else {
                it
            }
        }
        flow.value = items.toList()
    }

    override suspend fun reanchor(
        highlightId: Long,
        blockIndex: Int,
        charStart: Int,
        charEndExclusive: Int,
        updatedAt: Long,
    ) {
        items.replaceAll {
            if (it.id == highlightId) {
                it.copy(
                    blockIndex = blockIndex,
                    charStart = charStart,
                    charEndExclusive = charEndExclusive,
                    updatedAt = updatedAt,
                )
            } else {
                it
            }
        }
        flow.value = items.toList()
    }

    override suspend fun delete(highlightId: Long) {
        items.removeAll { it.id == highlightId }
        flow.value = items.toList()
    }
}

class NovelHighlightInteractorTest {

    @Test
    fun `add returns generated id and stores highlight`() = runTest {
        val repo = FakeNovelHighlightRepository()
        val id = AddNovelHighlight(repo).await(highlight())
        id shouldBe 1L
        repo.items.single().normalizedText shouldBe "test"
    }

    @Test
    fun `update changes note and color keeping identity`() = runTest {
        val repo = FakeNovelHighlightRepository()
        val id = AddNovelHighlight(repo).await(highlight())
        UpdateNovelHighlight(repo).await(id, note = "my note", colorArgb = 0xFF90CAF9, updatedAt = 200L)
        repo.items.single().let {
            it.note shouldBe "my note"
            it.colorArgb shouldBe 0xFF90CAF9
            it.updatedAt shouldBe 200L
            it.charStart shouldBe 10
        }
    }

    @Test
    fun `delete removes only target highlight`() = runTest {
        val repo = FakeNovelHighlightRepository()
        val add = AddNovelHighlight(repo)
        val first = add.await(highlight())
        val second = add.await(highlight())
        DeleteNovelHighlight(repo).await(first)
        repo.items.map { it.id } shouldBe listOf(second)
    }

    @Test
    fun `get returns all highlights of novel`() = runTest {
        val repo = FakeNovelHighlightRepository()
        val add = AddNovelHighlight(repo)
        add.await(highlight())
        add.await(highlight())
        GetNovelHighlights(repo).await(1L).size shouldBe 2
        GetNovelHighlights(repo).await(99L).size shouldBe 0
    }

    @Test
    fun `subscribeAll delegates to repository`() = runTest {
        val interactor = GetAllNovelHighlights(FakeNovelHighlightRepository())

        interactor.subscribeAll().first() shouldBe emptyList()
    }
}
