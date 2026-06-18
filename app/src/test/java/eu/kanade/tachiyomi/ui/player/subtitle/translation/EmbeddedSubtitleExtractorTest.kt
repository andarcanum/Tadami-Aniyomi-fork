package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EmbeddedSubtitleExtractorTest {
    @Test
    fun `unsupported extractor fails explicitly`() = runTest {
        val extractor = UnsupportedEmbeddedSubtitleExtractor()

        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                extractor.extract(
                    PlayerSubtitleTranslationTrack(
                        id = 1,
                        title = "Embedded",
                        language = "en",
                        url = null,
                        kind = PlayerSubtitleTranslationTrackKind.Embedded,
                        enabled = false,
                    ),
                )
            }
        }
    }

    @Test
    fun `parses ffprobe subtitle stream output`() {
        val raw = """
            {
              "streams": [
                { "index": 2, "tags": { "language": "eng", "title": "English CC" } },
                { "index": 4, "tags": { "language": "jpn" } }
              ]
            }
        """.trimIndent()

        val streams = parseEmbeddedSubtitleProbeOutput(raw)

        org.junit.jupiter.api.Assertions.assertEquals(2, streams.size)
        org.junit.jupiter.api.Assertions.assertEquals("0:2", streams[0].ffmpegSpecifier)
        org.junit.jupiter.api.Assertions.assertEquals("eng", streams[0].language)
        org.junit.jupiter.api.Assertions.assertEquals("English CC", streams[0].title)
        org.junit.jupiter.api.Assertions.assertEquals("0:4", streams[1].ffmpegSpecifier)
    }
}
