package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class GoogleUnofficialSelectedTextTranslationProviderTest {

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
    fun `parses valid translation payload`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[[["Привет","Hello",null,null,1]],null,"en"]""",
            ),
        )
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { 0L },
        )

        val outcome = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
                sourceLanguageHint = "English",
            ),
        )

        val success = outcome.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Success>()
        success.result.translation shouldBe "Привет"
        success.result.detectedSourceLanguage shouldBe "en"
        success.result.providerFingerprint shouldBe provider.fingerprint

        val request = server.takeRequest()
        request.requestUrl?.encodedPath shouldBe "/translate_a/single"
        request.requestUrl?.queryParameter("client") shouldBe "gtx"
        request.requestUrl?.queryParameter("sl") shouldBe "en"
        request.requestUrl?.queryParameter("tl") shouldBe "ru"
        request.requestUrl?.queryParameter("q") shouldBe "Hello"
    }

    @Test
    fun `rejects blank translation result`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[[["   ","Hello",null,null,1]],null,"en"]""",
            ),
        )
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { 0L },
        )

        val outcome = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )

        val unavailable = outcome.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
        unavailable.reason shouldBe NovelSelectedTextTranslationErrorReason.ParserFailure
    }

    @Test
    fun `maps parser drift to unavailable state`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"unexpected":"shape"}"""),
        )
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { 0L },
        )

        val outcome = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )

        val unavailable = outcome.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
        unavailable.reason shouldBe NovelSelectedTextTranslationErrorReason.ParserFailure
    }

    @Test
    fun `maps rate limit to cooldown and blocks immediate retry`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"message":"Too many requests"}}"""),
        )
        val currentTime = AtomicInteger(1_000)
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { currentTime.get().toLong() },
        )

        val first = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )
        val firstUnavailable = first.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
        val cooldown = firstUnavailable.reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.Cooldown>()
        cooldown.remainingSeconds shouldBe 60

        val second = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )
        val secondUnavailable = second.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
        secondUnavailable.reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.Cooldown>()
        server.requestCount shouldBe 1
    }

    @Test
    fun `allows immediate retry after transient network failure`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[[["Привет","Hello",null,null,1]],null,"en"]"""),
        )
        val attempts = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    if (attempts.getAndIncrement() == 0) {
                        throw IOException("socket closed")
                    }
                    chain.proceed(chain.request())
                },
            )
            .build()
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = client,
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { 0L },
        )

        val first = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )
        first.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
            .reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.NetworkFailure>()

        val second = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )
        second.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Success>()
        server.requestCount shouldBe 1
    }

    @Test
    fun `three consecutive failures trigger temporary cooldown`() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setBody("""{"unexpected":"shape"}"""))
        }
        val now = AtomicInteger(1_000)
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { now.get().toLong() },
        )

        repeat(2) {
            provider.translate(
                NovelSelectedTextTranslationRequest(
                    selectedText = "Hello",
                    targetLanguage = "Russian",
                ),
            ).shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
                .reason.shouldBe(NovelSelectedTextTranslationErrorReason.ParserFailure)
        }

        val third = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )
        third.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
            .reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.Cooldown>()

        val fourth = provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "Hello",
                targetLanguage = "Russian",
            ),
        )
        fourth.shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
            .reason.shouldBeInstanceOf<NovelSelectedTextTranslationErrorReason.Cooldown>()
        server.requestCount shouldBe 3
    }

    @Test
    fun `rejects blank or too long selections without network request`() = runTest {
        val provider = GoogleUnofficialSelectedTextTranslationProvider(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            endpointBaseUrl = server.url("/translate_a/single").toString(),
            clockMillis = { 0L },
        )

        provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "   ",
                targetLanguage = "Russian",
            ),
        ).shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
            .reason shouldBe NovelSelectedTextTranslationErrorReason.EmptySelection

        provider.translate(
            NovelSelectedTextTranslationRequest(
                selectedText = "a".repeat(321),
                targetLanguage = "Russian",
            ),
        ).shouldBeInstanceOf<NovelSelectedTextTranslationProviderOutcome.Unavailable>()
            .reason shouldBe NovelSelectedTextTranslationErrorReason.TooLongSelection

        server.requestCount shouldBe 0
    }
}
