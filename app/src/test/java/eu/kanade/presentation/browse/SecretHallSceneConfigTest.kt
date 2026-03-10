package eu.kanade.presentation.browse

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretHallSceneConfigTest {

    @Test
    fun `public stub parser ignores secret payloads and returns safe placeholder config`() {
        val config = parseSecretHallSceneConfig("""{"names":["real-secret"]}""")

        assertTrue(config.isStub)
        assertTrue(config.names.isEmpty())
        assertEquals(secretHallPublicStubTitle(), config.content.title)
    }

    @Test
    fun `fallback config stays inert in public builds`() {
        val config = secretHallSceneFallback()

        assertTrue(config.isStub)
        assertFalse(config.isEnabled)
        assertTrue(config.names.isEmpty())
        assertTrue(config.content.title.isNotBlank())
        assertEquals(secretHallPublicStubTitle(), config.content.title)
        assertEquals(0L, config.timing.totalCycleDurationMs)
    }
}
