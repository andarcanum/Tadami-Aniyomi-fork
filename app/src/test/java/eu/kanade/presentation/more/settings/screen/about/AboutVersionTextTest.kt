package eu.kanade.presentation.more.settings.screen.about

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AboutVersionTextTest {

    @Test
    fun `version subtitle returns original text when not primed`() {
        val stable = "Stable 1.0.0 (2026-03-09)"

        assertEquals(stable, buildAboutVersionSubtitle(stable, isPrimed = false))
    }

    @Test
    fun `version subtitle returns glitch text when primed`() {
        val stable = "Stable 1.0.0 (2026-03-09)"

        val glitched = buildAboutVersionSubtitle(stable, isPrimed = true)

        assertNotEquals(stable, glitched)
        assertTrue(glitched.contains("S"))
        assertTrue(glitched.any { it.code > 0x0300 })
    }
}
