package eu.kanade.tachiyomi.ui.reader.viewer

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ReaderPreviewCacheTest {

    @After
    fun tearDown() {
        ReaderPreviewCache.clear()
    }

    @Test
    fun keyDistinguishesChapterPageAndCropBorders() {
        assertNull(ReaderPreviewCache.key(chapterId = null, pageIndex = 0, cropBorders = false))
        val k1 = assertNotNull(ReaderPreviewCache.key(chapterId = 42L, pageIndex = 1, cropBorders = false))
        val k2 = assertNotNull(ReaderPreviewCache.key(chapterId = 42L, pageIndex = 1, cropBorders = true))
        val k3 = assertNotNull(ReaderPreviewCache.key(chapterId = 42L, pageIndex = 2, cropBorders = false))
        val k4 = assertNotNull(ReaderPreviewCache.key(chapterId = 99L, pageIndex = 1, cropBorders = false))
        assertNotEquals(k1, k2)
        assertNotEquals(k1, k3)
        assertNotEquals(k1, k4)
    }

    @Test
    fun putGetAndTrimMemoryEvictWithoutRecyclingBitmaps() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565)
        try {
            val key = assertNotNull(ReaderPreviewCache.key(10L, 0, false))
            ReaderPreviewCache.put(key, ReaderPreviewCache.Entry(bitmap, 1024, 1024))
            val hit = ReaderPreviewCache.get(key)
            assertNotNull(hit)
            assertEquals(1024, hit.sourceWidth)
            assertEquals(1024, hit.sourceHeight)

            ReaderPreviewCache.trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertNull(ReaderPreviewCache.get(key))
            assertFalse(bitmap.isRecycled, "Cache eviction must not recycle bitmaps in active use")
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun computePreviewSampleSizeRespectsPixelBudget() {
        val mangaSample = ReaderPagePrewarmer.computePreviewSampleSize(1200, 1800)
        assertTrue(mangaSample >= 4 && (mangaSample and (mangaSample - 1)) == 0)
        val mangaPixels = ((1200L + mangaSample - 1) / mangaSample) * ((1800L + mangaSample - 1) / mangaSample)
        assertTrue(mangaPixels <= 262_144L)

        val webtoonSample = ReaderPagePrewarmer.computePreviewSampleSize(800, 16_000)
        assertTrue(webtoonSample >= 8 && (webtoonSample and (webtoonSample - 1)) == 0)
        val webtoonPixels =
            ((800L + webtoonSample - 1) / webtoonSample) * ((16_000L + webtoonSample - 1) / webtoonSample)
        assertTrue(webtoonPixels <= 262_144L)

        assertEquals(1, ReaderPagePrewarmer.computePreviewSampleSize(400, 600))
    }
}
