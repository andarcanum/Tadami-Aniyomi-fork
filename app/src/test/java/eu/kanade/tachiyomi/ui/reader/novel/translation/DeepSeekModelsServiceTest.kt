package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class DeepSeekModelsServiceTest {

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
    fun `loads model ids from models endpoint`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"deepseek-chat"},{"id":"deepseek-reasoner"}]}""",
            ),
        )
        val service = DeepSeekModelsService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        val models = service.fetchModels(
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "test-key",
        )

        models shouldBe listOf("deepseek-chat", "deepseek-reasoner")
        server.takeRequest().path shouldBe "/models"
    }

    @Test
    fun `fetchModels throws on http non-success so callers can log invalid keys`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(403))
        val service = DeepSeekModelsService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
        )

        shouldThrow<IOException> {
            service.fetchModels(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
            )
        }.message shouldBe "HTTP 403"
    }
}
