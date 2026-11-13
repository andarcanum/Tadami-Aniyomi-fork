package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.presentation.more.settings.translation.AiGenerationField
import eu.kanade.tachiyomi.data.translation.profile.ProfileModelOperationsImpl
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.credentials.CredentialRead
import tachiyomi.domain.translation.credentials.CredentialSecret
import tachiyomi.domain.translation.credentials.CredentialStore
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.CredentialBinding
import tachiyomi.domain.translation.profile.CredentialHealth
import tachiyomi.domain.translation.profile.CredentialHealthStatus
import tachiyomi.domain.translation.profile.CredentialRoutingState
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.profile.TranslationProtocol

class ProfileModelOperationsImplTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setup() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `gemini models catalogue normalizes bare origin filters models resolves capabilities and skips public store`() =
        runTest {
            val credentialId = "00000000-0000-0000-0000-000000000091"
            val repository = mockk<TranslationProfileRepository>(relaxed = true)
            coEvery { repository.readRoutingState() } returns CredentialRoutingState(
                health = mapOf(credentialId to CredentialHealth(CredentialHealthStatus.READY)),
            )
            val credentials = object : CredentialStore {
                override suspend fun put(id: String, secret: String) = Unit
                override suspend fun remove(id: String) = Unit
                override suspend fun read(id: String): CredentialRead =
                    CredentialRead.Available(CredentialSecret("gemini-secret"))
            }
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "models": [
                        {"name": "models/gemma-4-31b-it", "displayName": "Gemma 4 31B", "supportedGenerationMethods": ["generateContent"], "thinking": true},
                        {"name": "models/gemma-4-26b-a4b-it", "displayName": "Gemma 4 26B", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemini-3.5-flash-lite", "displayName": "Gemini 3.5 Flash Lite", "supportedGenerationMethods": ["generateContent"], "outputTokenLimit": 8192, "temperature": 1.0, "topP": 0.95, "topK": 64},
                        {"name": "models/gemini-2.5-flash", "displayName": "Gemini 2.5 Flash", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemini-2.5-pro", "displayName": "Gemini 2.5 Pro", "supportedGenerationMethods": ["generateContent"], "thinking": false},
                        {"name": "models/gemini-future-custom-only-test", "displayName": "Future Custom", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemma-3-27b-it", "displayName": "Gemma 3 27B", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemini-2.5-flash-tts", "displayName": "TTS", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemini-2.5-flash-image", "displayName": "Image", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemini-embedding-001", "displayName": "Embed", "supportedGenerationMethods": ["generateContent"]}
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            val connection = ConnectionProfile(
                id = "00000000-0000-0000-0000-000000000011",
                name = "Custom Gemini Proxy",
                protocol = TranslationProtocol.GEMINI_GENERATE,
                baseUrl = server.url("/").toString().trimEnd('/'),
                auth = ProfileAuth.X_GOOG_API_KEY,
                credentialBindings = listOf(CredentialBinding(credentialId)),
                allowInsecureLocalHTTP = true,
            )
            val operations = ProfileModelOperationsImpl(
                client = OkHttpClient(),
                json = Json { ignoreUnknownKeys = true },
                credentials = credentials,
                repository = repository,
            )

            val rows = operations.models(connection)
            val request = server.takeRequest()
            request.path!!.startsWith("/v1beta/models?") shouldBe true
            request.getHeader("x-goog-api-key") shouldBe "gemini-secret"

            val byId = rows.associateBy { it.selection.exactModelId }
            rows.map { it.selection.exactModelId } shouldContainExactly listOf(
                "gemini-2.5-flash",
                "gemini-2.5-pro",
                "gemini-3.5-flash-lite",
                "gemini-future-custom-only-test",
                "gemma-4-26b-a4b-it",
                "gemma-4-31b-it",
            )
            rows.map { it.selection.exactModelId }.apply {
                shouldNotContain("gemma-3-27b-it")
                shouldNotContain("gemini-2.5-flash-tts")
                shouldNotContain("gemini-2.5-flash-image")
                shouldNotContain("gemini-embedding-001")
            }

            byId.getValue("gemma-4-31b-it").selection.reasoningContract shouldBe ReasoningContract.Toggle
            byId.getValue("gemma-4-26b-a4b-it").selection.reasoningContract shouldBe ReasoningContract.Toggle
            byId.getValue("gemini-3.5-flash-lite").selection.reasoningContract shouldBe
                ReasoningContract.Levels(listOf("minimal", "low", "medium", "high"))
            byId.getValue("gemini-3.5-flash-lite").supportedGenerationFields shouldBe setOf(
                AiGenerationField.TEMPERATURE,
                AiGenerationField.TOP_P,
                AiGenerationField.TOP_K,
                AiGenerationField.OUTPUT_TOKENS,
            )
            byId.getValue("gemini-2.5-flash").selection.reasoningContract shouldBe
                ReasoningContract.Budget(minimum = 0, maximum = 24576, canDisable = true, canUseDynamic = true)
            byId.getValue("gemini-2.5-pro").selection.reasoningContract shouldBe ReasoningContract.None
            byId.getValue("gemini-future-custom-only-test").selection.reasoningContract shouldBe
                ReasoningContract.Unknown
            GeminiModelMetadataStore.lookup("gemini-future-custom-only-test") shouldBe null
        }

    @Test
    fun `custom OpenAI endpoint without v1 fetches v1 models with bearer auth`() = runTest {
        val credentialId = "00000000-0000-0000-0000-000000000092"
        val repository = mockk<TranslationProfileRepository>(relaxed = true)
        coEvery { repository.readRoutingState() } returns CredentialRoutingState(
            health = mapOf(credentialId to CredentialHealth(CredentialHealthStatus.READY)),
        )
        val credentials = object : CredentialStore {
            override suspend fun put(id: String, secret: String) = Unit
            override suspend fun remove(id: String) = Unit
            override suspend fun read(id: String): CredentialRead =
                CredentialRead.Available(CredentialSecret("openai-secret"))
        }
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": [
                    {"id": "custom/model-a", "supported_parameters": ["max_completion_tokens", "temperature", "top_p"]},
                    {"id": "custom/model-b"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val connection = ConnectionProfile(
            id = "00000000-0000-0000-0000-000000000012",
            name = "Custom OpenAI",
            protocol = TranslationProtocol.OPENAI_CHAT,
            baseUrl = server.url("/").toString().trimEnd('/'),
            auth = ProfileAuth.BEARER,
            presetFlavor = PresetFlavor.NONE,
            credentialBindings = listOf(CredentialBinding(credentialId)),
            allowInsecureLocalHTTP = true,
        )
        val operations = ProfileModelOperationsImpl(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            credentials = credentials,
            repository = repository,
        )

        val rows = operations.models(connection)
        val request = server.takeRequest()
        request.path shouldBe "/v1/models"
        request.getHeader("Authorization") shouldBe "Bearer openai-secret"

        val byId = rows.associateBy { it.selection.exactModelId }
        rows.map { it.selection.exactModelId } shouldContainExactly listOf("custom/model-a", "custom/model-b")
        byId.getValue("custom/model-a").selection.outputTokenField shouldBe OutputTokenField.MAX_COMPLETION_TOKENS
        byId.getValue("custom/model-a").supportedGenerationFields shouldBe setOf(
            AiGenerationField.TEMPERATURE,
            AiGenerationField.TOP_P,
            AiGenerationField.OUTPUT_TOKENS,
        )
        byId.getValue("custom/model-b").selection.outputTokenField shouldBe OutputTokenField.OMIT
    }
}
