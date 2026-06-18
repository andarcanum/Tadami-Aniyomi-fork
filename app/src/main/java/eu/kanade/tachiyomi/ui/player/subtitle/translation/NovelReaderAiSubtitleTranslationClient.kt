package eu.kanade.tachiyomi.ui.player.subtitle.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationParams
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService

class NovelReaderAiSubtitleTranslationClient(
    private val preferences: NovelReaderPreferences,
    private val geminiTranslationService: GeminiTranslationService,
    private val openRouterTranslationService: OpenRouterTranslationService,
    private val deepSeekTranslationService: DeepSeekTranslationService,
    private val mistralTranslationService: MistralTranslationService,
    private val nvidiaTranslationService: NvidiaTranslationService,
    private val ollamaCloudTranslationService: OllamaCloudTranslationService,
) : AiSubtitleTranslationClient {
    override val fingerprint: String
        get() = "novel-reader-ai-${preferences.translationProvider().get().name.lowercase()}"

    override suspend fun translateIndexedSegments(
        prompt: String,
        segments: List<IndexedSubtitleSegment>,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        val provider = preferences.translationProvider().get()
        val sourceTexts = segments.map { it.text }
        val translated = when (provider) {
            NovelTranslationProvider.GEMINI,
            NovelTranslationProvider.GEMINI_PRIVATE,
            -> geminiTranslationService.translateBatch(
                segments = sourceTexts,
                params = GeminiTranslationParams(
                    apiKey = preferences.geminiApiKey().get(),
                    model = preferences.geminiModel().get(),
                    sourceLang = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() },
                    targetLang = targetLanguage.ifBlank { preferences.geminiTargetLang().get() },
                    reasoningEffort = preferences.geminiReasoningEffort().get(),
                    budgetTokens = preferences.geminiBudgetTokens().get(),
                    temperature = preferences.geminiTemperature().get(),
                    topP = preferences.geminiTopP().get(),
                    topK = preferences.geminiTopK().get(),
                    promptMode = preferences.geminiPromptMode().get(),
                    promptModifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get()),
                    provider = provider,
                    privateUnlocked = preferences.geminiPrivateUnlocked().get(),
                    privatePythonLikeMode = preferences.geminiPrivatePythonLikeMode().get(),
                ),
            )

            NovelTranslationProvider.OPENROUTER -> openRouterTranslationService.translateBatch(
                segments = sourceTexts,
                params = OpenRouterTranslationParams(
                    baseUrl = preferences.openRouterBaseUrl().get(),
                    apiKey = preferences.openRouterApiKey().get(),
                    model = preferences.openRouterModel().get(),
                    sourceLang = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() },
                    targetLang = targetLanguage.ifBlank { preferences.geminiTargetLang().get() },
                    promptMode = preferences.geminiPromptMode().get(),
                    promptModifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get()),
                    temperature = preferences.geminiTemperature().get(),
                    topP = preferences.geminiTopP().get(),
                    reasoningEffort = preferences.geminiReasoningEffort().get(),
                ),
            )

            NovelTranslationProvider.DEEPSEEK -> deepSeekTranslationService.translateBatch(
                segments = sourceTexts,
                params = DeepSeekTranslationParams(
                    baseUrl = preferences.deepSeekBaseUrl().get(),
                    apiKey = preferences.deepSeekApiKey().get(),
                    model = preferences.deepSeekModel().get(),
                    sourceLang = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() },
                    targetLang = targetLanguage.ifBlank { preferences.geminiTargetLang().get() },
                    promptMode = preferences.geminiPromptMode().get(),
                    promptModifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get()),
                    temperature = preferences.geminiTemperature().get(),
                    topP = preferences.geminiTopP().get(),
                    reasoningEffort = preferences.geminiReasoningEffort().get(),
                ),
            )

            NovelTranslationProvider.MISTRAL -> mistralTranslationService.translateBatch(
                segments = sourceTexts,
                params = MistralTranslationParams(
                    baseUrl = preferences.mistralBaseUrl().get(),
                    apiKey = preferences.mistralApiKey().get(),
                    model = preferences.mistralModel().get(),
                    sourceLang = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() },
                    targetLang = targetLanguage.ifBlank { preferences.geminiTargetLang().get() },
                    promptMode = preferences.geminiPromptMode().get(),
                    promptModifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get()),
                    temperature = preferences.geminiTemperature().get(),
                    topP = preferences.geminiTopP().get(),
                    reasoningEffort = preferences.geminiReasoningEffort().get(),
                ),
            )

            NovelTranslationProvider.NVIDIA -> nvidiaTranslationService.translateBatch(
                segments = sourceTexts,
                params = NvidiaTranslationParams(
                    baseUrl = preferences.nvidiaBaseUrl().get(),
                    apiKey = preferences.nvidiaApiKey().get(),
                    model = preferences.nvidiaModel().get(),
                    sourceLang = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() },
                    targetLang = targetLanguage.ifBlank { preferences.geminiTargetLang().get() },
                    promptMode = preferences.geminiPromptMode().get(),
                    promptModifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get()),
                    temperature = preferences.geminiTemperature().get(),
                    topP = preferences.geminiTopP().get(),
                ),
            )

            NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudTranslationService.translateBatch(
                segments = sourceTexts,
                params = OllamaCloudTranslationParams(
                    baseUrl = preferences.ollamaCloudBaseUrl().get(),
                    apiKey = preferences.ollamaCloudApiKey().get(),
                    model = preferences.ollamaCloudModel().get(),
                    sourceLang = sourceLanguage.ifBlank { preferences.geminiSourceLang().get() },
                    targetLang = targetLanguage.ifBlank { preferences.geminiTargetLang().get() },
                    promptMode = preferences.geminiPromptMode().get(),
                    promptModifiers = subtitlePromptModifiers(preferences.geminiPromptModifiers().get()),
                    temperature = preferences.geminiTemperature().get(),
                    topP = preferences.geminiTopP().get(),
                    reasoningEffort = preferences.geminiReasoningEffort().get(),
                ),
            )
        } ?: throw IllegalStateException("AI subtitle translation failed or provider is not configured")

        return translated.mapIndexedNotNull { position, value ->
            val originalIndex = segments.getOrNull(position)?.index ?: return@mapIndexedNotNull null
            val safe = value?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            "<s i=\"$originalIndex\">${escapeXmlText(safe)}</s>"
        }.joinToString("\n")
    }

    private fun subtitlePromptModifiers(userModifiers: String): String {
        val subtitleModifiers = """
            Subtitle mode: translate each segment as a concise subtitle cue.
            Do not add explanations, notes, speaker labels, markdown, or extra text.
            Do not merge, split, reorder, omit, or renumber segments.
            Preserve placeholders, line breaks when useful, and protected styling markers.
        """.trimIndent()
        return listOf(userModifiers.trim(), subtitleModifiers)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun escapeXmlText(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
