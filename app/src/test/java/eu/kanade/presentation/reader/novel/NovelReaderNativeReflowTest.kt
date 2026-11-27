package eu.kanade.presentation.reader.novel

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
class NovelReaderNativeReflowTest {

    @Test
    fun `equal output normalization keeps progress and source anchor live`() = withComposition { reader ->
        reader.pending shouldBe false
        reader.contentPages = portraitPages()
        reader.frame()
        reader.pending shouldBe false

        reader.currentPage = 1
        reader.frame()
        reader.anchor.sourceTextStart shouldBe 100
        resolveAnchoredPageReaderPageIndex(reader.anchor, portraitPages()) shouldBe 1
    }

    @Test
    fun `cancelled landscape acknowledgement cannot latch returned portrait layout`() = withComposition { reader ->
        reader.blockAcknowledgement = true
        reader.viewportSize = IntSize(800, 400)
        reader.contentPages = landscapePages()
        reader.frame()
        reader.pending shouldBe true

        reader.blockAcknowledgement = false
        reader.viewportSize = IntSize(400, 800)
        reader.contentPages = portraitPages()
        reader.frame()
        reader.pending shouldBe false

        reader.currentPage = 1
        reader.frame()
        reader.anchor.sourceTextStart shouldBe 100
        resolveAnchoredPageReaderPageIndex(reader.anchor, portraitPages()) shouldBe 1
    }

    @Test
    fun `round trips issue a fresh layout identity even for equal returned pages`() = withComposition { reader ->
        val firstPortraitIdentity = reader.identity
        reader.viewportSize = IntSize(800, 400)
        reader.contentPages = landscapePages()
        reader.frame()
        reader.pending shouldBe false
        (reader.identity === firstPortraitIdentity) shouldBe false

        reader.viewportSize = IntSize(400, 800)
        reader.contentPages = portraitPages()
        reader.frame()
        reader.pending shouldBe false
        (reader.identity === firstPortraitIdentity) shouldBe false
    }

    @Test
    fun `pager user drag settles before the rejected reflow is acknowledged`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val view = ComposeView(activity)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var pagerState: PagerState
        try {
            activity.setContentView(view)
            view.setContent {
                pagerState = rememberPagerState(initialPage = 1) { 5 }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize())
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            layout(view, 400, 800)
            val releaseUserDrag = CompletableDeferred<Unit>()
            val userDrag = scope.launch {
                pagerState.scroll(MutatePriority.UserInput) { releaseUserDrag.await() }
            }
            shadowOf(Looper.getMainLooper()).idle()
            var restored: Boolean? = null
            val restore = scope.launch { restored = restoreNovelPagerPageAfterUserInput(pagerState, 3) }
            shadowOf(Looper.getMainLooper()).idle()
            restore.isCompleted shouldBe false

            releaseUserDrag.complete(Unit)
            repeat(3) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idle()
                layout(view, 400, 800)
            }
            restore.isCompleted shouldBe true
            restored shouldBe false
            pagerState.currentPage shouldBe 1
            userDrag.isCompleted shouldBe true
        } finally {
            scope.cancel()
            view.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `native resize restores a paragraph normalized entirely above the viewport`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val view = ComposeView(activity)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var listState: LazyListState
        var paragraphHeightPx by mutableIntStateOf(800)
        try {
            activity.setContentView(view)
            view.setContent {
                val density = LocalDensity.current
                listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(with(density) { paragraphHeightPx.toDp() }))
                    }
                    item {
                        Box(Modifier.fillMaxWidth().height(with(density) { 1000.toDp() }))
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            layout(view, 400, 800)
            val initialScroll = scope.launch { listState.scrollToItem(0, 600) }
            shadowOf(Looper.getMainLooper()).idle()
            layout(view, 400, 800)
            initialScroll.isCompleted shouldBe true
            listState.firstVisibleItemIndex shouldBe 0
            listState.firstVisibleItemScrollOffset shouldBe 600

            paragraphHeightPx = 400
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
            layout(view, 800, 400)
            // Apply the old retained pixel offset to the real new-size LazyList. This avoids
            // assuming where Robolectric's window traversal publishes the width remeasurement.
            val retainedPosition = scope.launch { listState.scrollToItem(0, 600) }
            shadowOf(Looper.getMainLooper()).idle()
            layout(view, 800, 400)
            retainedPosition.isCompleted shouldBe true
            listState.firstVisibleItemIndex shouldBe 1
            listState.layoutInfo.visibleItemsInfo.any { it.index == 0 } shouldBe false
            val restore = scope.launch {
                restoreNovelNativeScrollViewportAnchor(
                    listState = listState,
                    anchor = NovelNativeScrollViewportAnchor(0, 0.75f, 400),
                    targetItemIndex = 0,
                )
            }
            shadowOf(Looper.getMainLooper()).idle()
            layout(view, 800, 400)
            restore.isCompleted shouldBe true
            listState.firstVisibleItemIndex shouldBe 0
            listState.firstVisibleItemScrollOffset shouldBe 300

            val releaseUserDrag = CompletableDeferred<Unit>()
            val userDrag = scope.launch {
                listState.scroll(MutatePriority.UserInput) { releaseUserDrag.await() }
            }
            shadowOf(Looper.getMainLooper()).idle()
            var rejectedRestore: Boolean? = null
            val rejected = scope.launch {
                rejectedRestore = restoreNovelNativeScrollViewportAnchor(
                    listState = listState,
                    anchor = NovelNativeScrollViewportAnchor(0, 0.5f, 800),
                    targetItemIndex = 0,
                )
            }
            shadowOf(Looper.getMainLooper()).idle()
            rejected.isCompleted shouldBe true
            rejectedRestore shouldBe false
            releaseUserDrag.complete(Unit)
            shadowOf(Looper.getMainLooper()).idle()
            userDrag.isCompleted shouldBe true
        } finally {
            scope.cancel()
            view.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `side navigation keeps native single and spread text budgets inside the foreground`() {
        val safe = resolveNovelHorizontalSafeInsetsDp(
            cutoutLeftPx = 90,
            cutoutRightPx = 0,
            navBarLeftPx = 0,
            navBarRightPx = 144,
            densityScale = 3f,
        )
        safe.left.value shouldBe 30f
        safe.right.value shouldBe 48f
        // 1080 - 90 - 144 - 96 = 750; each spread foreground gets 846/2 - 96 = 327.
        resolveNovelSpreadColumnTextWidth(1080, 96, 1, 90, 144) shouldBe 750
        resolveNovelSpreadColumnTextWidth(1080, 96, 2, 90, 144) shouldBe 327
    }

    @Test
    fun `opting out of camera guard cannot remove horizontal navigation protection`() {
        val safe = resolveNovelHorizontalSafeInsetsDp(
            cutoutLeftPx = 0,
            cutoutRightPx = 0,
            navBarLeftPx = 0,
            navBarRightPx = 144,
            densityScale = 3f,
        )
        safe.left.value shouldBe 0f
        safe.right.value shouldBe 48f
        resolveNovelSpreadColumnTextWidth(1080, 96, 1, 0, 144) shouldBe 840
        resolveNovelSpreadColumnTextWidth(1080, 96, 2, 0, 144) shouldBe 372
    }

    private fun withComposition(block: suspend TestScope.(ReaderComposition) -> Unit) = runTest {
        val reader = ReaderComposition(this)
        try {
            reader.frame()
            block(reader)
        } finally {
            reader.dispose()
            runCurrent()
        }
    }

    private class ReaderComposition(private val scope: TestScope) {
        var contentPages by mutableStateOf(portraitPages(), referentialEqualityPolicy())
        var viewportSize by mutableStateOf(IntSize(400, 800))
        var currentPage by mutableStateOf(0)
        var blockAcknowledgement by mutableStateOf(false)
        private var acknowledgedIdentity by mutableStateOf<Any?>(null)
        var anchor by mutableStateOf(NovelPageReaderAnchor(0, 0))
            private set
        lateinit var identity: Any
            private set
        var pending: Boolean = true
            private set
        private val acknowledgement = CompletableDeferred<Unit>()
        private val clock = BroadcastFrameClock()
        private val recomposer = Recomposer(scope.coroutineContext + clock)
        private val runner = scope.backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        private val composition = Composition(NoOpApplier(), recomposer)

        init {
            composition.setContent {
                val pages = contentPages
                val activeIdentity = rememberNovelPageReaderLayoutIdentity(
                    ownerKey = "chapter",
                    contentPages = pages,
                    viewportSize = viewportSize,
                    spreadColumns = 1,
                    rendererRoute = NovelPageReaderRendererRoute.COMPOSE_PAGER,
                )
                val shouldBlock = blockAcknowledgement
                identity = activeIdentity
                val reflowPending = acknowledgedIdentity !== activeIdentity
                pending = reflowPending
                LaunchedEffect(activeIdentity) {
                    if (shouldBlock) acknowledgement.await()
                    acknowledgedIdentity = activeIdentity
                }
                val settledPage = currentPage
                LaunchedEffect(settledPage, reflowPending) {
                    if (!reflowPending) {
                        anchor = resolveNextPageReaderAnchor(anchor, pages, settledPage, 1)!!
                    }
                }
            }
        }

        fun frame() {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                scope.runCurrent()
                clock.sendFrame(scope.testScheduler.currentTime * 1_000_000)
                scope.runCurrent()
            }
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

    companion object {
        private fun portraitPages() = listOf(page(0, 100), page(100, 200), page(200, 300))
        private fun landscapePages() = listOf(page(0, 200), page(200, 300))
        private fun page(start: Int, end: Int) = NovelPageContentPage(
            blocks = listOf(
                NovelPageContentBlock.Plain(
                    sourceBlockIndex = 0,
                    text = "x".repeat(end - start),
                    sourceTextStart = start,
                    sourceTextEndExclusive = end,
                    spacingBeforePx = 0,
                    firstLineIndentEm = null,
                ),
            ),
        )

        private fun layout(view: View, width: Int, height: Int) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
        }
    }
}
