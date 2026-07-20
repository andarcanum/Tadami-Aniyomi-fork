package eu.kanade.presentation.easteregg.aurora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Геометрия «Созвездия» должна оставаться совместимой со старой
 * 3×3 панелью (те же узлы 1..9 и «мосты»), иначе ранее загаданные
 * сигили перестанут проходить криптопроверку.
 */
class AuroraConstellationGeometryTest {

    @Test
    fun starsStayInsideField() {
        for (cell in 1..9) {
            val x = AuroraConstellationGeometry.starX(cell)
            val y = AuroraConstellationGeometry.starY(cell)
            assertTrue("star $cell x=$x", x in 0.05f..0.95f)
            assertTrue("star $cell y=$y", y in 0.05f..0.95f)
        }
    }

    @Test
    fun starsDoNotOverlap() {
        for (a in 1..9) {
            for (b in (a + 1)..9) {
                val dx = AuroraConstellationGeometry.starX(a) - AuroraConstellationGeometry.starX(b)
                val dy = AuroraConstellationGeometry.starY(a) - AuroraConstellationGeometry.starY(b)
                val dist = sqrt(dx * dx + dy * dy)
                assertTrue("stars $a and $b too close: $dist", dist > 0.15f)
            }
        }
    }

    @Test
    fun hitStarAtCenterReturnsCell() {
        for (cell in 1..9) {
            val hit = AuroraConstellationGeometry.hitStar(
                AuroraConstellationGeometry.starX(cell),
                AuroraConstellationGeometry.starY(cell),
            )
            assertEquals(cell, hit)
        }
    }

    @Test
    fun hitStarOutsideFieldReturnsNull() {
        assertNull(AuroraConstellationGeometry.hitStar(-1f, -1f))
        assertNull(AuroraConstellationGeometry.hitStar(2f, 2f))
    }

    @Test
    fun bridgeMatchesPatternLockSemantics() {
        assertEquals(5, AuroraConstellationGeometry.bridgeCell(2, 8))
        assertEquals(5, AuroraConstellationGeometry.bridgeCell(1, 9))
        assertEquals(5, AuroraConstellationGeometry.bridgeCell(3, 7))
        assertEquals(2, AuroraConstellationGeometry.bridgeCell(1, 3))
        assertNull(AuroraConstellationGeometry.bridgeCell(1, 2))
        assertNull(AuroraConstellationGeometry.bridgeCell(5, 6))
    }

    @Test
    fun registerStarInsertsBridge() {
        val path = mutableListOf(2)
        assertTrue(AuroraConstellationGeometry.registerStar(8, path))
        assertEquals(listOf(2, 5, 8), path)
    }

    @Test
    fun registerStarIgnoresDuplicatesAndNull() {
        val path = mutableListOf(2)
        assertFalse(AuroraConstellationGeometry.registerStar(2, path))
        assertFalse(AuroraConstellationGeometry.registerStar(null, path))
        assertEquals(listOf(2), path)
    }

    @Test
    fun threadBreaksOnlyWhenRushing() {
        // Спокойное ведение: ~1/8 поля за кадр — нить держится
        assertFalse(AuroraConstellationGeometry.threadBreaks(0.002f, 16L))
        assertFalse(AuroraConstellationGeometry.threadBreaks(0.05f, 16L))
        // Рывок через пол-поля за кадр — нить рвётся
        assertTrue(AuroraConstellationGeometry.threadBreaks(0.5f, 16L))
        // Некорректное время — не рвём
        assertFalse(AuroraConstellationGeometry.threadBreaks(1f, 0L))
    }
}
