package tachiyomi.domain.translation.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TranslationProfilesTest {
    @Test
    fun `profiles serialize credential refs and preserve exact model prompt and generation choices`() {
        val connection = connection()
        val profile = TranslationProfile(
            PROFILE,
            "Private label",
            connection.id,
            ModelSelection(
                "provider/exact-model:revision",
                ReasoningContract.Levels(listOf("low", "high")),
                GenerationSettings(
                    temperature = 0.7,
                    maxOutputTokens = 8192,
                    reasoning = ReasoningChoice.Level("high"),
                ),
                OutputTokenField.MAX_COMPLETION_TOKENS,
            ),
            TranslationLanguages("English", "Russian"),
            PromptSettings("ADULT_18", "LITERARY", listOf("dialogue"), "Custom private prompt"),
        )
        Json.decodeFromString<ConnectionProfile>(Json.encodeToString(connection)) shouldBe connection
        val encoded = Json.encodeToString(profile)
        val restored = Json.decodeFromString<TranslationProfile>(encoded)
        restored.exactModelId shouldBe "provider/exact-model:revision"
        restored.generation.maxOutputTokens shouldBe 8192
        restored.prompt.customText shouldBe "Custom private prompt"
        restored.prompt.styleId shouldBe "LITERARY"
        listOf("apiKey", "secret", "bearerToken").forEach { encoded.contains(it) shouldBe false }
        profile.toString().contains("Custom private prompt") shouldBe false
        profile.toString().contains("Private label") shouldBe false
        profile.prompt.toString().contains("Custom private prompt") shouldBe false
    }

    @Test
    fun `unknown and none reasoning do not guess support based on a model name`() {
        shouldThrow<IllegalArgumentException> {
            ModelSelection(
                "gemini-future-model",
                generation = GenerationSettings(reasoning = ReasoningChoice.Level("high")),
            )
        }
        shouldThrow<IllegalArgumentException> {
            ModelSelection(
                "model",
                ReasoningContract.None,
                GenerationSettings(reasoning = ReasoningChoice.Toggle(true)),
            )
        }
        ModelSelection("any-model").generation.reasoning shouldBe ReasoningChoice.ApiDefault
    }

    @Test
    fun `explicit toggle levels and token ranges validate their own choices only`() {
        ModelSelection("model", ReasoningContract.Toggle, GenerationSettings(reasoning = ReasoningChoice.Toggle(false)))
        shouldThrow<IllegalArgumentException> {
            ModelSelection(
                "model",
                ReasoningContract.Toggle,
                GenerationSettings(reasoning = ReasoningChoice.Level("low")),
            )
        }
        shouldThrow<IllegalArgumentException> {
            ModelSelection(
                "model",
                ReasoningContract.Levels(listOf("minimal", "high")),
                GenerationSettings(reasoning = ReasoningChoice.Level("medium")),
            )
        }
        val budget = ReasoningContract.Budget(128, 32768, canUseDynamic = true)
        ModelSelection("model", budget, GenerationSettings(reasoning = ReasoningChoice.Budget(-1)))
        shouldThrow<IllegalArgumentException> {
            ModelSelection("model", budget, GenerationSettings(reasoning = ReasoningChoice.Budget(0)))
        }
        shouldThrow<IllegalArgumentException> {
            ModelSelection("model", budget, GenerationSettings(reasoning = ReasoningChoice.Budget(32769)))
        }
    }

    @Test
    fun `optional generation fields require honest output wiring and finite numeric values`() {
        shouldThrow<IllegalArgumentException> {
            ModelSelection("model", generation = GenerationSettings(maxOutputTokens = 8192))
        }
        shouldThrow<IllegalArgumentException> { GenerationSettings(temperature = Double.NaN) }
        shouldThrow<IllegalArgumentException> { GenerationSettings(topP = 1.1) }
        shouldThrow<IllegalArgumentException> { GenerationSettings(topK = -1) }
        GenerationSettings(temperature = 3.0).temperature shouldBe 3.0 // No guessed universal temperature maximum.
    }

    @Test
    fun `canonical vault UUID refs duplicate bindings and unsafe endpoint forms are rejected without echo`() {
        val invalid = "sk-private-value"
        shouldThrow<IllegalArgumentException> { CredentialBinding(invalid) }.message!!.contains(invalid) shouldBe false
        shouldThrow<IllegalArgumentException> {
            connection().copy(credentialBindings = listOf(CredentialBinding(KEY_A), CredentialBinding(KEY_A)))
        }
        val unsafe = "https://user:private-value@example.com/v1?key=private-value"
        shouldThrow<IllegalArgumentException> {
            connection().copy(baseUrl = unsafe)
        }.message!!.contains("private-value") shouldBe
            false
        shouldThrow<IllegalArgumentException> { connection().copy(id = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA") }
    }

    @Test
    fun `HTTP requires explicit permission and a literal private or loopback endpoint`() {
        shouldThrow<IllegalArgumentException> { ProfileEndpointPolicy.origin("http://127.0.0.1:11434/api") }
        ProfileEndpointPolicy.origin("http://127.0.0.1:11434/api", true) shouldBe "http://127.0.0.1:11434"
        ProfileEndpointPolicy.origin("http://192.168.1.7:8000/v1", true) shouldBe "http://192.168.1.7:8000"
        ProfileEndpointPolicy.origin("http://[fd00::1]:8000/v1", true) shouldBe "http://[fd00::1]:8000"
        ProfileEndpointPolicy.origin("http://[0:0:0:0:0:0:0:1]:8000/v1", true) shouldBe "http://[0:0:0:0:0:0:0:1]:8000"
        shouldThrow<IllegalArgumentException> { ProfileEndpointPolicy.origin("//example.com/v1") }
        shouldThrow<IllegalArgumentException> { ProfileEndpointPolicy.origin("http://8.8.8.8/v1", true) }
        shouldThrow<IllegalArgumentException> { ProfileEndpointPolicy.origin("http://local.server/v1", true) }
        shouldThrow<IllegalArgumentException> { ProfileEndpointPolicy.origin("http://127.1/v1", true) }
        shouldThrow<IllegalArgumentException> { ProfileEndpointPolicy.origin("https://example.com/v1#secret") }
    }

    @Test
    fun `default execution is bounded while explicit service waiting cannot expand repairs or shrink cooldown`() {
        ExecutionPolicy().maxAttempts shouldBe 8
        ExecutionPolicy().maxRecoveryMillis shouldBe 600_000L
        shouldThrow<IllegalArgumentException> {
            ExecutionPolicy(retryMode = ProfileRetryMode.WAIT_FOR_SERVICE, repairAttempts = 4)
        }
        shouldThrow<IllegalArgumentException> { ExecutionPolicy(waitCooldownMinMillis = 1000) }
        shouldThrow<IllegalArgumentException> { ExecutionPolicy(maxRequests = 0) }
    }

    companion object {
        const val CONNECTION = "00000000-0000-0000-0000-000000000001"
        const val PROFILE = "00000000-0000-0000-0000-000000000002"
        const val KEY_A = "00000000-0000-0000-0000-000000000010"
        const val KEY_B = "00000000-0000-0000-0000-000000000020"
        fun connection(
            bindings: List<CredentialBinding> = listOf(CredentialBinding(KEY_A), CredentialBinding(KEY_B)),
        ) = ConnectionProfile(
            CONNECTION,
            "Connection",
            TranslationProtocol.OPENAI_CHAT,
            "https://api.example.com/v1",
            ProfileAuth.BEARER,
            credentialBindings = bindings,
        )
    }
}
