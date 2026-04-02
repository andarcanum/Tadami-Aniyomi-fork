package eu.kanade.presentation.entries.components.aurora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import eu.kanade.presentation.entries.manga.components.aurora.tintAuroraCardBackgroundColors
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraCardBackgroundTintTest {

    @Test
    fun `tints each glass background color with the overlay`() {
        val backgroundColors = listOf(
            Color(0xC3FFFFFF),
            Color(0xEEF2F7FD),
        )
        val overlay = Color(0x33FFB300)

        tintAuroraCardBackgroundColors(backgroundColors, overlay) shouldBe listOf(
            overlay.compositeOver(backgroundColors[0]),
            overlay.compositeOver(backgroundColors[1]),
        )
    }
}
