package eu.kanade.tachiyomi.ui.reader.viewer.autoscroll

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagerAutoScrollManagerTest {

    @Test
    fun `start waits full page delay and does not skip page at t=0`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewer = mockk<PagerViewer>()
        every { viewer.isCurrentPageReady() } returns true
        every { viewer.isAtAutoScrollEnd() } returns false
        justRun { viewer.moveToNext() }

        val manager = PagerAutoScrollManager(
            viewer = viewer,
            dispatcher = dispatcher,
            clockMs = { testScheduler.currentTime },
        )

        // Speed 100 -> 2080ms delay (10000 - 99 * 80)
        manager.start(speed = 100)
        runCurrent()
        verify(exactly = 0) { viewer.moveToNext() }

        advanceTimeBy(2079L)
        runCurrent()
        verify(exactly = 0) { viewer.moveToNext() }

        advanceTimeBy(1L)
        runCurrent()
        verify(exactly = 1) { viewer.moveToNext() }

        manager.destroy()
    }

    @Test
    fun `setSpeed updates cadence without immediately skipping page`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewer = mockk<PagerViewer>()
        every { viewer.isCurrentPageReady() } returns true
        every { viewer.isAtAutoScrollEnd() } returns false
        justRun { viewer.moveToNext() }

        val manager = PagerAutoScrollManager(
            viewer = viewer,
            dispatcher = dispatcher,
            clockMs = { testScheduler.currentTime },
        )

        manager.start(speed = 1) // 10000ms
        advanceTimeBy(1000L)
        runCurrent()
        verify(exactly = 0) { viewer.moveToNext() }

        manager.setSpeed(100) // 2080ms
        runCurrent()
        verify(exactly = 0) { viewer.moveToNext() }

        advanceTimeBy(2080L)
        runCurrent()
        verify(exactly = 1) { viewer.moveToNext() }

        manager.destroy()
    }

    @Test
    fun `waits until current page is ready before advancing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var pageReady = false
        val viewer = mockk<PagerViewer>()
        every { viewer.isCurrentPageReady() } answers { pageReady }
        every { viewer.isAtAutoScrollEnd() } returns false
        justRun { viewer.moveToNext() }

        val manager = PagerAutoScrollManager(
            viewer = viewer,
            dispatcher = dispatcher,
            clockMs = { testScheduler.currentTime },
        )

        manager.start(speed = 100) // 2080ms
        advanceTimeBy(2500L)
        runCurrent()
        verify(exactly = 0) { viewer.moveToNext() }

        pageReady = true
        advanceTimeBy(250L)
        runCurrent()
        verify(exactly = 1) { viewer.moveToNext() }

        manager.destroy()
    }

    @Test
    fun `stops and invokes onReachedEnd at end of manga`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var page = 0
        var reachedEndCalls = 0
        val viewer = mockk<PagerViewer>()
        every { viewer.isCurrentPageReady() } returns true
        every { viewer.isAtAutoScrollEnd() } answers { page >= 1 }
        every { viewer.moveToNext() } answers { page++ }

        val manager = PagerAutoScrollManager(
            viewer = viewer,
            dispatcher = dispatcher,
            clockMs = { testScheduler.currentTime },
        ).apply {
            onReachedEnd = { reachedEndCalls++ }
        }

        manager.start(speed = 100) // 2080ms
        advanceTimeBy(2080L)
        runCurrent()

        page shouldBe 1
        reachedEndCalls shouldBe 1
        manager.state.value.isActive shouldBe false

        // Further time produces no additional page turns
        advanceTimeBy(10_000L)
        runCurrent()
        page shouldBe 1
        reachedEndCalls shouldBe 1

        manager.destroy()
    }

    @Test
    fun `touch cooldown delays rather than accelerates page turn`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewer = mockk<PagerViewer>()
        every { viewer.isCurrentPageReady() } returns true
        every { viewer.isAtAutoScrollEnd() } returns false
        justRun { viewer.moveToNext() }

        val manager = PagerAutoScrollManager(
            viewer = viewer,
            dispatcher = dispatcher,
            clockMs = { testScheduler.currentTime },
        )

        manager.start(speed = 1) // 10000ms
        advanceTimeBy(1000L)
        manager.setCooldown(2000L)

        // Previously cooldown flipped the page immediately at +2000ms!
        advanceTimeBy(2000L)
        runCurrent()
        verify(exactly = 0) { viewer.moveToNext() }

        advanceTimeBy(10_000L)
        runCurrent()
        verify(exactly = 1) { viewer.moveToNext() }

        manager.destroy()
    }
}
