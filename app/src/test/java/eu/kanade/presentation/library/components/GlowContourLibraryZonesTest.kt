package eu.kanade.presentation.library.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GlowContourLibraryZonesTest {

    @Test
    fun `zone layer spec uses expected source geometry`() {
        resolveGlowContourZoneLayerSpec().let { spec ->
            spec.svgWidth shouldBe 256f
            spec.svgHeight shouldBe 269f
            spec.shellPathData.isNotBlank() shouldBe true
            spec.accentPathData.isNotBlank() shouldBe true
            spec.progressPathData.isNotBlank() shouldBe true
        }
    }

    @Test
    fun `zone layer spec keeps three zone derivations fixed`() {
        resolveGlowContourZoneLayerSpec().let { spec ->
            spec.posterDerivation shouldBe GlowContourZoneDerivation.SHELL_MINUS_ACCENT
            spec.accentDerivation shouldBe GlowContourZoneDerivation.ACCENT_MINUS_PROGRESS
            spec.progressDerivation shouldBe GlowContourZoneDerivation.PROGRESS
        }
    }
}
