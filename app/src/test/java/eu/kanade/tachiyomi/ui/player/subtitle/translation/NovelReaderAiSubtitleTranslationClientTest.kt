package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.data.translation.TranslationQueueProfileSnapshot
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ReasoningChoice
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol

class NovelReaderAiSubtitleTranslationClientTest {
    private fun preferences(): NovelReaderPreferences =
        NovelReaderPreferences(PersistingPreferenceStore(), Json { encodeDefaults = true })

    private fun buildClient(
        prefs: NovelReaderPreferences,
        geminiService: GeminiTranslationService = mockk(relaxed = true),
        openRouterService: OpenRouterTranslationService = mockk(relaxed = true),
        deepSeekService: DeepSeekTranslationService = mockk(relaxed = true),
        mistralService: MistralTranslationService = mockk(relaxed = true),
        nvidiaService: NvidiaTranslationService = mockk(relaxed = true),
        ollamaCloudService: OllamaCloudTranslationService = mockk(relaxed = true),
        activeProfile: () -> Pair<TranslationProfile, ConnectionProfile>? = { null },
        activeProfileSuspend: suspend () -> Pair<TranslationProfile, ConnectionProfile>? = { activeProfile() },
        legacyCredential: suspend (NovelTranslationProvider) -> String? = { null },
        profileTranslator: suspend (
            TranslationQueueProfileSnapshot,
            List<String>,
        ) -> List<String?> = { _, segments -> segments },
    ): NovelReaderAiSubtitleTranslationClient {
        return NovelReaderAiSubtitleTranslationClient(
            preferences = prefs,
            geminiTranslationService = geminiService,
            openRouterTranslationService = openRouterService,
            deepSeekTranslationService = deepSeekService,
            mistralTranslationService = mistralService,
            nvidiaTranslationService = nvidiaService,
            ollamaCloudTranslationService = ollamaCloudService,
            activeGlobalProfileResolver = activeProfile,
            activeGlobalProfileSuspendResolver = activeProfileSuspend,
            legacyCredentialResolver = legacyCredential,
            profileTranslator = profileTranslator,
        )
    }

    @Test
    fun `legacy fingerprint updates dynamically and normalizes reasoning effort`() = runTest {
        val prefs = preferences().apply {
            translationProvider().set(NovelTranslationProvider.GEMINI)
            geminiModel().set("gemma-4-31b-it")
            geminiReasoningEffort().set("medium")
            geminiApiKey().set("test-key")
        }
        val geminiService = mockk<GeminiTranslationService>()
        coEvery { geminiService.translateBatch(any(), any(), any()) } returns listOf("Privet")
        val client = buildClient(prefs = prefs, geminiService = geminiService)

        val fpUnsupportedMedium = client.fingerprint
        prefs.geminiReasoningEffort().set("minimal")
        assertEquals(fpUnsupportedMedium, client.fingerprint)

        prefs.geminiReasoningEffort().set("high")
        val fpHigh = client.fingerprint
        assertNotEquals(fpUnsupportedMedium, fpHigh)

        prefs.geminiBudgetTokens().set(4096)
        val fpBudget = client.fingerprint
        assertNotEquals(fpHigh, fpBudget)

        prefs.geminiTemperature().set(0.4f)
        val fpTemp = client.fingerprint
        assertNotEquals(fpBudget, fpTemp)

        prefs.translationProvider().set(NovelTranslationProvider.OPENROUTER)
        prefs.openRouterBaseUrl().set("https://openrouter.example/api/v1")
        val fpOpenRouter1 = client.fingerprint
        prefs.openRouterBaseUrl().set("https://other-proxy.example/v1")
        assertNotEquals(fpOpenRouter1, client.fingerprint)

        prefs.translationProvider().set(NovelTranslationProvider.GEMINI)
        prefs.geminiReasoningEffort().set("medium")
        client.translateIndexedSegments(
            prompt = "Prompt",
            segments = listOf(IndexedSubtitleSegment(1, "Hello")),
            sourceLanguage = "English",
            targetLanguage = "Russian",
        )
        coVerify(exactly = 1) {
            geminiService.translateBatch(
                segments = listOf("Hello"),
                params = match { it.model == "gemma-4-31b-it" && it.reasoningEffort == "minimal" },
                onLog = any(),
            )
        }
    }

    @Test
    fun `blank legacy API key fails fast with IllegalStateException without calling provider`() = runTest {
        val prefs = preferences().apply {
            translationProvider().set(NovelTranslationProvider.GEMINI)
            geminiApiKey().set("")
        }
        val geminiService = mockk<GeminiTranslationService>(relaxed = true)
        val client = buildClient(
            prefs = prefs,
            geminiService = geminiService,
            legacyCredential = { null },
        )

        assertThrows<IllegalStateException> {
            client.translateIndexedSegments(
                prompt = "Prompt",
                segments = listOf(IndexedSubtitleSegment(0, "Hello")),
                sourceLanguage = "English",
                targetLanguage = "Russian",
            )
        }
        coVerify(exactly = 0) { geminiService.translateBatch(any(), any(), any()) }
    }

    @Test
    fun `active global v2 profile drives dynamic fingerprint and profile translation`() = runTest {
        val prefs = preferences()
        val geminiService = mockk<GeminiTranslationService>(relaxed = true)
        val openRouterService = mockk<OpenRouterTranslationService>(relaxed = true)
        val connection1 = ConnectionProfile(
            id = "00000000-0000-0000-0000-000000000201",
            name = "Custom OpenAI",
            protocol = TranslationProtocol.OPENAI_CHAT,
            baseUrl = "https://custom-sub.example.com",
            auth = ProfileAuth.BEARER,
        )
        val profile1 = TranslationProfile(
            id = "00000000-0000-0000-0000-000000000301",
            name = "Sub Profile",
            connectionId = connection1.id,
            model = ModelSelection(
                exactModelId = "custom/sub-model-1",
                reasoningContract = ReasoningContract.Levels(listOf("low", "high")),
                generation = GenerationSettings(maxOutputTokens = 2048, reasoning = ReasoningChoice.Level("high")),
                outputTokenField = OutputTokenField.MAX_COMPLETION_TOKENS,
            ),
            languages = TranslationLanguages("English", "Russian"),
        )
        var current: Pair<TranslationProfile, ConnectionProfile>? = profile1 to connection1
        val capturedSnapshots = mutableListOf<TranslationQueueProfileSnapshot>()

        val client = buildClient(
            prefs = prefs,
            geminiService = geminiService,
            openRouterService = openRouterService,
            activeProfile = { current },
            profileTranslator = { snapshot, segments ->
                capturedSnapshots += snapshot
                segments.map { "RU:$it" }
            },
        )

        val fp1 = client.fingerprint
        val profile2 = profile1.copy(
            model = profile1.model.copy(exactModelId = "custom/sub-model-2"),
        )
        current = profile2 to connection1
        val fp2 = client.fingerprint
        assertNotEquals(fp1, fp2)

        current = profile1 to connection1
        val xml = client.translateIndexedSegments(
            prompt = "Translate cues naturally",
            segments = listOf(
                IndexedSubtitleSegment(2, "Hello"),
                IndexedSubtitleSegment(5, "A & B < C > D"),
            ),
            sourceLanguage = "English",
            targetLanguage = "Russian",
        )

        assertEquals(
            "<s i=\"2\">RU:Hello</s>\n<s i=\"5\">RU:A &amp; B &lt; C &gt; D</s>",
            xml,
        )
        assertEquals(1, capturedSnapshots.size)
        val snapshot = capturedSnapshots.single()
        assertEquals("custom/sub-model-1", snapshot.translationProfile?.exactModelId)
        assertTrue(snapshot.systemPromptSnapshot?.contains("Translate cues naturally") == true)
        assertTrue(snapshot.systemPromptSnapshot?.contains("Subtitle mode:") == true)
        coVerify(exactly = 0) { geminiService.translateBatch(any(), any(), any()) }
        coVerify(exactly = 0) { openRouterService.translateBatch(any(), any(), any()) }
    }
}
