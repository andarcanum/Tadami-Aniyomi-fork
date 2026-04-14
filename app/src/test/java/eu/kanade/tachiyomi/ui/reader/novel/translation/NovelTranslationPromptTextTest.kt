package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelTranslationPromptTextTest {

    @Test
    fun `buildNovelTranslationUserPrompt switches copy by family`() {
        val text = buildNovelTranslationUserPrompt(
            sourceLang = "English",
            targetLang = "Japanese",
            taggedInput = "<s i='0'>Hello</s>",
            family = NovelTranslationPromptFamily.ENGLISH,
        )

        text shouldContain "Make the reader believe this was written by a native English author"
        text shouldContain "Use popular genre terminology and natural English wording where appropriate"
        text shouldContain "<s i='0'>Hello</s>"
    }
}
