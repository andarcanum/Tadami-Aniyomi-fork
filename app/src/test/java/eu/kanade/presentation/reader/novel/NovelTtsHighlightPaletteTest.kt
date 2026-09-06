package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

/**
 * The approved V4 auto-contrast palette: the theme accent adapts to the luminance of the reader
 * background so the spoken paragraph stays visible on paper, parchment and dark surfaces alike.
 */
class NovelTtsHighlightPaletteTest {

    // Aurora Prime: light-scheme primary (dark green) and dark-scheme primary (lime).
    private val lightAccent = Color(0xFFB6F04C)
    private val darkAccent = Color(0xFF5A9E00)
    private val creamBackground = Color(0xFFF6EEDC)
    private val amoledBackground = Color(0xFF000000)

    @Test
    fun `a light accent is darkened on a light background`() {
        val palette = resolveNovelTtsHighlightPalette(lightAccent, creamBackground)

        palette.accentBar shouldBe lerp(lightAccent, Color.Black, 0.25f)
    }

    @Test
    fun `a light accent is kept as-is on a dark background`() {
        val palette = resolveNovelTtsHighlightPalette(lightAccent, amoledBackground)

        palette.accentBar shouldBe lightAccent
    }

    @Test
    fun `a dark accent is lightened on a dark background`() {
        val palette = resolveNovelTtsHighlightPalette(darkAccent, amoledBackground)

        palette.accentBar shouldBe lerp(darkAccent, Color.White, 0.35f)
    }

    @Test
    fun `a dark accent is kept as-is on a light background`() {
        val palette = resolveNovelTtsHighlightPalette(darkAccent, creamBackground)

        palette.accentBar shouldBe darkAccent
    }

    @Test
    fun `the paragraph backdrop is the solid accent at 26 percent`() {
        val palette = resolveNovelTtsHighlightPalette(lightAccent, creamBackground)

        palette.paragraphBackground shouldBe palette.accentBar.copy(alpha = 0.26f)
        palette.wordBackground shouldBe palette.accentBar
    }

    @Test
    fun `the word chip text contrasts with the accent`() {
        // Bright lime on a dark background stays bright -> dark ink on the chip.
        val bright = resolveNovelTtsHighlightPalette(lightAccent, amoledBackground)
        bright.wordTextColor shouldBe Color(0xFF141414)

        // A dark accent stays dark on a dark background even after lightening -> white chip text.
        val dark = resolveNovelTtsHighlightPalette(Color(0xFF1A4FE0), amoledBackground)
        dark.wordTextColor shouldBe Color.White
    }

    @Test
    fun `css rgba formatting rounds the alpha to two decimals`() {
        Color(0xFFB6F04C).toCssRgba() shouldBe "rgba(182, 240, 76, 1.0)"
        Color(0xFFB6F04C).copy(alpha = 0.26f).toCssRgba() shouldBe "rgba(182, 240, 76, 0.26)"
    }

    @Test
    fun `the chapter webview css carries the palette colors`() {
        val css = resolveNovelTtsHighlightPalette(lightAccent, creamBackground).toChapterWebViewCss()

        css.paragraphBackgroundRgba shouldContain "rgba("
        css.barInsetRgba shouldBe css.paragraphBackgroundRgba
            .replace("0.26", "1.0")
    }

    @Test
    fun `the book engine override beats the hardcoded gray flow rule`() {
        val css = resolveNovelTtsHighlightPalette(lightAccent, creamBackground).bookEngineOverrideCss()

        css shouldContain "#an-book-content [data-an-tts-highlight]"
        css shouldContain "background-color: rgba("
        css shouldContain "!important"
        css shouldContain "inset 3px 0 0"
    }
}
