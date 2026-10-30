package eu.kanade.tachiyomi.ui.reels.components

import android.app.Application
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class ReelsSeekNoticeTest {

    @Test
    fun `single seek expires after one second`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        advanceTimeBy(999)
        page.frame()
        page.notice.seconds shouldBe 10
        advanceTimeBy(1)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `same direction restarts expiry from the latest seek`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        advanceTimeBy(800)
        page.notice.show(10)
        page.frame()
        advanceTimeBy(200)
        page.frame()
        page.notice.seconds shouldBe 20
        advanceTimeBy(800)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `rapid same direction taps accumulate to thirty`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        page.notice.show(10)
        page.frame()
        page.notice.show(10)
        page.frame()
        page.notice.seconds shouldBe 30
        page.notice.forward shouldBe true
    }

    @Test
    fun `a direction change starts a fresh total on the other edge`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        page.notice.show(10)
        page.frame()
        page.notice.seconds shouldBe 20
        page.notice.show(-10)
        page.frame()
        page.notice.seconds shouldBe -10
        page.notice.forward shouldBe false
    }

    @Test
    fun `an expired series restarts the total from a single tap`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        advanceTimeBy(1000)
        page.frame()
        page.notice.seconds shouldBe null
        page.notice.show(10)
        page.frame()
        page.notice.seconds shouldBe 10
    }

    @Test
    fun `a boundary tap that moved nothing keeps the honest total`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        // At the very end of the reel the seek is clamped, so nothing new was actually moved.
        page.notice.show(0)
        page.frame()
        page.notice.seconds shouldBe 10
        page.notice.forward shouldBe true
    }

    @Test
    fun `the edge survives expiry so the exit animation cannot jump sides`() = withComposition { page ->
        page.notice.show(-10)
        page.frame()
        page.notice.forward shouldBe false
        advanceTimeBy(1000)
        page.frame()
        page.notice.seconds shouldBe null
        page.notice.forward shouldBe false
    }

    @Test
    fun `expiry followed by equal text in the same frame cannot leave a permanent notice`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        advanceTimeBy(1000)
        runCurrent()
        page.notice.seconds shouldBe null
        // The null is never composed: this tap restores the previous display text before a frame.
        page.notice.show(10)
        page.frame()
        advanceTimeBy(1000)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `a new seek before the next frame cannot be cleared by the older expiry`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        advanceTimeBy(999)
        page.notice.show(10)
        // The newer event has not been composed when the older expiry resumes.
        advanceTimeBy(1)
        runCurrent()
        page.notice.seconds shouldBe 20
        page.frame()
        advanceTimeBy(1000)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `opposite direction cancels the older expiry`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        advanceTimeBy(800)
        page.notice.show(-10)
        page.frame()
        advanceTimeBy(200)
        page.frame()
        page.notice.seconds shouldBe -10
        advanceTimeBy(800)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `inactive page clears its notice and starts a clean next visit`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        page.active = false
        page.frame()
        page.notice.seconds shouldBe null
        page.active = true
        page.frame()
        page.notice.show(-10)
        page.frame()
        advanceTimeBy(1000)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `item rebind clears the old notice without expiring the new one`() = withComposition { page ->
        val oldNotice = page.notice
        oldNotice.show(10)
        page.frame()
        advanceTimeBy(500)
        page.itemId = "second"
        page.frame()
        oldNotice.seconds shouldBe null
        page.notice.show(-10)
        page.frame()
        advanceTimeBy(500)
        page.frame()
        page.notice.seconds shouldBe -10
        advanceTimeBy(500)
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `picture in picture clears the notice`() = withComposition { page ->
        page.notice.show(10)
        page.frame()
        page.pip = true
        page.frame()
        page.notice.seconds shouldBe null
    }

    @Test
    fun `disposal clears the notice and cancels expiry`() = withComposition { page ->
        val notice = page.notice
        notice.show(10)
        page.frame()
        page.dispose()
        runCurrent()
        notice.seconds shouldBe null
    }

    private fun withComposition(block: suspend TestScope.(NoticeComposition) -> Unit) = runTest {
        val page = NoticeComposition(this)
        try {
            page.frame()
            block(page)
        } finally {
            page.dispose()
            runCurrent()
        }
    }

    private class NoticeComposition(private val scope: TestScope) {
        var itemId by mutableStateOf("first")
        var active by mutableStateOf(true)
        var pip by mutableStateOf(false)
        lateinit var notice: ReelsSeekNotice
            private set
        private val clock = BroadcastFrameClock()
        private val recomposer = Recomposer(scope.coroutineContext + clock)
        private val runner = scope.backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        private val composition = Composition(NoOpApplier(), recomposer)

        init {
            composition.setContent { notice = rememberReelsSeekNotice(itemId, active, pip) }
        }

        fun frame() {
            Snapshot.sendApplyNotifications()
            scope.runCurrent()
            clock.sendFrame(scope.testScheduler.currentTime * 1_000_000)
            scope.runCurrent()
        }

        fun dispose() {
            composition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }

    private class NoOpApplier : AbstractApplier<Unit>(Unit) {
        override fun onClear() = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
    }
}
