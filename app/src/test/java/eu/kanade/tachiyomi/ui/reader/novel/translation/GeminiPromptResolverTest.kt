package eu.kanade.tachiyomi.ui.reader.novel.translation

import android.app.Application
import android.content.res.AssetManager
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class GeminiPromptResolverTest {

    @Test
    fun `classic prompt keeps russian copy for russian family`() {
        val resolver = createResolver()

        resolver.resolveSystemPrompt(
            mode = GeminiPromptMode.CLASSIC,
            family = NovelTranslationPromptFamily.RUSSIAN,
        ) shouldBe GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT
    }

    @Test
    fun `classic prompt uses english copy for english family`() {
        val resolver = createResolver()

        resolver.resolveSystemPrompt(
            mode = GeminiPromptMode.CLASSIC,
            family = NovelTranslationPromptFamily.ENGLISH,
        ) shouldBe GeminiPromptResolver.CLASSIC_SYSTEM_PROMPT_EN
    }

    @Test
    fun `adult prompt ignores family selection`() {
        val resolver = createResolver(adultPromptText = "adult prompt")

        resolver.resolveSystemPrompt(
            mode = GeminiPromptMode.ADULT_18,
            family = NovelTranslationPromptFamily.ENGLISH,
        ) shouldBe "adult prompt"
    }

    private fun createResolver(adultPromptText: String? = null): GeminiPromptResolver {
        val application = mockk<Application>(relaxed = true)
        if (adultPromptText != null) {
            val assets = mockk<AssetManager>()
            every { application.assets } returns assets
            every { assets.open(any()) } returns ByteArrayInputStream(adultPromptText.toByteArray())
        }
        return GeminiPromptResolver(application)
    }
}
