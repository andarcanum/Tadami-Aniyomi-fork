package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelScreenModelGeminiStateTest {

    @Test
    fun `gemini off hides chapter action row`() {
        val chapter = NovelChapter.create().copy(id = 1L, novelId = 10L)

        val states = buildNovelChapterActionUiStates(
            geminiEnabled = false,
            chapters = listOf(chapter),
            translatedDownloadFormat = NovelTranslatedDownloadFormat.TXT,
            hasTranslationCache = { true },
            isTranslatedDownloaded = { true },
            isTranslatedDownloading = { true },
        )

        states[chapter.id]?.showGeminiRow shouldBe false
        states[chapter.id]?.translateState shouldBe NovelChapterActionIconState.Hidden
        states[chapter.id]?.downloadTranslatedState shouldBe NovelChapterActionIconState.Hidden
    }

    @Test
    fun `gemini on shows neutral actions without cache or export`() {
        val chapter = NovelChapter.create().copy(id = 2L, novelId = 10L)

        val states = buildNovelChapterActionUiStates(
            geminiEnabled = true,
            chapters = listOf(chapter),
            translatedDownloadFormat = NovelTranslatedDownloadFormat.TXT,
            hasTranslationCache = { false },
            isTranslatedDownloaded = { false },
            isTranslatedDownloading = { false },
        )

        states[chapter.id]?.showGeminiRow shouldBe true
        states[chapter.id]?.translateState shouldBe NovelChapterActionIconState.Neutral
        states[chapter.id]?.downloadTranslatedState shouldBe NovelChapterActionIconState.Neutral
        states[chapter.id]?.translatedDownloadFormat shouldBe NovelTranslatedDownloadFormat.TXT
    }

    @Test
    fun `translated cache marks translate action active`() {
        val chapter = NovelChapter.create().copy(id = 3L, novelId = 10L)

        val states = buildNovelChapterActionUiStates(
            geminiEnabled = true,
            chapters = listOf(chapter),
            translatedDownloadFormat = NovelTranslatedDownloadFormat.DOCX,
            hasTranslationCache = { true },
            isTranslatedDownloaded = { false },
            isTranslatedDownloading = { false },
        )

        states[chapter.id]?.translateState shouldBe NovelChapterActionIconState.Active
        states[chapter.id]?.translatedDownloadFormat shouldBe NovelTranslatedDownloadFormat.DOCX
    }

    @Test
    fun `queued translated export marks download action in progress`() {
        val chapter = NovelChapter.create().copy(id = 4L, novelId = 10L)

        val states = buildNovelChapterActionUiStates(
            geminiEnabled = true,
            chapters = listOf(chapter),
            translatedDownloadFormat = NovelTranslatedDownloadFormat.TXT,
            hasTranslationCache = { true },
            isTranslatedDownloaded = { false },
            isTranslatedDownloading = { true },
        )

        states[chapter.id]?.downloadTranslatedState shouldBe NovelChapterActionIconState.InProgress
    }
}
