package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelChapterActionStateResolverTest {
    @Test
    fun `gemini mode off hides the row`() {
        val state = NovelChapterActionStateResolver.resolve(
            geminiEnabled = false,
            hasTranslationCache = true,
            isTranslating = true,
            isTranslatedDownloaded = true,
            isTranslatedDownloading = true,
        )

        state.showGeminiRow shouldBe false
        state.translateState shouldBe NovelChapterActionIconState.Hidden
        state.downloadTranslatedState shouldBe NovelChapterActionIconState.Hidden
    }

    @Test
    fun `translation cache marks translate active`() {
        val state = NovelChapterActionStateResolver.resolve(
            geminiEnabled = true,
            hasTranslationCache = true,
            isTranslating = false,
            isTranslatedDownloaded = false,
            isTranslatedDownloading = false,
        )

        state.showGeminiRow shouldBe true
        state.translateState shouldBe NovelChapterActionIconState.Active
        state.downloadTranslatedState shouldBe NovelChapterActionIconState.Neutral
    }

    @Test
    fun `missing translation cache keeps translate neutral`() {
        val state = NovelChapterActionStateResolver.resolve(
            geminiEnabled = true,
            hasTranslationCache = false,
            isTranslating = false,
            isTranslatedDownloaded = false,
            isTranslatedDownloading = false,
        )

        state.translateState shouldBe NovelChapterActionIconState.Neutral
    }

    @Test
    fun `translated file marks download active`() {
        val state = NovelChapterActionStateResolver.resolve(
            geminiEnabled = true,
            hasTranslationCache = true,
            isTranslating = false,
            isTranslatedDownloaded = true,
            isTranslatedDownloading = false,
            translatedDownloadFormat = NovelTranslatedDownloadFormat.DOCX,
        )

        state.downloadTranslatedState shouldBe NovelChapterActionIconState.Active
        state.translatedDownloadFormat shouldBe NovelTranslatedDownloadFormat.DOCX
    }

    @Test
    fun `queued translated export marks download in progress`() {
        val state = NovelChapterActionStateResolver.resolve(
            geminiEnabled = true,
            hasTranslationCache = true,
            isTranslating = false,
            isTranslatedDownloaded = false,
            isTranslatedDownloading = true,
        )

        state.downloadTranslatedState shouldBe NovelChapterActionIconState.InProgress
    }
}
