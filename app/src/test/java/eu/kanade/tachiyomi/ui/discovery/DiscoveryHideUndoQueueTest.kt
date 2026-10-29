package eu.kanade.tachiyomi.ui.discovery

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryHideUndoQueueTest {
    @Test
    fun `undo waits for a hide suspended before its database writes`() = runTest {
        val queue = DiscoveryHideUndoQueue(this, StandardTestDispatcher(testScheduler))
        val releaseHide = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var hidden = false
        var hasHideSignal = false

        val hide = queue.enqueue {
            events += "backfill"
            releaseHide.await()
            hasHideSignal = true
            hidden = true
            events += "hide"
        }
        runCurrent()
        events shouldBe listOf("backfill")

        val undo = queue.enqueue {
            hasHideSignal = false
            hidden = false
            events += "undo"
        }
        runCurrent()
        events shouldBe listOf("backfill")
        undo.isCompleted shouldBe false

        releaseHide.complete(Unit)
        advanceUntilIdle()
        hide.join()
        undo.join()
        events shouldBe listOf("backfill", "hide", "undo")
        hidden shouldBe false
        hasHideSignal shouldBe false
    }

    @Test
    fun `undo submitted before dispatcher starts still follows hide`() = runTest {
        val queue = DiscoveryHideUndoQueue(this, StandardTestDispatcher(testScheduler))
        val events = mutableListOf<String>()
        queue.enqueue { events += "hide" }
        queue.enqueue { events += "undo" }
        events shouldBe emptyList()
        advanceUntilIdle()
        events shouldBe listOf("hide", "undo")
    }

    @Test
    fun `interleaved titles retain the submission order across suspensions`() = runTest {
        val queue = DiscoveryHideUndoQueue(this, StandardTestDispatcher(testScheduler))
        val events = mutableListOf<Int>()
        repeat(50) { index ->
            queue.enqueue {
                yield()
                events += index
            }
        }
        advanceUntilIdle()
        events shouldBe (0 until 50).toList()
    }

    @Test
    fun `a cancelled predecessor does not leave undo waiting forever`() = runTest {
        val queue = DiscoveryHideUndoQueue(this, StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>()
        var undone = false
        val hide = queue.enqueue {
            started.complete(Unit)
            awaitCancellation()
        }
        runCurrent()
        started.isCompleted shouldBe true
        val undo = queue.enqueue { undone = true }
        hide.cancel()
        advanceUntilIdle()
        undo.join()
        undone shouldBe true
    }

    @Test
    fun `screen cancellation cancels active and queued operations`() = runTest {
        val owner = Job(coroutineContext[Job])
        val ownedScope = CoroutineScope(coroutineContext + owner)
        val queue = DiscoveryHideUndoQueue(ownedScope, StandardTestDispatcher(testScheduler))
        var undone = false
        val hide = queue.enqueue { awaitCancellation() }
        runCurrent()
        val undo = queue.enqueue { undone = true }
        owner.cancel()
        advanceUntilIdle()
        hide.isCancelled shouldBe true
        undo.isCancelled shouldBe true
        undone shouldBe false
    }

    @Test
    fun `independent screen queues do not block each other`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = DiscoveryHideUndoQueue(this, dispatcher)
        val second = DiscoveryHideUndoQueue(this, dispatcher)
        val release = CompletableDeferred<Unit>()
        var secondFinished = false
        first.enqueue { release.await() }
        second.enqueue { secondFinished = true }
        runCurrent()
        secondFinished shouldBe true
        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `supervised operation failure does not prevent a later undo`() = runTest {
        val owner = SupervisorJob(coroutineContext[Job])
        val errors = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, error -> errors += error }
        val ownedScope = CoroutineScope(coroutineContext + owner + handler)
        val queue = DiscoveryHideUndoQueue(ownedScope, StandardTestDispatcher(testScheduler))
        var undone = false
        try {
            queue.enqueue { throw IOException("hide failed") }
            val undo = queue.enqueue { undone = true }
            advanceUntilIdle()
            undo.join()
            undone shouldBe true
            errors.size shouldBe 1
            (errors.single() is IOException) shouldBe true
        } finally {
            ownedScope.cancel()
        }
    }
}
