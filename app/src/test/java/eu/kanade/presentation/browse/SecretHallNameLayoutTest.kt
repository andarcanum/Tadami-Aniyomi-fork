package eu.kanade.presentation.browse

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SecretHallNameLayoutTest {

    @Test
    fun `public stub exposes only generic unavailable copy`() {
        assertEquals("Secret Hall unavailable", secretHallPublicStubTitle())
        assertEquals(
            "This build does not include the local Secret Hall implementation.",
            secretHallPublicStubMessage(),
        )
    }
}
