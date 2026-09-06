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

class MistralModelsServiceTest {

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
    fun `fetchModels throws on http non-success so callers can log invalid keys`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(403))
        val service = MistralModelsService(
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
