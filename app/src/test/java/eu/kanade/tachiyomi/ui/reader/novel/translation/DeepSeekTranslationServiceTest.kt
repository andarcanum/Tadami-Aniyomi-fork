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

class DeepSeekTranslationServiceTest {

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
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { mode ->
                when (mode) {
                    GeminiPromptMode.CLASSIC -> "classic_system"
                    GeminiPromptMode.ADULT_18 -> "adult_system"
                }
            },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello", "World"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-chat",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.ADULT_18,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe listOf("Privet", "Mir")
        val request = server.takeRequest()
        request.path shouldBe "/chat/completions"
        val body = request.body.readUtf8()
        body.shouldContain("\"stream\":false")
        body.shouldContain("adult_system")
    }

    @Test
    fun `returns null when model is blank`() = runTest {
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "",
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
