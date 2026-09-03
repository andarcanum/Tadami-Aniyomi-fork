package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test

class NovelTranslationControllerAutoStartTest {

    @Test
    fun `auto-start works over a book even though the entry-chapter content model is empty`() {
        val settings = mockk<NovelReaderSettings>(relaxed = true)
        every { settings.geminiEnabled } returns true
        every { settings.geminiAutoTranslateEnglishSource } returns true
        every { settings.geminiSourceLang } returns "English"

        val queueManager = mockk<TranslationQueueManager>(relaxed = true)
        val host = mockk<NovelTranslationHost>(relaxed = true)
        every { host.translationScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { host.translationReaderSettings() } returns settings
        every { host.translationHasConfiguredProvider(settings) } returns true
        every { host.translationActiveChapterId() } returns 7L
        every { host.translationCurrentChapter() } returns null
        // Book mode: the parsed content model belongs to the entry chapter and stays empty.
        every { host.translationIsBookRuntimeActive() } returns true
        every { host.translationCurrentParsedTextBlocks() } returns emptyList()
        every { host.translationHolderIsEmpty("gemini") } returns true

        val controller = NovelTranslationController(
            host = host,
            application = mockk(relaxed = true),
            novelReaderPreferences = mockk<NovelReaderPreferences>(relaxed = true),
            googleTranslationService = mockk<GoogleTranslationService>(relaxed = true),
            translationQueueManager = queueManager,
        )

        controller.setPendingAutoStart(true)
        controller.maybeAutoStartGeminiTranslation(settings)

        // Pre-fix the empty-content-model gate returned early over a book, so auto-start never
        // queued the chapter even though manual start has an explicit book bypass for this case.
        coVerify(timeout = 5_000) { queueManager.addToQueue(listOf(7L), any()) }
    }
}
