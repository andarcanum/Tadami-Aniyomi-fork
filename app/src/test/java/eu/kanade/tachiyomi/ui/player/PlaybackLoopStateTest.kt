package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaybackLoopStateTest {
    @Test
    fun `AbLoopState advances from empty to A, then B, then clears`() {
        val empty = AbLoopState()
        val withA = empty.advance(30.0)
        withA.pointA shouldBe 30.0
        withA.pointB shouldBe null
        withA.isLooping shouldBe false

        val withAB = withA.advance(45.0)
        withAB.pointA shouldBe 30.0
        withAB.pointB shouldBe 45.0
        withAB.isLooping shouldBe true

        val cleared = withAB.advance(50.0)
        cleared shouldBe AbLoopState()
    }

    @Test
    fun `formatBufferingCaption formats percentage and speed cleanly`() {
        formatBufferingCaption(42, 2_500_000L) shouldBe "42% · 2.4 MB/s"
        formatBufferingCaption(75, null) shouldBe "75%"
        formatBufferingCaption(100, 512 * 1024L) shouldBe "512 KB/s"
        formatBufferingCaption(0, 0L) shouldBe null
        formatVideoZoomPercent(0f) shouldBe 100
        formatVideoZoomPercent(1f) shouldBe 200
    }
}
