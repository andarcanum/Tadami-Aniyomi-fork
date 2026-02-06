package eu.kanade.tachiyomi.extension.novel.repo

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class NovelPluginRepoServiceTest {

    @Test
    fun `fetchRepoEntries returns parsed list`() {
        runTest {
            val body = """
            [
            {
            "id": "novel",
            "name": "Novel Source",
            "site": "https://example.org",
            "lang": "en",
            "version": 1,
            "url": "https://example.org/novel.js",
            "iconUrl": null,
            "sha256": "deadbeef"
            }
            ]
            """.trimIndent()
            val client = clientWithBody(body, code = 200)
            val service = NovelPluginRepoService(
            client = client,
            parser = NovelPluginRepoParser(Json { ignoreUnknownKeys = true }),
            )

            val entries = service.fetchRepoEntries("https://example.org/repo.json")

            entries.size shouldBe 1
            entries.first().id shouldBe "novel"
        }
    }

        @Test
        fun `fetchRepoEntries returns empty list on http error`() {
            runTest {
                val client = clientWithBody("{}", code = 500)
                val service = NovelPluginRepoService(
                client = client,
                parser = NovelPluginRepoParser(Json { ignoreUnknownKeys = true }),
                )

                val entries = service.fetchRepoEntries("https://example.org/repo.json")

                entries shouldBe emptyList()
            }
        }

        private fun clientWithBody(body: String, code: Int): OkHttpClient {
            val jsonMedia = "application/json".toMediaType()
            val interceptor = Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("mock")
                    .body(body.toResponseBody(jsonMedia))
                    .build()
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }
}
