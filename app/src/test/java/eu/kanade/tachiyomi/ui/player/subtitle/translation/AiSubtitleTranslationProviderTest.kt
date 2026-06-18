package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiSubtitleTranslationProviderTest {
    @Test
    fun `parses indexed ai response and ignores prose`() {
        val raw = """
            Sure, here is the translation:
            <s i="1">Привет</s>
            <s i='2'>Мир</s>
        """.trimIndent()

        val parsed = parseIndexedSubtitleTranslationResponse(raw)

        assertEquals(mapOf(1 to "Привет", 2 to "Мир"), parsed)
    }

    @Test
    fun `parses escaped xml text from ai response`() {
        val raw = "<s i=\"3\">Tom &amp; Jerry &lt;3</s>"

        val parsed = parseIndexedSubtitleTranslationResponse(raw)

        assertEquals("Tom & Jerry <3", parsed[3])
    }

    @Test
    fun `unconfigured ai provider fails safely`() = runTest {
        val provider = AiSubtitleTranslationProvider(client = null)

        val result = provider.translate(
            cues = listOf(SubtitleCue(1, 0, 1000, "Hello")),
            sourceLanguage = "en",
            targetLanguage = "ru",
        )

        assertTrue(result is SubtitleTranslationProviderResult.Failure)
        assertEquals(false, (result as SubtitleTranslationProviderResult.Failure).retryable)
    }

    @Test
    fun `ai provider chunks large cue batches`() = runTest {
        var calls = 0
        val provider = AiSubtitleTranslationProvider(
            client = object : AiSubtitleTranslationClient {
                override val fingerprint = "fake"

                override suspend fun translateIndexedSegments(
                    prompt: String,
                    segments: List<IndexedSubtitleSegment>,
                    sourceLanguage: String,
                    targetLanguage: String,
                ): String {
                    calls += 1
                    return segments.joinToString("\n") { "<s i=\"${it.index}\">${it.text} translated</s>" }
                }
            },
            maxCueChars = 1_000,
        )

        val result = provider.translate(
            cues = listOf(
                SubtitleCue(1, 0, 1000, "hello world".repeat(80)),
                SubtitleCue(2, 1000, 2000, "second line".repeat(80)),
                SubtitleCue(3, 2000, 3000, "third line"),
            ),
            sourceLanguage = "en",
            targetLanguage = "ru",
        )

        result as SubtitleTranslationProviderResult.Success
        assertTrue(calls > 1)
        assertEquals(3, result.translatedByIndex.size)
        assertEquals("third line translated", result.translatedByIndex[3])
    }

    @Test
    fun `ai provider maps translated indexes back to cue indexes`() = runTest {
        val provider = AiSubtitleTranslationProvider(
            client = object : AiSubtitleTranslationClient {
                override val fingerprint = "fake"

                override suspend fun translateIndexedSegments(
                    prompt: String,
                    segments: List<IndexedSubtitleSegment>,
                    sourceLanguage: String,
                    targetLanguage: String,
                ): String {
                    return segments.joinToString("\n") { "<s i=\"${it.index}\">${it.text} translated</s>" }
                }
            },
        )

        val result = provider.translate(
            cues = listOf(
                SubtitleCue(7, 0, 1000, "Hello"),
                SubtitleCue(9, 1000, 2000, "{\\an8}World"),
            ),
            sourceLanguage = "en",
            targetLanguage = "ru",
        )

        result as SubtitleTranslationProviderResult.Success
        assertEquals("Hello translated", result.translatedByIndex[7])
        assertEquals("{\\an8}World translated", result.translatedByIndex[9])
    }
}
