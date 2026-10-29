package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDiscoveryExposureTrackerTest {

    private fun testItem(title: String, mediaType: DiscoveryMediaType = DiscoveryMediaType.ANIME) =
        HomeHubDiscoveryItem(
            title = title,
            cleanTitle = title.lowercase(),
            coverUrl = null,
            seedTitle = null,
            reasonPayload = null,
            provider = "test",
            rowType = DiscoveryRowType.TREND,
            mediaType = mediaType,
            genres = emptyList(),
        )

    @Test
    fun `visibility of 50 percent for 1000ms triggers exposure callback`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.50f,
            isSectionActive = true,
            isResumed = true,
        )

        // At 900ms, not exposed yet
        testScheduler.advanceTimeBy(900)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()

        // At 1001ms, exposure is triggered
        testScheduler.advanceTimeBy(101)
        testScheduler.runCurrent()
        exposed shouldBe listOf("card 1")
    }

    @Test
    fun `quick fling under 1000ms cancels timer and does not trigger exposure`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.80f,
            isSectionActive = true,
            isResumed = true,
        )

        // Card is in view for 300ms, then scrolled away (visibility drops to 0)
        testScheduler.advanceTimeBy(300)
        testScheduler.runCurrent()
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.0f,
            isSectionActive = true,
            isResumed = true,
        )

        testScheduler.advanceTimeBy(1000)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()
    }

    @Test
    fun `visibility below 50 percent does not start exposure timer`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.49f, // below 50%
            isSectionActive = true,
            isResumed = true,
        )

        testScheduler.advanceTimeBy(2000)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()
    }

    @Test
    fun `inactive section cancels active timer and prevents exposure`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.60f,
            isSectionActive = true,
            isResumed = true,
        )

        testScheduler.advanceTimeBy(500)
        testScheduler.runCurrent()
        // User switched section tab: isSectionActive becomes false
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.60f,
            isSectionActive = false,
            isResumed = true,
        )

        testScheduler.advanceTimeBy(1000)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()
    }

    @Test
    fun `app going to background onPause cancels exposure timer`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(
            item = item,
            visibleRatio = 0.60f,
            isSectionActive = true,
            isResumed = true,
        )

        testScheduler.advanceTimeBy(500)
        testScheduler.runCurrent()
        tracker.onPauseOrInactive()

        testScheduler.advanceTimeBy(1000)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()
    }

    @Test
    fun `stage focused item records immediately when section and lifecycle active`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Stage Hero")
        tracker.recordStageFocus(item, isSectionActive = true, isResumed = true)

        exposed shouldBe listOf("stage hero")

        // Inactive section rejects stage focus
        val inactiveItem = testItem("Other Hero")
        tracker.recordStageFocus(inactiveItem, isSectionActive = false, isResumed = true)
        exposed shouldBe listOf("stage hero")
    }

    @Test
    fun `duplicate visibility in same session does not re-trigger exposure`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(item, 0.60f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(1001)
        testScheduler.runCurrent()
        exposed shouldBe listOf("card 1")

        // Scrolled away then returned
        tracker.onVisibilityChanged(item, 0.0f, isSectionActive = true, isResumed = true)
        tracker.onVisibilityChanged(item, 0.60f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(2000)
        testScheduler.runCurrent()

        // Still only 1 exposure recorded
        exposed shouldBe listOf("card 1")
    }

    @Test
    fun `injected clock allows re-recording after 15 minute interval`() = runTest {
        var currentTime = 1_000_000L
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(
            scope = this,
            clock = { currentTime },
            onExposure = { exposed.add(it.cleanTitle) },
        )

        val item = testItem("Card 1")
        tracker.onVisibilityChanged(item, 0.60f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(1001)
        testScheduler.runCurrent()
        exposed shouldBe listOf("card 1")

        // 10 minutes later: within dedupe interval, not exposed again
        currentTime += 10 * 60_000L
        tracker.onVisibilityChanged(item, 0.0f, isSectionActive = true, isResumed = true)
        tracker.onVisibilityChanged(item, 0.60f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(1001)
        testScheduler.runCurrent()
        exposed shouldBe listOf("card 1")

        // 16 minutes after first exposure: interval expired, records again
        currentTime += 6 * 60_000L
        tracker.onVisibilityChanged(item, 0.0f, isSectionActive = true, isResumed = true)
        tracker.onVisibilityChanged(item, 0.60f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(1001)
        testScheduler.runCurrent()
        exposed shouldBe listOf("card 1", "card 1")
    }

    @Test
    fun `resume re-triggers exposure when card remains visible`() = runTest {
        val exposed = mutableListOf<String>()
        val tracker = HomeDiscoveryExposureTracker(scope = this, onExposure = { exposed.add(it.cleanTitle) })
        val item = testItem("Card 1")

        // Visible for 500ms
        tracker.onVisibilityChanged(item, 0.70f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(500)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()

        // App paused
        tracker.onVisibilityChanged(item, 0f, isSectionActive = false, isResumed = false)
        testScheduler.advanceTimeBy(1000)
        testScheduler.runCurrent()
        exposed shouldBe emptyList()

        // App resumed without scroll, with remembered visible ratio
        tracker.onVisibilityChanged(item, 0.70f, isSectionActive = true, isResumed = true)
        testScheduler.advanceTimeBy(1001)
        testScheduler.runCurrent()
        exposed shouldBe listOf("card 1")
    }
}
