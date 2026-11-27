package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.TranslationProtocol

class ProfileCompatibleApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ProfileCompatibleApi

    @BeforeEach
    fun setup() {
        server = MockWebServer().apply { start() }
        api = ProfileCompatibleApi(OkHttpClient(), Json { ignoreUnknownKeys = true })
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `gemini plaintext profile mode parses multi-segment responses`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Один\n\nДва"}]}}]}""",
            ),
        )
        val connection = ConnectionProfile(
            "00000000-0000-0000-0000-000000000101",
            "Gemini",
            TranslationProtocol.GEMINI_GENERATE,
            server.url("/v1beta").toString().trimEnd('/'),
            ProfileAuth.X_GOOG_API_KEY,
            allowInsecureLocalHTTP = true,
        )
        val prepared = PreparedProfileRequest(
            url = server.url("/v1beta/models/gemini-2.5-flash:generateContent").toString(),
            body = buildJsonObject {},
            isPlaintext = true,
        )
        val translated = api.dispatch(
            connection = connection,
            prepared = prepared,
            expectedCount = 2,
            resolveCredential = { CredentialSecret("fake-key") },
        )
        translated shouldBe listOf("Один", "Два")
    }

    @Test
    fun `gemini non-plaintext mode still rejects missing xml tags when expecting multiple segments`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Один\n\nДва"}]}}]}""",
            ),
        )
        val connection = ConnectionProfile(
            "00000000-0000-0000-0000-000000000102",
            "Gemini",
            TranslationProtocol.GEMINI_GENERATE,
            server.url("/v1beta").toString().trimEnd('/'),
            ProfileAuth.X_GOOG_API_KEY,
            allowInsecureLocalHTTP = true,
        )
        val prepared = PreparedProfileRequest(
            url = server.url("/v1beta/models/gemini-2.5-flash:generateContent").toString(),
            body = buildJsonObject {},
            isPlaintext = false,
        )
        val translated = api.dispatch(
            connection = connection,
            prepared = prepared,
            expectedCount = 2,
            resolveCredential = { CredentialSecret("fake-key") },
        )
        translated shouldBe listOf(null, null)
    }

    @Test
    fun `http 503 returns transient failure without accepting partial json`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503).setBody("""{"error":{"code":503,"status":"UNAVAILABLE"}}"""),
        )
        val connection = ConnectionProfile(
            "00000000-0000-0000-0000-000000000103",
            "OpenAI",
            TranslationProtocol.OPENAI_CHAT,
            server.url("/v1").toString().trimEnd('/'),
            ProfileAuth.BEARER,
            allowInsecureLocalHTTP = true,
        )
        val prepared = PreparedProfileRequest(
            url = server.url("/v1/chat/completions").toString(),
            body = buildJsonObject {},
        )
        val error = runCatching {
            api.dispatch(
                connection = connection,
                prepared = prepared,
                expectedCount = 1,
                resolveCredential = { CredentialSecret("fake-key") },
            )
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.kind shouldBe TranslationFailureKind.TRANSIENT
        error.failure.httpStatus shouldBe 503
    }
}
