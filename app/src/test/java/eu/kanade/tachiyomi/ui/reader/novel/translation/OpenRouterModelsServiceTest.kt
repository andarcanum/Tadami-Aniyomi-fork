package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterModelsServiceTest {

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
    fun `loads only free model ids from models endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":[
                  {"id":"google/gemma-3-27b-it:free"},
                  {"id":"deepseek/deepseek-chat-v3-0324:free"},
                  {"id":"openai/gpt-4.1-mini"}
                ]}
                """.trimIndent(),
            ),
        )
        val service = OpenRouterModelsService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val models = service.fetchFreeModels(
            baseUrl = server.url("/api/v1").toString().trimEnd('/'),
            apiKey = "test-key",
        )

        models shouldBe listOf(
            "deepseek/deepseek-chat-v3-0324:free",
            "google/gemma-3-27b-it:free",
        )
        server.takeRequest().path shouldBe "/api/v1/models"
    }
}
