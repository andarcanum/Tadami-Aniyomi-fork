package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationBatchResponse
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test

class NovelTranslationControllerGoogleTest {

    private fun createController(
        response: GoogleTranslationBatchResponse,
        sourceBlocks: List<String>,
    ): NovelTranslationController {
        val settings = mockk<NovelReaderSettings>(relaxed = true)
        every { settings.googleTranslationEnabled } returns true
        every { settings.googleTranslationSourceLang } returns "en"
        every { settings.googleTranslationTargetLang } returns "ru"

        val host = mockk<NovelTranslationHost>(relaxed = true)
        every { host.translationScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { host.translationReaderSettings() } returns settings
        every { host.translationActiveChapterId() } returns 1L
        coEvery { host.translationSourceTextBlocks(1L) } returns sourceBlocks

        val service = mockk<GoogleTranslationService>()
        coEvery { service.translateBatch(any(), any(), any(), any()) } returns response

        return NovelTranslationController(
            host = host,
            application = mockk<Application>(relaxed = true),
            novelReaderPreferences = mockk<NovelReaderPreferences>(relaxed = true),
            googleTranslationService = service,
            translationQueueManager = mockk<TranslationQueueManager>(relaxed = true),
        )
    }

    @Test
    fun `rate limited partial result stays visible but is not a complete cache`() {
        val controller = createController(
            response = GoogleTranslationBatchResponse(
                translatedByIndex = mapOf(0 to "а", 1 to "б"),
                rateLimited = true,
            ),
            sourceBlocks = listOf("a", "b", "c"),
        )

        controller.startGoogleTranslation()

        val state = controller.snapshot()
        state.isGoogleTranslating shouldBe false
        state.isGoogleTranslationVisible shouldBe true
        // A 429 storm must surface the rate-limit state so the dialog offers Resume...
        state.googleRateLimited shouldBe true
        // ...and the partial result must not pin the chapter as "translated": auto-start and
        // Resume heal the gaps instead of the session cache treating half a chapter as done.
        state.hasGoogleTranslationCache shouldBe false
    }

    @Test
    fun `complete result is a full cache without the rate limit flag`() {
        val controller = createController(
            response = GoogleTranslationBatchResponse(
                translatedByIndex = mapOf(0 to "а", 1 to "б", 2 to "в"),
                rateLimited = false,
            ),
            sourceBlocks = listOf("a", "b", "c"),
        )

        controller.startGoogleTranslation()

        val state = controller.snapshot()
        state.hasGoogleTranslationCache shouldBe true
        state.googleRateLimited shouldBe false
        state.isGoogleTranslationVisible shouldBe true
    }
}
