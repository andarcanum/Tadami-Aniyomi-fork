package eu.kanade.presentation.components

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraAsymmetricTabSpringTest {

    @Test
    fun `moving right uses trailing left and leading right stiffness`() {
        val (left, right) = resolveAsymmetricTabEdgeStiffness(isMovingRight = true)
        left shouldBe AURORA_TAB_TRAILING_STIFFNESS
        right shouldBe AURORA_TAB_LEADING_STIFFNESS
        left shouldBeLessThan right
    }

    @Test
    fun `moving left uses leading left and trailing right stiffness`() {
        val (left, right) = resolveAsymmetricTabEdgeStiffness(isMovingRight = false)
        left shouldBe AURORA_TAB_LEADING_STIFFNESS
        right shouldBe AURORA_TAB_TRAILING_STIFFNESS
        left shouldBeGreaterThan right
    }

    @Test
    fun `move tracker keeps sticky direction across repeated reads`() {
        val tracker = AsymmetricTabMoveTracker(initialIndex = 0)
        tracker.onSelected(1) shouldBe true
        // Same selection again must not flip direction
        tracker.onSelected(1) shouldBe true
        tracker.onSelected(0) shouldBe false
        tracker.onSelected(0) shouldBe false
        tracker.onSelected(2) shouldBe true
    }

    @Test
    fun `stretch radius factor is 1 at rest and lower when elongated`() {
        resolveAsymmetricTabStretchRadiusFactor(drawWidth = 100f, restWidth = 100f) shouldBe 1f
        val stretched = resolveAsymmetricTabStretchRadiusFactor(drawWidth = 180f, restWidth = 100f)
        stretched shouldBeLessThan 1f
        stretched shouldBeGreaterThan 0.54f
    }
}
