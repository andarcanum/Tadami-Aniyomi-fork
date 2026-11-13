package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class TranslationHelperTest {

    // ──────────────────────── trimNonXmlTail ────────────────────────

    @Test
    fun `trimNonXmlTail returns source when no XML segments present`() {
        val input = "Hello world, no segments here"
        input.trimNonXmlTail() shouldBe input
    }

    @Test
    fun `trimNonXmlTail strips preamble before first segment`() {
        val input = "Some preamble text\n<s i='0'>First</s><s i='1'>Second</s>"
        input.trimNonXmlTail() shouldBe "<s i='0'>First</s><s i='1'>Second</s>"
    }

    @Test
    fun `trimNonXmlTail strips postamble after last closing tag`() {
        val input = "<s i='0'>First</s>\n\nSome trailing text after"
        input.trimNonXmlTail() shouldBe "<s i='0'>First</s>"
    }

    @Test
    fun `trimNonXmlTail strips both preamble and postamble`() {
        val input = "Preamble\n<s i='0'>Content</s>\nPostamble"
        input.trimNonXmlTail() shouldBe "<s i='0'>Content</s>"
    }

    @Test
    fun `trimNonXmlTail handles double-quoted index attribute`() {
        val input = "Intro\n<s i=\"0\">Text</s>\nTrailing"
        input.trimNonXmlTail() shouldBe "<s i=\"0\">Text</s>"
    }

    // ──────────────────────── extractRetryAfterSeconds ────────────────────────

    @Test
    fun `extractRetryAfterSeconds parses integer seconds`() {
        extractRetryAfterSeconds("Please try again in 30 seconds.") shouldBe 30.0
    }

    @Test
    fun `extractRetryAfterSeconds parses decimal seconds`() {
        extractRetryAfterSeconds("try again in 1.5 seconds") shouldBe 1.5
    }

    @Test
    fun `extractRetryAfterSeconds is case insensitive`() {
        extractRetryAfterSeconds("TRY AGAIN IN 60 SECONDS") shouldBe 60.0
    }

    @Test
    fun `extractRetryAfterSeconds returns null when no match`() {
        extractRetryAfterSeconds("Service unavailable") shouldBe null
    }

    // ──────────────────────── computeRateLimitDelayMs ────────────────────────

    @Test
    fun `computeRateLimitDelayMs uses hint when provided`() {
        val hint = 10.0
        val expected = ((10.0 + 0.3) * 1000.0).toLong()
        computeRateLimitDelayMs(attempt = 1, hintSeconds = hint) shouldBe expected
    }

    @Test
    fun `computeRateLimitDelayMs clamps hint to minimum 1200ms`() {
        computeRateLimitDelayMs(attempt = 1, hintSeconds = 0.0) shouldBe 1_200L
    }

    @Test
    fun `computeRateLimitDelayMs clamps hint to maximum 120000ms`() {
        computeRateLimitDelayMs(attempt = 1, hintSeconds = 200.0) shouldBe 120_000L
    }

    @Test
    fun `computeRateLimitDelayMs uses exponential backoff without hint`() {
        computeRateLimitDelayMs(attempt = 1, hintSeconds = null) shouldBe 2_000L
        computeRateLimitDelayMs(attempt = 2, hintSeconds = null) shouldBe 5_000L
        computeRateLimitDelayMs(attempt = 3, hintSeconds = null) shouldBe 15_000L
        computeRateLimitDelayMs(attempt = 4, hintSeconds = null) shouldBe 60_000L
    }

    // ──────────────────────── extractOpenAiStyleChoiceContent ────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extractOpenAiStyleChoiceContent reads message content string`() {
        val choice = buildJsonObject {
            put(
                "message",
                buildJsonObject {
                    put("content", "Hello translation")
                },
            )
        }
        choice.extractOpenAiStyleChoiceContent() shouldBe "Hello translation"
    }

    @Test
    fun `extractOpenAiStyleChoiceContent falls back to choice content field`() {
        val choice = buildJsonObject {
            put("content", "Fallback text")
        }
        choice.extractOpenAiStyleChoiceContent() shouldBe "Fallback text"
    }

    @Test
    fun `extractOpenAiStyleChoiceContent handles content array and skips thinking blocks`() {
        val choice = buildJsonObject {
            put(
                "message",
                buildJsonObject {
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "thinking")
                                    put("text", "internal thoughts")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", "visible output")
                                },
                            )
                        },
                    )
                },
            )
        }
        choice.extractOpenAiStyleChoiceContent() shouldBe "visible output"
    }

    @Test
    fun `extractOpenAiStyleChoiceContent joins all answer fragments without dropping repeated text`() {
        val choice = json.parseToJsonElement(
            """{ "message": { "content": [ { "type": "text", "text": "First" }, { "type": "thinking", "text": "private" }, { "type": "text", "text": "Second" }, { "type": "text", "text": "Second" } ] } }""",
        ) as kotlinx.serialization.json.JsonObject
        choice.extractOpenAiStyleChoiceContent() shouldBe "FirstSecondSecond"
    }

    @Test
    fun `extractOpenAiStyleChoiceContent returns empty string when choice is empty`() {
        val choice = buildJsonObject {}
        choice.extractOpenAiStyleChoiceContent() shouldBe ""
    }

    // ──────────────────────── computeOpenAiStyleMaxTokens ────────────────────────

    @Test
    fun `computeOpenAiStyleMaxTokens clamps to minimum when segments are tiny`() {
        val segments = listOf("Hi")
        computeOpenAiStyleMaxTokens(segments) shouldBe 4_096
    }

    @Test
    fun `computeOpenAiStyleMaxTokens scales with total segment length`() {
        // ~16000 chars → estimated = 8000 + overhead, clamped to 8192
        val segments = (1..10).map { "a".repeat(1600) }
        computeOpenAiStyleMaxTokens(segments) shouldBe 8_192
    }

    @Test
    fun `computeOpenAiStyleMaxTokens respects custom minTokens and maxTokens`() {
        val segments = listOf("Short")
        computeOpenAiStyleMaxTokens(segments, minTokens = 2_048, maxTokens = 4_096) shouldBe 2_048
    }
}
