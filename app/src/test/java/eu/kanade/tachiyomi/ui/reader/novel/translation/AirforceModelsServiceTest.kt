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

class AirforceModelsServiceTest {

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
    fun `loads model ids from v1 models`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"openai/gpt-4.1-mini"},{"id":"anthropic/claude-3.5-sonnet"}]}""",
            ),
        )
        val service = AirforceModelsService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val models = service.fetchModels(
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "test-key",
        )

        models shouldBe listOf("anthropic/claude-3.5-sonnet", "openai/gpt-4.1-mini")
        server.takeRequest().path shouldBe "/v1/models"
    }
}
