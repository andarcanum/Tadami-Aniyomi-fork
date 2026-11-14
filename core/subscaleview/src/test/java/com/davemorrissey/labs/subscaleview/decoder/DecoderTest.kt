package com.davemorrissey.labs.subscaleview.decoder

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.core.common.util.image.NativeImageDecoder
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DecoderTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun hdrDecoderReportsIsHdrAndSkipsHardwareConversion() {
        mockkObject(NativeImageDecoder.Companion)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 2000
        every { native.height } returns 3000
        every { native.isHdr } returns true
        every { native.decode(any(), any()) } answers {
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val decoder = Decoder(cropBorders = false, hardwareTilesEnabled = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val size = decoder.init(context, InputProvider { ByteArrayInputStream(byteArrayOf(1)) })

        assertEquals(Point(2000, 3000), size)
        assertTrue(decoder.isReady())
        assertTrue(decoder.isHdr)

        val tile = decoder.decodeRegion(Rect(0, 0, 512, 512), 1)
        assertTrue(tile.config != Bitmap.Config.HARDWARE, "HDR tiles must stay non-HARDWARE")
        tile.recycle()

        decoder.recycle()
        assertFalse(decoder.isReady())
        assertFalse(decoder.isHdr)
    }

    @Test
    fun concurrentDecodeRegionAndRecycleNeverThrowsNullPointer() {
        mockkObject(NativeImageDecoder.Companion)
        val enteredDecode = CountDownLatch(1)
        val allowRecycle = CountDownLatch(1)
        val native = mockk<NativeImageDecoder>(relaxed = true)
        every { native.width } returns 1000
        every { native.height } returns 1000
        every { native.isHdr } returns false
        every { native.decode(any(), any()) } answers {
            enteredDecode.countDown()
            allowRecycle.await(2, TimeUnit.SECONDS)
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        }
        every { NativeImageDecoder.open(any(), any()) } returns native

        val decoder = Decoder(cropBorders = false, hardwareTilesEnabled = false)
        val context = ApplicationProvider.getApplicationContext<Context>()
        decoder.init(context, InputProvider { ByteArrayInputStream(byteArrayOf(1)) })

        val pool = Executors.newFixedThreadPool(2)
        try {
            val decodeFuture = pool.submit<Bitmap> {
                decoder.decodeRegion(Rect(0, 0, 256, 256), 1)
            }
            assertTrue(enteredDecode.await(2, TimeUnit.SECONDS))
            val recycleFuture = pool.submit {
                decoder.recycle()
            }
            // Release decode only after recycle starts waiting on the monitor lock.
            Thread.sleep(50)
            allowRecycle.countDown()

            val bitmap = decodeFuture.get(2, TimeUnit.SECONDS)
            recycleFuture.get(2, TimeUnit.SECONDS)
            assertFalse(bitmap.isRecycled)
            bitmap.recycle()
            assertFalse(decoder.isReady())
        } finally {
            pool.shutdownNow()
        }
    }
}
