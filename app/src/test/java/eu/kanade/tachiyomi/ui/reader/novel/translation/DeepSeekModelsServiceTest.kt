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
    fun `loads model ids from models endpoint`() = runTest {
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
}
