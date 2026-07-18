package eu.kanade.presentation.theme

import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.easteregg.aurora.AuroraPrimeColors
import eu.kanade.presentation.theme.colorscheme.AuroraColorScheme
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class AuroraPrimeOverlayTest {

    private val live = AuroraPrimeColors(
        primary = Color(0xFF64FFDA),
        secondary = Color(0xFF7C4DFF),
        accent = Color(0xFFFF6E9C),
        background = Color(0xFF050B14),
        surface = Color(0xFF0A1626),
    )

    @Test
    fun `light mode keeps bright surfaces and only overlays accents`() {
        val base = AuroraColorScheme.getColorScheme(isDark = false, isAmoled = false)
        val result = applyAuroraPrimeOverlay(
            base = base,
            live = live,
            isAmoled = false,
            isDark = false,
        )

        result.background shouldBe base.background
        result.surface shouldBe base.surface
        result.onBackground shouldBe base.onBackground
        result.onSurface shouldBe base.onSurface
        result.surfaceVariant shouldBe base.surfaceVariant
        result.onSurfaceVariant shouldBe base.onSurfaceVariant

        result.primary shouldBe live.primary
        result.secondary shouldBe live.secondary
        result.tertiary shouldBe live.accent
        result.surfaceTint shouldBe live.primary
    }

    @Test
    fun `dark mode applies living night surfaces from payload`() {
        val base = AuroraColorScheme.getColorScheme(isDark = true, isAmoled = false)
        val result = applyAuroraPrimeOverlay(
            base = base,
            live = live,
            isAmoled = false,
            isDark = true,
        )

        result.background shouldBe live.background
        result.surface shouldBe live.surface
        result.onBackground shouldBe Color(0xFFDCEBFF)
        result.onSurface shouldBe Color(0xFFDCEBFF)
        result.primary shouldBe live.primary
        result.secondary shouldBe live.secondary
        result.tertiary shouldBe live.accent
    }

    @Test
    fun `dark amoled keeps base black surfaces`() {
        val base = AuroraColorScheme.getColorScheme(isDark = true, isAmoled = true)
        val result = applyAuroraPrimeOverlay(
            base = base,
            live = live,
            isAmoled = true,
            isDark = true,
        )

        result.background shouldBe base.background
        result.surface shouldBe base.surface
        result.background shouldNotBe live.background
        result.primary shouldBe live.primary
    }
}
