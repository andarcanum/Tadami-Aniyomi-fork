package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReadingSpeedTrackerTest {

    @Test
    fun `should return null when there are less than 2 transitions`() {
        val tracker = ReadingSpeedTracker(maxHistorySize = 5)
        tracker.getAverageSpeedSeconds().shouldBeNull()

        tracker.addPageTransition(1000L)
        tracker.getAverageSpeedSeconds().shouldBeNull()
    }

    @Test
    fun `should calculate correct average speed for valid transitions`() {
        val tracker = ReadingSpeedTracker(maxHistorySize = 5)

        // Page 1 at 0s
        tracker.addPageTransition(0L)
        // Page 2 at 10s (duration = 10s)
        tracker.addPageTransition(10000L)
        // Page 3 at 22s (duration = 12s)
        tracker.addPageTransition(22000L)

        // Average should be (10 + 12) / 2 = 11.0 seconds
        tracker.getAverageSpeedSeconds() shouldBe 11.0
    }

    @Test
    fun `should ignore transitions exceeding maxGapMs`() {
        val tracker = ReadingSpeedTracker(maxHistorySize = 5, maxGapMs = 120000L)

        // Page 1 at 0s
        tracker.addPageTransition(0L)
        // Page 2 at 10s (duration = 10s - valid)
        tracker.addPageTransition(10000L)
        // Page 3 at 150s (duration = 140s - invalid, exceeds 120s)
        tracker.addPageTransition(150000L)
        // Page 4 at 165s (duration = 15s - valid)
        tracker.addPageTransition(165000L)

        // Valid transitions: 10s and 15s. Average: (10 + 15) / 2 = 12.5 seconds
        tracker.getAverageSpeedSeconds() shouldBe 12.5
    }

    @Test
    fun `should respect maxHistorySize`() {
        val tracker = ReadingSpeedTracker(maxHistorySize = 2)

        tracker.addPageTransition(0L)
        tracker.addPageTransition(10000L) // 10s
        tracker.addPageTransition(20000L) // 10s
        tracker.addPageTransition(45000L) // 25s - now history has [10000, 20000, 45000]

        // Average of last 2 transitions: (10s + 25s) / 2 = 17.5 seconds
        tracker.getAverageSpeedSeconds() shouldBe 17.5
    }
}
