package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
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

class AirforceTranslationServiceTest {

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
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello", "World"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
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

        translated shouldBe listOf("Privet", "Mir")
        val request = server.takeRequest()
        request.path shouldBe "/v1/chat/completions"
        val body = request.body.readUtf8()
        body.shouldContain("\"stream\":false")
        body.shouldContain("\"max_tokens\":4096")
    }

    @Test
    fun `parses array content format`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"choices":[{"message":{"content":[{"type":"text","text":"<s i='0'>One</s>"}]}}]}
                """.trimIndent(),
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("One"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
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

        translated shouldBe listOf("One")
    }

    @Test
    fun `falls back to choice text when message content is empty`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"choices":[{"index":0,"message":{"role":"assistant","content":""},"text":"<s i='0'>Privet</s>","finish_reason":"stop"}]}
                """.trimIndent(),
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
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

        translated shouldBe listOf("Privet")
    }

    @Test
    fun `trims non xml tail after translated segments`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"choices":[{"message":{"content":"<s i='0'>Privet</s>\nNeed proxies cheaper than the market?\nhttps://op.wtf"}}]}
                """.trimIndent(),
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
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

        translated shouldBe listOf("Privet")
    }

    @Test
    fun `stops after first http 429 rate limit without retry`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody(
                    """{"error":{"message":"Global rate limit exceeded. Try again in 0.1 seconds.","type":"rate_limit_exceeded","code":"429"}}""",
                ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}""",
            ),
        )

        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            retryDelay = { },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
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

        translated shouldBe null
        server.takeRequest().path shouldBe "/v1/chat/completions"
        server.requestCount shouldBe 1
    }

    @Test
    fun `logs finish reason and usage when candidate text is empty`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"choices":[{"index":0,"message":{"role":"assistant","content":""},"finish_reason":"stop"}],"usage":{"prompt_tokens":120,"completion_tokens":0,"total_tokens":120}}
                """.trimIndent(),
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )
        val logs = mutableListOf<String>()

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "openai/gpt-4.1-mini",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
            onLog = logs::add,
        )

        translated shouldBe null
        logs.joinToString("\n").shouldContain("finish_reason=stop")
        logs.joinToString("\n").shouldContain("completion_tokens=0")
    }

    @Test
    fun `falls back to stream response when non stream content is empty`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"choices":[{"index":0,"message":{"role":"assistant","content":""},"finish_reason":"stop"}],"usage":{"prompt_tokens":120,"completion_tokens":0,"total_tokens":120}}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"<s i='0'>Pri"}}]}

                    data: {"choices":[{"delta":{"content":"vet</s>"}}]}

                    data: [DONE]
                    """.trimIndent(),
                ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v3.2-thinking",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe listOf("Privet")
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        firstRequest.body.readUtf8().shouldContain("\"stream\":false")
        secondRequest.body.readUtf8().shouldContain("\"stream\":true")
    }

    @Test
    fun `logs api error payload when body contains error object`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"error":{"message":"Invalid API key","type":"invalid_request_error","code":"401"}}""",
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )
        val logs = mutableListOf<String>()

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v3.2-thinking",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
            onLog = logs::add,
        )

        translated shouldBe null
        logs.joinToString("\n").shouldContain("Airforce API error 401: Invalid API key")
    }

    @Test
    fun `stops when stream fallback returns rate limit banner`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"choices":[{"index":0,"message":{"role":"assistant","content":""},"finish_reason":"stop"}]}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                data: {"choices":[{"delta":{"content":"Need proxies cheaper than the market?\nhttps://op.wtf\ndiscord.gg/airforce"}}]}

                data: [DONE]
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}""",
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            retryDelay = { },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v3.2-thinking",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe null
        server.takeRequest().body.readUtf8().shouldContain("\"stream\":false")
        server.takeRequest().body.readUtf8().shouldContain("\"stream\":true")
        server.requestCount shouldBe 2
    }

    @Test
    fun `returns null when assistant text has no xml blocks`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"Plain answer without XML"}}]}""",
            ),
        )
        val service = AirforceTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = AirforceTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v3.2-thinking",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe null
    }
}
