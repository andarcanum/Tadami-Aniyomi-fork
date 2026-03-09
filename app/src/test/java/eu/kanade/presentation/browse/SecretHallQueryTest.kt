package eu.kanade.presentation.browse

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class SecretHallQueryTest {

    @Test
    fun `fallback gate disables secret hall when local implementation is absent`() {
        val gate = createSecretHallGate("eu.kanade.presentation.browse.local.MissingSecretHallGate")

        assertFalse(gate.isSecretHallQuery(null))
        assertFalse(gate.isSecretHallQuery(""))
        assertFalse(gate.isSecretHallQuery("tatakae"))
        assertFalse(gate.isSecretHallQuery("татакаэ"))
    }
}
