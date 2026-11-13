package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.test.assertIcuSafeRegexPattern
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind

class AiSubtitleTranslationProviderTest {
    @Test
    fun `protected ass tag regex keeps its braces escaped for the Android ICU engine`() {
        assertIcuSafeRegexPattern(subtitleAssBlockRegex.pattern)
    }

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

    @Test
    fun `fingerprint reflects client fingerprint changes dynamically`() {
        var currentFingerprint = "fp-1"
        val provider = AiSubtitleTranslationProvider(
            client = object : AiSubtitleTranslationClient {
                override val fingerprint: String
                    get() = currentFingerprint

                override suspend fun translateIndexedSegments(
                    prompt: String,
                    segments: List<IndexedSubtitleSegment>,
                    sourceLanguage: String,
                    targetLanguage: String,
                ): String = ""
            },
        )

        assertEquals("ai-subtitle-fp-1-v2", provider.fingerprint)
        currentFingerprint = "fp-2"
        assertEquals("ai-subtitle-fp-2-v2", provider.fingerprint)
    }

    @Test
    fun `fails fast without retry on blank key IllegalStateException and non-retryable attempt errors`() = runTest {
        val errors: List<Throwable> = listOf(
            IllegalStateException("AI subtitle translation API key is not configured"),
            TranslationAttemptException(TranslationFailure(TranslationFailureKind.CONFIGURATION, "Bad model")),
            TranslationAttemptException(TranslationFailure(TranslationFailureKind.AUTHORIZATION, "HTTP 401")),
            TranslationAttemptException(TranslationFailure(TranslationFailureKind.QUOTA, "Quota exceeded")),
            TranslationAttemptException(TranslationFailure(TranslationFailureKind.REFUSED, "Content refused")),
        )

        for (error in errors) {
            var attempts = 0
            val provider = AiSubtitleTranslationProvider(
                client = object : AiSubtitleTranslationClient {
                    override val fingerprint: String = "fp"

                    override suspend fun translateIndexedSegments(
                        prompt: String,
                        segments: List<IndexedSubtitleSegment>,
                        sourceLanguage: String,
                        targetLanguage: String,
                    ): String {
                        attempts++
                        throw error
                    }
                },
                maxAttemptsPerChunk = 3,
            )

            val result = provider.translate(
                cues = listOf(SubtitleCue(1, 0, 1000, "Hello")),
                sourceLanguage = "en",
                targetLanguage = "ru",
            )

            val failure = result as SubtitleTranslationProviderResult.Failure
            assertFalse(failure.retryable, "Expected retryable=false for $error")
            assertEquals(1, attempts, "Expected no retries for $error")
        }
    }

    @Test
    fun `retries transient 503 and rate limit failures then succeeds`() = runTest {
        var attempts = 0
        val provider = AiSubtitleTranslationProvider(
            client = object : AiSubtitleTranslationClient {
                override val fingerprint: String = "fp"

                override suspend fun translateIndexedSegments(
                    prompt: String,
                    segments: List<IndexedSubtitleSegment>,
                    sourceLanguage: String,
                    targetLanguage: String,
                ): String {
                    attempts++
                    return when (attempts) {
                        1 -> throw TranslationAttemptException(
                            TranslationFailure(TranslationFailureKind.TRANSIENT, "HTTP 503"),
                        )
                        2 -> throw TranslationAttemptException(
                            TranslationFailure(TranslationFailureKind.RATE_LIMIT, "HTTP 429"),
                        )
                        else -> "<s i=\"1\">Privet</s>"
                    }
                }
            },
            maxAttemptsPerChunk = 3,
        )

        val result = provider.translate(
            cues = listOf(SubtitleCue(1, 0, 1000, "Hello")),
            sourceLanguage = "en",
            targetLanguage = "ru",
        ) as SubtitleTranslationProviderResult.Success

        assertEquals(3, attempts)
        assertEquals("Privet", result.translatedByIndex[1])
    }
}
