package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Watch-qualification rules (acceptance FY09–FY16). A fake clock drives everything: no
 * `Thread.sleep`, no real player.
 */
class ReelsInteractionTrackerTest {

    private val tracker = ReelsInteractionTracker(watchThresholdMs = 5_000L, coverageThreshold = 0.8f)

    /** Feeds `count` samples spaced [stepMs] apart, advancing the media position 1:1. */
    private fun play(
        itemId: String,
        fromMs: Long,
        count: Int,
        stepMs: Long = 250L,
        durationMs: Long? = 60_000L,
        eligible: Boolean = true,
    ): ReelsInteractionTracker.WatchEvent? {
        var event: ReelsInteractionTracker.WatchEvent? = null
        repeat(count) { index ->
            val now = fromMs + (index + 1) * stepMs
            val position = (index + 1) * stepMs
            event = tracker.onSample(itemId, now, position, eligible) ?: event
        }
        return event
    }

    @Test
    fun `READY but paused or buffering never accrues watch time`() {
        tracker.startVisit("a", durationMs = 60_000L, nowMs = 0L)
        tracker.onFirstFrame("a")

        // 20 s of samples with eligible = false: paused / buffering / backgrounded.
        val event = play("a", fromMs = 0L, count = 80, eligible = false)

        event shouldBe null
        val snapshot = tracker.snapshot("a")
        snapshot?.watchedMs shouldBe 0L
        snapshot?.qualified shouldBe false
    }

    @Test
    fun `time before the first frame does not count`() {
        tracker.startVisit("a", durationMs = 60_000L, nowMs = 0L)

        // Eligible, but the player never presented a frame: nothing may accrue.
        play("a", fromMs = 0L, count = 40)
        tracker.snapshot("a")?.watchedMs shouldBe 0L

        tracker.onFirstFrame("a")
        // Only the interval AFTER the frame counts (the preceding 10 s are not credited).
        tracker.onSample("a", 10_250L, 10_250L, eligible = true)
        tracker.snapshot("a")?.watchedMs shouldBe 250L
    }

    @Test
    fun `five seconds of foreground playback qualifies on the final card`() {
        tracker.startVisit("last", durationMs = 120_000L, nowMs = 0L)
        tracker.onFirstFrame("last")

        val event = play("last", fromMs = 0L, count = 20)

        event shouldNotBe null
        event?.itemId shouldBe "last"
        event?.watchedMs shouldBe 5_000L
    }

    @Test
    fun `a short clip qualifies by unique coverage before the time threshold`() {
        tracker.startVisit("short", durationMs = 3_000L, nowMs = 0L)
        tracker.onFirstFrame("short")

        val event = play("short", fromMs = 0L, count = 12, durationMs = 3_000L)

        event shouldNotBe null
        (event?.coverage ?: 0f) shouldBe 1f
        ((event?.watchedMs ?: Long.MAX_VALUE) < 5_000L) shouldBe true
    }

    @Test
    fun `seek straight to the end does not fabricate coverage`() {
        tracker.startVisit("seek", durationMs = 60_000L, nowMs = 0L)
        tracker.onFirstFrame("seek")

        // Two seconds of real playback, then a jump to the last second.
        play("seek", fromMs = 0L, count = 8)
        val event = tracker.onSample("seek", nowMs = 2_250L, positionMs = 59_000L, eligible = true)

        event shouldBe null
        val snapshot = tracker.snapshot("seek")
        snapshot?.qualified shouldBe false
        ((snapshot?.coverage ?: 1f) < 0.1f) shouldBe true
    }

    @Test
    fun `repeated short loops do not inflate unique coverage`() {
        tracker.startVisit("loop", durationMs = 10_000L, nowMs = 0L)
        tracker.onFirstFrame("loop")

        // Five passes over the first two seconds of a ten-second clip: 10 s of playback,
        // but only 2 s of unique media.
        var now = 0L
        repeat(5) {
            for (position in longArrayOf(250L, 750L, 1_250L, 1_750L, 2_000L)) {
                now += 250L
                tracker.onSample("loop", now, position, eligible = true)
            }
        }

        val snapshot = tracker.snapshot("loop")
        ((snapshot?.coverage ?: 1f) < 0.8f) shouldBe true
        // 6 s of real playback qualify by TIME, never by the inflated coverage a loop counter
        // would produce.
        snapshot?.watchedMs shouldBe 6_250L
        snapshot?.qualified shouldBe true
    }

    @Test
    fun `a late callback from the previous binding cannot credit the new item`() {
        tracker.startVisit("a", durationMs = 60_000L, nowMs = 0L)
        tracker.onFirstFrame("a")

        // The pager moved on: a fresh visit replaced the old one.
        tracker.startVisit("b", durationMs = 60_000L, nowMs = 1_000L)

        // Late samples still carrying the old item id must be dropped, not credited to "b".
        repeat(20) { index ->
            tracker.onSample("a", nowMs = 1_250L + index * 250L, positionMs = 500L, eligible = true)
        }

        tracker.snapshot("b")?.watchedMs shouldBe 0L
        tracker.snapshot("a") shouldBe null
    }

    @Test
    fun `a watch is reported exactly once per visit`() {
        tracker.startVisit("once", durationMs = 120_000L, nowMs = 0L)
        tracker.onFirstFrame("once")

        val first = play("once", fromMs = 0L, count = 20)
        first shouldNotBe null

        // Duplicate player callbacks after the threshold must not emit again.
        repeat(40) { index ->
            tracker.onSample("once", 5_000L + (index + 1) * 250L, 5_000L + (index + 1) * 250L, true) shouldBe null
        }
    }

    @Test
    fun `ending a visit returns the final values and clears it`() {
        tracker.startVisit("end", durationMs = 60_000L, nowMs = 0L)
        tracker.onFirstFrame("end")
        play("end", fromMs = 0L, count = 8)

        val snapshot = tracker.endVisit("end", nowMs = 2_250L, positionMs = 2_250L)

        snapshot?.itemId shouldBe "end"
        snapshot?.watchedMs shouldBe 2_000L
        tracker.snapshot("end") shouldBe null
    }
}
