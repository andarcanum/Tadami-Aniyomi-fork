package eu.kanade.tachiyomi.network.interceptor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareClearanceWaiterTest {

    @Test
    fun doesNotReleaseOnFirstPageFinishedWithoutCookie() {
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, maxWaitMs = 1_000)
        val released = waiter.onPageFinished { false }
        assertFalse(released)
        assertFalse(waiter.bypassed)
        assertFalse(waiter.shouldRelease)
    }

    @Test
    fun bypassesImmediatelyWhenCookiePresentOnPageFinished() {
        val waiter = CloudflareClearanceWaiter()
        assertTrue(waiter.onPageFinished { true })
        assertTrue(waiter.bypassed)
        assertTrue(waiter.shouldRelease)
    }

    @Test
    fun keepsWaitingAfterPageFinishedWithoutReleasing() {
        // The resolver ignores HTTP errors and keeps polling; the waiter must not transition
        // to release on its own until the cookie appears or the timeout is reached.
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, maxWaitMs = 1_000)
        waiter.onPageFinished { false }
        assertFalse(waiter.shouldRelease)
    }

    @Test
    fun releasesAtTimeoutWhenCookieNeverAppears() {
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, maxWaitMs = 1_000)
        waiter.onPageFinished { false }
        var ticks = 0
        while (!waiter.shouldRelease && ticks < 100) {
            waiter.tick { false }
            ticks++
        }
        assertTrue(waiter.shouldRelease)
        assertFalse(waiter.bypassed)
        // 1_000 / 100 = 10 ticks to reach the timeout.
        assertTrue(ticks >= 10)
    }

    @Test
    fun bypassesWhenCookieAppearsDuringPolling() {
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, maxWaitMs = 10_000)
        var cookie = false
        waiter.onPageFinished { cookie }
        assertFalse(waiter.bypassed)
        waiter.tick { cookie }
        cookie = true
        assertTrue(waiter.tick { cookie })
        assertTrue(waiter.bypassed)
    }

    @Test
    fun doesNotBypassOnProbePresenceWithoutCookie() {
        // И1 regression guard: the resolver must NOT release on widget/turnstile *presence*.
        // Only the cookie (or timeout) may end the wait. This models the resolver firing
        // onPageFinished several times (page + redirects) while the auto-solve cookie is not
        // yet set, and asserts the waiter stays non-bypassed until the cookie actually flips.
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, maxWaitMs = 10_000)
        var cookie = false
        // Resolver now probes the widget DOM on every page finish but must not release on it:
        // the waiter only knows about the cookie, so several page-finished events with no
        // cookie must keep it waiting.
        repeat(3) {
            waiter.onPageFinished { cookie }
            assertFalse(waiter.bypassed)
            assertFalse(waiter.shouldRelease)
        }
        // Multiple false ticks (poll loop) also must not release early.
        repeat(5) {
            waiter.tick { cookie }
            assertFalse(waiter.bypassed)
            assertFalse(waiter.shouldRelease)
        }
        // Only the cookie appearing flips the state -- the exact transition the resolver's
        // poller relies on after removing the premature widget-based release.
        cookie = true
        assertTrue(waiter.tick { cookie })
        assertTrue(waiter.bypassed)
        assertTrue(waiter.shouldRelease)
    }

    @Test
    fun softLimitReachesOnlyAtSoftTimeoutNotHard() {
        // softLimitMs=700 (7 ticks of 100), maxWaitMs=2_000 (20 ticks). The soft limit must
        // become visible at ~7 ticks while shouldRelease stays false until the hard limit.
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, softLimitMs = 700, maxWaitMs = 2_000)
        repeat(6) {
            waiter.tick { false }
            assertFalse(waiter.softLimitReached)
            assertFalse(waiter.shouldRelease)
        }
        waiter.tick { false } // 7th tick -> elapsed 700
        assertTrue(waiter.softLimitReached)
        assertFalse(waiter.shouldRelease)
        // Still no release until the hard limit.
        repeat(10) { waiter.tick { false } } // 17 total -> elapsed 1_700
        assertFalse(waiter.shouldRelease)
    }

    @Test
    fun bypassedDuringStageTwoWinsOverSoftLimit() {
        // Models stage 1 timeout (soft limit reached, no cookie), then the cookie appearing
        // during stage 2/between polls: bypassed must flip and shouldRelease must become true
        // immediately, so the resolver treats it as a success rather than an interactive fail.
        val waiter = CloudflareClearanceWaiter(pollIntervalMs = 100, softLimitMs = 700, maxWaitMs = 10_000)
        var cookie = false
        repeat(7) { waiter.tick { cookie } } // past soft limit, no cookie yet
        assertTrue(waiter.softLimitReached)
        assertFalse(waiter.bypassed)
        cookie = true
        assertTrue(waiter.tick { cookie })
        assertTrue(waiter.bypassed)
        assertTrue(waiter.shouldRelease)
    }
}
