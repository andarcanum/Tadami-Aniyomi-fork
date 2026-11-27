package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.presentation.entries.translation.googleTranslationLanguageCodeFor
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GoogleTranslationServiceTest {

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
    fun `auth failures are not retried as transient`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(401)) }
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        service.translateSingle("Hello", "en", "ru") shouldBe null

        // Pre-fix all three attempts (with delays) were consumed on a permanent auth error.
        server.requestCount shouldBe 1
    }

    @Test
    fun `batch response surfaces rate limiting after 429 responses`() = runTest {
        // Wrapped chunk request (3 attempts) + per-segment fallback (3 attempts), all 429.
        repeat(8) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        }
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        val response = service.translateBatch(
            texts = listOf("Hello"),
            params = GoogleTranslationParams(sourceLang = "en", targetLang = "ru"),
        )

        response.translatedByIndex shouldBe emptyMap()
        response.rateLimited shouldBe true
    }

    @Test
    fun `uses get for short text translation`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[[["Короткий текст","Short text",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        val translated = service.translateSingle(
            text = "Short text",
            sourceLanguage = "auto",
            targetLanguage = "ru",
        )

        translated shouldBe "Короткий текст"
        val request = server.takeRequest()
        request.method shouldBe "GET"
        request.requestUrl?.queryParameter("q") shouldBe "Short text"
    }

    @Test
    fun `uses post for long text translation`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[[["Длинный текст","${"A".repeat(600)}",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        service.translateSingle(
            text = "A".repeat(600),
            sourceLanguage = "en",
            targetLanguage = "ru",
        ) shouldBe "Длинный текст"

        val request = server.takeRequest()
        request.method shouldBe "POST"
        request.body.readUtf8() shouldBe "client=gtx&sl=en&tl=ru&dt=t&q=${"A".repeat(600)}"
    }

    @Test
    fun `translates chunked paragraphs using novela style markers`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[[["[0]\nПервый\n\n[1]\nВторой","[0]\nFirst\n\n[1]\nSecond",null,null,1]],null,"en"]""",
            ),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
            maxChunkChars = 64,
        )

        val result = service.translateBatch(
            texts = listOf("First", "Second"),
            params = GoogleTranslationParams(sourceLang = "auto", targetLang = "ru"),
        )

        result.translatedByIndex shouldContain (0 to "Первый")
        result.translatedByIndex shouldContain (1 to "Второй")
    }

    @Test
    fun `falls back to single translation when a marker is missing from chunk response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[[["[0]\nПервый","[0]\nFirst\n\n[1]\nSecond",null,null,1]],null,"en"]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody("""[[["Второй","Second",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
            maxChunkChars = 64,
        )

        val result = service.translateBatch(
            texts = listOf("First", "Second"),
            params = GoogleTranslationParams(sourceLang = "auto", targetLang = "ru"),
        )

        result.translatedByIndex shouldContain (0 to "Первый")
        result.translatedByIndex shouldContain (1 to "Второй")
        server.requestCount shouldBe 2
    }

    @Test
    fun `free-text language names resolve to catalog codes on the wire`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[[["[0]\nOl\u00e1","[0]\nHello",null,null,1]],null,"en"]"""),
        )
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        val result = service.translateBatch(
            texts = listOf("Hello"),
            params = GoogleTranslationParams(
                sourceLang = "English",
                targetLang = "\u041f\u043e\u0440\u0442\u0443\u0433\u0430\u043b\u044c\u0441\u043a\u0438\u0439",
            ),
        )

        val request = server.takeRequest()
        // Device-locale tables miss aliases and trimmed firmware locale sets miss names entirely;
        // the catalog must answer first so the wire always carries a real language tag.
        request.requestUrl?.queryParameter("sl") shouldBe "en"
        request.requestUrl?.queryParameter("tl") shouldBe "pt"
        result.translatedByIndex.isEmpty() shouldBe false
    }

    @Test
    fun `unmappable target language fails loudly without hitting the network`() = runTest {
        val service = GoogleTranslationService(
            client = OkHttpClient(),
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )
        val logs = mutableListOf<String>()

        val result = service.translateBatch(
            texts = listOf("Hello"),
            params = GoogleTranslationParams(sourceLang = "en", targetLang = "Portuguese (Brazilian)"),
            onLog = { logs += it },
        )

        result.translatedByIndex shouldBe emptyMap()
        server.requestCount shouldBe 0
        logs.any { it.startsWith("Unknown target language") } shouldBe true
    }

    @Test
    fun `catalog resolves names codes and aliases case-insensitively`() {
        googleTranslationLanguageCodeFor("Portuguese") shouldBe "pt"
        googleTranslationLanguageCodeFor("pt") shouldBe "pt"
        googleTranslationLanguageCodeFor(
            "\u043f\u043e\u0440\u0442\u0443\u0433\u0430\u043b\u044c\u0441\u043a\u0438\u0439",
        ) shouldBe
            "pt"
        googleTranslationLanguageCodeFor("AUTO") shouldBe "auto"
        googleTranslationLanguageCodeFor("Portuguese (Brazil)") shouldBe null
    }

    @Test
    fun `cancellation propagates CancellationException and cancels in-flight OkHttp call`() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val callCanceled = CountDownLatch(1)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestStarted.countDown()
                releaseServer.await(5, TimeUnit.SECONDS)
                return MockResponse().setBody("""[[["Привет","Hello",null,null,1]],null,"en"]""")
            }
        }

        val client = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) {
                        callCanceled.countDown()
                    }
                },
            )
            .build()

        val service = GoogleTranslationService(
            client = client,
            translateUrl = server.url("/translate_a/single"),
            userAgent = "unit-test-agent",
        )

        val deferred = async(Dispatchers.Default) {
            service.translateSingle("Hello", "en", "ru")
        }

        try {
            requestStarted.await(5, TimeUnit.SECONDS) shouldBe true
            deferred.cancel()

            val thrown = runCatching { deferred.await() }.exceptionOrNull()
            (thrown is CancellationException) shouldBe true
            callCanceled.await(5, TimeUnit.SECONDS) shouldBe true
            server.requestCount shouldBe 1
        } finally {
            releaseServer.countDown()
        }
    }
}
