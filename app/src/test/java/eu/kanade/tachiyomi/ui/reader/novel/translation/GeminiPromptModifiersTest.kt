package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class GeminiPromptModifiersTest {

    @Test
    fun `default buildPromptText keeps russian comedy wording`() {
        val text = GeminiPromptModifiers.buildPromptText(
            enabledIds = listOf("comedy"),
            customModifier = "",
        )

        text shouldContain "Use modern Russian internet slang and memes where fitting"
        text shouldContain "Tsukkomi/boke dynamics should feel natural in Russian"
    }

    @Test
    fun `english family buildPromptText uses english comedy wording`() {
        val text = GeminiPromptModifiers.buildPromptText(
            enabledIds = listOf("comedy"),
            customModifier = "",
            family = NovelTranslationPromptFamily.ENGLISH,
        )

        text shouldContain "Use modern English internet slang and memes where fitting"
        text shouldContain "Tsukkomi/boke dynamics should feel natural in English"
    }

    @Test
    fun `custom modifier is appended unchanged`() {
        val customModifier = "Do not translate character names"

        GeminiPromptModifiers.buildPromptText(
            enabledIds = emptyList(),
            customModifier = customModifier,
        ) shouldBe """
            ### CUSTOM DIRECTIVE
            Do not translate character names
        """.trimIndent()
    }
}
