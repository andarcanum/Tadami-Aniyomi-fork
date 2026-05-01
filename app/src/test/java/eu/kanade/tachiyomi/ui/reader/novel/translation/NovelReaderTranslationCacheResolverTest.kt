package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderTranslationCacheResolverTest {
    @Test
    fun `matching cache is valid`() {
        val requirements = requirements()

        NovelReaderTranslationCacheResolver.matches(
            cached = cache(),
            requirements = requirements,
        ) shouldBe true
    }

    @Test
    fun `cache becomes invalid after switching provider and model`() {
        NovelReaderTranslationCacheResolver.matches(
            cached = cache(
                provider = NovelTranslationProvider.MISTRAL,
                model = "mistral-small-latest",
            ),
            requirements = requirements(
                translationProvider = NovelTranslationProvider.NVIDIA,
                modelId = "nvidia/llama-3.1-nemotron-ultra-253b-v1",
            ),
        ) shouldBe false
    }

    @Test
    fun `mismatched language is invalid`() {
        NovelReaderTranslationCacheResolver.matches(
            cached = cache(sourceLang = "Japanese"),
            requirements = requirements(),
        ) shouldBe false
    }

    @Test
    fun `missing cache is invalid`() {
        NovelReaderTranslationCacheResolver.matches(
            cached = null,
            requirements = requirements(),
        ) shouldBe false
    }

    private fun requirements(
        translationProvider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
        modelId: String = "gemini-3.1-flash-lite-preview",
    ): NovelReaderTranslationCacheRequirements {
        return NovelReaderTranslationCacheRequirements(
            geminiEnabled = true,
            geminiDisableCache = false,
            translationProvider = translationProvider,
            modelId = modelId,
            sourceLang = "English",
            targetLang = "Russian",
            promptMode = GeminiPromptMode.ADULT_18,
            stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
        )
    }

    private fun cache(
        provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
        model: String = "gemini-3.1-flash-lite-preview",
        sourceLang: String = "English",
        targetLang: String = "Russian",
    ): GeminiTranslationCacheEntry {
        return GeminiTranslationCacheEntry(
            chapterId = 1L,
            translatedByIndex = mapOf(0 to "hello"),
            provider = provider,
            model = model,
            sourceLang = sourceLang,
            targetLang = targetLang,
            promptMode = GeminiPromptMode.ADULT_18,
            stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
        )
    }
}
