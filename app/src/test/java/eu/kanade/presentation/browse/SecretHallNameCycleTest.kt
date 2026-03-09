package eu.kanade.presentation.browse

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SecretHallNameCycleTest {

    private val timing = SecretHallTimingConfig()

    @Test
    fun `public stub cycle never advances through secret phases`() {
        val cycle = SecretHallNameCycle(timing)

        assertEquals(SecretHallNamePhase.BetweenNames, cycle.phaseAt(0L))
        assertEquals(SecretHallNamePhase.BetweenNames, cycle.phaseAt(1_450L))
        assertEquals(SecretHallNamePhase.BetweenNames, cycle.phaseAt(2_800L))
        assertEquals(SecretHallNamePhase.BetweenNames, cycle.phaseAt(4_600L))
    }

    @Test
    fun `public stub cycle keeps name index clamped to zero`() {
        val cycle = SecretHallNameCycle(timing)

        assertEquals(0L, timing.totalCycleDurationMs)
        assertEquals(0, cycle.nameIndexAt(0L, 3))
        assertEquals(0, cycle.nameIndexAt(16_080L, 3))
    }

    @Test
    fun `public stub launched name count stays at zero`() {
        val cycle = SecretHallNameCycle(timing)

        assertEquals(0, cycle.launchedNameCountAt(0L, 3))
        assertEquals(0, cycle.launchedNameCountAt(5_359L, 3))
        assertEquals(0, cycle.launchedNameCountAt(10_720L, 3))
    }
}
