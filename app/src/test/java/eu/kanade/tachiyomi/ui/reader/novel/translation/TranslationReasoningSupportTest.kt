package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationReasoningSupportTest {

    @Test
    fun `bundled gemini models keep their thinking level options`() {
        resolveTranslationReasoningOptions(
            NovelTranslationProvider.GEMINI,
            "gemini-3-flash-preview",
        ) shouldBe listOf("minimal", "low", "medium", "high")
        resolveTranslationReasoningOptions(
            NovelTranslationProvider.GEMINI,
            "gemini-3-pro-preview",
        ) shouldBe listOf("low", "high")
        resolveTranslationReasoningOptions(
            NovelTranslationProvider.GEMINI,
            "gemini-3.1-flash-lite-preview",
        ) shouldBe listOf("minimal", "low", "medium", "high")
    }

    @Test
    fun `unknown gemini model has no thinking level options`() {
        resolveTranslationReasoningOptions(
            NovelTranslationProvider.GEMINI,
            "gemini-4.5-turbo-preview",
        ).shouldBeEmpty()
    }

    @Test
    fun `normalize keeps a valid effort and falls back to the first supported option`() {
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.GEMINI,
            "gemini-3.1-flash-lite-preview",
            "medium",
        ) shouldBe "medium"
        // "minimal" is not a valid level for the pro model; first supported option wins.
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.GEMINI,
            "gemini-3-pro-preview",
            "minimal",
        ) shouldBe "low"
    }

    @Test
    fun `normalize returns null for unknown gemini model so thinking config is skipped`() {
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.GEMINI,
            "gemini-4.5-turbo-preview",
            "minimal",
        ).shouldBeNull()
    }

    @Test
    fun `documented flash models expose all four levels on both Gemini providers`() {
        listOf(NovelTranslationProvider.GEMINI, NovelTranslationProvider.GEMINI_PRIVATE).forEach { provider ->
            listOf(
                "gemini-3.5-flash-lite",
                "gemini-3.5-flash-lite-preview",
                "gemini-3.1-flash-lite",
                "gemini-3-flash-preview",
            ).forEach { model ->
                resolveTranslationReasoningOptions(provider, model) shouldBe listOf("minimal", "low", "medium", "high")
            }
        }
    }

    @Test
    fun `Gemma 4 exposes only the documented minimal high thinking toggle`() {
        listOf("gemma-4-31b-it", "gemma-4-26b-a4b-it").forEach { model ->
            resolveTranslationReasoningOptions(
                NovelTranslationProvider.GEMINI,
                model,
            ) shouldBe listOf("minimal", "high")
            normalizeTranslationReasoningEffort(
                NovelTranslationProvider.GEMINI,
                model,
                "high",
            ) shouldBe "high"
        }
    }

    @Test
    fun `specialized or merely similar Gemini ids do not inherit a thinking contract`() {
        listOf(
            "gemini-3-flash-image-preview",
            "gemini-3-flash-native-audio",
            "gemini-3-proposal",
            "gemma-4-unverified-it",
        ).forEach { model ->
            resolveTranslationReasoningOptions(NovelTranslationProvider.GEMINI, model).shouldBeEmpty()
            normalizeTranslationReasoningEffort(NovelTranslationProvider.GEMINI, model, "high").shouldBeNull()
        }
    }

    @Test
    fun `legacy providers retain normalization and fallback behavior`() {
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.DEEPSEEK,
            "deepseek-reasoner",
            " MAX ",
        ) shouldBe "max"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.DEEPSEEK,
            "deepseek-reasoner",
            "unknown",
        ) shouldBe "none"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.MISTRAL,
            "mistral-small-latest",
            "low",
        ) shouldBe "none"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.OPENROUTER,
            "google/gemini-3-pro-preview",
            "unsupported",
        ) shouldBe "minimal"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.OLLAMA_CLOUD,
            "qwen3:latest",
            " Medium ",
        ) shouldBe "medium"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.NVIDIA,
            "any-model",
            "high",
        ).shouldBeNull()
    }

    @Test
    fun `explicit Gemini 3 Flash legacy alias retains its preview reasoning contract`() {
        "gemini-3-flash".normalizeGeminiModelId() shouldBe "gemini-3-flash-preview"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.GEMINI,
            "gemini-3-flash".normalizeGeminiModelId(),
            "minimal",
        ) shouldBe "minimal"
    }

    @Test
    fun `Gemini 2_5 Flash selection is preserved rather than silently changed into a level model`() {
        val model = "gemini-2.5-flash".normalizeGeminiModelId()
        model shouldBe "gemini-2.5-flash"
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.GEMINI,
            model,
            "minimal",
        ).shouldBeNull()
    }
}
