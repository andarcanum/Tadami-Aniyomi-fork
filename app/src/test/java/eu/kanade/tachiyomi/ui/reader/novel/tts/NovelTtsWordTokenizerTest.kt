package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsWordTokenizerTest {

    @Test
    fun `tokenize extracts stable word ranges and skips punctuation only spans`() {
        val tokens = NovelTtsWordTokenizer.tokenize("Hello, brave new world!")

        tokens.shouldHaveSize(4)
        tokens[0] shouldBe NovelTtsWordRange(wordIndex = 0, text = "Hello", startChar = 0, endChar = 5)
        tokens[1] shouldBe NovelTtsWordRange(wordIndex = 1, text = "brave", startChar = 7, endChar = 12)
        tokens[2] shouldBe NovelTtsWordRange(wordIndex = 2, text = "new", startChar = 13, endChar = 16)
        tokens[3] shouldBe NovelTtsWordRange(wordIndex = 3, text = "world", startChar = 17, endChar = 22)
    }

    @Test
    fun `tokenize preserves apostrophes and hyphenated compounds inside words`() {
        val tokens = NovelTtsWordTokenizer.tokenize("Don't stop the ever-shifting story.")

        tokens.map { it.text } shouldBe listOf("Don't", "stop", "the", "ever-shifting", "story")
    }
}
