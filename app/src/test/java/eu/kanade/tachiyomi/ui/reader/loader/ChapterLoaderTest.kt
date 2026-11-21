package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

class ChapterLoaderTest {

    @Test
    fun `loadChapter recycles PageLoader and clears chapter pageLoader when getPages fails`(): Unit = runBlocking {
        val recycleCount = AtomicInteger(0)
        val failingLoader = object : PageLoader() {
            override var isLocal: Boolean = false
            override suspend fun getPages(): List<ReaderPage> {
                throw IOException("network failure")
            }

            override fun recycle() {
                super.recycle()
                recycleCount.incrementAndGet()
            }
        }

        val chapterLoader = ChapterLoader(
            context = mockk<Context>(relaxed = true),
            downloadManager = mockk<MangaDownloadManager>(relaxed = true),
            downloadProvider = mockk<MangaDownloadProvider>(relaxed = true),
            manga = Manga.create().copy(id = 1L),
            source = mockk<MangaSource>(relaxed = true),
            pageLoaderFactory = { failingLoader },
        )
        val chapter = ReaderChapter(
            Chapter.create().copy(id = 10L, mangaId = 1L, url = "https://example.org/ch-10"),
        )

        assertFailsWith<IOException> {
            chapterLoader.loadChapter(chapter)
        }

        failingLoader.isRecycled shouldBe true
        recycleCount.get() shouldBe 1
        chapter.pageLoader shouldBe null
        chapter.state.shouldBeInstanceOf<ReaderChapter.State.Error>()
    }

    @Test
    fun `loadChapter recycles PageLoader and clears chapter pageLoader when cancelled during getPages`(): Unit =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val recycleCount = AtomicInteger(0)
            val hangingLoader = object : PageLoader() {
                override var isLocal: Boolean = false
                override suspend fun getPages(): List<ReaderPage> {
                    started.complete(Unit)
                    gate.await()
                    return emptyList()
                }

                override fun recycle() {
                    super.recycle()
                    recycleCount.incrementAndGet()
                }
            }

            val chapterLoader = ChapterLoader(
                context = mockk<Context>(relaxed = true),
                downloadManager = mockk<MangaDownloadManager>(relaxed = true),
                downloadProvider = mockk<MangaDownloadProvider>(relaxed = true),
                manga = Manga.create().copy(id = 1L),
                source = mockk<MangaSource>(relaxed = true),
                pageLoaderFactory = { hangingLoader },
            )
            val chapter = ReaderChapter(
                Chapter.create().copy(id = 11L, mangaId = 1L, url = "https://example.org/ch-11"),
            )

            val job = launch {
                chapterLoader.loadChapter(chapter)
            }
            started.await()
            job.cancel(CancellationException("aborted"))
            job.join()

            hangingLoader.isRecycled shouldBe true
            recycleCount.get() shouldBe 1
            chapter.pageLoader shouldBe null
            chapter.state.shouldBeInstanceOf<ReaderChapter.State.Error>()
        }
}
