package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import java.io.IOException

class GeminiModelsServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    @Test
    fun `keeps gemini text models and strips the models prefix`() {
        val raw = """
            {
              "models": [
                {
                  "name": "models/gemini-3-flash-preview",
                  "displayName": "Gemini 3 Flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"],
                  "outputTokenLimit": 65536
                },
                {
                  "name": "models/gemini-3.1-flash-lite-preview",
                  "displayName": "Gemini 3.1 Flash Lite",
                  "supportedGenerationMethods": ["generateContent"],
                  "outputTokenLimit": 65536
                }
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)) shouldBe listOf(
            GeminiModelEntry(id = "gemini-3-flash-preview", displayName = "Gemini 3 Flash"),
            GeminiModelEntry(id = "gemini-3.1-flash-lite-preview", displayName = "Gemini 3.1 Flash Lite"),
        )
    }

    @Test
    fun `drops non gemini models and gemini models without generateContent`() {
        val raw = """
            {
              "models": [
                {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]},
                {"name": "models/imagen-4.0-generate-001", "supportedGenerationMethods": ["predict"]},
                {"name": "models/gemini-3-flash-preview", "supportedGenerationMethods": ["countTokens"]}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).shouldBeEmpty()
    }

    @Test
    fun `drops specialized gemini variants by id substring`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemini-2.5-flash-preview-tts", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-2.5-flash-image-preview", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-2.5-flash-native-audio-dialogue", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-2.5-computer-use-preview", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["generateContent", "embedContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-3-flash-preview", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)) shouldBe listOf(
            GeminiModelEntry(id = "gemini-3-flash-preview", displayName = "gemini-3-flash-preview"),
        )
    }

    @Test
    fun `drops models whose output limit is below the translation max output tokens`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemini-1.5-flash", "displayName": "Gemini 1.5 Flash", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192},
                {"name": "models/gemini-3-pro-preview", "displayName": "Gemini 3 Pro", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)) shouldBe listOf(
            GeminiModelEntry(id = "gemini-3-pro-preview", displayName = "Gemini 3 Pro"),
        )
    }

    @Test
    fun `keeps models with missing output limit, dedupes and sorts by id`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemini-3.5-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3-flash-preview", "displayName": "Gemini 3 Flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3-flash-preview", "displayName": "Gemini 3 Flash dup", "supportedGenerationMethods": ["generateContent"]}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)) shouldBe listOf(
            GeminiModelEntry(id = "gemini-3-flash-preview", displayName = "Gemini 3 Flash"),
            GeminiModelEntry(id = "gemini-3.5-flash", displayName = "gemini-3.5-flash"),
        )
    }

    @Test
    fun `returns empty list for null or malformed payload`() {
        extractGeminiModelEntries(null).shouldBeEmpty()
        extractGeminiModelEntries(payload("not-json")).shouldBeEmpty()
        extractGeminiModelEntries(payload("""{"models": "oops"}""")).shouldBeEmpty()
    }

    @Test
    fun `fetchModels throws on http non-success so callers can log invalid keys`() = runBlocking<Unit> {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403))
            val service = GeminiModelsService(
                client = OkHttpClient(),
                json = Json { ignoreUnknownKeys = true },
            )

            shouldThrow<IOException> {
                service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))
            }.message shouldBe "HTTP 403"
        } finally {
            server.shutdown()
        }
    }
}
