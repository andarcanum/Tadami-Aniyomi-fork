package eu.kanade.tachiyomi.extension.novel.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NetworkNovelPluginIndexFetcherTest {

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
    fun `fetches plugin index from base url`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val fetcher = NetworkNovelPluginIndexFetcher(OkHttpClient())
        val payload = fetcher.fetch(baseUrl)

        payload shouldBe "[]"
        server.takeRequest().path shouldBe "/plugins.min.json"
    }

    @Test
    fun `fetches plugin index when repo url already points to json`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val repoUrl = server.url("/plugins.min.json").toString()

        val fetcher = NetworkNovelPluginIndexFetcher(OkHttpClient())
        val payload = fetcher.fetch(repoUrl)

        payload shouldBe "[]"
        server.takeRequest().path shouldBe "/plugins.min.json"
    }
}
