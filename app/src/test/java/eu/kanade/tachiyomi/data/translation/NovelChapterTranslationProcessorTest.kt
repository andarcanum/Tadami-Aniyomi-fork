package eu.kanade.tachiyomi.data.translation

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.MistralTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NvidiaTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OllamaCloudTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NovelChapterTranslationProcessorTest {

    private val application = mockk<Application>(relaxed = true)
    private val geminiService = mockk<GeminiTranslationService>()
    private val openRouterService = mockk<OpenRouterTranslationService>()
    private val deepSeekService = mockk<DeepSeekTranslationService>()
    private val mistralService = mockk<MistralTranslationService>()
    private val nvidiaService = mockk<NvidiaTranslationService>()
    private val ollamaService = mockk<OllamaCloudTranslationService>()

    private val settings = mockk<NovelReaderSettings>(relaxed = true)

    private lateinit var processor: NovelChapterTranslationProcessor

    @BeforeEach
    fun setup() {
        processor = NovelChapterTranslationProcessor(
            application = application,
            geminiTranslationService = geminiService,
            openRouterTranslationService = openRouterService,
            deepSeekTranslationService = deepSeekService,
            mistralTranslationService = mistralService,
            nvidiaTranslationService = nvidiaService,
            ollamaCloudTranslationService = ollamaService,
        )

        every { settings.geminiEnabled } returns true
        every { settings.geminiTargetLang } returns "Russian"
        every { settings.geminiSourceLang } returns "English"
        every { settings.geminiPromptMode } returns GeminiPromptMode.ADULT_18
        every { settings.geminiStylePreset } returns NovelTranslationStylePreset.PROFESSIONAL
        every { settings.geminiBatchSize } returns 2
        every { settings.geminiConcurrency } returns 1
        every { settings.geminiRelaxedMode } returns true
        every { settings.geminiApiKey } returns "fake-key"
        every { settings.geminiModel } returns "fake-model"
        every { settings.geminiTemperature } returns 0.7f
        every { settings.geminiTopP } returns 0.95f
        every { settings.geminiTopK } returns 40
        every { settings.geminiReasoningEffort } returns "minimal"
        every { settings.geminiPromptModifiers } returns ""
        every { settings.geminiCustomPromptModifier } returns ""
        every { settings.geminiEnabledPromptModifiers } returns emptyList()
        NovelChapterTranslationProcessor.clearCache()
        every { settings.geminiDisableCache } returns false
    }

    @Test
    fun `segment cache is isolated per prompt shaping settings`() = runTest {
        every { settings.translationProvider } returns NovelTranslationProvider.GEMINI
        coEvery {
            geminiService.translateBatch(any(), any(), any())
        } returns listOf("Привет", "Мир")

        processor.translateSegments(listOf("Hello", "World"), settings)[0] shouldBe "Привет"

        // Same text and target language but a different style preset: the old (text,targetLang)-only
        // cache key served the previous configuration's translation with zero API calls.
        every { settings.geminiStylePreset } returns
            NovelTranslationStylePreset.entries.first { it != NovelTranslationStylePreset.PROFESSIONAL }
        coEvery {
            geminiService.translateBatch(any(), any(), any())
        } returns listOf("Здравствуй", "Свет")

        val second = processor.translateSegments(listOf("Hello", "World"), settings)
        second[0] shouldBe "Здравствуй"
        second[1] shouldBe "Свет"
    }

    @Test
    fun `successful translation returns translated map`() = runTest {
        every { settings.translationProvider } returns NovelTranslationProvider.GEMINI

        coEvery {
            geminiService.translateBatch(any(), any(), any())
        } returns listOf("Привет", "Мир")

        val result = processor.translateSegments(
            segments = listOf("Hello", "World"),
            settings = settings,
        )

        result[0] shouldBe "Привет"
        result[1] shouldBe "Мир"
    }

    @Test
    fun `empty response throws exception when relaxed mode is disabled`() = runTest {
        every { settings.translationProvider } returns NovelTranslationProvider.GEMINI
        every { settings.geminiRelaxedMode } returns false

        coEvery {
            geminiService.translateBatch(any(), any(), any())
        } returns null

        val exception = runCatching {
            processor.translateSegments(
                segments = listOf("Hello", "World"),
                settings = settings,
            )
        }.exceptionOrNull()

        assert(exception is IllegalStateException)
    }

    @Test
    fun `partial chunk recovery runs successfully`() = runTest {
        every { settings.translationProvider } returns NovelTranslationProvider.GEMINI
        every { settings.geminiRelaxedMode } returns true

        // First attempt returns incomplete list
        coEvery {
            geminiService.translateBatch(any(), any(), any())
        } returns listOf("Привет", null)

        val result = processor.translateSegments(
            segments = listOf("Hello", "World"),
            settings = settings,
        )

        result[0] shouldBe "Привет"
        result.containsKey(1) shouldBe false // segment 1 is omitted but translation succeeds overall
    }

    @Test
    fun `failure recovery using fallback executes correctly`() = runTest {
        every { settings.translationProvider } returns NovelTranslationProvider.GEMINI
        every { settings.geminiRelaxedMode } returns true

        // Entire chunk fails
        coEvery {
            geminiService.translateBatch(any(), any(), any())
        } returns null

        val exception = runCatching {
            processor.translateSegments(
                segments = listOf("Hello", "World"),
                settings = settings,
            )
        }.exceptionOrNull()

        // Since it is relaxed mode, if everything fails, we still get an exception at the end because translated map is empty.
        assert(exception is IllegalStateException)
    }
}
