package eu.kanade.tachiyomi.data.coil

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Half
import coil3.gif.PixelOpacity
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Closeable
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Real drawable/coroutine lifetime checks with only the external native source controlled.
 * Source-frame capacity, target bitmap allocation and scratch sizes are separate logical payloads;
 * they do not include encoded storage, native codec allocations, GC reclamation or device RSS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AnimatedNativeDrawableLifecycleTest {
    @Test
    fun `main close keeps heartbeat responsive during slow native IO and releases ownership exactly once`() =
        runTest(timeout = 30.seconds) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val samples = mutableListOf<CloseSample>()
                for (hdr in listOf(false, true)) {
                    repeat(3) { index -> samples += characterizeClose(hdr, index + 1) }
                    val group = samples.filter { it.hdr == hdr }
                    println(
                        "M09_CLOSE_SUMMARY hdr=$hdr samples=" + group.size +
                            " minNs=" + group.minOf { it.closeNanos } +
                            " maxNs=" + group.maxOf { it.closeNanos },
                    )
                }
                assertTrue(
                    samples.all { it.heartbeatBeforeRelease },
                    "Main heartbeat must run before the slow native decode is released",
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `close without in-flight native IO is immediate and exactly once`() {
        val source = ControlledNativeSource(hdr = false)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val drawable = drawable(source, bitmap, IntArray(1), null)

        drawable.close()
        assertEquals(1, source.closeCalls.get(), "Idle native cleanup must complete before close returns")
        assertTrue(bitmap.isRecycled)
        drawable.close()
        drawable.start()
        drawable.setVisible(false, false)
        drawable.setVisible(true, true)
        assertEquals(1, source.closeCalls.get())
        assertEquals(0, source.decodeCalls.get())
        assertFalse(source.closedBeforeTerminal.get())
        assertFalse(drawable.isRunning)
    }

    @Test
    fun `main close also releases ownership when slow native IO fails`() = runTest(timeout = 10.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sample = characterizeClose(hdr = false, sample = 1, decodeFailure = IOException("Slow native failure"))
            assertTrue(sample.heartbeatBeforeRelease, "Native failure must not turn disposal into a Main wait")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `canceled queued native IO consumes no extra frames before latest visible playback`() =
        runTest(timeout = 15.seconds) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val source = ControlledNativeSource(hdr = false)
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            val drawable = drawable(source, bitmap, IntArray(1), null)
            drawable.setRepeatCount(0)
            val jobs = mutableListOf<Job>()
            try {
                drawable.start()
                val first = playbackJob(drawable).also { jobs += it }
                runCurrent()
                assertTrue(source.firstDecodeEntered.await(2, TimeUnit.SECONDS), "First native frame must be held")
                val firstIo = ioChild(first)
                val monitor = decoderMonitor(drawable)

                drawable.setVisible(false, false)
                drawable.setVisible(true, false)
                val queuedOne = playbackJob(drawable).also { jobs += it }
                runCurrent()
                awaitBlockedWorkers(monitor, 1)
                val queuedOneIo = ioChild(queuedOne)

                drawable.setVisible(false, false)
                drawable.setVisible(true, false)
                val queuedTwo = playbackJob(drawable).also { jobs += it }
                runCurrent()
                val blockedWorkers = awaitBlockedWorkers(monitor, 2)
                val queuedTwoIo = ioChild(queuedTwo)

                drawable.setVisible(false, false)
                drawable.setVisible(true, false)
                // This newest Main loop stays queued while shared visibility is already true.
                val latest = playbackJob(drawable).also { jobs += it }
                val ioDone = CountDownLatch(3)
                for (io in listOf(firstIo, queuedOneIo, queuedTwoIo)) {
                    assertFalse(io.isCompleted, "The held/blocked IO operation must not have completed")
                    io.invokeOnCompletion { ioDone.countDown() }
                }
                source.releaseFirstDecode.countDown()
                // These IO-child completions do not require pumping the queued latest Main loop.
                assertTrue(ioDone.await(5, TimeUnit.SECONDS), "All obsolete IO workers must reach their terminal state")
                val callsBeforeLatest = source.decodeCalls.get()
                println(
                    "M03_QUEUED_IO blockedWorkers=" + blockedWorkers.size +
                        " oldIoTerminal=3 nativeCallsBeforeLatestMain=$callsBeforeLatest expected=1" +
                        " sourceFrame=64x32 capacityBytes=" + source.frameCapacity.get() +
                        " framesCreated=" + source.framesCreated.get() +
                        " framesClosed=" + source.framesClosed.get(),
                )
                assertEquals(1, callsBeforeLatest, "Canceled queued IO must not consume native frames")
                assertEquals(Color.BLACK, bitmap.getPixel(0, 0), "Canceled frames must not be uploaded")

                runCurrent()
                for (old in jobs.dropLast(1)) old.join()
                latest.join()
                assertEquals(Color.BLUE, bitmap.getPixel(0, 0), "Latest playback must receive the next native frame")
                assertEquals(2, source.decodeCalls.get())
                assertEquals(2, source.framesClosed.get())
                assertFalse(drawable.isRunning)
            } finally {
                source.releaseFirstDecode.countDown()
                try {
                    drawable.close()
                    for (job in jobs) job.join()
                    assertEquals(1, source.closeCalls.get())
                    assertEquals(source.framesCreated.get(), source.framesClosed.get())
                    assertFalse(source.closedBeforeTerminal.get())
                    assertTrue(bitmap.isRecycled)
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }

    private suspend fun TestScope.characterizeClose(
        hdr: Boolean,
        sample: Int,
        decodeFailure: Exception? = null,
    ): CloseSample {
        val source = ControlledNativeSource(hdr, decodeFailure)
        val config = if (hdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(1, 1, config)
        val ints = if (hdr) null else IntArray(1)
        val scratch = if (hdr) ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()) else null
        val bitmapBytes = bitmap.allocationByteCount.toLong()
        val sdrScratchBytes = (ints?.size ?: 0) * 4
        val hdrScratchBytes = scratch?.capacity() ?: 0
        val drawable = drawable(source, bitmap, ints, scratch)
        val reportedImageBytes = drawable.size
        val invalidations = AtomicInteger()
        drawable.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                invalidations.incrementAndGet()
            }
            override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) = Unit
            override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
        }
        val executor = Executors.newSingleThreadExecutor()
        val jobs = mutableListOf<Job>()
        var watchdog: Future<Boolean>? = null
        try {
            drawable.start()
            val playback = playbackJob(drawable).also { jobs += it }
            runCurrent()
            assertTrue(source.firstDecodeEntered.await(2, TimeUnit.SECONDS), "Native decode must hold its source frame")
            assertEquals(if (hdr) 16_384 else 8192, source.frameCapacity.get())
            assertEquals(source.frameCapacity.get(), source.frameRemaining.get())
            assertTrue(source.lastBuffer.get() !== scratch, "Source and target scratch must not alias")

            val heartbeat = CountDownLatch(1)
            val heartbeatBeforeRelease = AtomicBoolean()
            val closeNanos = AtomicLong()
            val heartbeatWatchdog = executor.submit<Boolean> {
                val observed = heartbeat.await(1, TimeUnit.SECONDS)
                source.releaseFirstDecode.countDown()
                observed
            }
            watchdog = heartbeatWatchdog
            jobs += launch(Dispatchers.Main) {
                val started = System.nanoTime()
                drawable.close()
                closeNanos.set(System.nanoTime() - started)
            }
            jobs += launch(Dispatchers.Main) {
                heartbeatBeforeRelease.set(source.releaseFirstDecode.count > 0)
                heartbeat.countDown()
            }
            runCurrent()
            for (ui in jobs.drop(1)) ui.join()
            val heartbeatObserved = heartbeatWatchdog.get(2, TimeUnit.SECONDS)
            playback.join()
            drawable.close()
            assertTrue(source.sourceClosed.await(2, TimeUnit.SECONDS), "Native cleanup must eventually complete")

            assertEquals(heartbeatObserved, heartbeatBeforeRelease.get())
            assertEquals(1, source.decodeCalls.get())
            assertEquals(1, source.framesCreated.get())
            assertEquals(1, source.framesClosed.get())
            assertEquals(1, source.maxOpenFrames.get())
            assertEquals(1, source.closeCalls.get())
            assertFalse(source.closedBeforeTerminal.get(), "Source closes only after decode and frame release")
            assertEquals(0, invalidations.get(), "Disposed IO must never publish a late frame")
            assertTrue(bitmap.isRecycled)
            assertFalse(drawable.isRunning)
            val sourceCapacity = source.frameCapacity.get()
            val sourceRemaining = source.frameRemaining.get()
            val bufferIdentities = source.bufferIdentities.size
            val elapsed = closeNanos.get()
            val beforeRelease = heartbeatBeforeRelease.get()
            val logicalPayload = bitmapBytes + sdrScratchBytes + hdrScratchBytes + sourceCapacity
            val failureType = decodeFailure?.javaClass?.simpleName ?: "none"
            println(
                "M09_CLOSE_SAMPLE hdr=$hdr sample=$sample failure=$failureType" +
                    " heartbeatBeforeRelease=$beforeRelease closeNs=$elapsed" +
                    " target=1x1 bitmapBytes=$bitmapBytes reportedImageBytes=$reportedImageBytes" +
                    " sdrScratchBytes=$sdrScratchBytes hdrScratchBytes=$hdrScratchBytes" +
                    " sourceFrame=64x32 capacityBytes=$sourceCapacity remainingBytes=$sourceRemaining" +
                    " uniqueSourceBuffers=$bufferIdentities maxOpenFrames=1 framesClosed=1 sourceClosed=1" +
                    " logicalPayloadBytes=$logicalPayload environment=controlledJVM" +
                    " nativeCodecStorage=unknown deviceRss=unmeasured",
            )
            return CloseSample(hdr, elapsed, beforeRelease)
        } finally {
            source.releaseFirstDecode.countDown()
            try {
                drawable.close()
                for (job in jobs) job.join()
                watchdog?.get(2, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun drawable(
        source: ControlledNativeSource,
        bitmap: Bitmap,
        ints: IntArray?,
        scratch: ByteBuffer?,
    ) = AnimatedNativeDrawable(
        source = source,
        displayBitmap = bitmap,
        intScratch = ints,
        hdrScratch = scratch,
        isHdr = source.hdr,
        pageCount = 3,
        initialIndex = 0,
        initialLast = false,
        initialDurationMs = 200,
        scale = Scale.FIT,
        animatedTransformation = null,
        pixelOpacity = PixelOpacity.UNCHANGED,
    )

    private fun playbackJob(drawable: AnimatedNativeDrawable): Job {
        val field = AnimatedNativeDrawable::class.java.getDeclaredField("playJob")
        field.isAccessible = true
        return checkNotNull(field.get(drawable) as? Job)
    }

    private fun decoderMonitor(drawable: AnimatedNativeDrawable): Any {
        val field = AnimatedNativeDrawable::class.java.getDeclaredField("decoderLock")
        field.isAccessible = true
        return checkNotNull(field.get(drawable))
    }

    private fun ioChild(job: Job): Job = job.children.single()

    private fun awaitBlockedWorkers(monitor: Any, expected: Int): Set<Long> {
        val bean = ManagementFactory.getThreadMXBean()
        val identity = System.identityHashCode(monitor)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        do {
            val blocked = bean.getThreadInfo(bean.allThreadIds, 16).filterNotNull().filter {
                it.threadState == Thread.State.BLOCKED && it.lockInfo?.identityHashCode == identity
            }.map { it.threadId }.toSet()
            if (blocked.size >= expected) return blocked
            // Bounded observation of a real JVM monitor, not a sleep used to guess job ordering.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        } while (System.nanoTime() < deadline)
        fail("Expected " + expected + " native IO workers blocked on the decoder monitor")
    }

    private data class CloseSample(val hdr: Boolean, val closeNanos: Long, val heartbeatBeforeRelease: Boolean)

    private class ControlledNativeSource(
        val hdr: Boolean,
        private val decodeFailure: Exception? = null,
    ) : NativeAnimationSource {
        val firstDecodeEntered = CountDownLatch(1)
        val releaseFirstDecode = CountDownLatch(1)
        val decodeCalls = AtomicInteger()
        val framesCreated = AtomicInteger()
        val framesClosed = AtomicInteger()
        val maxOpenFrames = AtomicInteger()
        val closeCalls = AtomicInteger()
        val sourceClosed = CountDownLatch(1)
        val frameCapacity = AtomicInteger()
        val frameRemaining = AtomicInteger()
        val closedBeforeTerminal = AtomicBoolean()
        val lastBuffer = AtomicReference<ByteBuffer>()
        val bufferIdentities: MutableSet<ByteBuffer> = Collections.synchronizedSet(
            Collections.newSetFromMap(IdentityHashMap<ByteBuffer, Boolean>()),
        )
        private val decodeInProgress = AtomicBoolean()

        override fun decodeNext(): NativeFrameData {
            decodeInProgress.set(true)
            val index = decodeCalls.incrementAndGet()
            val color = if (index == 2) Color.BLUE else Color.RED
            val bytesPerPixel = if (hdr) 8 else 4
            val buffer = ByteBuffer.allocate(64 * 32 * bytesPerPixel).order(ByteOrder.nativeOrder())
            repeat(64 * 32) {
                if (hdr) {
                    buffer.putShort(Half.toHalf(if (color == Color.RED) 1f else 0f))
                    buffer.putShort(Half.toHalf(0f))
                    buffer.putShort(Half.toHalf(if (color == Color.BLUE) 1f else 0f))
                    buffer.putShort(Half.toHalf(1f))
                } else {
                    buffer.put((if (color == Color.RED) 255 else 0).toByte())
                    buffer.put(0)
                    buffer.put((if (color == Color.BLUE) 255 else 0).toByte())
                    buffer.put((-1).toByte())
                }
            }
            buffer.flip()
            frameCapacity.set(buffer.capacity())
            frameRemaining.set(buffer.remaining())
            lastBuffer.set(buffer)
            bufferIdentities.add(buffer)
            val open = framesCreated.incrementAndGet() - framesClosed.get()
            maxOpenFrames.updateAndGet { maxOf(it, open) }
            val frame = NativeFrameData(
                width = 64,
                height = 32,
                image = buffer,
                index = index,
                duration = 200,
                closeable = Closeable { framesClosed.incrementAndGet() },
            )
            try {
                if (index == 1) {
                    firstDecodeEntered.countDown()
                    check(releaseFirstDecode.await(10, TimeUnit.SECONDS)) { "Held native frame was not released" }
                    decodeFailure?.let { throw it }
                }
                return frame
            } catch (error: Throwable) {
                frame.close()
                throw error
            } finally {
                decodeInProgress.set(false)
            }
        }

        override fun close() {
            if (decodeInProgress.get() || framesCreated.get() != framesClosed.get()) {
                closedBeforeTerminal.set(true)
            }
            closeCalls.incrementAndGet()
            sourceClosed.countDown()
        }
    }
}
