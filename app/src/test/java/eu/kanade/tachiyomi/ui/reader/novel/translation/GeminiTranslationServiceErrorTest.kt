package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GeminiTranslationServiceErrorTest {

    @Test
    fun `extracts structured gemini api error details`() {
        val raw = """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""

        extractGeminiApiErrorMessage(raw) shouldBe "RESOURCE_EXHAUSTED: Quota exceeded"
    }

    @Test
    fun `returns null for non json error payload`() {
        extractGeminiApiErrorMessage("not-json").shouldBeNull()
    }

    @Test
    fun `extracts openai style error message`() {
        val raw = """{"error":{"message":"Invalid API key","type":"invalid_request_error"}}"""

        extractOpenAiApiErrorMessage(raw) shouldBe "invalid_request_error: Invalid API key"
    }
}
