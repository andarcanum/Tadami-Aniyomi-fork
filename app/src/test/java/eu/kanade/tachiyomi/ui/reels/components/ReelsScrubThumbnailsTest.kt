package eu.kanade.tachiyomi.ui.reels.components

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsScrubThumbnailsTest {

    @Test
    fun `computeScrubThumbnailSize preserves aspect ratio within maxDim`() {
        computeScrubThumbnailSize(1920, 1080, maxDim = 256) shouldBe (256 to 144)
        computeScrubThumbnailSize(1080, 1920, maxDim = 256) shouldBe (144 to 256)
        computeScrubThumbnailSize(0, 0, maxDim = 256) shouldBe (256 to 144)
        computeScrubThumbnailSize(160, 90, maxDim = 256) shouldBe (160 to 90)
    }

    @Test
    fun `thumbnail cache returns exact and nearest frames and evicts LRU entries`() {
        val cache = ReelsScrubThumbnailCache<String>(maxEntries = 3)
        val duration = 60f

        cache.put(fraction = 10f / duration, durationSec = duration, frame = "f10")
        cache.put(fraction = 20f / duration, durationSec = duration, frame = "f20")
        cache.put(fraction = 30f / duration, durationSec = duration, frame = "f30")

        cache.getExact(10f / duration, duration) shouldBe "f10"
        cache.getBestFrame(18f / duration, duration, maxDeltaSec = 5f) shouldBe "f20"

        cache.put(fraction = 40f / duration, durationSec = duration, frame = "f40")
        cache.size() shouldBe 3
        cache.getExact(30f / duration, duration) shouldBe null
        cache.getExact(40f / duration, duration) shouldBe "f40"
    }

    @Test
    fun `thumbnail cache returns frames sorted within filmstrip window`() {
        val cache = ReelsScrubThumbnailCache<String>(maxEntries = 10)
        val duration = 100f

        cache.put(0.10f, duration, "t10")
        cache.put(0.50f, duration, "t50")
        cache.put(0.42f, duration, "t42")
        cache.put(0.58f, duration, "t58")
        cache.put(0.90f, duration, "t90")

        val window = cache.framesInWindow(centerSec = 50f, halfWindowSec = 10f)
        window shouldHaveSize 3
        window.map { it.frame } shouldBe listOf("t42", "t50", "t58")
    }

    @Test
    fun `fine scrub controller enters and exits precision mode with hysteresis`() {
        val controller = ReelsFineScrubController(
            fineEnterLiftPx = 80f,
            fineExitLiftPx = 24f,
            fineWindowMaxSec = 24f,
        )
        controller.start(initialProgress = 0.5f, barWidthPx = 1000f, durationSec = 120f)
        controller.isFineMode shouldBe false

        controller.move(dx = 100f, dy = 0f, allowFineMode = true) shouldBe false
        controller.progress.shouldBeWithinPercentageOf(0.6f, 0.1)

        controller.move(dx = 0f, dy = -90f, allowFineMode = true) shouldBe true
        controller.isFineMode shouldBe true

        val beforeFine = controller.progress
        controller.move(dx = 100f, dy = 0f, allowFineMode = true) shouldBe false
        (controller.progress - beforeFine).shouldBeWithinPercentageOf(0.02f, 0.5)

        controller.move(dx = 0f, dy = 40f, allowFineMode = true) shouldBe false
        controller.isFineMode shouldBe true

        controller.move(dx = 0f, dy = 40f, allowFineMode = true) shouldBe true
        controller.isFineMode shouldBe false
        controller.end()!!.shouldBeWithinPercentageOf(0.62f, 0.5)
    }

    @Test
    fun `fine scrub controller clamps progress and ignores fine mode when disabled`() {
        val controller = ReelsFineScrubController(fineEnterLiftPx = 50f, fineExitLiftPx = 15f)
        controller.start(initialProgress = 0.9f, barWidthPx = 500f, durationSec = 60f)

        controller.move(dx = 200f, dy = -120f, allowFineMode = false) shouldBe false
        controller.isFineMode shouldBe false
        controller.progress shouldBe 1f
        controller.cancel()
        controller.end() shouldBe null
    }
}
