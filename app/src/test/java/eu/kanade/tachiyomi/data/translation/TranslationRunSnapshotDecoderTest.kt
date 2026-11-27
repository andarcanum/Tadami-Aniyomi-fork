package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.profile.GeminiProfileRequestShape
import eu.kanade.tachiyomi.data.translation.profile.withTranslationProfile
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol

class TranslationRunSnapshotDecoderTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val connection =
        ConnectionProfile(
            "00000000-0000-0000-0000-000000000041",
            "Custom",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.test/root/v1",
            ProfileAuth.NONE,
        )
    private val profile = TranslationProfile(
        "00000000-0000-0000-0000-000000000042",
        "Profile",
        connection.id,
        ModelSelection("manual/model-id"),
        TranslationLanguages("English", "Russian"),
    )
    private val snapshot = NovelReaderPreferences(InMemoryPreferenceStore(), json).resolveSettings(1)
        .withTranslationProfile(profile, connection).copy(systemPromptSnapshot = "Frozen prompt")
        .toTranslationQueueProfileSnapshot(replaceRulesSnapshot = emptyList())
    private fun encoded(
        value: TranslationQueueProfileSnapshot = snapshot,
    ) = json.encodeToString(TranslationQueueProfileSnapshot.serializer(), value)

    @Test
    fun `legacy and modern snapshots decode without live defaults`() {
        decodeTranslationRunSnapshot("{\"snapshotVersion\":1}", json).snapshotVersion shouldBe 1
        decodeTranslationRunSnapshot(encoded(), json) shouldBe snapshot
    }

    @Test
    fun `unknown root or nested credential fields fail with a redacted typed error`() {
        val root = Json.parseToJsonElement(encoded()) as JsonObject
        assertInvalid(JsonObject(root + ("geminiApiKey" to JsonPrimitive("sensitive-untrusted-value"))).toString())
        val connectionRoot = root.getValue("connectionProfile") as JsonObject
        assertInvalid(
            JsonObject(
                root + (
                    "connectionProfile" to JsonObject(
                        connectionRoot +
                            ("apiKey" to JsonPrimitive("sensitive-untrusted-value")),
                    )
                    ),
            ).toString(),
        )
    }

    @Test
    fun `unknown or quoted versions never coerce into a supported execution`() {
        val root = Json.parseToJsonElement(encoded()) as JsonObject
        assertInvalid(JsonObject(root + ("snapshotVersion" to JsonPrimitive("2"))).toString())
        assertInvalid(encoded(snapshot.copy(snapshotVersion = 3)))
    }

    @Test
    fun `modern intent needs a matching connection and bounded frozen prompt`() {
        assertInvalid(encoded(snapshot.copy(systemPromptSnapshot = null)))
        assertInvalid(encoded(snapshot.copy(systemPromptSnapshot = " ")))
        assertInvalid(encoded(snapshot.copy(systemPromptSnapshot = "x".repeat(100_001))))
        assertInvalid(encoded(snapshot.copy(translationProfile = profile.copy(connectionId = profile.id))))
    }

    @Test
    fun `legacy cannot carry hidden modern metadata`() {
        assertInvalid(encoded(snapshot.copy(snapshotVersion = 1)))
    }

    @Test
    fun `provider and native wire shape must match the selected protocol`() {
        assertInvalid(encoded(snapshot.copy(translationProvider = "GEMINI")))
        assertInvalid(
            encoded(
                snapshot.copy(
                    geminiRequestShape = GeminiProfileRequestShape.PUBLIC_LEGACY,
                    geminiGenerationConfig = JsonObject(emptyMap()),
                ),
            ),
        )
        assertInvalid(
            encoded(
                snapshot.copy(
                    geminiGenerationConfig = JsonObject(mapOf("apiKey" to JsonPrimitive("sensitive-untrusted-value"))),
                ),
            ),
        )
    }

    @Test
    fun `oversized payload fails before decoding`() {
        assertInvalid("x".repeat(1_048_577))
    }

    @Test
    fun `only modern action failures park and budget reservations remain classified separately`() {
        val budget = TranslationFailure(TranslationFailureKind.QUOTA, "Budget", attempted = false)
        translationActionPauseReason(encoded(), budget) shouldBe TranslationActionPauseReason.BUDGET_LIMIT
        translationActionPauseReason(encoded(), budget.copy(attempted = true)) shouldBe
            TranslationActionPauseReason.NEEDS_ACTION
        translationActionPauseReason("{\"snapshotVersion\":1}", budget) shouldBe null
        translationActionPauseReason(encoded(), budget.copy(kind = TranslationFailureKind.TRANSIENT)) shouldBe null
    }

    private fun assertInvalid(raw: String) {
        val error = runCatching {
            decodeTranslationRunSnapshot(raw, json)
        }.exceptionOrNull() as TranslationAttemptException
        error.failure.kind shouldBe TranslationFailureKind.CONFIGURATION
        error.failure.attempted shouldBe false
        error.message.orEmpty().contains("sensitive-untrusted-value") shouldBe false
    }
}
