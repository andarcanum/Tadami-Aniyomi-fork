package eu.kanade.tachiyomi.data.translation.profile

import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.toTranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol

class ReaderTranslationProfileSettingsTest {
    private val base = NovelReaderPreferences(InMemoryPreferenceStore(), Json).resolveSettings(7)
        .copy(
            geminiApiKey = "test-key",
            openRouterApiKey = "test-key",
            deepSeekApiKey = "test-key",
            mistralApiKey = "test-key",
            nvidiaApiKey = "test-key",
            ollamaCloudApiKey = "test-key",
        )
    private val connection =
        ConnectionProfile(
            "00000000-0000-0000-0000-000000000051",
            "Connection",
            TranslationProtocol.OPENAI_CHAT,
            "https://example.test/custom/v1",
            ProfileAuth.NONE,
        )
    private val profile = TranslationProfile(
        "00000000-0000-0000-0000-000000000052",
        "Profile",
        connection.id,
        ModelSelection("manual/model"),
        TranslationLanguages("English", "Russian"),
    )

    @Test
    fun `reader uses the admission snapshot identity without a UI secret`() = runTest {
        val frozen = base.withTranslationProfile(profile, connection).copy(systemPromptSnapshot = "Frozen prompt")
            .toTranslationQueueProfileSnapshot(replaceRulesSnapshot = emptyList())
        val queue = mockk<TranslationQueueManager>()
        coEvery { queue.freezeProfileSnapshot(any(), 11, emptyList()) } returns frozen
        val settings = queue.readerProfileSettings(base, 11, emptyList())
        settings.connectionProfile shouldBe connection
        settings.translationProfile shouldBe profile
        settings.systemPromptSnapshot shouldBe "Frozen prompt"
        settings.translationCacheModelId() shouldBe "manual/model"
        listOf(
            settings.geminiApiKey,
            settings.openRouterApiKey,
            settings.deepSeekApiKey,
            settings.mistralApiKey,
            settings.nvidiaApiKey,
            settings.ollamaCloudApiKey,
        ) shouldBe List(6) { "" }
        coVerify {
            queue.freezeProfileSnapshot(
                match {
                    it.geminiApiKey.isEmpty() && it.openRouterApiKey.isEmpty()
                },
                11,
                emptyList(),
            )
        }
    }

    @Test
    fun `legacy reader settings retain their behavior but never a copied key`() = runTest {
        val queue = mockk<TranslationQueueManager>()
        coEvery { queue.freezeProfileSnapshot(any(), 11, emptyList()) } returns base.toTranslationQueueProfileSnapshot()
        val settings = queue.readerProfileSettings(base, 11, emptyList())
        settings.translationProfile shouldBe null
        settings.geminiModel shouldBe base.geminiModel
        settings.geminiApiKey shouldBe ""
    }

    @Test
    fun `withTranslationProfile resets stale legacy reasoning defaults`() {
        val dirtyBase = base.copy(geminiReasoningEffort = "high", geminiBudgetTokens = 16_384)

        val apiDefault = dirtyBase.withTranslationProfile(profile, connection)
        apiDefault.geminiReasoningEffort shouldBe "minimal"
        apiDefault.geminiBudgetTokens shouldBe 8192

        val leveled = dirtyBase.withTranslationProfile(
            profile.copy(
                model = ModelSelection(
                    "manual/model",
                    ReasoningContract.Levels(listOf("low", "medium", "high")),
                    GenerationSettings(reasoning = ReasoningChoice.Level("low")),
                ),
            ),
            connection,
        )
        leveled.geminiReasoningEffort shouldBe "low"
        leveled.geminiBudgetTokens shouldBe 8192

        val budgeted = dirtyBase.withTranslationProfile(
            profile.copy(
                model = ModelSelection(
                    "manual/model",
                    ReasoningContract.Budget(0, 24_576),
                    GenerationSettings(reasoning = ReasoningChoice.Budget(4_096)),
                ),
            ),
            connection,
        )
        budgeted.geminiReasoningEffort shouldBe "minimal"
        budgeted.geminiBudgetTokens shouldBe 4_096
    }

    @Test
    fun `nativeGeminiRequest joins prompts and segments with real newlines`() {
        val nativeConnection = connection.copy(
            protocol = TranslationProtocol.GEMINI_GENERATE,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        )
        val nativeProfile = profile.copy(
            model = ModelSelection("gemini-2.5-flash"),
        )
        val snapshot = base.withTranslationProfile(nativeProfile, nativeConnection)
            .toTranslationQueueProfileSnapshot()
            .copy(
                systemPromptSnapshot = "SYS_PROMPT",
                geminiRequestShape = GeminiProfileRequestShape.PUBLIC_LEGACY,
                geminiGenerationConfig = buildJsonObject { },
            )

        val prepared = snapshot.nativeGeminiRequest(listOf("First", "Second"))
        val userText = prepared.body["contents"]!!
            .jsonArray[0].jsonObject["parts"]!!
            .jsonArray[0].jsonObject["text"]!!
            .jsonPrimitive.content

        prepared.isPlaintext shouldBe false
        userText.contains("\\n") shouldBe false
        userText.startsWith("SYS_PROMPT\n\n") shouldBe true
        userText.contains("<s i='0'>First</s>\n<s i='1'>Second</s>") shouldBe true
    }
}
