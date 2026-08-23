package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Progress notifications are posted twice per updated entry; with big libraries that is
 * thousands of Binder IPC calls per run. The throttle collapses them to at most one post
 * per interval, while never suppressing the first post of a run.
 */
class ProgressPostThrottleTest {

    @Test
    fun `first post is always allowed`() {
        var now = 1_000L
        val throttle = ProgressPostThrottle(minIntervalMillis = 300L) { now }

        throttle.shouldPostNow() shouldBe true
    }

    @Test
    fun `post inside the interval window is suppressed`() {
        var now = 1_000L
        val throttle = ProgressPostThrottle(minIntervalMillis = 300L) { now }
        throttle.shouldPostNow()

        now += 100L
        throttle.shouldPostNow() shouldBe false
    }

    @Test
    fun `post after the interval window is allowed`() {
        var now = 1_000L
        val throttle = ProgressPostThrottle(minIntervalMillis = 300L) { now }
        throttle.shouldPostNow()

        now += 299L
        throttle.shouldPostNow() shouldBe false

        now += 1L
        throttle.shouldPostNow() shouldBe true
    }

    @Test
    fun `suppressed attempt does not extend the window`() {
        var now = 1_000L
        val throttle = ProgressPostThrottle(minIntervalMillis = 300L) { now }
        throttle.shouldPostNow()

        now += 150L
        throttle.shouldPostNow() shouldBe false
        now += 150L
        throttle.shouldPostNow() shouldBe true
    }

    @Test
    fun `non positive interval posts every time`() {
        var now = 0L
        val throttle = ProgressPostThrottle(minIntervalMillis = 0L) { now }

        repeat(3) {
            now += 0L
            throttle.shouldPostNow() shouldBe true
        }
    }
}
