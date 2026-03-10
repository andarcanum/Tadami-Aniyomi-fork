package eu.kanade.presentation.browse

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretHallOrbitalSpecsTest {

    @Test
    fun `public stub does not expose real orbital specs`() {
        val specs = buildSecretHallOrbitalSpecs(nameCount = 10)

        assertTrue(specs.isEmpty())
    }

    @Test
    fun `public stub never renders an active electron`() {
        assertFalse(secretHallShouldRenderActiveElectron(SecretHallNamePhase.Emerge))
        assertFalse(secretHallShouldRenderActiveElectron(SecretHallNamePhase.Hold))
        assertFalse(secretHallShouldRenderActiveElectron(SecretHallNamePhase.Burn))
        assertFalse(secretHallShouldRenderActiveElectron(SecretHallNamePhase.Ash))
        assertFalse(secretHallShouldRenderActiveElectron(SecretHallNamePhase.BetweenNames))
    }

    @Test
    fun `public stub exposes no visible orbit radii`() {
        val radii = secretHallVisibleOrbitRadiusFactors(emptyList())

        assertEquals(emptyList(), radii)
    }
}
