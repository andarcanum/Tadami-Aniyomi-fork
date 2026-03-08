package eu.kanade.presentation.more.settings.screen.about

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AboutEasterEggTypewriterTest {

    @Test
    fun `typewriter timeline reveals characters only after the configured start delay`() {
        assertEquals("", revealTypewriterText("System", elapsedMs = 80, startDelayMs = 120, millisPerChar = 40))
        assertEquals("S", revealTypewriterText("System", elapsedMs = 160, startDelayMs = 120, millisPerChar = 40))
        assertEquals("Sys", revealTypewriterText("System", elapsedMs = 240, startDelayMs = 120, millisPerChar = 40))
    }

    @Test
    fun `typewriter timeline clamps to the full string once reveal duration has elapsed`() {
        assertEquals(
            "Prologue",
            revealTypewriterText("Prologue", elapsedMs = 4_000, startDelayMs = 180, millisPerChar = 35),
        )
    }
}
