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
    fun `legacy aliases normalize to canonical ids before reasoning resolution`() {
        normalizeTranslationReasoningEffort(
            NovelTranslationProvider.GEMINI,
            "gemini-2.5-flash".normalizeGeminiModelId(),
            "minimal",
        ) shouldBe "minimal"
    }
}
