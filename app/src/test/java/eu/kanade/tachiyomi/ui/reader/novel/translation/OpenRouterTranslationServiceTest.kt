package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterTranslationServiceTest {

    private val server = MockWebServer()

    @BeforeEach
    fun setup() {
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `parses xml translation from chat completions response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s><s i='1'>Mir</s>"}}]}""",
            ),
        )
        val service = OpenRouterTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello", "World"),
            params = OpenRouterTranslationParams(
                baseUrl = server.url("/api/v1").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "google/gemma-3-27b-it:free",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe listOf("Privet", "Mir")
        val request = server.takeRequest()
        request.path shouldBe "/api/v1/chat/completions"
        request.body.readUtf8().shouldContain("\"stream\":false")
    }

    @Test
    fun `rejects non free model id`() = runTest {
        val service = OpenRouterTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = OpenRouterTranslationParams(
                baseUrl = server.url("/api/v1").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "openai/gpt-4.1-mini",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated.shouldBeNull()
        server.requestCount shouldBe 0
    }
}
