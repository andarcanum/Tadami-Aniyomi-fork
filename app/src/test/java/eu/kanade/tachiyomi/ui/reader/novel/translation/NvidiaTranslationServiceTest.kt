package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NvidiaTranslationServiceTest {

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
    fun `uses chat completions endpoint when base url already points to endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}""",
            ),
        )
        val service = NvidiaTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = NvidiaTranslationParams(
                baseUrl = server.url("/v1/chat/completions").toString(),
                apiKey = "test-key",
                model = "nvidia/test-model",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe listOf("Privet")
        server.takeRequest().path shouldBe "/v1/chat/completions"
    }

    @Test
    fun `falls back to plaintext response when nvidia omits xml tags`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"Privet\n\nMir"}}]}""",
            ),
        )
        val service = NvidiaTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello", "World"),
            params = NvidiaTranslationParams(
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "nvidia/test-model",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
            ),
        )

        translated shouldBe listOf("Privet", "Mir")
    }
}
