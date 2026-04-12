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
    fun `mismatched model is invalid`() {
        NovelReaderTranslationCacheResolver.matches(
            cached = cache(model = "different"),
            requirements = requirements(),
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

    private fun requirements(): NovelReaderTranslationCacheRequirements {
        return NovelReaderTranslationCacheRequirements(
            geminiEnabled = true,
            geminiDisableCache = false,
            translationProvider = NovelTranslationProvider.GEMINI,
            modelId = "gemini-3.1-flash-lite-preview",
            sourceLang = "English",
            targetLang = "Russian",
            promptMode = GeminiPromptMode.ADULT_18,
            stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
        )
    }

    private fun cache(
        model: String = "gemini-3.1-flash-lite-preview",
        sourceLang: String = "English",
        targetLang: String = "Russian",
    ): GeminiTranslationCacheEntry {
        return GeminiTranslationCacheEntry(
            chapterId = 1L,
            translatedByIndex = mapOf(0 to "hello"),
            model = model,
            sourceLang = sourceLang,
            targetLang = targetLang,
            promptMode = GeminiPromptMode.ADULT_18,
            stylePreset = NovelTranslationStylePreset.PROFESSIONAL,
        )
    }
}
