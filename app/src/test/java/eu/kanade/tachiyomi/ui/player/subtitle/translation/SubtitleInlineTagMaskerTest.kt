package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.test.assertIcuSafeRegexPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleInlineTagMaskerTest {

    @Test
    fun `ass block regex keeps its braces escaped for the Android ICU engine`() {
        assertIcuSafeRegexPattern(SubtitleInlineTagMasker.assBlock.pattern)
    }

    @Test
    fun `masks and restores ass and html tags`() {
        val original = "{\\an8}<i>Hello</i> world"
        val masked = SubtitleInlineTagMasker.mask(original)
        assertEquals(3, masked.tokens.size)
        assertTrue("masked text must not contain raw tags", !masked.text.contains("{") && !masked.text.contains("<"))

        // Simulate a translator that keeps placeholders and changes the words.
        val translated = masked.text.replace("Hello", "Privet").replace("world", "mir")
        val restored = SubtitleInlineTagMasker.restore(translated, masked)
        assertEquals("{\\an8}<i>Privet</i> mir", restored)
    }

    @Test
    fun `returns null when a placeholder is dropped`() {
        val masked = SubtitleInlineTagMasker.mask("<b>Bold</b>")
        // Drop everything (model removed the placeholder).
        val restored = SubtitleInlineTagMasker.restore("Bold translated", masked)
        assertNull(restored)
    }

    @Test
    fun `plain text round trips untouched`() {
        val masked = SubtitleInlineTagMasker.mask("Just text")
        assertEquals("Just text [x]", SubtitleInlineTagMasker.restore("Just text [x]", masked))
    }
}
