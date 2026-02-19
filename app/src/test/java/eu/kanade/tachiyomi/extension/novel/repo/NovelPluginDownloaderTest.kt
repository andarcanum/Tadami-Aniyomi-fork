package eu.kanade.tachiyomi.extension.novel.repo

import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class NovelPluginDownloaderTest {

    @Test
    fun `download returns package with optional assets`() {
        runTest {
            val script = "console.log('main')".toByteArray()
            val customJs = "console.log('custom')".toByteArray()
            val customCss = "body { color: red; }".toByteArray()
            val entry = sampleEntry(
                sha256 = Hash.sha256(script),
                customJsUrl = "https://example.org/custom.js",
                customCssUrl = "https://example.org/custom.css",
            )
            val client = clientWithBodies(
                mapOf(
                    entry.url to script,
                    entry.customJsUrl!! to customJs,
                    entry.customCssUrl!! to customCss,
                ),
            )
            val downloader = NovelPluginDownloader(client, NovelPluginPackageFactory())

            val result = downloader.download(entry)

            result.isSuccess shouldBe true
            val pkg = result.getOrNull()!!
            pkg.script.contentEquals(script) shouldBe true
            pkg.customJs?.contentEquals(customJs) shouldBe true
            pkg.customCss?.contentEquals(customCss) shouldBe true
        }
    }

    @Test
    fun `download fails on checksum mismatch`() {
        runTest {
            val script = "console.log('main')".toByteArray()
            val entry = sampleEntry(sha256 = "deadbeef")
            val client = clientWithBodies(mapOf(entry.url to script))
            val downloader = NovelPluginDownloader(client, NovelPluginPackageFactory())

            val result = downloader.download(entry)

            result.isFailure shouldBe true
        }
    }

    private fun clientWithBodies(responses: Map<String, ByteArray>): OkHttpClient {
        val jsonMedia = "application/javascript".toMediaType()
        val interceptor = Interceptor { chain ->
            val body = responses[chain.request().url.toString()] ?: ByteArray(0)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("mock")
                .body(body.toResponseBody(jsonMedia))
                .build()
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun sampleEntry(
        sha256: String,
        customJsUrl: String? = null,
        customCssUrl: String? = null,
    ) = NovelPluginRepoEntry(
        id = "novel",
        name = "Novel Source",
        site = "https://example.org",
        lang = "en",
        version = 1,
        url = "https://example.org/novel.js",
        iconUrl = null,
        customJsUrl = customJsUrl,
        customCssUrl = customCssUrl,
        hasSettings = false,
        sha256 = sha256,
    )
}
