package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.NovelAiProviderController
import eu.kanade.tachiyomi.ui.reader.novel.NovelAiProviderHost
import eu.kanade.tachiyomi.ui.reader.novel.NovelAiProviderState
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.TimeUnit

class GeminiModelsServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    @Test
    fun `keeps gemini text models and strips the models prefix`() {
        val raw = """
            {
              "models": [
                {
                  "name": "models/gemini-3-flash-preview",
                  "displayName": "Gemini 3 Flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"],
                  "outputTokenLimit": 65536
                },
                {
                  "name": "models/gemini-3.1-flash-lite-preview",
                  "displayName": "Gemini 3.1 Flash Lite",
                  "supportedGenerationMethods": ["generateContent"],
                  "outputTokenLimit": 65536
                }
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).map { it.id to it.displayName } shouldBe listOf(
            "gemini-3-flash-preview" to "Gemini 3 Flash",
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite",
        )
    }

    @Test
    fun `drops non gemini models and gemini models without generateContent`() {
        val raw = """
            {
              "models": [
                {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]},
                {"name": "models/imagen-4.0-generate-001", "supportedGenerationMethods": ["predict"]},
                {"name": "models/gemini-3-flash-preview", "supportedGenerationMethods": ["countTokens"]}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).shouldBeEmpty()
    }

    @Test
    fun `drops specialized gemini variants by id substring`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemini-2.5-flash-preview-tts", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-2.5-flash-image-preview", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-2.5-flash-native-audio-dialogue", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-2.5-computer-use-preview", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-embedding-001", "supportedGenerationMethods": ["generateContent", "embedContent"], "outputTokenLimit": 65536},
                {"name": "models/gemini-3-flash-preview", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).map { it.id to it.displayName } shouldBe listOf(
            "gemini-3-flash-preview" to "gemini-3-flash-preview",
        )
    }

    @Test
    fun `keeps text models with a smaller output limit instead of hiding them`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemini-1.5-flash", "displayName": "Gemini 1.5 Flash", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192},
                {"name": "models/gemini-3-pro-preview", "displayName": "Gemini 3 Pro", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 65536}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).map { it.id } shouldBe listOf(
            "gemini-1.5-flash",
            "gemini-3-pro-preview",
        )
    }

    @Test
    fun `keeps models with missing output limit, dedupes and sorts by id`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemini-3.5-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3-flash-preview", "displayName": "Gemini 3 Flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3-flash-preview", "displayName": "Gemini 3 Flash dup", "supportedGenerationMethods": ["generateContent"]}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).map { it.id to it.displayName } shouldBe listOf(
            "gemini-3-flash-preview" to "Gemini 3 Flash",
            "gemini-3.5-flash" to "gemini-3.5-flash",
        )
    }

    @Test
    fun `malformed payload is an error rather than an empty catalogue`() {
        shouldThrow<IOException> { extractGeminiModelEntries(null) }
        shouldThrow<IOException> { extractGeminiModelEntries(payload("not-json")) }
        shouldThrow<IOException> { extractGeminiModelEntries(payload("""{"models": "oops"}""")) }
        shouldThrow<IOException> { extractGeminiModelEntries(payload("""{"models": [null]}""")) }
    }

    @Test
    fun `keeps both documented Gemma 4 text models with generateContent`() {
        val raw = """
            {
              "models": [
                {"name": "models/gemma-4-31b-it", "displayName": "Gemma 4 31B", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192},
                {"name": "models/gemma-4-26b-a4b-it", "displayName": "Gemma 4 26B A4B", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192},
                {"name": "models/gemma-4-tts", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemma-4-count-only", "supportedGenerationMethods": ["countTokens"]},
                {"name": "models/gemma-3-27b-it", "supportedGenerationMethods": ["generateContent"]}
              ]
            }
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).map { it.id } shouldBe listOf(
            "gemma-4-26b-a4b-it",
            "gemma-4-31b-it",
        )
    }

    @Test
    fun `fetchModels authenticates by header without putting credentials in the URL`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(MockResponse().setBody("""{"models": []}"""))

            service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))

            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            request.getHeader("x-goog-api-key") shouldBe "test-key"
            request.requestUrl!!.queryParameterNames shouldBe setOf("pageSize")
            request.requestUrl!!.queryParameter("pageSize") shouldBe "1000"
        }
    }

    @Test
    fun `fetchModels merges every page and preserves opaque page tokens without query injection`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(
                MockResponse().setBody(
                    """{"models": [{"name": "models/gemini-z-text", "supportedGenerationMethods": ["generateContent"]}], "nextPageToken": "next+/=&? #"}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"models": [{"name": "models/gemini-a-text", "supportedGenerationMethods": ["generateContent"]}, {"name": "models/gemini-z-text", "supportedGenerationMethods": ["generateContent"]}], "nextPageToken": "last-page"}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"models": [{"name": "models/gemma-4-31b-it", "supportedGenerationMethods": ["generateContent"]}]}""",
                ),
            )

            val entries = service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))

            entries.map { it.id } shouldBe listOf("gemini-a-text", "gemini-z-text", "gemma-4-31b-it")
            server.requestCount shouldBe 3
            val first = server.takeRequest(1, TimeUnit.SECONDS)!!
            val second = server.takeRequest(1, TimeUnit.SECONDS)!!
            val third = server.takeRequest(1, TimeUnit.SECONDS)!!
            first.requestUrl!!.queryParameterNames shouldBe setOf("pageSize")
            second.requestUrl!!.queryParameterNames shouldBe setOf("pageSize", "pageToken")
            second.requestUrl!!.queryParameter("pageToken") shouldBe "next+/=&? #"
            third.requestUrl!!.queryParameter("pageToken") shouldBe "last-page"
            listOf(first, second, third).forEach { it.getHeader("x-goog-api-key") shouldBe "test-key" }
        }
    }

    @Test
    fun `fetchModels throws if a later page fails instead of returning a partial catalogue`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(
                MockResponse().setBody(
                    """{"models": [{"name": "models/gemini-text", "supportedGenerationMethods": ["generateContent"]}], "nextPageToken": "next"}""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(503))

            shouldThrow<IOException> {
                service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))
            }.message shouldBe "HTTP 503"
            server.requestCount shouldBe 2
        }
    }

    @Test
    fun `fetchModels throws if a later page is malformed instead of publishing the first page`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(
                MockResponse().setBody(
                    """{"models": [{"name": "models/gemini-text", "supportedGenerationMethods": ["generateContent"]}], "nextPageToken": "next"}""",
                ),
            )
            server.enqueue(MockResponse().setBody("not-json"))

            shouldThrow<IOException> {
                service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))
            }
            server.requestCount shouldBe 2
        }
    }

    @Test
    fun `fetchModels detects a repeated token before requesting the same page again`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(MockResponse().setBody("""{"models": [], "nextPageToken": "repeat"}"""))
            server.enqueue(MockResponse().setBody("""{"models": [], "nextPageToken": "repeat"}"""))
            // Bound a broken implementation's request loop without mistaking that HTTP error for detection.
            server.enqueue(MockResponse().setResponseCode(500))

            shouldThrow<IOException> {
                service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))
            }
            server.requestCount shouldBe 2
        }
    }

    @Test
    fun `fetchModels rejects malformed first pages instead of a successful empty catalogue`() = runBlocking<Unit> {
        withServer { server, service ->
            listOf("not-json", "[]", """{"models": "oops"}""", """{"error": {"code": 200}}""").forEach { raw ->
                server.enqueue(MockResponse().setBody(raw))
                shouldThrow<IOException> {
                    service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))
                }
            }
        }
    }

    @Test
    fun `blank key does not request a catalogue`() = runBlocking<Unit> {
        withServer { server, service ->
            service.fetchModels(apiKey = "  ", baseUrl = server.url("/").toString().trimEnd('/')).shouldBeEmpty()
            server.requestCount shouldBe 0
        }
    }

    @Test
    fun `failed controller refresh preserves the last known good catalogue`() = runBlocking<Unit> {
        val fixture = ControllerFixture()
        try {
            val previous = listOf(GeminiModelEntry("gemini-good", "Last known good"))
            fixture.controller.restoreFromReaderState(NovelAiProviderState(geminiModelEntries = previous))
            fixture.controller.refreshGeminiModels()
            fixture.awaitRequest().completeExceptionally(IOException("HTTP 503"))
            fixture.awaitIdle()

            fixture.controller.snapshot().geminiModelEntries shouldBe previous
            fixture.controller.snapshot().isGeminiModelsLoading shouldBe false
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `older controller refresh cannot overwrite a newer generation with the same key`() = runBlocking<Unit> {
        val fixture = ControllerFixture()
        try {
            fixture.controller.refreshGeminiModels()
            val older = fixture.awaitRequest()
            fixture.controller.refreshGeminiModels()
            val newer = fixture.awaitRequest()
            val newestEntries = listOf(GeminiModelEntry("gemini-new", "New catalogue"))
            newer.complete(newestEntries)
            fixture.awaitIdle()
            older.complete(listOf(GeminiModelEntry("gemini-old", "Old catalogue")))
            fixture.joinRefreshes()

            fixture.controller.snapshot().geminiModelEntries shouldBe newestEntries
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `controller rejects a reply after its key or provider changes`() = runBlocking<Unit> {
        listOf(
            providerSettings(key = "replacement-key"),
            providerSettings(provider = NovelTranslationProvider.OPENROUTER),
        ).forEach { replacement ->
            val fixture = ControllerFixture()
            try {
                val previous = listOf(GeminiModelEntry("gemini-good", "Last known good"))
                fixture.controller.restoreFromReaderState(NovelAiProviderState(geminiModelEntries = previous))
                fixture.controller.refreshGeminiModels()
                val pending = fixture.awaitRequest()
                fixture.settings = replacement
                pending.complete(listOf(GeminiModelEntry("gemini-stale", "Stale catalogue")))
                fixture.joinRefreshes()

                fixture.controller.snapshot().geminiModelEntries shouldBe previous
                fixture.controller.snapshot().isGeminiModelsLoading shouldBe false
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `reset transient state fences an outstanding catalogue reply`() = runBlocking<Unit> {
        val fixture = ControllerFixture()
        try {
            val previous = listOf(GeminiModelEntry("gemini-good", "Last known good"))
            fixture.controller.restoreFromReaderState(NovelAiProviderState(geminiModelEntries = previous))
            fixture.controller.refreshGeminiModels()
            val pending = fixture.awaitRequest()
            fixture.controller.resetTransientState()
            pending.complete(listOf(GeminiModelEntry("gemini-stale", "Stale catalogue")))
            fixture.joinRefreshes()

            fixture.controller.snapshot().geminiModelEntries shouldBe previous
            fixture.controller.snapshot().isGeminiModelsLoading shouldBe false
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `discovery carries optional token sampling and thinking metadata`() {
        val raw = """
            {"models": [{
              "name": "models/gemini-discovery-metadata-fixture",
              "displayName": "Metadata fixture",
              "supportedGenerationMethods": ["generateContent", "countTokens"],
              "inputTokenLimit": 131072,
              "outputTokenLimit": 8192,
              "temperature": 1.0,
              "maxTemperature": 2.0,
              "topP": 0.95,
              "topK": 64,
              "thinking": true
            }]}
        """.trimIndent()

        extractGeminiModelEntries(payload(raw)).single() shouldBe GeminiModelEntry(
            id = "gemini-discovery-metadata-fixture",
            displayName = "Metadata fixture",
            inputTokenLimit = 131072,
            outputTokenLimit = 8192,
            temperature = 1f,
            maxTemperature = 2f,
            topP = 0.95f,
            topK = 64,
            thinking = true,
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
        )
    }

    @Test
    fun `invalid token metadata throws instead of silently accepting a damaged catalogue`() {
        listOf("0", "-1", "2147483648", "\"8192\"", "{}").forEach { invalid ->
            val raw = """
                {"models": [{
                  "name": "models/gemini-invalid-metadata-fixture",
                  "supportedGenerationMethods": ["generateContent"],
                  "outputTokenLimit": $invalid
                }]}
            """.trimIndent()
            shouldThrow<IOException> { extractGeminiModelEntries(payload(raw)) }
        }
    }

    @Test
    fun `repeated page token detection catches a longer cycle before another request`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(MockResponse().setBody("""{"models": [], "nextPageToken": "a"}"""))
            server.enqueue(MockResponse().setBody("""{"models": [], "nextPageToken": "b"}"""))
            server.enqueue(MockResponse().setBody("""{"models": [], "nextPageToken": "a"}"""))
            server.enqueue(MockResponse().setResponseCode(500))

            shouldThrow<IOException> {
                service.fetchModels("test-key", server.url("/").toString().trimEnd('/'))
            }
            server.requestCount shouldBe 3
        }
    }

    @Test
    fun `non-string page token is an error rather than the end of the catalogue`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(MockResponse().setBody("""{"models": [], "nextPageToken": 123}"""))
            shouldThrow<IOException> {
                service.fetchModels("test-key", server.url("/").toString().trimEnd('/'))
            }
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `catalogue logger removal preserves the inherited client`() = runBlocking<Unit> {
        val server = MockWebServer()
        server.start()
        try {
            val messages = Collections.synchronizedList(mutableListOf<String>())
            val logger = HttpLoggingInterceptor { messages.add(it) }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            val inherited = OkHttpClient.Builder().addInterceptor(logger).addNetworkInterceptor(logger).build()
            server.enqueue(MockResponse().setBody("""{"models": []}"""))
            GeminiModelsService(inherited, json).fetchModels("test-key", server.url("/").toString().trimEnd('/'))

            messages.shouldBeEmpty()
            inherited.interceptors shouldBe listOf(logger)
            inherited.networkInterceptors shouldBe listOf(logger)
            logger.level shouldBe HttpLoggingInterceptor.Level.HEADERS
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `catalogue redirects never forward the key to another origin`() = runBlocking<Unit> {
        val destination = MockWebServer()
        destination.start()
        try {
            withServer { origin, service ->
                origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/models")))
                destination.enqueue(MockResponse().setBody("""{"models": []}"""))

                shouldThrow<IOException> {
                    service.fetchModels("test-key", origin.url("/").toString().trimEnd('/'))
                }.message shouldBe "HTTP 302"
                origin.requestCount shouldBe 1
                destination.requestCount shouldBe 0
            }
        } finally {
            destination.shutdown()
        }
    }

    @Test
    fun `successful complete public catalogue supplies default output sizing metadata`() = runBlocking<Unit> {
        val id = "gemini-registry-complete-success-fixture"
        val service = GeminiModelsService(
            queuedGoogleClient(
                200 to
                    """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192}], "nextPageToken": "next"}""",
                200 to
                    """{"models": [{"name": "models/gemini-registry-second-success-fixture", "supportedGenerationMethods": ["generateContent"]}]}""",
            ),
            json,
        )

        service.fetchModels("test-key").size shouldBe 2
        resolveGeminiModelCapabilities(id).maxOutputTokens() shouldBe 8192
    }

    @Test
    fun `failed later public page cannot publish new limits or erase last known good metadata`() = runBlocking<Unit> {
        val id = "gemini-registry-failed-page-fixture"
        GeminiModelsService(
            queuedGoogleClient(
                200 to
                    """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192}]}""",
            ),
            json,
        ).fetchModels("test-key")
        listOf(503 to "offline", 200 to "not-json").forEach { failedPage ->
            val service = GeminiModelsService(
                queuedGoogleClient(
                    200 to
                        """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 4096}], "nextPageToken": "next"}""",
                    failedPage,
                ),
                json,
            )
            shouldThrow<IOException> { service.fetchModels("test-key") }
            resolveGeminiModelCapabilities(id).maxOutputTokens() shouldBe 8192
        }
    }

    @Test
    fun `custom origin descriptors cannot replace or enter the public Google metadata registry`() = runBlocking<Unit> {
        val id = "gemini-registry-origin-fence-fixture"
        val foreignId = "gemini-registry-custom-only-fixture"
        GeminiModelsService(
            queuedGoogleClient(
                200 to
                    """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192}]}""",
            ),
            json,
        ).fetchModels("test-key")
        withServer { server, service ->
            server.enqueue(
                MockResponse().setBody(
                    """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 4096}, {"name": "models/$foreignId", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 1024}]}""",
                ),
            )
            val fetched = service.fetchModels("different-test-key", server.url("/").toString().trimEnd('/'))

            fetched.first { it.id == id }.outputTokenLimit shouldBe 4096
            resolveGeminiModelCapabilities(id).maxOutputTokens() shouldBe 8192
            resolveGeminiModelCapabilities(foreignId).maxOutputTokens() shouldBe 16384
        }
    }

    @Test
    fun `invalid header credentials produce a safe error without making a request`() = runBlocking<Unit> {
        withServer { server, service ->
            server.enqueue(MockResponse().setBody("""{"models": []}"""))
            shouldThrow<IOException> {
                service.fetchModels("test-key\ninjected", server.url("/").toString().trimEnd('/'))
            }.message shouldBe "Invalid model catalogue credential"
            server.requestCount shouldBe 0
        }
    }

    @Test
    fun `successful catalogue with omitted optional limits keeps the known descriptor sizing`() = runBlocking<Unit> {
        val id = "gemini-registry-omitted-fields-fixture"
        val service = GeminiModelsService(
            queuedGoogleClient(
                200 to
                    """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192}]}""",
                200 to """{"models": [{"name": "models/$id", "supportedGenerationMethods": ["generateContent"]}]}""",
                200 to """{"models": []}""",
            ),
            json,
        )
        service.fetchModels("test-key")
        service.fetchModels("different-test-key")
        resolveGeminiModelCapabilities(id).maxOutputTokens() shouldBe 8192
        // A credential's availability list is not the public descriptor registry.
        service.fetchModels("another-test-key").shouldBeEmpty()
        resolveGeminiModelCapabilities(id).maxOutputTokens() shouldBe 8192
    }

    private fun queuedGoogleClient(vararg responses: Pair<Int, String>): OkHttpClient {
        val pending = ArrayDeque(responses.toList())
        return OkHttpClient.Builder().addInterceptor { chain ->
            val response = synchronized(pending) { pending.pollFirst() }
                ?: throw IOException("Unexpected model catalogue request")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(response.first)
                .message("Test response")
                .body(response.second.toResponseBody())
                .build()
        }.build()
    }

    private suspend fun <T> withServer(block: suspend (MockWebServer, GeminiModelsService) -> T): T {
        val server = MockWebServer()
        server.start()
        try {
            return block(server, GeminiModelsService(OkHttpClient(), json))
        } finally {
            server.shutdown()
        }
    }

    private class ControllerFixture {
        private val scopeJob = Job()
        private val requests = Channel<CompletableDeferred<List<GeminiModelEntry>>>(Channel.UNLIMITED)
        private val states = Channel<NovelAiProviderState>(Channel.UNLIMITED)
        var settings = providerSettings()
        val controller: NovelAiProviderController

        init {
            val service = mockk<GeminiModelsService>()
            val host = mockk<NovelAiProviderHost>(relaxed = true)
            every { host.providerScope } returns CoroutineScope(scopeJob)
            every { host.providerReaderSettings() } answers { settings }
            every { host.providerApplyAiProvidersState(any()) } answers {
                states.trySend(firstArg<NovelAiProviderState>())
                Unit
            }
            coEvery { service.fetchModels(any(), any()) } coAnswers {
                val response = CompletableDeferred<List<GeminiModelEntry>>()
                requests.send(response)
                response.await()
            }
            controller = NovelAiProviderController(
                host = host,
                application = mockk(relaxed = true),
                novelReaderPreferences = mockk(relaxed = true),
                geminiModelsService = service,
                openRouterModelsService = mockk(relaxed = true),
                deepSeekModelsService = mockk(relaxed = true),
                mistralModelsService = mockk(relaxed = true),
                nvidiaModelsService = mockk(relaxed = true),
                ollamaCloudModelsService = mockk(relaxed = true),
            )
        }

        suspend fun awaitRequest(): CompletableDeferred<List<GeminiModelEntry>> = withTimeout(3000) {
            requests.receive()
        }

        suspend fun awaitIdle() = withTimeout(3000) {
            while (states.receive().isGeminiModelsLoading) {
                // Consume loading states until the controller has applied a terminal reply.
            }
        }

        suspend fun joinRefreshes() = withTimeout(3000) {
            scopeJob.children.toList().forEach { it.join() }
        }

        suspend fun close() = scopeJob.cancelAndJoin()
    }

    companion object {
        private fun providerSettings(
            key: String = "test-key",
            provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
        ): NovelReaderSettings = mockk<NovelReaderSettings>(relaxed = true).also {
            every { it.geminiApiKey } returns key
            every { it.translationProvider } returns provider
            // A relaxed mock invents a connection profile and a 0L source id, which would route
            // every refresh into the profile/services path instead of the legacy fetch under test.
            every { it.connectionProfile } returns null
            every { it.translationSourceId } returns null
        }
    }

    @Test
    fun `fetchModels throws on http non-success so callers can log invalid keys`() = runBlocking<Unit> {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403))
            val service = GeminiModelsService(
                client = OkHttpClient(),
                json = Json { ignoreUnknownKeys = true },
            )

            shouldThrow<IOException> {
                service.fetchModels(apiKey = "test-key", baseUrl = server.url("/").toString().trimEnd('/'))
            }.message shouldBe "HTTP 403"
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchModels normalizes v1beta without duplicating path and keeps gemma-4 text models`() =
        runBlocking<Unit> {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(
                    MockResponse().setBody(
                        """
                        {
                          "models": [
                            {"name": "models/gemini-2.5-flash", "displayName": "Gemini 2.5 Flash", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemma-4-26b-a4b-it", "displayName": "Gemma 4 26B", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemma-4-31b-it", "displayName": "Gemma 4 31B", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemma-3-27b-it", "displayName": "Gemma 3 27B", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemini-2.5-flash-tts", "displayName": "Gemini TTS", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemini-2.5-flash-image", "displayName": "Gemini Image", "supportedGenerationMethods": ["generateContent"]},
                            {"name": "models/gemini-embedding-001", "displayName": "Gemini Embed", "supportedGenerationMethods": ["generateContent"]}
                          ]
                        }
                        """.trimIndent(),
                    ),
                )
                val service = GeminiModelsService(
                    client = OkHttpClient(),
                    json = Json { ignoreUnknownKeys = true },
                )

                val models = service.fetchModels(
                    apiKey = "test-key",
                    baseUrl = server.url("/v1beta").toString(),
                )

                val request = server.takeRequest()
                request.path!!.startsWith("/v1beta/models?") shouldBe true
                request.path!!.startsWith("/v1beta/v1beta/") shouldBe false
                models.map { it.id } shouldBe listOf(
                    "gemini-2.5-flash",
                    "gemma-4-26b-a4b-it",
                    "gemma-4-31b-it",
                )
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `isPublicGoogleModelsOrigin accepts bare and versioned Google origins and rejects custom paths`() {
        "https://generativelanguage.googleapis.com".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe true
        "https://generativelanguage.googleapis.com/".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe true
        "https://generativelanguage.googleapis.com/v1beta".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe true
        "https://generativelanguage.googleapis.com/v1".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe true
        "https://generativelanguage.googleapis.com/v1alpha".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe true
        "https://generativelanguage.googleapis.com/v2".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe false
        "https://proxy.example.com/v1beta".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe false
        "http://generativelanguage.googleapis.com/v1beta".toHttpUrl().isPublicGoogleModelsOrigin() shouldBe false
    }
}
