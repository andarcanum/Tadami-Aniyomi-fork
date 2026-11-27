package eu.kanade.tachiyomi.ui.reader.viewer

import android.app.Application
import android.graphics.Bitmap
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.core.common.util.image.NativeImageDecoder
import tachiyomi.core.common.util.system.ImageUtil
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ReaderPagePrewarmerTest {

    @After
    fun tearDown() {
        ReaderPreviewCache.clear()
        unmockkAll()
    }

    @Test
    fun prewarmAroundSkipsPreviewDecodeForPreviousPageAndBoundPagesWhileSniffingDimensions() = runBlocking {
        mockkObject(ImageUtil)
        every { ImageUtil.sniffImageMetadata(any()) } returns ImageUtil.ImageMetadata(
            isAnimated = false,
            dimensions = ImageUtil.ImageDimensions(800, 1200),
        )

        val decodedCount = AtomicInteger(0)
        val createdBitmaps = mutableListOf<Bitmap>()
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 800
        every { native.height } returns 1200
        every { native.isHdr } returns false
        every { native.hasGainmap } returns false
        every { native.decode(any(), any()) } answers {
            decodedCount.incrementAndGet()
            Bitmap.createBitmap(16, 16, Bitmap.Config.RGB_565).also(createdBitmaps::add)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val chapter = ReaderChapter(
            ChapterImpl().apply {
                id = 100L
                manga_id = 1L
                url = "chapter-100"
                name = "Chapter 100"
            },
        )
        val pages = (0..3).map { idx ->
            ReaderPage(
                index = idx,
                stream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
            ).apply {
                this.chapter = chapter
            }
        }

        val boundPage = pages[2] // currentIdx + 1 is already bound in viewer
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sniffedPages = mutableListOf<Int>()
        val prewarmer = ReaderPagePrewarmer(
            scope = scope,
            onDimensionsSniffed = { page, _ -> sniffedPages += page.index },
            isPageAlreadyBound = { page -> page === boundPage },
            mainDispatcher = Dispatchers.Unconfined,
        )

        try {
            prewarmer.prewarmAround(
                currentPage = pages[1],
                currentPages = pages,
                nextChapterPages = null,
                cropBorders = false,
            )
            prewarmer.awaitIdle()

            // Metadata must be sniffed for +1, +2, and -1 so webtoon placeholders never jump.
            assertEquals(listOf(2, 3, 0), sniffedPages)
            assertNotNull(pages[0].imageDimensions)
            assertNotNull(pages[2].imageDimensions)
            assertNotNull(pages[3].imageDimensions)

            // Preview bitmap decode must be skipped for already-bound (+1) and previous (-1) pages.
            assertNull(ReaderPreviewCache.get(ReaderPreviewCache.key(100L, 0, false)!!))
            assertNull(ReaderPreviewCache.get(ReaderPreviewCache.key(100L, 2, false)!!))
            // Only unbound forward page (+2) gets decoded into the preview cache.
            assertNotNull(ReaderPreviewCache.get(ReaderPreviewCache.key(100L, 3, false)!!))
            assertEquals(1, decodedCount.get())
        } finally {
            prewarmer.cancel()
            scope.cancel()
            createdBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
