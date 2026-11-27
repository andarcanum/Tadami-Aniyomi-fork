package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationBatchResponse
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.TranslationPhase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NovelTranslationControllerGoogleTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

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

    @Test
    fun `onProgress and onLog callbacks do not call translationUpdateContent`() {
        val settings = mockk<NovelReaderSettings>(relaxed = true)
        every { settings.googleTranslationEnabled } returns true
        every { settings.googleTranslationSourceLang } returns "en"
        every { settings.googleTranslationTargetLang } returns "ru"

        val host = mockk<NovelTranslationHost>(relaxed = true)
        every { host.translationScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { host.translationReaderSettings() } returns settings
        every { host.translationActiveChapterId() } returns 1L
        coEvery { host.translationSourceTextBlocks(1L) } returns listOf("a", "b")

        val service = mockk<GoogleTranslationService>()
        coEvery { service.translateBatch(any(), any(), any(), any()) } answers {
            val onLog = arg<((String) -> Unit)?>(2)
            val onProgress = arg<((TranslationPhase, Int) -> Unit)?>(3)
            onProgress?.invoke(TranslationPhase.TRANSLATING, 50)
            onLog?.invoke("Simple chunk 1/2: paragraphs=1, chars=1")
            onProgress?.invoke(TranslationPhase.TRANSLATING, 100)
            onLog?.invoke("Simple chunk 2/2: paragraphs=1, chars=1")
            GoogleTranslationBatchResponse(translatedByIndex = mapOf(0 to "а", 1 to "б"))
        }

        val controller = NovelTranslationController(
            host = host,
            application = mockk<Application>(relaxed = true),
            novelReaderPreferences = mockk<NovelReaderPreferences>(relaxed = true),
            googleTranslationService = service,
            translationQueueManager = mockk<TranslationQueueManager>(relaxed = true),
        )

        controller.startGoogleTranslation()

        // Only the pre-start reset and final completion should rebuild reader HTML; IO callbacks
        // must not call translationUpdateContent on every tick.
        verify(exactly = 2) { host.translationUpdateContent(settings) }
    }

    @Test
    fun `stale google completion when active chapter changed does not overwrite holder or visibility`() {
        val settings = mockk<NovelReaderSettings>(relaxed = true)
        every { settings.googleTranslationEnabled } returns true
        every { settings.googleTranslationSourceLang } returns "en"
        every { settings.googleTranslationTargetLang } returns "ru"

        var activeChapterId = 1L
        val host = mockk<NovelTranslationHost>(relaxed = true)
        every { host.translationScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { host.translationReaderSettings() } returns settings
        every { host.translationActiveChapterId() } answers { activeChapterId }
        coEvery { host.translationSourceTextBlocks(1L) } returns listOf("a", "b")

        val gate = CompletableDeferred<GoogleTranslationBatchResponse>()
        val service = mockk<GoogleTranslationService>()
        coEvery { service.translateBatch(any(), any(), any(), any()) } coAnswers { gate.await() }

        val controller = NovelTranslationController(
            host = host,
            application = mockk<Application>(relaxed = true),
            novelReaderPreferences = mockk<NovelReaderPreferences>(relaxed = true),
            googleTranslationService = service,
            translationQueueManager = mockk<TranslationQueueManager>(relaxed = true),
        )

        controller.startGoogleTranslation()
        controller.snapshot().isGoogleTranslating shouldBe true

        // User scrolled to chapter 2 in Book Mode while chapter 1 was translating.
        activeChapterId = 2L
        gate.complete(
            GoogleTranslationBatchResponse(
                translatedByIndex = mapOf(0 to "а", 1 to "б"),
                rateLimited = false,
            ),
        )

        verify(exactly = 0) { host.translationHolderPut("google", any()) }
        verify(exactly = 0) { host.translationRefreshBookModeSection(any()) }
        val state = controller.snapshot()
        state.isGoogleTranslating shouldBe false
        state.isGoogleTranslationVisible shouldBe false
        state.hasGoogleTranslationCache shouldBe false
    }
}
