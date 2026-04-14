package eu.kanade.tachiyomi.ui.reader.novel.translation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelTranslationPromptFamilyTest {

    @Test
    fun `russian aliases resolve to russian family`() {
        resolveNovelTranslationPromptFamily("Russian") shouldBe NovelTranslationPromptFamily.RUSSIAN
        resolveNovelTranslationPromptFamily("ru") shouldBe NovelTranslationPromptFamily.RUSSIAN
        resolveNovelTranslationPromptFamily("ru-RU") shouldBe NovelTranslationPromptFamily.RUSSIAN
    }

    @Test
    fun `non russian targets resolve to english family`() {
        resolveNovelTranslationPromptFamily("English") shouldBe NovelTranslationPromptFamily.ENGLISH
        resolveNovelTranslationPromptFamily("zh") shouldBe NovelTranslationPromptFamily.ENGLISH
        resolveNovelTranslationPromptFamily("Chinese") shouldBe NovelTranslationPromptFamily.ENGLISH
    }

    @Test
    fun `blank target language resolves to english family`() {
        resolveNovelTranslationPromptFamily("") shouldBe NovelTranslationPromptFamily.ENGLISH
        resolveNovelTranslationPromptFamily(null) shouldBe NovelTranslationPromptFamily.ENGLISH
    }
}
