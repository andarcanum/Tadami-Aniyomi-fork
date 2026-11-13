package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.presentation.more.settings.translation.AiTranslationModelUi
import eu.kanade.tachiyomi.data.translation.profile.withTranslationProfile
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiModelEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.GenerationSettings
import tachiyomi.domain.translation.profile.ModelSelection
import tachiyomi.domain.translation.profile.OutputTokenField
import tachiyomi.domain.translation.profile.PresetFlavor
import tachiyomi.domain.translation.profile.ProfileAuth
import tachiyomi.domain.translation.profile.ReasoningContract
import tachiyomi.domain.translation.profile.TranslationLanguages
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProtocol
import java.io.IOException

class NovelAiProviderControllerTest {

    private fun baseSettings(): NovelReaderSettings {
        return NovelReaderPreferences(
            InMemoryPreferenceStore(),
            Json { encodeDefaults = true },
        ).resolveSettings(-1L)
    }

    private fun createController(
        settingsProvider: () -> NovelReaderSettings?,
        openRouterModelsService: OpenRouterModelsService = mockk(relaxed = true),
        fetchProfileModels: suspend (ConnectionProfile) -> List<AiTranslationModelUi>,
    ): Pair<NovelAiProviderController, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = mockk<NovelAiProviderHost>(relaxed = true)
        every { host.providerScope } returns scope
        every { host.providerReaderSettings() } answers { settingsProvider() }
        val controller = NovelAiProviderController(
            host = host,
            application = mockk(relaxed = true),
            novelReaderPreferences = mockk(relaxed = true),
            geminiModelsService = mockk(relaxed = true),
            openRouterModelsService = openRouterModelsService,
            deepSeekModelsService = mockk(relaxed = true),
            mistralModelsService = mockk(relaxed = true),
            nvidiaModelsService = mockk(relaxed = true),
            ollamaCloudModelsService = mockk(relaxed = true),
            fetchProfileModels = fetchProfileModels,
        )
        return controller to scope
    }

    @Test
    fun `profile model refresh sets loading true immediately and resets false on success and failure`() = runBlocking {
        val connection = ConnectionProfile(
            id = "00000000-0000-0000-0000-000000000101",
            name = "OpenRouter",
            protocol = TranslationProtocol.OPENAI_CHAT,
            baseUrl = "https://openrouter.ai/api/v1",
            auth = ProfileAuth.BEARER,
            presetFlavor = PresetFlavor.OPENROUTER,
        )
        val profile = TranslationProfile(
            id = "00000000-0000-0000-0000-000000000102",
            name = "OR Profile",
            connectionId = connection.id,
            model = ModelSelection("openai/gpt-4o-mini"),
            languages = TranslationLanguages("English", "Russian"),
        )
        val settings = baseSettings().withTranslationProfile(profile, connection)
        var pending = CompletableDeferred<List<AiTranslationModelUi>>()
        val (controller, scope) = createController(settingsProvider = { settings }) {
            pending.await()
        }

        try {
            controller.refreshOpenRouterModels()
            controller.snapshot().isOpenRouterModelsLoading shouldBe true

            pending.complete(
                listOf(
                    AiTranslationModelUi(
                        selection = ModelSelection("anthropic/claude-sonnet-4"),
                        label = "Claude Sonnet 4",
                    ),
                ),
            )
            withTimeout(5_000) {
                while (controller.snapshot().isOpenRouterModelsLoading) {
                    delay(10)
                }
            }
            controller.snapshot().openRouterModelIds shouldBe listOf("anthropic/claude-sonnet-4")

            pending = CompletableDeferred()
            controller.refreshOpenRouterModels()
            controller.snapshot().isOpenRouterModelsLoading shouldBe true

            pending.completeExceptionally(IOException("catalogue offline"))
            withTimeout(5_000) {
                while (controller.snapshot().isOpenRouterModelsLoading) {
                    delay(10)
                }
            }
            controller.snapshot().openRouterModelIds shouldBe listOf("anthropic/claude-sonnet-4")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `gemini profile model refresh preserves existing metadata and infers reasoning limits`() = runBlocking {
        val connection = ConnectionProfile(
            id = "00000000-0000-0000-0000-000000000201",
            name = "Gemini Private",
            protocol = TranslationProtocol.GEMINI_GENERATE,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            auth = ProfileAuth.X_GOOG_API_KEY,
            presetFlavor = PresetFlavor.GEMINI_PRIVATE,
        )
        val profile = TranslationProfile(
            id = "00000000-0000-0000-0000-000000000202",
            name = "Gemini Profile",
            connectionId = connection.id,
            model = ModelSelection("gemini-2.5-pro"),
            languages = TranslationLanguages("English", "Russian"),
        )
        val settings = baseSettings().withTranslationProfile(profile, connection)
        val existingEntry = GeminiModelEntry(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro Old",
            inputTokenLimit = 1_048_576,
            outputTokenLimit = 65_536,
            temperature = 1.0f,
            maxTemperature = 2.0f,
            topP = 0.95f,
            topK = 64,
            thinking = true,
            supportedGenerationMethods = listOf("generateContent"),
        )
        val (controller, scope) = createController(settingsProvider = { settings }) {
            listOf(
                AiTranslationModelUi(
                    selection = ModelSelection("gemini-2.5-pro"),
                    label = "Gemini 2.5 Pro",
                ),
                AiTranslationModelUi(
                    selection = ModelSelection(
                        exactModelId = "gemini-3-flash",
                        reasoningContract = ReasoningContract.Levels(listOf("low", "high")),
                        generation = GenerationSettings(maxOutputTokens = 8192, temperature = 0.7),
                        outputTokenField = OutputTokenField.MAX_OUTPUT_TOKENS,
                    ),
                    label = "Gemini 3 Flash",
                ),
            )
        }

        try {
            controller.restoreFromReaderState(
                NovelAiProviderState(geminiModelEntries = listOf(existingEntry)),
            )

            controller.refreshGeminiModels()
            withTimeout(5_000) {
                while (controller.snapshot().isGeminiModelsLoading ||
                    controller.snapshot().geminiModelEntries.size != 2
                ) {
                    delay(10)
                }
            }

            val updatedPro = controller.snapshot().geminiModelEntries.first { it.id == "gemini-2.5-pro" }
            updatedPro.displayName shouldBe "Gemini 2.5 Pro"
            updatedPro.inputTokenLimit shouldBe 1_048_576
            updatedPro.outputTokenLimit shouldBe 65_536
            updatedPro.maxTemperature shouldBe 2.0f
            updatedPro.thinking shouldBe true
            updatedPro.supportedGenerationMethods shouldBe listOf("generateContent")

            val inferredFlash = controller.snapshot().geminiModelEntries.first { it.id == "gemini-3-flash" }
            inferredFlash.outputTokenLimit shouldBe 8192
            inferredFlash.temperature shouldBe 0.7f
            inferredFlash.thinking shouldBe true
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `custom openai chat connection avoids openRouterModelIds and OpenRouter fallback`() = runBlocking {
        val customOpenAi = ConnectionProfile(
            id = "00000000-0000-0000-0000-000000000301",
            name = "Custom vLLM",
            protocol = TranslationProtocol.OPENAI_CHAT,
            baseUrl = "https://api.example.com/v1",
            auth = ProfileAuth.BEARER,
            presetFlavor = PresetFlavor.NONE,
        )
        val profile = TranslationProfile(
            id = "00000000-0000-0000-0000-000000000302",
            name = "Custom OpenAI Profile",
            connectionId = customOpenAi.id,
            model = ModelSelection("qwen-32b"),
            languages = TranslationLanguages("English", "Russian"),
        )
        val settings = baseSettings()
            .copy(openRouterApiKey = "or-secret-key", openRouterBaseUrl = "https://openrouter.ai/api/v1")
            .withTranslationProfile(profile, customOpenAi)
        val openRouterService = mockk<OpenRouterModelsService>(relaxed = true)
        var fetchCalls = 0
        val (controller, scope) = createController(
            settingsProvider = { settings },
            openRouterModelsService = openRouterService,
        ) {
            fetchCalls++
            listOf(AiTranslationModelUi(ModelSelection("qwen-32b"), "Qwen 32B"))
        }

        try {
            controller.restoreFromReaderState(
                NovelAiProviderState(openRouterModelIds = listOf("openrouter/existing")),
            )

            controller.refreshOpenRouterModels()
            delay(50)

            fetchCalls shouldBe 0
            coVerify(exactly = 0) { openRouterService.fetchModels(any(), any()) }
            controller.snapshot().isOpenRouterModelsLoading shouldBe false
            controller.snapshot().openRouterModelIds shouldBe listOf("openrouter/existing")
        } finally {
            scope.cancel()
        }
    }
}
