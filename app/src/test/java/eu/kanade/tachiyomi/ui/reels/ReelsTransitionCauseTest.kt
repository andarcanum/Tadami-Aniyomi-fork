package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Gesture → transition classification.
 *
 * The pager cannot answer "was this a user decision?" on its own: a fling releases the finger
 * while the pager is still on the origin page, and an auto-advance arrives exactly like a
 * swipe. Only a real drag END inside [ReelsForYou.USER_NAV_WINDOW_MS] turns a settled-page
 * change into a skip, which is what keeps already-swiped reels out of the next generation.
 */
class ReelsTransitionCauseTest {

    @Test
    fun `a fresh drag forward is a user forward`() {
        ReelsForYou.resolveTransitionCause(
            fromPage = 0,
            toPage = 3,
            nowMs = 10_000L,
            lastUserNavAtMs = 10_000L - (ReelsForYou.USER_NAV_WINDOW_MS - 1),
        ) shouldBe ReelsTransitionCause.USER_FORWARD
    }

    @Test
    fun `a drag older than the window is programmatic`() {
        ReelsForYou.resolveTransitionCause(
            fromPage = 0,
            toPage = 1,
            nowMs = 10_000L,
            lastUserNavAtMs = 10_000L - ReelsForYou.USER_NAV_WINDOW_MS,
        ) shouldBe ReelsTransitionCause.PROGRAMMATIC
    }

    @Test
    fun `a page change without any recorded gesture stays programmatic`() {
        ReelsForYou.resolveTransitionCause(
            fromPage = 0,
            toPage = 2,
            nowMs = 10_000L,
            lastUserNavAtMs = 0L,
        ) shouldBe ReelsTransitionCause.PROGRAMMATIC
    }

    @Test
    fun `a startup restore inside the first window is not a skip`() {
        // elapsedRealtime() is small right after boot; a restore that settles at 800 ms must
        // not be mistaken for a swipe just because "0" looks recent.
        ReelsForYou.resolveTransitionCause(
            fromPage = 0,
            toPage = 5,
            nowMs = 800L,
            lastUserNavAtMs = 0L,
        ) shouldBe ReelsTransitionCause.PROGRAMMATIC
    }

    @Test
    fun `a recent drag backwards is a user backward`() {
        ReelsForYou.resolveTransitionCause(
            fromPage = 5,
            toPage = 4,
            nowMs = 10_000L,
            lastUserNavAtMs = 9_900L,
        ) shouldBe ReelsTransitionCause.USER_BACKWARD
    }

    @Test
    fun `a drag that did not move the page is not a skip`() {
        ReelsForYou.resolveTransitionCause(
            fromPage = 2,
            toPage = 2,
            nowMs = 10_000L,
            lastUserNavAtMs = 9_900L,
        ) shouldBe ReelsTransitionCause.PROGRAMMATIC
    }
}
