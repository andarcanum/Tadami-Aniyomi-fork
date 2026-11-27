package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.viewer.autoscroll.AutoScrollControlLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControlsLevel4Test {

    @Test
    fun `edge guard disabled returns false for all points`() {
        assertEquals(0f, ReaderEdgeGuard.computeGuardPx(0, 3f), 0.001f)
        assertFalse(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 2f,
                rawY = 500f,
                viewLeft = 0,
                viewTop = 0,
                viewWidth = 1080,
                viewHeight = 2400,
                guardPx = 0f,
            ),
        )
    }

    @Test
    fun `edge guard blocks left and right bezel taps but allows center`() {
        val guardPx = ReaderEdgeGuard.computeGuardPx(guardDp = 20, density = 3f) // 60px
        assertEquals(60f, guardPx, 0.001f)

        // Left bezel
        assertTrue(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 45f,
                rawY = 1200f,
                viewLeft = 0,
                viewTop = 0,
                viewWidth = 1080,
                viewHeight = 2400,
                guardPx = guardPx,
            ),
        )
        // Right bezel
        assertTrue(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 1035f,
                rawY = 1200f,
                viewLeft = 0,
                viewTop = 0,
                viewWidth = 1080,
                viewHeight = 2400,
                guardPx = guardPx,
            ),
        )
        // Center tap allowed
        assertFalse(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 540f,
                rawY = 1200f,
                viewLeft = 0,
                viewTop = 0,
                viewWidth = 1080,
                viewHeight = 2400,
                guardPx = guardPx,
            ),
        )
    }

    @Test
    fun `edge guard caps side margin at 15 percent on narrow windows`() {
        // 200px wide window -> max guard per side is 30px even if guardPx = 100px
        assertTrue(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 20f,
                rawY = 200f,
                viewLeft = 0,
                viewTop = 0,
                viewWidth = 200,
                viewHeight = 600,
                guardPx = 100f,
            ),
        )
        assertFalse(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 45f,
                rawY = 200f,
                viewLeft = 0,
                viewTop = 0,
                viewWidth = 200,
                viewHeight = 600,
                guardPx = 100f,
            ),
        )
    }

    @Test
    fun `edge guard respects non-zero view offset`() {
        assertTrue(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 120f,
                rawY = 300f,
                viewLeft = 100,
                viewTop = 50,
                viewWidth = 900,
                viewHeight = 1600,
                guardPx = 40f,
            ),
        )
        assertFalse(
            ReaderEdgeGuard.isInDeadZone(
                rawX = 200f,
                rawY = 300f,
                viewLeft = 100,
                viewTop = 50,
                viewWidth = 900,
                viewHeight = 1600,
                guardPx = 40f,
            ),
        )
    }

    @Test
    fun `auto scroll step speed clamps strictly within 1 to 100`() {
        assertEquals(60, AutoScrollControlLogic.stepSpeed(50, +10))
        assertEquals(40, AutoScrollControlLogic.stepSpeed(50, -10))
        assertEquals(1, AutoScrollControlLogic.stepSpeed(5, -10))
        assertEquals(100, AutoScrollControlLogic.stepSpeed(95, +10))
    }

    @Test
    fun `auto scroll compact badge formats pager seconds and webtoon percent`() {
        assertEquals("10.0s", AutoScrollControlLogic.formatCompactBadge(1, isPagerMode = true))
        assertEquals("2.1s", AutoScrollControlLogic.formatCompactBadge(100, isPagerMode = true))
        assertEquals("50%", AutoScrollControlLogic.formatCompactBadge(50, isPagerMode = false))
    }

    @Test
    fun `double tap zoom multiplier clamps to safe bounds`() {
        assertEquals(1.5f, AutoScrollControlLogic.doubleTapZoomMultiplier(150), 0.001f)
        assertEquals(2.0f, AutoScrollControlLogic.doubleTapZoomMultiplier(200), 0.001f)
        assertEquals(3.0f, AutoScrollControlLogic.doubleTapZoomMultiplier(300), 0.001f)
        assertEquals(1.25f, AutoScrollControlLogic.doubleTapZoomMultiplier(50), 0.001f)
        assertEquals(3.5f, AutoScrollControlLogic.doubleTapZoomMultiplier(900), 0.001f)
    }
}
