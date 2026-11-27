package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.service.TranslationRequestGate
import java.util.concurrent.TimeUnit

class TranslationHttpTransportTest {
    private lateinit var server: MockWebServer
    private val now = 1_794_402_000_000L
    private lateinit var transport: TranslationHttpTransport

    @BeforeEach fun setup() {
        server = MockWebServer().apply { start() }
        transport = TranslationHttpTransport(OkHttpClient(), Json, TranslationRequestGate { now }, { now })
    }

    @AfterEach fun close() {
        server.shutdown()
    }
    private fun request() = POST(
        server.url("/translate").toString(),
        headersOf("Content-Type", "application/json", "Authorization", "Bearer fake-secret"),
        "{}".toRequestBody(jsonMime),
    )

    @Test
    fun `503 preserves typed status retry minimum and never leaks the key or retries itself`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503).setHeader("Retry-After", "300")
                .setBody("""{"error":{"status":"UNAVAILABLE","message":"busy fake-secret"}}"""),
        )
        val error = runCatching {
            transport.execute(request(), "Gemini", "fake-secret")
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.kind shouldBe TranslationFailureKind.TRANSIENT
        error.failure.httpStatus shouldBe 503
        error.failure.retryAfterAt shouldBe now + 300_000
        error.message!!.contains("fake-secret") shouldBe false
        server.requestCount shouldBe 1
    }

    @Test
    fun `zero retry after cannot trigger a hidden OkHttp POST follow up`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("{}"))
        val error = runCatching {
            transport.execute(request(), "API", "fake-secret")
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.httpStatus shouldBe 503
        server.requestCount shouldBe 1
    }

    @Test
    fun `auth and daily quota are terminal classes rather than a string retry hint`() = runTest {
        classifyTranslationHttpFailure(401, "{}", null, now, "API").kind shouldBe TranslationFailureKind.AUTHORIZATION
        classifyTranslationHttpFailure(
            429,
            """{"error":{"details":[{"violations":[{"quotaId":"GenerateRequestsPerDay"}]}]}}""",
            "300",
            now,
            "API",
        ).kind shouldBe TranslationFailureKind.QUOTA
        classifyTranslationHttpFailure(400, "{}", null, now, "API").kind shouldBe TranslationFailureKind.CONFIGURATION
    }

    @Test
    fun `free form provider source echo never enters shared failure diagnostics`() {
        val failure =
            classifyTranslationHttpFailure(
                400,
                """{"error":{"message":"NOVEL_SOURCE_SENTINEL fake-secret","status":"NOVEL_SOURCE_SENTINEL"}}""",
                null,
                now,
                "API",
                secret = "  fake-secret  ",
            )
        failure.message.contains("NOVEL_SOURCE_SENTINEL") shouldBe false
        failure.message.contains("fake-secret") shouldBe false
        failure.providerCode shouldBe null
    }

    @Test
    fun `HTTP date delay is respected and invalid delay ignored`() {
        parseTranslationRetryAfter("Wed, 11 Nov 2026 13:05:00 GMT", now) shouldBe now + 300_000
        parseTranslationRetryAfter("not a date", now) shouldBe null
        parseTranslationRetryAfter("NaN", now) shouldBe null
    }

    @Test
    fun `redirect never forwards credentials to a second origin`() = runTest {
        val target = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.url("/secret")))
            val error = runCatching {
                transport.execute(request(), "API", "fake-secret")
            }.exceptionOrNull() as TranslationAttemptException
            error.failure.httpStatus shouldBe 302
            target.requestCount shouldBe 0
        } finally {
            target.shutdown()
        }
    }

    @Test
    fun `cancellation aborts a pending call and releases provider admission`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { transport.execute(request(), "API", "fake-secret") }
        (server.takeRequest(2, TimeUnit.SECONDS) != null) shouldBe true
        pending.cancel()
        (runCatching { pending.await() }.exceptionOrNull() is CancellationException) shouldBe true
        server.enqueue(MockResponse().setBody("{}"))
        transport.execute(request(), "API", "fake-secret") shouldBe "{}"
    }

    @Test
    fun `unsafe header and URL values fail without exposing them in diagnostics`() {
        val key = "bad" + 10.toChar() + "key"
        val headerError = runCatching { validateTranslationCredential(key) }.exceptionOrNull()
        (headerError is TranslationAttemptException) shouldBe true
        headerError?.message?.contains(key) shouldBe false
        (
            runCatching {
                validatedTranslationUrl("https://example.com?key=fake-secret")
            }.exceptionOrNull() is TranslationAttemptException
            ) shouldBe
            true
        redactTranslationDiagnostic(
            "Bearer fake-secret https://example.com?key=fake-secret",
            listOf("fake-secret"),
        ).contains("fake-secret") shouldBe
            false
    }

    @Test
    fun `529 and 520 to 524 classify as transient and quota codes classify as quota`() {
        listOf(520, 521, 522, 523, 524, 529).forEach { status ->
            val failure = classifyTranslationHttpFailure(status, "<html>unavailable</html>", null, now, "API")
            failure.kind shouldBe TranslationFailureKind.TRANSIENT
            failure.httpStatus shouldBe status
        }
        classifyTranslationHttpFailure(
            429,
            """{"error":{"code":"insufficient_quota","type":"tokens"}}""",
            null,
            now,
            "API",
        ).kind shouldBe TranslationFailureKind.QUOTA
        classifyTranslationHttpFailure(
            400,
            """{"error":{"code":"rate_limit_exceeded","message":"slow down"}}""",
            null,
            now,
            "API",
        ).kind shouldBe TranslationFailureKind.RATE_LIMIT
    }

    @Test
    fun `oversized non-2xx error preserves 503 status and retry after instead of invalid response`() = runTest {
        val hugeHtml = "<html>" + "x".repeat(2_200_000) + "</html>"
        server.enqueue(
            MockResponse().setResponseCode(503).setHeader("Retry-After", "45").setBody(hugeHtml),
        )
        val error = runCatching {
            transport.execute(request(), "API", "fake-secret")
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.kind shouldBe TranslationFailureKind.TRANSIENT
        error.failure.httpStatus shouldBe 503
        error.failure.retryAfterAt shouldBe now + 45_000L
    }

    @Test
    fun `http 200 json error defers gate and parses non-int code cleanly`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"error":{"code":"503","status":"UNAVAILABLE","message":"busy"}}""",
            ),
        )
        val first = runCatching {
            transport.execute(request(), "API", "fake-secret")
        }.exceptionOrNull() as TranslationAttemptException
        first.failure.kind shouldBe TranslationFailureKind.TRANSIENT
        first.failure.httpStatus shouldBe 503

        val deferred = runCatching {
            transport.execute(request(), "API", "fake-secret")
        }.exceptionOrNull() as TranslationAttemptException
        deferred.failure.kind shouldBe TranslationFailureKind.WAITING
        deferred.failure.attempted shouldBe false
        server.requestCount shouldBe 1

        parseTranslationPayload("""{"choices":[],"error":null}""", Json, "API").containsKey("choices") shouldBe true
        val stringCodeError = runCatching {
            parseTranslationPayload("""{"error":{"code":"503","status":"UNAVAILABLE"}}""", Json, "API")
        }.exceptionOrNull() as TranslationAttemptException
        stringCodeError.failure.kind shouldBe TranslationFailureKind.TRANSIENT
        stringCodeError.failure.httpStatus shouldBe 503
    }
}
