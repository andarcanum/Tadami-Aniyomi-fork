package eu.kanade.tachiyomi.ui.reels.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class ReelsScrubGestureTest {
    private val slop = 8f
    private val width = 1000f

    private fun ReelsScrubGesture.progressMillis(): Int = (progress * 1000f).roundToInt()

    @Test
    fun `tap without movement commits the pressed fraction`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = 250f, width = width)

        gesture.isActive shouldBe true
        gesture.end() shouldBe 0.25f
        gesture.isActive shouldBe false
    }

    @Test
    fun `vertical movement cancels the press without a commit`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = 250f, width = width)

        gesture.move(dx = 0f, dy = 24f, consumedByOther = false) shouldBe ReelsScrubOutcome.Cancelled
        gesture.isActive shouldBe false
        gesture.end() shouldBe null
    }

    @Test
    fun `movement consumed by the pager cancels the press`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = 250f, width = width)

        gesture.move(dx = 30f, dy = 0f, consumedByOther = true) shouldBe ReelsScrubOutcome.Cancelled
        gesture.end() shouldBe null
    }

    @Test
    fun `horizontal drag follows the finger and commits once`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = 100f, width = width)

        gesture.move(dx = 30f, dy = 2f, consumedByOther = false) shouldBe ReelsScrubOutcome.Owned
        gesture.progressMillis() shouldBe 130
        gesture.move(dx = 20f, dy = 0f, consumedByOther = false) shouldBe ReelsScrubOutcome.Owned
        gesture.progressMillis() shouldBe 150

        (gesture.end()!! * 1000f).roundToInt() shouldBe 150
        gesture.end() shouldBe null
    }

    @Test
    fun `sub-slop movement stays pending and commits the down position`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = 100f, width = width)

        gesture.move(dx = 3f, dy = 1f, consumedByOther = false) shouldBe ReelsScrubOutcome.Pending
        gesture.end() shouldBe 0.1f
    }

    @Test
    fun `travel accumulated before the slop is applied once`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = 100f, width = width)

        gesture.move(dx = 9f, dy = 1f, consumedByOther = false) shouldBe ReelsScrubOutcome.Owned
        gesture.progressMillis() shouldBe 109
        gesture.move(dx = 1f, dy = 0f, consumedByOther = false) shouldBe ReelsScrubOutcome.Owned
        gesture.progressMillis() shouldBe 110
    }

    @Test
    fun `progress is clamped to the bar`() {
        val gesture = ReelsScrubGesture(slop)
        gesture.start(x = -50f, width = width)
        gesture.progress shouldBe 0f

        gesture.move(dx = -500f, dy = 0f, consumedByOther = false) shouldBe ReelsScrubOutcome.Owned
        gesture.progress shouldBe 0f

        gesture.move(dx = 5000f, dy = 0f, consumedByOther = false) shouldBe ReelsScrubOutcome.Owned
        gesture.progress shouldBe 1f
        gesture.end() shouldBe 1f
    }
}
