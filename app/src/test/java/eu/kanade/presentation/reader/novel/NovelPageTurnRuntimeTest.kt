@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.wewox.pagecurl.page.Edge
import eu.wewox.pagecurl.page.PageCurlState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelPageTurnRuntimeTest {
    @Test
    fun `upstream cancelled turn can settle into replacement geometry on the same state`() = runTest {
        val state = PageCurlState(initialCurrent = 2)
        state.setupForTest(layout())
        val turn = launch {
            state.next { size ->
                snapTo(Edge(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)))
                CompletableDeferred<Unit>().await()
            }
        }
        runCurrent()
        assertEquals(0.5f, state.progress)

        state.setupForTest(layout(width = 800, height = 400))
        state.snapTo(3)
        turn.cancel()
        turn.join()

        // Characterize the real 1.5.1 dependency: cancel alone is not an application fix.
        assertEquals(4, state.current)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `old forward completion cannot advance the reflowed source anchor`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 2))
        val portrait = layout()
        val old = runtime.generation(portrait, initialPrimaryPage = 2)
        old.primaryState.setupForTest(portrait)
        val finish = CompletableDeferred<Unit>()
        val turn = launch {
            old.primaryState.next { size ->
                snapTo(Edge(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)))
                finish.await()
            }
        }
        runCurrent()
        assertEquals(0.5f, old.primaryState.progress)

        val landscape = layout(width = 800, height = 400, epoch = 2)
        val active = runtime.generation(landscape, initialPrimaryPage = 3)
        active.primaryState.setupForTest(landscape)
        active.primaryState.snapTo(3)
        finish.complete(Unit)
        turn.join()

        assertEquals(3, active.primaryState.current)
        assertEquals(0f, active.primaryState.progress)
    }

    @Test
    fun `cancelled old backward completion cannot rewind the reflowed source anchor`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 5))
        val portrait = layout()
        val old = runtime.generation(portrait, initialPrimaryPage = 5)
        old.primaryState.setupForTest(portrait)
        val turn = launch {
            old.primaryState.prev { size ->
                snapTo(Edge(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)))
                CompletableDeferred<Unit>().await()
            }
        }
        runCurrent()
        assertEquals(-0.5f, old.primaryState.progress)

        val landscape = layout(width = 800, height = 400, epoch = 2)
        val active = runtime.generation(landscape, initialPrimaryPage = 3)
        active.primaryState.setupForTest(landscape)
        active.primaryState.snapTo(3)
        turn.cancel()
        turn.join()

        assertEquals(3, active.primaryState.current)
        assertEquals(0f, active.primaryState.progress)
    }

    @Test
    fun `equal output reflow epoch still detaches a partial turn`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 2))
        val original = layout()
        val old = runtime.generation(original, initialPrimaryPage = 2)
        old.primaryState.setupForTest(original)
        val finish = CompletableDeferred<Unit>()
        val turn = launch {
            old.primaryState.next { size ->
                snapTo(Edge(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)))
                finish.await()
            }
        }
        runCurrent()
        assertEquals(0.5f, old.primaryState.progress)

        val reflowed = original.copy(layoutGeneration = 2)
        val active = runtime.generation(reflowed, initialPrimaryPage = 4)
        active.primaryState.setupForTest(reflowed)
        active.primaryState.snapTo(4)
        finish.complete(Unit)
        turn.join()

        assertEquals(4, active.primaryState.current)
        assertFalse(old.isActive)
    }

    @Test
    fun `obsolete completion cannot publish progress restore ack or chapter navigation`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 2))
        val original = layout()
        val old = runtime.generation(original, initialPrimaryPage = 2)
        old.primaryState.setupForTest(original)
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val turn = launch {
            try {
                old.primaryState.next { finish.await() }
            } finally {
                old.publish { events += "old-progress" }
                old.publish { events += "old-restore-ack" }
                old.publish { events += "old-next-chapter" }
            }
        }
        runCurrent()

        val replacement = layout(width = 800, height = 400, epoch = 2)
        val active = runtime.generation(replacement, initialPrimaryPage = 3)
        active.primaryState.setupForTest(replacement)
        active.primaryState.snapTo(3)
        turn.cancel()
        turn.join()
        active.publish { events += "active-progress:3" }

        assertEquals(listOf("active-progress:3"), events)
    }

    @Test
    fun `old spread forward completion cannot promote either restored surface`() = runTest {
        assertObsoleteSpreadTurnIsDetached(turnLeft = false)
    }

    @Test
    fun `old spread backward completion cannot promote either restored surface`() = runTest {
        assertObsoleteSpreadTurnIsDetached(turnLeft = true)
    }

    @Test
    fun `restore target interrupts a partial turn even without geometry change`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 2))
        val key = layout()
        val old = runtime.generation(key, initialPrimaryPage = 2)
        old.primaryState.setupForTest(key)
        val turn = launch {
            old.primaryState.next { size ->
                snapTo(Edge(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)))
                CompletableDeferred<Unit>().await()
            }
        }
        runCurrent()
        assertEquals(0.5f, old.primaryState.progress)

        val active = runtime.generation(key, initialPrimaryPage = 6, requestedPage = 6)
        active.primaryState.setupForTest(key)
        active.primaryState.snapTo(6)
        turn.cancel()
        turn.join()

        assertEquals(6, active.primaryState.current)
        assertFalse(old.isActive)
    }

    @Test
    fun `request acknowledgement and ordinary progress do not replace a live generation`() {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 3))
        val key = layout()
        val requested = runtime.generation(key, initialPrimaryPage = 3, requestedPage = 3)
        val acknowledged = runtime.generation(key, initialPrimaryPage = 3, requestedPage = -1)
        val progressed = runtime.generation(key, initialPrimaryPage = 4, requestedPage = -1)

        assertSame(requested, acknowledged)
        assertSame(requested, progressed)
        assertTrue(requested.isActive)
    }

    @Test
    fun `a repeated restore after acknowledgement starts a fresh generation`() {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 3))
        val key = layout()
        val first = runtime.generation(key, initialPrimaryPage = 3, requestedPage = 3)
        runtime.generation(key, initialPrimaryPage = 3, requestedPage = -1)
        val second = runtime.generation(key, initialPrimaryPage = 3, requestedPage = 3)

        assertFalse(first.isActive)
        assertTrue(second.isActive)
    }

    @Test
    fun `disposed surface cannot publish into its former host`() {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 3))
        val generation = runtime.generation(layout(), initialPrimaryPage = 3)
        val events = mutableListOf<String>()
        generation.dispose()
        generation.publish { events += "old-chapter" }

        assertTrue(events.isEmpty())
    }

    @Test
    fun `actual application curl animation still settles forward and backward`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 2))
        val key = layout()
        val generation = runtime.generation(key, initialPrimaryPage = 2)
        generation.primaryState.setupForTest(key)
        val clock = SchedulerFrameClock(testScheduler)
        val forward = launch(clock) {
            generation.primaryState.next(createPageTurnAnimation(240, forward = true, curlAmount = 0.6f))
        }
        advanceTimeBy(112)
        runCurrent()
        assertTrue(generation.primaryState.progress > 0f)
        assertTrue(generation.primaryState.progress < 1f)
        advanceUntilIdle()
        forward.join()
        assertEquals(3, generation.primaryState.current)
        assertEquals(0f, generation.primaryState.progress)

        val backward = launch(clock) {
            generation.primaryState.prev(createPageTurnAnimation(240, forward = false, curlAmount = 0.6f))
        }
        advanceUntilIdle()
        backward.join()
        assertEquals(2, generation.primaryState.current)
        assertEquals(0f, generation.primaryState.progress)
    }

    @Test
    fun `chapter edge guards remain on the real curl state`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 0))
        val key = layout(count = 1)
        val generation = runtime.generation(key, initialPrimaryPage = 0)
        generation.primaryState.setupForTest(key)
        var animationStarted = false
        generation.primaryState.next { animationStarted = true }
        generation.primaryState.prev { animationStarted = true }

        assertEquals(0, generation.primaryState.current)
        assertFalse(animationStarted)
    }

    @Test
    fun `saved single page follows the replacement state not the obsolete remembered state`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 2))
        runtime.generation(layout(), initialPrimaryPage = 2)
        val replacementKey = layout(epoch = 2)
        val active = runtime.generation(replacementKey, initialPrimaryPage = 4)
        active.primaryState.setupForTest(replacementKey)
        active.primaryState.snapTo(5)
        val saved = with(NovelPageTurnRuntime.Saver) { saveScope.save(runtime) }!!
        val restored = NovelPageTurnRuntime.Saver.restore(saved)!!
        assertEquals(5, restored.generation(replacementKey, initialPrimaryPage = 0).primaryState.current)
    }

    @Test
    fun `saved spread restores both current replacement surfaces`() = runTest {
        val runtime = NovelPageTurnRuntime(PageCurlState(initialCurrent = 6), PageCurlState(initialCurrent = 1))
        runtime.generation(layout(columns = 2), initialPrimaryPage = 6, initialSecondaryPage = 1)
        val replacementKey = layout(columns = 2, epoch = 2)
        val active = runtime.generation(replacementKey, initialPrimaryPage = 4, initialSecondaryPage = 3)
        active.primaryState.setupForTest(replacementKey)
        checkNotNull(active.secondaryState).setupForTest(replacementKey)
        val saved = with(NovelPageTurnRuntime.Saver) { saveScope.save(runtime) }!!
        val restored = NovelPageTurnRuntime.Saver.restore(saved)!!
        val result = restored.generation(replacementKey, initialPrimaryPage = 0, initialSecondaryPage = 0)
        assertEquals(4, result.primaryState.current)
        assertEquals(3, checkNotNull(result.secondaryState).current)
    }

    private val saveScope = object : SaverScope {
        override fun canBeSaved(value: Any) = true
    }

    private suspend fun TestScope.assertObsoleteSpreadTurnIsDetached(turnLeft: Boolean) {
        val original = layout(width = 800, height = 400, columns = 2)
        val runtime = NovelPageTurnRuntime(
            PageCurlState(initialCurrent = 6),
            PageCurlState(initialCurrent = 1),
        )
        val old = runtime.generation(original, initialPrimaryPage = 6, initialSecondaryPage = 1)
        old.primaryState.setupForTest(original)
        checkNotNull(old.secondaryState).setupForTest(original)
        val turningState = if (turnLeft) old.primaryState else checkNotNull(old.secondaryState)
        val turn = launch {
            turningState.next { size ->
                snapTo(Edge(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height)))
                CompletableDeferred<Unit>().await()
            }
        }
        runCurrent()
        assertEquals(0.5f, turningState.progress)

        val replacement = original.copy(layoutGeneration = 2, constraints = Constraints.fixed(960, 480))
        val active = runtime.generation(replacement, initialPrimaryPage = 4, initialSecondaryPage = 3)
        active.primaryState.setupForTest(replacement)
        checkNotNull(active.secondaryState).setupForTest(replacement)
        active.primaryState.snapTo(4)
        checkNotNull(active.secondaryState).snapTo(3)
        turn.cancel()
        turn.join()
        // This is the renderer's paired-surface collector; obsolete callbacks must not synchronize.
        if (old.isActive) {
            val other = if (turnLeft) checkNotNull(old.secondaryState) else old.primaryState
            other.snapTo(7 - turningState.current)
        }

        assertEquals(4, active.primaryState.current)
        assertEquals(3, checkNotNull(active.secondaryState).current)
        assertEquals(0f, active.primaryState.progress)
        assertEquals(0f, checkNotNull(active.secondaryState).progress)
    }

    private fun layout(
        width: Int = 400,
        height: Int = 800,
        epoch: Long = 1,
        columns: Int = 1,
        count: Int = 8,
    ) = NovelPageTurnLayoutKey(
        chapterId = 42,
        layoutGeneration = epoch,
        constraints = Constraints.fixed(width, height),
        contentPages = List(count) { NovelPageContentPage(emptyList(), pageIndex = it) },
        spreadColumns = columns,
        virtualPageCount = count,
        hasPreviousChapter = false,
        hasNextChapter = false,
        horizontalPadding = DpOffset(0.dp, 0.dp),
    )

    private fun PageCurlState.setupForTest(layout: NovelPageTurnLayoutKey) {
        // Invoke the real dependency setup, not a copied InternalState or mock. Its Constraints
        // parameter is an inline value, so the JVM method receives the unboxed packed long.
        val constraints = Constraints.fixed(
            layout.constraints.maxWidth / layout.spreadColumns,
            layout.constraints.maxHeight,
        )
        val packed = Constraints::class.java.getDeclaredMethod("unbox-impl").invoke(constraints)
        val setup = PageCurlState::class.java.declaredMethods.single {
            it.name.startsWith("setup") && it.parameterCount == 2
        }
        setup.isAccessible = true
        setup.invoke(this, layout.virtualPageCount, packed)
    }

    private class SchedulerFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }
}
