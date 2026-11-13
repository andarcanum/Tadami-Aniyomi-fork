package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationSessionResults
import eu.kanade.tachiyomi.ui.reader.novel.replace.replaceRulesFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.repository.TranslationRunRepository
import tachiyomi.domain.translation.service.TranslationFragmentPlanner

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
        coVerify(timeout = 5_000) { queueManager.enqueueChapters(listOf(7L), any(), settings, any()) }
    }

    @Test
    fun `cold-open async cache restore HIT blocks duplicate auto-start`() = runTest {
        val harness = createHarness(CoroutineScope(StandardTestDispatcher(testScheduler)))
        val requirements = harness.settings.toTranslationCacheRequirements(
            replaceRulesFingerprint = replaceRulesFingerprint(emptyList()),
            sourceFingerprint = TranslationFragmentPlanner.sourceFingerprint(harness.source),
        ).copy(geminiDisableCache = false)
        val cachedEntry = GeminiTranslationCacheEntry(
            chapterId = 7L,
            translatedByIndex = mapOf(0 to "Привет"),
            provider = requirements.translationProvider,
            model = requirements.modelId,
            sourceLang = requirements.sourceLang,
            targetLang = requirements.targetLang,
            promptMode = requirements.promptMode,
            stylePreset = requirements.stylePreset,
            extractorVersion = requirements.extractorVersion,
            promptModifiersFingerprint = requirements.promptModifiersFingerprint,
            replaceRulesFingerprint = requirements.replaceRulesFingerprint,
            sourceSegmentCount = harness.source.size,
            sourceFingerprint = requirements.sourceFingerprint,
            generationFingerprint = requirements.generationFingerprint,
        )
        val cachedRun = TranslationRun(
            id = 1L,
            chapterId = 7L,
            novelId = 10L,
            batchToken = "hit",
            batchOrder = 0,
            profileSnapshotJson = null,
            status = TranslationRunStatus.COMPLETE,
            progress = 100,
            errorMessage = null,
            createdAt = 0L,
            updatedAt = 0L,
            retryCount = 0,
            generation = 1L,
        )

        TranslationSessionResults.store.clearChapter(7L)
        TranslationSessionResults.store.publish(cachedRun, cachedEntry)
        try {
            harness.controller.restoreGeminiTranslationFromCache(7L, harness.settings)
            harness.controller.setPendingAutoStart(true)
            harness.controller.maybeAutoStartGeminiTranslation(harness.settings)

            testScheduler.advanceUntilIdle()

            harness.controller.snapshot().hasGeminiTranslationCache shouldBe true
            harness.controller.snapshot().isGeminiTranslationVisible shouldBe true
            coVerify(exactly = 0) { harness.queueManager.enqueueChapters(any(), any(), any(), any()) }
        } finally {
            TranslationSessionResults.store.clearChapter(7L)
        }
    }

    @Test
    fun `cold-open async cache restore MISS proceeds to auto-start`() = runTest {
        val harness = createHarness(CoroutineScope(StandardTestDispatcher(testScheduler)))

        TranslationSessionResults.store.clearChapter(7L)
        try {
            harness.controller.restoreGeminiTranslationFromCache(7L, harness.settings)
            harness.controller.setPendingAutoStart(true)
            harness.controller.maybeAutoStartGeminiTranslation(harness.settings)

            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { harness.queueManager.enqueueChapters(listOf(7L), any(), harness.settings, any()) }
        } finally {
            TranslationSessionResults.store.clearChapter(7L)
        }
    }

    @Test
    fun `startGeminiTranslation resets isGeminiTranslating when joining an already paused run`() = runTest {
        val pausedRun = TranslationRun(
            id = 5L,
            chapterId = 7L,
            novelId = 10L,
            batchToken = "b1",
            batchOrder = 0,
            profileSnapshotJson = "{}",
            status = TranslationRunStatus.PAUSED,
            progress = 50,
            errorMessage = "Paused",
            createdAt = 0L,
            updatedAt = 0L,
            retryCount = 0,
            generation = 2L,
        )
        val harness = createHarness(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            existingRun = pausedRun,
            observedRuns = MutableStateFlow(listOf(pausedRun)),
        )

        harness.controller.startGeminiTranslation()
        harness.controller.snapshot().isGeminiTranslating shouldBe true

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { harness.queueManager.enqueueChapters(listOf(7L), any(), harness.settings, any()) }
        harness.controller.snapshot().isGeminiTranslating shouldBe false
    }

    private data class Harness(
        val controller: NovelTranslationController,
        val queueManager: TranslationQueueManager,
        val settings: NovelReaderSettings,
        val source: List<String>,
    )

    private fun createHarness(
        scope: CoroutineScope,
        existingRun: TranslationRun? = null,
        observedRuns: MutableStateFlow<List<TranslationRun>> = MutableStateFlow(emptyList()),
    ): Harness {
        val preferences = NovelReaderPreferences(InMemoryPreferenceStore(), Json { encodeDefaults = true })
        val settings = preferences.resolveSettings(-1L).copy(
            geminiEnabled = true,
            geminiDisableCache = true,
            geminiAutoTranslateEnglishSource = true,
            geminiSourceLang = "English",
        )
        val source = listOf("Hello")
        val repository = mockk<TranslationRunRepository>(relaxed = true)
        coEvery { repository.getByChapterId(7L) } returns existingRun
        every { repository.observeRuns(10L) } returns observedRuns
        val queueManager = mockk<TranslationQueueManager>(relaxed = true)
        every { queueManager.repository } returns repository

        var holderEmpty = true
        val host = mockk<NovelTranslationHost>(relaxed = true)
        every { host.translationScope } returns scope
        every { host.translationReaderSettings() } returns settings
        every { host.translationHasConfiguredProvider(settings) } returns true
        every { host.translationActiveChapterId() } returns 7L
        every { host.translationCurrentChapter() } returns null
        every { host.translationIsBookRuntimeActive() } returns false
        every { host.translationCurrentParsedTextBlocks() } returns source
        coEvery { host.translationSourceTextBlocks(7L) } returns source
        every { host.translationHolderIsEmpty("gemini") } answers { holderEmpty }
        every { host.translationHolderPut("gemini", any()) } answers { holderEmpty = false }
        every { host.translationHolderClear("gemini") } answers { holderEmpty = true }

        val controller = NovelTranslationController(
            host = host,
            application = mockk(relaxed = true),
            novelReaderPreferences = preferences,
            googleTranslationService = mockk<GoogleTranslationService>(relaxed = true),
            translationQueueManager = queueManager,
        )
        return Harness(controller, queueManager, settings, source)
    }
}
