package eu.kanade.tachiyomi.data.prefetch

import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderChapterPrefetchCache
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class ContentPrefetchServiceTest {

    @AfterEach
    fun tearDown() {
        NovelReaderChapterPrefetchCache.clear()
    }

    @Test
    fun `prefetch skips when gates are closed`() = runBlocking {
        val service = ContentPrefetchService(
            environment = FakeEnvironment(powerSaveMode = true, hasNetwork = true),
        )
        val novel = createNovel()
        val chapter = createChapter(id = 2L)
        val source = mockk<NovelSource>(relaxed = true)
        val downloadManager = mockk<NovelDownloadManager>(relaxed = true)

        val result = service.prefetchNovelChapterText(
            prefetchEnabled = true,
            novel = novel,
            chapter = chapter,
            source = source,
            downloadManager = downloadManager,
            cacheReadChapters = false,
        )

        result shouldBe false
        coVerify(exactly = 0) { source.getChapterText(any()) }
    }

    @Test
    fun `prefetch suppresses duplicate in flight requests`() = runBlocking {
        val service = ContentPrefetchService(environment = FakeEnvironment())
        val novel = createNovel()
        val chapter = createChapter(id = 3L)
        val downloadManager = mockk<NovelDownloadManager>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        val source = mockk<NovelSource>()

        coEvery { downloadManager.getDownloadedChapterText(novel, chapter.id) } returns null
        coEvery { source.getChapterText(any()) } coAnswers {
            gate.await()
            "<p>Chapter</p>"
        }

        val first = async {
            service.prefetchNovelChapterText(
                prefetchEnabled = true,
                novel = novel,
                chapter = chapter,
                source = source,
                downloadManager = downloadManager,
                cacheReadChapters = false,
            )
        }

        yield()

        val second = service.prefetchNovelChapterText(
            prefetchEnabled = true,
            novel = novel,
            chapter = chapter,
            source = source,
            downloadManager = downloadManager,
            cacheReadChapters = false,
        )

        second shouldBe false
        gate.complete(Unit)
        first.await() shouldBe true
        coVerify(exactly = 1) { source.getChapterText(any()) }
    }

    @Test
    fun `resolve uses prefetched cache before fetching source`() = runBlocking {
        val service = ContentPrefetchService()
        val novel = createNovel()
        val chapter = createChapter(id = 4L)
        val downloadManager = mockk<NovelDownloadManager>(relaxed = true)
        val source = mockk<NovelSource>(relaxed = true)

        NovelReaderChapterPrefetchCache.put(chapter.id, "<p>Cached</p>")
        coEvery { downloadManager.getDownloadedChapterText(novel, chapter.id) } returns null

        val html = service.resolveNovelChapterText(
            novel = novel,
            chapter = chapter,
            source = source,
            downloadManager = downloadManager,
            cacheReadChapters = false,
        )

        html shouldBe "<p>Cached</p>"
        coVerify(exactly = 0) { source.getChapterText(any()) }
    }

    private fun createNovel(): Novel {
        return Novel.create().copy(
            id = 1L,
            source = 11L,
            title = "Novel",
        )
    }

    private fun createChapter(id: Long): NovelChapter {
        return NovelChapter.create().copy(
            id = id,
            novelId = 1L,
            name = "Chapter $id",
            url = "https://example.org/chapter-$id",
        )
    }

    private class FakeEnvironment(
        private val powerSaveMode: Boolean = false,
        private val hasNetwork: Boolean = true,
    ) : ContentPrefetchEnvironment {
        override fun isPowerSaveMode(): Boolean = powerSaveMode
        override fun hasActiveNetwork(): Boolean = hasNetwork
    }
}
