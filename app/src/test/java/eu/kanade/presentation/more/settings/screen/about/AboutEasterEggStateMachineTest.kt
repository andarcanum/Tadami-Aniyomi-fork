package eu.kanade.presentation.more.settings.screen.about

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AboutEasterEggStateMachineTest {

    private fun machine() = AboutEasterEggStateMachine(
        requiredPrimarySignals = 4,
        primedWindowMs = 2_500L,
        tapStreakWindowMs = 900L,
    )

    @Test
    fun `primary signals report light feedback before primed and strong feedback on priming signal`() {
        val machine = machine()

        val firstTap = machine.onPrimarySignal(0L)
        val secondTap = machine.onPrimarySignal(250L)
        val thirdTap = machine.onPrimarySignal(500L)
        val fourthTap = machine.onPrimarySignal(750L)

        assertEquals(AboutEasterEggTapFeedback.Light, firstTap)
        assertEquals(AboutEasterEggTapFeedback.Light, secondTap)
        assertEquals(AboutEasterEggTapFeedback.Light, thirdTap)
        assertEquals(AboutEasterEggTapFeedback.Primed, fourthTap)
        assertEquals(AboutEasterEggPhase.Primed, machine.phase)
    }

    @Test
    fun `configured primary signal count arms easter egg for limited time`() {
        val machine = machine()

        machine.onPrimarySignal(0L)
        machine.onPrimarySignal(250L)
        machine.onPrimarySignal(500L)
        machine.onPrimarySignal(750L)

        assertEquals(AboutEasterEggPhase.Primed, machine.phase)
        assertTrue(machine.isPrimedAt(3_249L))
        assertFalse(machine.isPrimedAt(3_251L))
    }

    @Test
    fun `secondary signal starts animation only while primed`() {
        val machine = machine()

        machine.onPrimarySignal(0L)
        machine.onPrimarySignal(250L)
        machine.onPrimarySignal(500L)
        machine.onPrimarySignal(750L)

        assertTrue(machine.onSecondarySignal(1_500L))
        assertEquals(AboutEasterEggPhase.GlyphRain, machine.phase)
    }

    @Test
    fun `extra primary signal after exact code cancels primed state`() {
        val machine = machine()

        machine.onPrimarySignal(0L)
        machine.onPrimarySignal(250L)
        machine.onPrimarySignal(500L)
        machine.onPrimarySignal(750L)
        machine.onPrimarySignal(850L)

        assertFalse(machine.onSecondarySignal(1_000L))
        assertEquals(AboutEasterEggPhase.Idle, machine.phase)
    }

    @Test
    fun `secondary signal without primed state does nothing`() {
        val machine = machine()

        assertFalse(machine.onSecondarySignal(0L))
        assertEquals(AboutEasterEggPhase.Idle, machine.phase)
    }

    @Test
    fun `expired primed state resets to idle before secondary signal`() {
        val machine = machine()

        machine.onPrimarySignal(0L)
        machine.onPrimarySignal(250L)
        machine.onPrimarySignal(500L)
        machine.onPrimarySignal(750L)
        machine.tick(3_300L)

        assertFalse(machine.onSecondarySignal(3_300L))
        assertEquals(AboutEasterEggPhase.Idle, machine.phase)
    }

    @Test
    fun `state machine advances through cinematic lifecycle`() {
        val machine = machine()

        machine.onPrimarySignal(0L)
        machine.onPrimarySignal(250L)
        machine.onPrimarySignal(500L)
        machine.onPrimarySignal(750L)
        machine.onSecondarySignal(1_000L)
        machine.onGlyphRainFinished()
        machine.onPageMaterialized()
        machine.dismiss()
        machine.onDismissFinished()

        assertEquals(AboutEasterEggPhase.Idle, machine.phase)
    }
}
