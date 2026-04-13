package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test

class DeepSeekPromptResolverTest {

    @Test
    fun `classic prompt uses family specific copy`() {
        val resolver = DeepSeekPromptResolver(mockk(relaxed = true))

        resolver.resolveSystemPrompt(
            mode = GeminiPromptMode.CLASSIC,
            family = NovelTranslationPromptFamily.RUSSIAN,
        ) shouldBe GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
        resolver.resolveSystemPrompt(
            mode = GeminiPromptMode.CLASSIC,
            family = NovelTranslationPromptFamily.ENGLISH,
        ) shouldBe GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT_EN
    }
}
