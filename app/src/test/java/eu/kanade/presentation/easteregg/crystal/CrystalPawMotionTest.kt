package eu.kanade.presentation.easteregg.crystal

import org.junit.jupiter.api.Test
import kotlin.math.abs

class CrystalPawMotionTest {
    @Test
    fun birthsAreFivePrintsSpacedBy860Milliseconds() {
        check(CrystalPawMotion.PAW_COUNT == 5)
        val births = floatArrayOf(0f, 860f, 1720f, 2580f, 3440f)
        for (index in births.indices) {
            val birth = births[index]
            check(CrystalPawMotion.opacityAt(birth - 1f, index) == 0f)
            check(CrystalPawMotion.opacityAt(birth, index) == 0f)
            checkClose(CrystalPawMotion.opacityAt(birth + 150f, index), 0.09f)
            checkClose(CrystalPawMotion.opacityAt(birth + 300f, index), 0.18f)
        }
        check(CrystalPawMotion.opacityAt(4000f, 5) == 0f)
    }

    @Test
    fun enterAndFadeMatchApprovedQuinticEnvelope() {
        check(CrystalPawMotion.opacityAt(0f, 0) == 0f)
        checkClose(CrystalPawMotion.opacityAt(75f, 0), 0.0186328125f)
        checkClose(CrystalPawMotion.opacityAt(150f, 0), 0.09f)
        checkClose(CrystalPawMotion.opacityAt(300f, 0), 0.18f)
        checkClose(CrystalPawMotion.opacityAt(1049f, 0), 0.18f)
        checkClose(CrystalPawMotion.opacityAt(1050f, 0), 0.18f)
        checkClose(CrystalPawMotion.opacityAt(1362.5f, 0), 0.1613671875f)
        checkClose(CrystalPawMotion.opacityAt(1675f, 0), 0.09f)
        checkClose(CrystalPawMotion.opacityAt(1987.5f, 0), 0.0186328125f)
        check(CrystalPawMotion.opacityAt(2300f, 0) == 0f)
    }

    @Test
    fun eachPrintLives2300MillisecondsWithoutReappearingInThePause() {
        for (index in 0 until 5) {
            val end = index * 860f + 2300f
            check(CrystalPawMotion.opacityAt(end - 10f, index) > 0f)
            check(CrystalPawMotion.opacityAt(end, index) == 0f)
            check(CrystalPawMotion.opacityAt(end + 1f, index) == 0f)
        }
        check(CrystalPawMotion.CYCLE_MS == 16_000f)
        check(CrystalPawMotion.CYCLE_MS - 5740f > 10_000f)
        for (time in 5740..16_000) {
            check(visibleCount(time.toFloat()) == 0) { "Unexpected paw in the clean pause at $time ms" }
        }
    }

    @Test
    fun noMoreThanThreePrintsAreVisibleTogether() {
        var maximum = 0
        for (time in 0 until 16_000) {
            val count = visibleCount(time.toFloat())
            check(count <= 3) { "Too many paws at $time ms: $count" }
            maximum = maxOf(maximum, count)
        }
        check(maximum == 3)
        check(visibleCount(1740f) == 3)
        check(visibleCount(3441f) == 3)
    }

    @Test
    fun cycleAndRouteResetOnlyWhileAllPrintsAreInvisible() {
        check(CrystalPawMotion.localTimeMs(15_999f) == 15_999f)
        check(CrystalPawMotion.localTimeMs(16_000f) == 0f)
        check(CrystalPawMotion.localTimeMs(16_150f) == 150f)
        for (cycle in 1..3) {
            val boundary = cycle * 16_000f
            check(CrystalPawMotion.routeVariant(boundary - 1f) == (cycle - 1) % 3)
            check(CrystalPawMotion.routeVariant(boundary) == cycle % 3)
            check(visibleCount(boundary - 1f) == 0)
            check(visibleCount(boundary) == 0)
            checkClose(CrystalPawMotion.opacityAt(boundary + 150f, 0), 0.09f)
            for (time in floatArrayOf(0f, 150f, 300f, 1740f, 3441f, 5730f, 5740f, 15_999f)) {
                for (index in 0 until 5) {
                    checkClose(
                        CrystalPawMotion.opacityAt(boundary + time, index),
                        CrystalPawMotion.opacityAt(time, index),
                    )
                }
            }
        }
    }

    @Test
    fun negativeTimeAndInvalidIndicesStayInvisibleAndAlphaIsClamped() {
        for (time in floatArrayOf(-Float.MAX_VALUE, -16_000f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            check(CrystalPawMotion.localTimeMs(time) == 0f)
            check(CrystalPawMotion.routeVariant(time) == 0)
            check(visibleCount(time) == 0)
        }
        check(CrystalPawMotion.opacityAt(300f, -1) == 0f)
        check(CrystalPawMotion.opacityAt(300f, 5) == 0f)
        for (time in 0..48_000 step 13) {
            for (index in 0 until 5) {
                val alpha = CrystalPawMotion.opacityAt(time.toFloat(), index)
                check(alpha.isFinite() && alpha in 0f..0.18f)
            }
        }
    }

    private fun visibleCount(timeMs: Float): Int =
        (0 until CrystalPawMotion.PAW_COUNT).count { CrystalPawMotion.opacityAt(timeMs, it) > 0f }

    private fun checkClose(actual: Float, expected: Float) {
        check(abs(actual - expected) < 0.00001f) { "Expected $expected, got $actual" }
    }
}
