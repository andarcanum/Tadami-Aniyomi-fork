package tachiyomi.domain.updates.novel.interactor

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.updates.novel.model.NovelUpdatesWithRelations
import tachiyomi.domain.updates.novel.repository.NovelUpdatesRepository
import java.time.Instant

class GetNovelUpdatesTest {

    @Test
    fun `await delegates read query with fixed limit`() = runTest {
        val expected = listOf(sampleUpdate())
        val repo = FakeNovelUpdatesRepository(
            awaitResult = expected,
            subscribeAllFlow = MutableStateFlow(emptyList()),
            subscribeWithReadFlow = MutableStateFlow(emptyList()),
        )
        val interactor = GetNovelUpdates(repo)

        val result = interactor.await(read = false, after = 123L)

        result shouldContainExactly expected
        repo.awaitCalls shouldContainExactly listOf(Triple(false, 123L, 500L))
    }

    @Test
    fun `subscribe with instant delegates epoch millis with fixed limit`() = runTest {
        val expected = listOf(sampleUpdate())
        val repo = FakeNovelUpdatesRepository(
            awaitResult = emptyList(),
            subscribeAllFlow = MutableStateFlow(expected),
            subscribeWithReadFlow = MutableStateFlow(emptyList()),
        )
        val interactor = GetNovelUpdates(repo)
        val instant = Instant.ofEpochMilli(9876L)

        val result = interactor.subscribe(instant).first()

        result shouldContainExactly expected
        repo.subscribeAllCalls shouldContainExactly listOf(9876L to 500L)
    }

    private fun sampleUpdate() = NovelUpdatesWithRelations(
        novelId = 1L,
        novelTitle = "Novel",
        chapterId = 2L,
        chapterName = "Chapter 1",
        scanlator = null,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        sourceId = 3L,
        dateFetch = 10L,
        coverData = NovelCover(
            novelId = 1L,
            sourceId = 3L,
            isNovelFavorite = true,
            url = "https://example.com/cover.jpg",
            lastModified = 0L,
        ),
    )

    private class FakeNovelUpdatesRepository(
        private val awaitResult: List<NovelUpdatesWithRelations>,
        private val subscribeAllFlow: MutableStateFlow<List<NovelUpdatesWithRelations>>,
        private val subscribeWithReadFlow: MutableStateFlow<List<NovelUpdatesWithRelations>>,
    ) : NovelUpdatesRepository {
        val awaitCalls = mutableListOf<Triple<Boolean, Long, Long>>()
        val subscribeAllCalls = mutableListOf<Pair<Long, Long>>()

        override suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<NovelUpdatesWithRelations> {
            awaitCalls += Triple(read, after, limit)
            return awaitResult
        }

        override fun subscribeAllNovelUpdates(after: Long, limit: Long): Flow<List<NovelUpdatesWithRelations>> {
            subscribeAllCalls += after to limit
            return subscribeAllFlow
        }

        override fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<NovelUpdatesWithRelations>> {
            return subscribeWithReadFlow
        }
    }
}
