package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderPreloadManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class HttpPageLoaderRecoveryTest {

    @BeforeEach
    fun setUpPreloadManager() {
        mockkObject(ReaderPreloadManager)
        every { ReaderPreloadManager.dynamicPreloadPagesAfter } returns 1
    }

    @AfterEach
    fun tearDownPreloadManager() {
        unmockkObject(ReaderPreloadManager)
    }

    private fun createResponse(url: String, payload: ByteArray = ByteArray(16) { 1 }): Response {
        return Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(payload.toResponseBody("image/png".toMediaType()))
            .build()
    }

    private fun createReaderPreferences(): ReaderPreferences {
        return mockk<ReaderPreferences>().also { prefs ->
            every { prefs.preloadPagesBefore() } returns mockk { every { get() } returns 1 }
            every { prefs.webtoonSmartFit() } returns mockk { every { get() } returns false }
            every { prefs.cropBordersWebtoon() } returns mockk { every { get() } returns false }
        }
    }

    private fun createSourcePreferences(): SourcePreferences {
        return mockk<SourcePreferences>().also { prefs ->
            every { prefs.dataSaver() } returns mockk {
                every { get() } returns SourcePreferences.DataSaver.NONE
            }
        }
    }

    @Test
    fun `cancelling one overlapping loadPage keeps page queued for remaining subscriber`(): Unit = runBlocking {
        val blocker = CompletableDeferred<Unit>()
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getImage(any()) } coAnswers {
            val p = firstArg<Page>()
            if (p.index < 3) {
                blocker.await()
            }
            createResponse(p.imageUrl!!)
        }

        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.isImageInCache(any()) } returns false
        justRun { chapterCache.putImageToCache(any(), any()) }
        every { chapterCache.openImageStream(any()) } answers { ByteArrayInputStream(ByteArray(16)) }
        every { chapterCache.removeImageFromCache(any()) } returns true
        justRun { chapterCache.putPageListToCache(any(), any()) }

        val chapter = ReaderChapter(
            Chapter.create().copy(id = 10L, mangaId = 1L, url = "https://example.org/ch-10"),
        )
        // Use 12 pages so target page 10 lies outside the preload window of busy pages 0..2.
        val pages = (0..11).map { idx ->
            ReaderPage(idx, "https://example.org/p$idx", "https://example.org/img-$idx.png").also {
                it.chapter = chapter
            }
        }
        chapter.state = ReaderChapter.State.Loaded(pages)

        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = createSourcePreferences(),
            readerPreferences = createReaderPreferences(),
        )

        try {
            // Occupy all 3 worker slots with pages 0..2 so page 10 stays queued in the priority queue.
            val busyJobs = (0..2).map { i -> launch { loader.loadPage(pages[i]) } }
            withTimeout(5_000L) {
                (0..2).forEach { i ->
                    pages[i].statusFlow.first { it == Page.State.DOWNLOAD_IMAGE }
                }
            }

            // Two holders subscribe to page 10 concurrently; cancelling the first must not cancel the second.
            val firstSubscriber = launch { loader.loadPage(pages[10]) }
            val secondSubscriber = launch { loader.loadPage(pages[10]) }
            kotlinx.coroutines.delay(50L)

            firstSubscriber.cancel()
            kotlinx.coroutines.delay(50L)

            // Release workers: page 10 must still be picked up and reach READY.
            blocker.complete(Unit)
            withTimeout(5_000L) {
                pages[10].statusFlow.first { it == Page.State.READY }
            }
            pages[10].status shouldBe Page.State.READY

            secondSubscriber.cancel()
            busyJobs.forEach { it.cancel() }
        } finally {
            blocker.complete(Unit)
            loader.recycle()
        }
    }

    @Test
    fun `retryPage evicts stale cache entry and resets metadata before redownloading`(): Unit = runBlocking {
        val downloads = AtomicInteger(0)
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getImage(any()) } answers {
            downloads.incrementAndGet()
            createResponse(firstArg<Page>().imageUrl!!)
        }

        val cachedUrls = mutableSetOf("https://example.org/corrupt.png")
        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.isImageInCache(any()) } answers { firstArg<String>() in cachedUrls }
        every { chapterCache.removeImageFromCache(any()) } answers { cachedUrls.remove(firstArg()) }
        every { chapterCache.putImageToCache(any(), any()) } answers {
            cachedUrls.add(firstArg())
        }
        every { chapterCache.openImageStream(any()) } answers { ByteArrayInputStream(ByteArray(16)) }
        justRun { chapterCache.putPageListToCache(any(), any()) }

        val chapter = ReaderChapter(
            Chapter.create().copy(id = 11L, mangaId = 1L, url = "https://example.org/ch-11"),
        )
        val page = ReaderPage(0, "https://example.org/p0", "https://example.org/corrupt.png").apply {
            this.chapter = chapter
            status = Page.State.READY
            imageDimensions = ImageUtil.ImageDimensions(800, 1200)
            isAnimatedImage = true
        }
        chapter.state = ReaderChapter.State.Loaded(listOf(page))

        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = createSourcePreferences(),
            readerPreferences = createReaderPreferences(),
        )

        try {
            loader.retryPage(page)

            withTimeout(5_000L) {
                page.statusFlow.first { it == Page.State.READY && downloads.get() >= 1 }
            }
            verify(exactly = 1) { chapterCache.removeImageFromCache("https://example.org/corrupt.png") }
            coVerify(exactly = 1) { source.getImage(page) }
            page.isAnimatedImage shouldBe false
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun `retryPage recovers orphaned page stuck in QUEUE`(): Unit = runBlocking {
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getImage(any()) } answers { createResponse(firstArg<Page>().imageUrl!!) }

        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.isImageInCache(any()) } returns false
        every { chapterCache.removeImageFromCache(any()) } returns false
        justRun { chapterCache.putImageToCache(any(), any()) }
        every { chapterCache.openImageStream(any()) } answers { ByteArrayInputStream(ByteArray(16)) }
        justRun { chapterCache.putPageListToCache(any(), any()) }

        val chapter = ReaderChapter(
            Chapter.create().copy(id = 12L, mangaId = 1L, url = "https://example.org/ch-12"),
        )
        val orphanedPage = ReaderPage(0, "https://example.org/p0", "https://example.org/orphan.png").apply {
            this.chapter = chapter
            status = Page.State.QUEUE
        }
        chapter.state = ReaderChapter.State.Loaded(listOf(orphanedPage))

        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = createSourcePreferences(),
            readerPreferences = createReaderPreferences(),
        )

        try {
            loader.retryPage(orphanedPage)
            withTimeout(5_000L) {
                orphanedPage.statusFlow.first { it == Page.State.READY }
            }
            orphanedPage.status shouldBe Page.State.READY
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun `stall watchdog fails hung download without killing worker pool`(): Unit = runBlocking {
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getImage(any()) } coAnswers {
            val p = firstArg<Page>()
            if (p.index < 3) {
                awaitCancellation()
            }
            createResponse(p.imageUrl!!)
        }

        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.isImageInCache(any()) } returns false
        every { chapterCache.removeImageFromCache(any()) } returns false
        justRun { chapterCache.putImageToCache(any(), any()) }
        every { chapterCache.openImageStream(any()) } answers { ByteArrayInputStream(ByteArray(16)) }
        justRun { chapterCache.putPageListToCache(any(), any()) }

        val chapter = ReaderChapter(
            Chapter.create().copy(id = 13L, mangaId = 1L, url = "https://example.org/ch-13"),
        )
        val pages = (0..10).map { idx ->
            ReaderPage(idx, "https://example.org/p$idx", "https://example.org/img-$idx.png").apply {
                this.chapter = chapter
            }
        }
        chapter.state = ReaderChapter.State.Loaded(pages)

        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = createSourcePreferences(),
            readerPreferences = createReaderPreferences(),
            loadPageTimeoutMs = 200L,
            downloadStallTimeoutMs = 150L,
            stallRetryThresholdMs = 50L,
        )

        try {
            // Stall all 3 workers simultaneously and verify every worker recovers after timeout.
            val stuckJobs = (0..2).map { i -> launch { loader.loadPage(pages[i]) } }
            withTimeout(5_000L) {
                (0..2).forEach { i ->
                    pages[i].statusFlow.first { it == Page.State.ERROR }
                }
            }
            (0..2).forEach { i -> pages[i].status shouldBe Page.State.ERROR }

            val healthyJob = launch { loader.loadPage(pages[10]) }
            withTimeout(5_000L) {
                pages[10].statusFlow.first { it == Page.State.READY }
            }
            pages[10].status shouldBe Page.State.READY

            stuckJobs.forEach { it.cancel() }
            healthyJob.cancel()
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun `loadPageTimeout fails hung getImageUrl without killing worker pool`(): Unit = runBlocking {
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getImageUrl(any()) } coAnswers {
            val p = firstArg<Page>()
            if (p.index == 0) {
                awaitCancellation()
            }
            "https://example.org/resolved-${p.index}.png"
        }
        coEvery { source.getImage(any()) } answers { createResponse(firstArg<Page>().imageUrl!!) }

        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.isImageInCache(any()) } returns false
        every { chapterCache.removeImageFromCache(any()) } returns false
        justRun { chapterCache.putImageToCache(any(), any()) }
        every { chapterCache.openImageStream(any()) } answers { ByteArrayInputStream(ByteArray(16)) }
        justRun { chapterCache.putPageListToCache(any(), any()) }

        val chapter = ReaderChapter(
            Chapter.create().copy(id = 14L, mangaId = 1L, url = "https://example.org/ch-14"),
        )
        val hungUrlPage = ReaderPage(0, "https://example.org/p0", null).apply {
            this.chapter = chapter
        }
        val okPage = ReaderPage(1, "https://example.org/p1", null).apply {
            this.chapter = chapter
        }
        chapter.state = ReaderChapter.State.Loaded(listOf(hungUrlPage, okPage))

        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = createSourcePreferences(),
            readerPreferences = createReaderPreferences(),
            loadPageTimeoutMs = 150L,
            downloadStallTimeoutMs = 500L,
        )

        try {
            val job0 = launch { loader.loadPage(hungUrlPage) }
            withTimeout(5_000L) {
                hungUrlPage.statusFlow.first { it == Page.State.ERROR }
            }
            hungUrlPage.status shouldBe Page.State.ERROR

            val job1 = launch { loader.loadPage(okPage) }
            withTimeout(5_000L) {
                okPage.statusFlow.first { it == Page.State.READY }
            }
            okPage.status shouldBe Page.State.READY

            job0.cancel()
            job1.cancel()
        } finally {
            loader.recycle()
        }
    }
}
