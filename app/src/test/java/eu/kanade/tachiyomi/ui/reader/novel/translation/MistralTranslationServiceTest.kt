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

class MistralTranslationServiceTest {

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
    fun `adds reasoning effort for supported mistral models`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}""",
            ),
        )
        val service = MistralTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = MistralTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "mistral-small-latest",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "high",
            ),
        )

        translated shouldBe listOf("Privet")
        val request = server.takeRequest()
        request.path shouldBe "/v1/chat/completions"
        request.body.readUtf8().shouldContain("\"reasoning_effort\":\"high\"")
    }
}
