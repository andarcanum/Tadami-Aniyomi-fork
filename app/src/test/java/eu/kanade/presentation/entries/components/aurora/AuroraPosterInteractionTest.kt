package eu.kanade.presentation.entries.components.aurora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraPosterInteractionTest {

    @Test
    fun `dismiss is disabled while poster is zoomed or panned`() {
        assertFalse(shouldAllowPosterDismissal(isZoomed = true, isPanned = false))
        assertFalse(shouldAllowPosterDismissal(isZoomed = false, isPanned = true))
        assertTrue(shouldAllowPosterDismissal(isZoomed = false, isPanned = false))
    }
}
