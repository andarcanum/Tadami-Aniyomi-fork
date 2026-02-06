package tachiyomi.data.extension.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetworkNovelPluginDownloaderTest {

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
    fun `downloads bytes from url`() = runTest {
        val payload = "console.log('hi')".toByteArray()
        server.enqueue(MockResponse().setBody(String(payload)))

        val client = OkHttpClient()
        val downloader = NetworkNovelPluginDownloader(client)

        val url = server.url("/plugin.js").toString()
        val bytes = downloader.download(url)

        bytes shouldBe payload
        server.takeRequest().path shouldBe "/plugin.js"
    }
}
