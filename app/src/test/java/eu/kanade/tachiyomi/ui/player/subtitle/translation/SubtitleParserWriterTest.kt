package eu.kanade.tachiyomi.ui.player.subtitle.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtitleParserWriterTest {
    @Test
    fun `parses and writes srt cues`() {
        val raw = """
            1
            00:00:01,500 --> 00:00:03,000
            Hello world

            2
            00:00:04,000 --> 00:00:05,250
            Second line
        """.trimIndent()

        val document = SubtitleParser.parse(raw, "srt")

        assertEquals(SubtitleFormat.Srt, document.format)
        assertEquals(2, document.cues.size)
        assertEquals(1500L, document.cues[0].startMs)
        assertEquals("Hello world", document.cues[0].text)
        assertTrue(SubtitleWriter.write(document).contains("00:00:01,500 --> 00:00:03,000"))
    }

    @Test
    fun `parses and writes vtt cues with settings`() {
        val raw = """
            WEBVTT

            00:00:01.000 --> 00:00:02.500 align:start position:10%
            Hello VTT
        """.trimIndent()

        val document = SubtitleParser.parse(raw, "vtt")

        assertEquals(SubtitleFormat.Vtt, document.format)
        assertEquals(1, document.cues.size)
        assertEquals("align:start position:10%", document.cues[0].settings)
        assertTrue(SubtitleWriter.write(document).startsWith("WEBVTT"))
    }

    @Test
    fun `parses and writes ass dialogue preserving header`() {
        val raw = """
            [Script Info]
            Title: Test
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\NASS
        """.trimIndent()

        val document = SubtitleParser.parse(raw, "ass")

        assertEquals(SubtitleFormat.Ass, document.format)
        assertEquals(1, document.cues.size)
        assertEquals("Hello\nASS", document.cues[0].text)
        assertTrue(SubtitleWriter.write(document).contains("Dialogue:"))
    }
}
