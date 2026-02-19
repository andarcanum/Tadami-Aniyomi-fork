package eu.kanade.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuroraInstantTabSwitchingTest {

    @Test
    fun `swipe left moves to next tab when threshold reached`() {
        val decision = resolveInstantTabSwitch(
            currentIndex = 1,
            lastIndex = 2,
            totalDragPx = -160f,
            switchThresholdPx = 120f,
        )

        assertEquals(2, decision.targetIndex)
        assertFalse(decision.shouldBounceEdge)
    }

    @Test
    fun `swipe right moves to previous tab when threshold reached`() {
        val decision = resolveInstantTabSwitch(
            currentIndex = 1,
            lastIndex = 2,
            totalDragPx = 180f,
            switchThresholdPx = 120f,
        )

        assertEquals(0, decision.targetIndex)
        assertFalse(decision.shouldBounceEdge)
    }

    @Test
    fun `swipe beyond first tab keeps tab and triggers bounce`() {
        val decision = resolveInstantTabSwitch(
            currentIndex = 0,
            lastIndex = 2,
            totalDragPx = 180f,
            switchThresholdPx = 120f,
        )

        assertEquals(0, decision.targetIndex)
        assertTrue(decision.shouldBounceEdge)
    }

    @Test
    fun `swipe beyond last tab keeps tab and triggers bounce`() {
        val decision = resolveInstantTabSwitch(
            currentIndex = 2,
            lastIndex = 2,
            totalDragPx = -180f,
            switchThresholdPx = 120f,
        )

        assertEquals(2, decision.targetIndex)
        assertTrue(decision.shouldBounceEdge)
    }

    @Test
    fun `small drag keeps current tab without bounce`() {
        val decision = resolveInstantTabSwitch(
            currentIndex = 1,
            lastIndex = 2,
            totalDragPx = -40f,
            switchThresholdPx = 120f,
        )

        assertEquals(1, decision.targetIndex)
        assertFalse(decision.shouldBounceEdge)
    }
}
