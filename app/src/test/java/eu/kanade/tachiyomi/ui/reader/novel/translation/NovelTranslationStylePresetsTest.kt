package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelTranslationStylePresetsTest {

    @Test
    fun `prompt directive switches with family`() {
        NovelTranslationStylePresets.promptDirective(
            id = NovelTranslationStylePreset.PROFESSIONAL,
            family = NovelTranslationPromptFamily.RUSSIAN,
        ) shouldBe
            "STYLE PRESET: PROFESSIONAL.\n" +
            "Use neutral professional literary Russian.\n" +
            "Avoid slang, vulgarity, and over-stylization unless explicitly present in source."

        NovelTranslationStylePresets.promptDirective(
            id = NovelTranslationStylePreset.PROFESSIONAL,
            family = NovelTranslationPromptFamily.ENGLISH,
        ) shouldBe
            "STYLE PRESET: PROFESSIONAL.\n" +
            "Use neutral professional literary English.\n" +
            "Avoid slang, vulgarity, and over-stylization unless explicitly present in source."
    }
}
