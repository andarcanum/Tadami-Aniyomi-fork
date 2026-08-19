package eu.kanade.tachiyomi.extension.novel.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkExtensionStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
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

    @Test
    fun `falls back to plugins json when plugins min fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("[]"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val fetcher = NetworkNovelPluginIndexFetcher(OkHttpClient())
        val payload = fetcher.fetch(baseUrl)

        payload shouldBe "[]"
        server.takeRequest().path shouldBe "/plugins.min.json"
        server.takeRequest().path shouldBe "/plugins.json"
    }

    @Test
    fun `falls back to tachiyomi index json after novel plugin indexes fail`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("[]"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val fetcher = NetworkNovelPluginIndexFetcher(OkHttpClient())
        val payload = fetcher.fetch(baseUrl)

        payload shouldBe "[]"
        server.takeRequest().path shouldBe "/plugins.min.json"
        server.takeRequest().path shouldBe "/plugins.json"
        server.takeRequest().path shouldBe "/index.json"
    }

    @Test
    fun `fetches protobuf index from index pb after json candidates miss`() = runTest {
        val store = NetworkExtensionStore(
            name = "PB",
            badgeLabel = "PB",
            signingKey = "fp",
            contact = NetworkExtensionStore.Contact(website = "https://pb.example", discord = null),
            extensionList = NetworkExtensionStore.ExtensionList(
                listOf(
                    NetworkExtensionStore.Extension(
                        name = "PB Ext",
                        packageName = "pb.ext",
                        resources = NetworkExtensionStore.Resources(
                            apkUrl = "https://cdn.example/pb.apk",
                            iconUrl = "https://cdn.example/pb.png",
                        ),
                        extensionLib = "1.4",
                        versionCode = 7,
                        versionName = "1.0.7",
                        contentWarning = NetworkExtensionStore.ContentWarning.NSFW,
                        sources = listOf(
                            NetworkExtensionStore.Source(
                                id = 1234567890123,
                                name = "PB",
                                language = "all",
                                homeUrl = "https://pb.example",
                            ),
                        ),
                    ),
                ),
            ),
            extensionListUrl = null,
        )
        val protoBytes = ProtoBuf.encodeToByteArray(NetworkExtensionStore.serializer(), store)

        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(Buffer().write(gzip(protoBytes))))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val fetcher = NetworkNovelPluginIndexFetcher(OkHttpClient())
        val payload = fetcher.fetch(baseUrl)

        payload shouldContain "\"id\":\"pb.ext\""
        payload shouldContain "\"version\":7"
        payload shouldContain "\"url\":\"https://cdn.example/pb.apk\""
        server.takeRequest().path shouldBe "/plugins.min.json"
        server.takeRequest().path shouldBe "/plugins.json"
        server.takeRequest().path shouldBe "/index.json"
        server.takeRequest().path shouldBe "/index.pb"
    }

    @Test
    fun `decodes new-format json index published as index json`() = runTest {
        val keiyoushiStyle = """
            {
              "name": "Kei",
              "badgeLabel": "KEI",
              "signingKey": "fp",
              "contact": { "website": "https://kei.example", "discord": null },
              "extensionList": {
                "extensions": [
                  {
                    "name": "Json Ext",
                    "packageName": "json.ext",
                    "resources": {
                      "apkUrl": "https://cdn.example/json.apk",
                      "iconUrl": "https://cdn.example/json.png"
                    },
                    "extensionLib": "1.6",
                    "versionCode": "3",
                    "versionName": "1.6.3",
                    "contentWarning": "CONTENT_WARNING_MIXED",
                    "sources": [ { "id": "42", "name": "Json", "language": "all", "homeUrl": "https://json.example" } ]
                  }
                ]
              },
              "extensionListUrl": null
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(keiyoushiStyle))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val fetcher = NetworkNovelPluginIndexFetcher(OkHttpClient())
        val payload = fetcher.fetch(baseUrl)

        payload shouldContain "\"id\":\"json.ext\""
        payload shouldContain "\"version\":3"
        server.takeRequest().path shouldBe "/plugins.min.json"
        server.takeRequest().path shouldBe "/plugins.json"
        server.takeRequest().path shouldBe "/index.json"
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.write(bytes) }
        return buffer.readByteArray()
    }
}
