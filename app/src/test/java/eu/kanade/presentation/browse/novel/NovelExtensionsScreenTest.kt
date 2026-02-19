package eu.kanade.presentation.browse.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelExtensionsScreenTest {

    @Test
    fun `shouldLoadNovelPluginIcon returns false for null`() {
        shouldLoadNovelPluginIcon(null) shouldBe false
    }

    @Test
    fun `shouldLoadNovelPluginIcon returns false for blank`() {
        shouldLoadNovelPluginIcon("   ") shouldBe false
    }

    @Test
    fun `shouldLoadNovelPluginIcon returns true for non blank value`() {
        shouldLoadNovelPluginIcon("https://example.org/icon.png") shouldBe true
    }
}
