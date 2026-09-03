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

    @Test
    fun `cache written by an older extractor version is invalid`() {
        // The canonical block extraction changed the index space (select -> collect): overlaying a
        // legacy map onto the new blocks would shift translations onto wrong paragraphs, so legacy
        // entries (extractorVersion defaults to 0) must never match and get retranslated instead.
        NovelReaderTranslationCacheResolver.matches(
            cached = cache(extractorVersion = 0),
            requirements = requirements(),
        ) shouldBe false
    }

    @Test
    fun `cache produced under different prompt modifiers is invalid`() {
        NovelReaderTranslationCacheResolver.matches(
            cached = cache(promptModifiersFingerprint = "xianxia\u0000custom-directive"),
            requirements = requirements(),
        ) shouldBe false
    }

    @Test
    fun `cache produced under different replace rules is invalid`() {
        NovelReaderTranslationCacheResolver.matches(
            cached = cache(replaceRulesFingerprint = "rule-1:true:false:false:true:0:a->b"),
            requirements = requirements(),
        ) shouldBe false
    }

    @Test
    fun `adult prompt support matches the providers carrying an adult prompt`() {
        NovelTranslationProvider.GEMINI.supportsAdultPromptMode() shouldBe true
        NovelTranslationProvider.GEMINI_PRIVATE.supportsAdultPromptMode() shouldBe true
        NovelTranslationProvider.DEEPSEEK.supportsAdultPromptMode() shouldBe true
        NovelTranslationProvider.MISTRAL.supportsAdultPromptMode() shouldBe true
        // These build prompts inline with CLASSIC texts only; ADULT_18 downgrades there.
        NovelTranslationProvider.OPENROUTER.supportsAdultPromptMode() shouldBe false
        NovelTranslationProvider.NVIDIA.supportsAdultPromptMode() shouldBe false
        NovelTranslationProvider.OLLAMA_CLOUD.supportsAdultPromptMode() shouldBe false
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
            extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
        )
    }

    private fun cache(
        provider: NovelTranslationProvider = NovelTranslationProvider.GEMINI,
        model: String = "gemini-3.1-flash-lite-preview",
        sourceLang: String = "English",
        targetLang: String = "Russian",
        extractorVersion: Int = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
        promptModifiersFingerprint: String = "",
        replaceRulesFingerprint: String = "",
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
            extractorVersion = extractorVersion,
            promptModifiersFingerprint = promptModifiersFingerprint,
            replaceRulesFingerprint = replaceRulesFingerprint,
        )
    }
}
