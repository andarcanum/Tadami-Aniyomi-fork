package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.translation.TranslationJob
import eu.kanade.tachiyomi.data.translation.TranslationProgressUpdate
import eu.kanade.tachiyomi.data.translation.TranslationQueueItem
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.data.translation.TranslationStatus
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginPackage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.interactor.GetNovelSeriesWithEntries
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import java.util.Collections

@Isolated
class NovelReaderScreenModelTest {
    private val activeScreenModels = mutableListOf<NovelReaderScreenModel>()
    private val syncNovelChaptersWithSource = mockk<SyncNovelChaptersWithSource>(relaxed = true)
    private val geminiTranslationService = mockk<GeminiTranslationService>(relaxed = true)
    private val openRouterTranslationService = mockk<OpenRouterTranslationService>(relaxed = true)
    private val openRouterModelsService = mockk<OpenRouterModelsService>(relaxed = true)
    private val deepSeekTranslationService = mockk<DeepSeekTranslationService>(relaxed = true)
    private val deepSeekModelsService = mockk<DeepSeekModelsService>(relaxed = true)
    private val getNovelSeriesWithEntries = mockk<GetNovelSeriesWithEntries>(relaxed = true)
    private val googleTranslationService = mockk<GoogleTranslationService>(relaxed = true)
    private val translationQueueUpdates = MutableSharedFlow<TranslationProgressUpdate>(extraBufferCapacity = 16)
    private val translationQueueActiveTranslation = MutableStateFlow<TranslationQueueItem?>(null)
    private val translationQueueItems = MutableStateFlow<List<TranslationQueueItem>>(emptyList())
    private val translationQueueManager = mockk<TranslationQueueManager>(relaxed = true).also {
        every { it.progressUpdates } returns translationQueueUpdates
        every { it.activeTranslation } returns translationQueueActiveTranslation
        every { it.queue } returns translationQueueItems
        coEvery { it.hasPendingOrActive(any()) } returns false
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
        runBlocking {
            yield()
        }
        NovelReaderChapterPrefetchCache.clear()
        NovelReaderTranslationDiskCacheStore.clear()
        io.mockk.unmockkAll()
        Dispatchers.resetMain()
    }

    @BeforeEach
    fun setupPerTestEnvironment() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        ensureReaderScreenModelDependencies()
    }

    @Test
    fun `loads chapter html from source`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(5_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("Hello") shouldBe true
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `webview javascript stays enabled for selected text translation`() {
        runBlocking {
            ensureReaderScreenModelDependencies()
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(
                    selectedTextTranslationEnabled = true,
                ),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.enableJs shouldBe true
        }
    }

    @Test
    fun `clear chapter transient state resets chapter scoped data`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val successState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val sentinelTime = 123L
            setPrivateField(screenModel, "rawHtml", "<p>stale</p>")
            setPrivateField(screenModel, "parsedContentBlocks", successState.contentBlocks)
            setPrivateField(
                screenModel,
                "parsedRichContentResult",
                NovelRichContentParseResult(
                    blocks = emptyList(),
                    unsupportedFeaturesDetected = false,
                ),
            )
            setPrivateField(screenModel, "geminiTranslatedByIndex", mapOf(0 to "g"))
            setPrivateField(screenModel, "googleTranslatedByIndex", mapOf(0 to "g"))
            setPrivateField(screenModel, "geminiLogs", listOf("gemini"))
            setPrivateField(screenModel, "googleLogs", listOf("google"))
            setPrivateField(screenModel, "isGeminiTranslating", true)
            setPrivateField(screenModel, "isGoogleTranslating", true)
            setPrivateField(screenModel, "isGeminiTranslationVisible", true)
            setPrivateField(screenModel, "isGoogleTranslationVisible", true)
            setPrivateField(screenModel, "hasGeminiTranslationCache", true)
            setPrivateField(screenModel, "hasGoogleTranslationCache", true)
            setPrivateField(screenModel, "geminiTranslationProgress", 77)
            setPrivateField(screenModel, "googleTranslationProgress", 88)
            setPrivateField(screenModel, "hasTriggeredNextChapterPrefetch", true)
            setPrivateField(screenModel, "hasTriggeredNextChapterGeminiPrefetch", true)
            setPrivateField(screenModel, "hasTriggeredGeminiAutoStart", true)
            setPrivateField(screenModel, "attemptedJaomixPages", mutableSetOf(1))
            setPrivateField(screenModel, "chapterReadStartTimeMs", sentinelTime)

            invokePrivateClearChapterTransientState(screenModel)

            getPrivateFieldOrNull<String>(screenModel, "rawHtml") shouldBe null
            getPrivateFieldOrNull<List<Any?>>(screenModel, "parsedContentBlocks") shouldBe null
            getPrivateFieldOrNull<NovelRichContentParseResult>(screenModel, "parsedRichContentResult") shouldBe null
            getPrivateField<Map<Any?, Any?>>(screenModel, "geminiTranslatedByIndex") shouldBe emptyMap<Any?, Any?>()
            getPrivateField<Map<Any?, Any?>>(screenModel, "googleTranslatedByIndex") shouldBe emptyMap<Any?, Any?>()
            getPrivateField<List<Any?>>(screenModel, "geminiLogs") shouldBe emptyList<Any?>()
            getPrivateField<List<Any?>>(screenModel, "googleLogs") shouldBe emptyList<Any?>()
            getPrivateField<Boolean>(screenModel, "isGeminiTranslating") shouldBe false
            getPrivateField<Boolean>(screenModel, "isGoogleTranslating") shouldBe false
            getPrivateField<Boolean>(screenModel, "isGeminiTranslationVisible") shouldBe false
            getPrivateField<Boolean>(screenModel, "isGoogleTranslationVisible") shouldBe false
            getPrivateField<Boolean>(screenModel, "hasGeminiTranslationCache") shouldBe false
            getPrivateField<Boolean>(screenModel, "hasGoogleTranslationCache") shouldBe false
            getPrivateField<Int>(screenModel, "geminiTranslationProgress") shouldBe 0
            getPrivateField<Int>(screenModel, "googleTranslationProgress") shouldBe 0
            getPrivateField<Boolean>(screenModel, "hasTriggeredNextChapterPrefetch") shouldBe false
            getPrivateField<Boolean>(screenModel, "hasTriggeredNextChapterGeminiPrefetch") shouldBe false
            getPrivateField<Boolean>(screenModel, "hasTriggeredGeminiAutoStart") shouldBe false
            getPrivateField<MutableSet<Int>>(screenModel, "attemptedJaomixPages") shouldBe emptySet()
            (getPrivateField<Long>(screenModel, "chapterReadStartTimeMs") > sentinelTime) shouldBe true
        }
    }

    @Test
    fun `loads chapter text off caller thread to avoid main-thread blocking`() {
        runBlocking {
            val callerThreadId = Thread.currentThread().id
            var sourceCallThreadId: Long? = null

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val sourceManager = FakeNovelSourceManager(
                sourceId = novel.source,
                chapterHtml = "<p>Hello</p>",
                onGetChapterText = { sourceCallThreadId = Thread.currentThread().id },
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = sourceManager,
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            (sourceCallThreadId == callerThreadId) shouldBe false
        }
    }

    @Test
    fun `gemini log updates do not rebuild translated content`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val novelReaderPreferences = createNovelReaderPreferences()

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p><p>World</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = novelReaderPreferences,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val successState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val translatedByIndex = (successState.textBlocks.indices).associateWith { index ->
                "Translated $index"
            }
            val translatedSettings = successState.readerSettings.copy(
                geminiEnabled = true,
                geminiApiKey = "test-key",
            )

            setPrivateField(screenModel, "geminiTranslatedByIndex", translatedByIndex)
            setPrivateField(screenModel, "isGeminiTranslationVisible", true)
            setPrivateField(screenModel, "hasGeminiTranslationCache", true)
            setPrivateField(screenModel, "isGeminiTranslating", false)
            setPrivateField(screenModel, "geminiTranslationProgress", 100)
            invokePrivateUpdateContent(screenModel, translatedSettings)

            val before = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val beforeContentBlocks = before.contentBlocks
            before.contentBlocks.any { block ->
                (block as? NovelReaderScreenModel.ContentBlock.Text)?.text?.startsWith("Translated") == true
            } shouldBe true

            screenModel.addGeminiLog("Gemini UI log")

            val after = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            after.geminiLogs.firstOrNull() shouldBe "Gemini UI log"
            (after.contentBlocks === beforeContentBlocks) shouldBe true
        }
    }

    @Test
    fun `starting gemini translation queues background work`() {
        runBlocking {
            mockkObject(TranslationJob.Companion)
            every { TranslationJob.isRunning(any()) } returns false
            justRun { TranslationJob.runImmediately(any()) }

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val prefs = createNovelReaderPreferences().also {
                it.geminiEnabled().set(true)
                it.geminiApiKey().set("test-key")
            }

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p><p>World</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.startGeminiTranslation()

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.isGeminiTranslating shouldBe true
            state.geminiTranslationProgress shouldBe 0
            state.isGeminiTranslationVisible shouldBe false

            coVerify(timeout = 1_000) {
                translationQueueManager.addToQueue(listOf(chapter.id), novel.id)
            }
            verify(timeout = 1_000) {
                TranslationJob.runImmediately(any())
            }
        }
    }

    @Test
    fun `stopping gemini translation cancels queue work`() {
        runBlocking {
            mockkObject(TranslationJob.Companion)
            every { TranslationJob.isRunning(any()) } returns true
            justRun { TranslationJob.stop(any()) }
            justRun { TranslationJob.runImmediately(any()) }

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val prefs = createNovelReaderPreferences().also {
                it.geminiEnabled().set(true)
                it.geminiApiKey().set("test-key")
            }

            coEvery { translationQueueManager.cancelChapter(chapter.id) } returns true
            coEvery { translationQueueManager.hasNext() } returns false

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p><p>World</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.startGeminiTranslation()
            screenModel.stopGeminiTranslation()

            coVerify(timeout = 1_000) {
                translationQueueManager.cancelChapter(chapter.id)
            }
            verify(timeout = 1_000) {
                TranslationJob.stop(any())
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.isGeminiTranslating shouldBe false
            state.geminiTranslationProgress shouldBe 0
        }
    }

    @Test
    fun `re-starting gemini translation after completion processes second completion event`() {
        runBlocking {
            mockkObject(TranslationJob.Companion)
            every { TranslationJob.isRunning(any()) } returns false
            justRun { TranslationJob.runImmediately(any()) }
            justRun { TranslationJob.stop(any()) }

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val prefs = createNovelReaderPreferences().also {
                it.geminiEnabled().set(true)
                it.geminiApiKey().set("test-key")
            }

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p><p>World</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }
            val initialState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()

            NovelReaderTranslationDiskCacheStore.put(
                GeminiTranslationCacheEntry(
                    chapterId = chapter.id,
                    translatedByIndex = mapOf(0 to "Translated hello", 1 to "Translated world"),
                    provider = initialState.readerSettings.translationProvider,
                    model = initialState.readerSettings.translationCacheModelId(),
                    sourceLang = initialState.readerSettings.geminiSourceLang,
                    targetLang = initialState.readerSettings.geminiTargetLang,
                    promptMode = initialState.readerSettings.geminiPromptMode,
                    stylePreset = initialState.readerSettings.geminiStylePreset,
                ),
            )

            // First translation cycle
            screenModel.startGeminiTranslation()
            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.isGeminiTranslating !=
                    true
                ) {
                    yield()
                }
            }

            translationQueueUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = chapter.id,
                    novelId = novel.id,
                    status = TranslationStatus.COMPLETED,
                    progress = 100,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = chapter.name,
                    errorMessage = null,
                ),
            )
            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.isGeminiTranslating !=
                    false
                ) {
                    yield()
                }
            }

            // Second translation cycle — subscription must be alive
            screenModel.startGeminiTranslation()
            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.isGeminiTranslating !=
                    true
                ) {
                    yield()
                }
            }

            translationQueueUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = chapter.id,
                    novelId = novel.id,
                    status = TranslationStatus.COMPLETED,
                    progress = 100,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = chapter.name,
                    errorMessage = null,
                ),
            )
            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.isGeminiTranslating !=
                    false
                ) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.isGeminiTranslating shouldBe false
        }
    }

    @Test
    fun `completed gemini queue updates restore cached translation into reader`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val prefs = createNovelReaderPreferences().also {
                it.geminiEnabled().set(true)
                it.geminiApiKey().set("test-key")
            }

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p><p>World</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val initialState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            NovelReaderTranslationDiskCacheStore.put(
                GeminiTranslationCacheEntry(
                    chapterId = chapter.id,
                    translatedByIndex = mapOf(0 to "Translated hello", 1 to "Translated world"),
                    provider = initialState.readerSettings.translationProvider,
                    model = initialState.readerSettings.translationCacheModelId(),
                    sourceLang = initialState.readerSettings.geminiSourceLang,
                    targetLang = initialState.readerSettings.geminiTargetLang,
                    promptMode = initialState.readerSettings.geminiPromptMode,
                    stylePreset = initialState.readerSettings.geminiStylePreset,
                ),
            )

            translationQueueUpdates.emit(
                TranslationProgressUpdate(
                    chapterId = chapter.id,
                    novelId = novel.id,
                    status = TranslationStatus.COMPLETED,
                    progress = 100,
                    currentChunk = 0,
                    totalChunks = 0,
                    chapterName = chapter.name,
                    errorMessage = null,
                ),
            )

            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.hasGeminiTranslationCache !=
                    true
                ) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.isGeminiTranslating shouldBe false
            state.isGeminiTranslationVisible shouldBe true
            state.hasGeminiTranslationCache shouldBe true
            state.contentBlocks.filterIsInstance<NovelReaderScreenModel.ContentBlock.Text>()
                .map { it.text }
                .any { it == "Translated hello" } shouldBe true
        }
    }

    @Test
    fun `tts prefers translated text even when translation is hidden`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<h1>Original title</h1><p>Original paragraph</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val settings = state.readerSettings.copy(
                geminiEnabled = true,
                ttsPreferTranslatedText = true,
                ttsReadChapterTitle = false,
            )
            val originalBlocks = listOf(
                NovelReaderScreenModel.ContentBlock.Text("Original paragraph"),
            )
            val richBlocks = listOf(
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment("Original paragraph")),
                ),
            )

            setPrivateField(screenModel, "currentChapter", chapter)
            setPrivateField(screenModel, "geminiTranslatedByIndex", mapOf(0 to "Переведенный абзац"))
            setPrivateField(screenModel, "isGeminiTranslationVisible", false)

            val translatedModel = invokePrivateResolveTranslatedTtsChapterModel(
                target = screenModel,
                chapterId = chapter.id,
                chapterTitle = chapter.name,
                originalContentBlocks = originalBlocks,
                richContentBlocks = richBlocks,
                settings = settings,
            )

            val resolvedTranslatedModel = translatedModel ?: error("Expected translated TTS model")
            resolvedTranslatedModel.utterances.map { it.text } shouldBe listOf("Переведенный абзац")
        }
    }

    @Test
    fun `tts prefers google translated text even when rich content exists`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<h1>Original title</h1><p>Original paragraph</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val settings = state.readerSettings.copy(
                googleTranslationEnabled = true,
                ttsPreferTranslatedText = false,
                ttsReadChapterTitle = false,
            )
            val originalBlocks = listOf(
                NovelReaderScreenModel.ContentBlock.Text("Original paragraph"),
            )
            val richBlocks = listOf(
                NovelRichContentBlock.Heading(
                    level = 1,
                    segments = listOf(NovelRichTextSegment("Original title")),
                ),
                NovelRichContentBlock.Paragraph(
                    segments = listOf(NovelRichTextSegment("Original paragraph")),
                ),
            )

            setPrivateField(screenModel, "currentChapter", chapter)
            setPrivateField(screenModel, "googleTranslatedByIndex", mapOf(0 to "Переведенный абзац"))
            setPrivateField(screenModel, "isGoogleTranslationVisible", true)

            val translatedModel = invokePrivateResolveTranslatedTtsChapterModel(
                target = screenModel,
                chapterId = chapter.id,
                chapterTitle = chapter.name,
                originalContentBlocks = originalBlocks,
                richContentBlocks = richBlocks,
                settings = settings,
            )

            val resolvedTranslatedModel = translatedModel ?: error("Expected translated TTS model")
            resolvedTranslatedModel.utterances.map { it.text } shouldBe listOf("Переведенный абзац")
        }
    }

    @Test
    fun `update content syncs translated preference into tts controller`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Original paragraph</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            setPrivateField(screenModel, "googleTranslatedByIndex", mapOf(0 to "Переведённый абзац"))
            setPrivateField(screenModel, "isGoogleTranslationVisible", true)

            invokePrivateUpdateContent(
                target = screenModel,
                settings = state.readerSettings.copy(googleTranslationEnabled = true),
            )

            val ttsController = getPrivateField<Any>(screenModel, "ttsSessionController")
            getPrivateField<Boolean>(ttsController, "preferredTranslatedText") shouldBe true
        }
    }

    @Test
    fun `disable tts updates preference and reader state`() {
        runBlocking {
            val novelReaderPreferences = createNovelReaderPreferences(
                ttsEnabled = true,
            )
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Original paragraph</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = novelReaderPreferences,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.disableTts()

            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.readerSettings?.ttsEnabled !=
                    false
                ) {
                    yield()
                }
            }

            novelReaderPreferences.ttsEnabled().get() shouldBe false
            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.readerSettings.ttsEnabled shouldBe false
        }
    }

    @Test
    fun `injects chapter title heading when chapter html has no heading`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Том 1 Глава 0 - Система сил(?)",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("<h1 class=\"an-reader-chapter-title\">Том 1 Глава 0 - Система сил(?)</h1>") shouldBe
                true
            state.textBlocks.firstOrNull() shouldBe "Том 1 Глава 0 - Система сил(?)"
        }
    }

    @Test
    fun `does not prepend chapter title when html already contains heading`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<h1>Сайтовый заголовок</h1><p>Hello</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.textBlocks.firstOrNull() shouldBe "Сайтовый заголовок"
            state.textBlocks.contains("Chapter 1") shouldBe false
        }
    }

    @Test
    fun `builds mixed content blocks with text and resolved image urls`() {
        runBlocking {
            val novel = Novel.create().copy(
                id = 1L,
                source = 10L,
                title = "Novel",
                url = "https://example.org/book/slug",
            )
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/book/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Intro</p><img src=\"/images/pic.jpg\" /><p>Outro</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.contentBlocks.size shouldBe 4
            state.contentBlocks[0].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>().text shouldBe
                "Chapter 1"
            state.contentBlocks[1].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>().text shouldBe "Intro"
            state.contentBlocks[2].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Image>().url shouldBe
                "https://example.org/images/pic.jpg"
            state.contentBlocks[3].shouldBeInstanceOf<NovelReaderScreenModel.ContentBlock.Text>().text shouldBe "Outro"
        }
    }

    @Test
    fun `html payload keeps normal paragraphs and recovers malformed list fragment`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val htmlWithMalformedFragment =
                "<div><p>Эта информация не обязательна для понимания.</p><p>{\"type: \"bulletList\", \"content: [{\"type\": \"listItem\", \"content\": [{\"type\": \"paragraph\", \"content\": [{\"type\": \"text\", \"text\": \"Магия в этом мире основана на математике и формулах\"}]}]}]}</p><p>Финальная строка.</p></div>"

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = htmlWithMalformedFragment,
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.textBlocks.size shouldBe 4
            state.textBlocks[0] shouldBe "Chapter 1"
            state.textBlocks[1].isNotBlank() shouldBe true
            state.textBlocks[2].startsWith("\u2022 ") shouldBe true
            state.textBlocks[3].endsWith(".") shouldBe true
        }
    }

    @Test
    fun `loads custom js and css for plugin source`() {
        runBlocking {
            val pluginId = "plugin.test"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val customJs = "console.log('custom');"
            val customCss = "body { color: red; }"
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = customJs.toByteArray(),
                customCss = customCss.toByteArray(),
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.enableJs shouldBe true
            state.html.contains(customJs) shouldBe true
            state.html.contains(customCss) shouldBe true
            state.lastSavedIndex shouldBe 0
            state.chapterWebUrl shouldBe "https://example.org/ch1"
            Unit
        }
    }

    @Test
    fun `applies plugin css text indent to rich native blocks`() {
        runBlocking {
            val pluginId = "plugin.indent"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = null,
                customCss = "p { text-indent: 2em; }".toByteArray(),
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val paragraph = state.richContentBlocks
                .filterIsInstance<NovelRichContentBlock.Paragraph>()
                .first { block ->
                    block.segments.any { it.text.contains("Hello") }
                }
            paragraph.firstLineIndentEm shouldBe 2f
        }
    }

    @Test
    fun `applies plugin descendant css text indent and align to rich native blocks`() {
        runBlocking {
            val pluginId = "plugin.indent.descendant"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "https://example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = null,
                customCss = ".entry p { text-indent: 2em; text-align: justify; }".toByteArray(),
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<div class=\"entry\"><p>Hello</p></div>",
                ),
                pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val paragraph = state.richContentBlocks
                .filterIsInstance<NovelRichContentBlock.Paragraph>()
                .first { block ->
                    block.segments.any { it.text.contains("Hello") }
                }
            paragraph.firstLineIndentEm shouldBe 2f
            paragraph.textAlign shouldBe NovelRichBlockTextAlign.JUSTIFY
        }
    }

    @Test
    fun `resolves chapter web url from plugin site when chapter path is relative`() {
        runBlocking {
            val pluginId = "plugin.relative"
            val entry = NovelPluginRepoEntry(
                id = pluginId,
                name = "Plugin",
                site = "example.org",
                lang = "en",
                version = 1,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "ignored",
            )
            val pkg = NovelPluginPackage(
                entry = entry,
                script = "console.log('main');".toByteArray(),
                customJs = null,
                customCss = null,
            )

            val novel = Novel.create().copy(id = 1L, source = pluginId.hashCode().toLong(), title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "/book/chapter-1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(listOf(pkg)),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.chapterWebUrl shouldBe "https://example.org/book/chapter-1"
            Unit
        }
    }

    @Test
    fun `uses source chapter web url resolver when available`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "book-slug/1/1/0",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello</p>",
                    chapterWebUrlResolver = { chapterPath, _ ->
                        if (chapterPath == "book-slug/1/1/0") {
                            "https://ranobelib.me/ru/book-slug/read/v1/c1?bid=0"
                        } else {
                            null
                        }
                    },
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.chapterWebUrl shouldBe "https://ranobelib.me/ru/book-slug/read/v1/c1?bid=0"
            Unit
        }
    }

    @Test
    fun `applies reader settings to html`() {
        runBlocking {
            val store = InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_font_size",
                        20,
                        NovelReaderPreferences.DEFAULT_FONT_SIZE,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_line_height",
                        1.8f,
                        NovelReaderPreferences.DEFAULT_LINE_HEIGHT,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_margins",
                        24,
                        NovelReaderPreferences.DEFAULT_MARGIN,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_theme",
                        NovelReaderTheme.LIGHT,
                        NovelReaderTheme.SYSTEM,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_cache_read_chapters",
                        false,
                        true,
                    ),
                    InMemoryPreferenceStore.InMemoryPreference(
                        "novel_reader_cache_read_chapters_unlimited",
                        false,
                        false,
                    ),
                ),
            )
            val prefs = NovelReaderPreferences(
                preferenceStore = store,
                json = Json { encodeDefaults = true },
            )

            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.html.contains("font-size: 20px") shouldBe true
            state.html.contains("line-height: 1.8") shouldBe true
            state.html.contains("padding: 24px") shouldBe true
            state.readerSettings.fontSize shouldBe 20
            state.readerSettings.lineHeight shouldBe 1.8f
            state.readerSettings.margin shouldBe 24
            state.readerSettings.theme shouldBe NovelReaderTheme.LIGHT
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `reuses parsed content blocks when settings change`() {
        runBlocking {
            val store = ReactivePreferenceStore()
            val prefs = NovelReaderPreferences(
                preferenceStore = store,
                json = Json { encodeDefaults = true },
            ).also {
                it.cacheReadChapters().set(false)
                it.cacheReadChaptersUnlimited().set(false)
            }
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Intro</p><p>Outro</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            try {
                withTimeout(1_000) {
                    while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                        yield()
                    }
                }

                val initialState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                val initialBlocks = initialState.contentBlocks

                prefs.fontSize().set(22)

                withTimeout(1_000) {
                    while (true) {
                        val state = screenModel.state.value
                        if (state is NovelReaderScreenModel.State.Success && state.readerSettings.fontSize == 22) {
                            break
                        }
                        yield()
                    }
                }

                val updatedState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                (updatedState.contentBlocks === initialBlocks) shouldBe true
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `exposes rich parsed content and unsupported flag in success state`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello <strong>world</strong></p><table><tr><td>x</td></tr></table>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.richContentBlocks.isNotEmpty() shouldBe true
            state.richContentBlocks.any { it is NovelRichContentBlock.Paragraph } shouldBe true
            state.richContentUnsupportedFeaturesDetected shouldBe true
        }
    }

    @Test
    fun `reuses parsed rich content blocks when settings change`() {
        runBlocking {
            val store = ReactivePreferenceStore()
            val prefs = NovelReaderPreferences(
                preferenceStore = store,
                json = Json { encodeDefaults = true },
            ).also {
                it.cacheReadChapters().set(false)
                it.cacheReadChaptersUnlimited().set(false)
            }
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>Hello <em>styled</em> world</p>",
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            try {
                withTimeout(1_000) {
                    while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                        yield()
                    }
                }

                val initialState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                val initialRichBlocks = initialState.richContentBlocks

                prefs.fontSize().set(22)

                withTimeout(1_000) {
                    while (true) {
                        val state = screenModel.state.value
                        if (state is NovelReaderScreenModel.State.Success && state.readerSettings.fontSize == 22) {
                            break
                        }
                        yield()
                    }
                }

                val updatedState = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                (updatedState.richContentBlocks === initialRichBlocks) shouldBe true
            } finally {
                screenModel.onDispose()
            }
        }
    }

    @Test
    fun `missing chapter shows error state`() {
        runBlocking {
            val screenModel = trackedNovelReaderScreenModel(
                chapterId = 99L,
                novelChapterRepository = FakeNovelChapterRepository(null),
                getNovel = GetNovel(FakeNovelRepository(Novel.create())),
                sourceManager = FakeNovelSourceManager(sourceId = 10L, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Error>()
            Unit
        }
    }

    @Test
    fun `update reading progress marks read near end`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 9, totalItems = 10)
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 9L
        }
    }

    @Test
    fun `update reading progress marks single page chapter as read`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 0, totalItems = 1)
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 0L
        }
    }

    @Test
    fun `percent based tracking does not mark read too early`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 95, totalItems = 100, persistedProgress = 95L)
            yield()
            chapterRepo.lastUpdate?.read shouldBe false
            chapterRepo.lastUpdate?.lastPageRead shouldBe 95L

            screenModel.updateReadingProgress(currentIndex = 99, totalItems = 100, persistedProgress = 99L)
            yield()
            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 99L
        }
    }

    @Test
    fun `encoded webview progress is restored as percent without affecting native index`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = encodeWebScrollProgressPercent(42),
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.lastSavedWebProgressPercent shouldBe 42
            state.lastSavedIndex shouldBe 0
            Unit
        }
    }

    @Test
    fun `encoded page reader progress is restored separately from native and web state`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = encodePageReaderProgress(index = 7, totalItems = 10),
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = FakeNovelChapterRepository(chapter),
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.lastSavedPageReaderProgress shouldBe PageReaderProgress(index = 7, totalItems = 10)
            state.lastSavedIndex shouldBe 0
            state.lastSavedWebProgressPercent shouldBe 0
            Unit
        }
    }

    @Test
    fun `page reader progress restore is independent from selected transition style`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = encodePageReaderProgress(index = 7, totalItems = 10),
            )

            suspend fun loadStateFor(style: NovelPageTransitionStyle): NovelReaderScreenModel.State.Success {
                val preferences = createNovelReaderPreferences().also {
                    it.pageTransitionStyle().set(style)
                }
                val screenModel = trackedNovelReaderScreenModel(
                    chapterId = chapter.id,
                    novelChapterRepository = FakeNovelChapterRepository(chapter),
                    getNovel = GetNovel(FakeNovelRepository(novel)),
                    sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                    pluginStorage = FakeNovelPluginStorage(emptyList()),
                    novelReaderPreferences = preferences,
                    isSystemDark = { false },
                )

                withTimeout(1_000) {
                    while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                        yield()
                    }
                }

                return screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            }

            val slideState = loadStateFor(NovelPageTransitionStyle.SLIDE)
            val curlState = loadStateFor(NovelPageTransitionStyle.CURL)

            slideState.lastSavedPageReaderProgress shouldBe PageReaderProgress(index = 7, totalItems = 10)
            curlState.lastSavedPageReaderProgress shouldBe slideState.lastSavedPageReaderProgress
            curlState.lastSavedIndex shouldBe slideState.lastSavedIndex
            curlState.lastSavedWebProgressPercent shouldBe slideState.lastSavedWebProgressPercent
            Unit
        }
    }

    @Test
    fun `progress callback after read completion does not reset chapter to unread`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            // WebView/native can emit low/stale progress on disposal; this must not unread chapter.
            screenModel.updateReadingProgress(currentIndex = 99, totalItems = 100)
            yield()
            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 99L

            screenModel.updateReadingProgress(currentIndex = 0, totalItems = 100)
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 99L
        }
    }

    @Test
    fun `read chapter keeps saved progress when stale callback arrives`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                read = true,
                lastPageRead = 99L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 0, totalItems = 100)
            yield()

            chapterRepo.lastUpdate shouldBe null
        }
    }

    @Test
    fun `read chapter can move saved native progress back from chapter end`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val endProgress = encodeNativeScrollProgress(index = 9, offsetPx = 0)
            val middleProgress = encodeNativeScrollProgress(index = 4, offsetPx = 320)
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                read = true,
                lastPageRead = endProgress,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(
                currentIndex = 4,
                totalItems = 10,
                persistedProgress = middleProgress,
            )
            yield()

            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe middleProgress
        }
    }

    @Test
    fun `defers history writes for progress updates until reader is disposed`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)
            val historyRepository = FakeNovelHistoryRepository()

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                historyRepository = historyRepository,
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            historyRepository.lastUpdate?.chapterId shouldBe chapter.id
            historyRepository.updates.size shouldBe 1

            screenModel.updateReadingProgress(currentIndex = 1, totalItems = 10)
            yield()

            historyRepository.lastUpdate?.chapterId shouldBe chapter.id
            historyRepository.updates.size shouldBe 1

            delay(5)
            screenModel.onDispose()
            yield()

            historyRepository.lastUpdate?.chapterId shouldBe chapter.id
            historyRepository.updates.size shouldBe 2
            (historyRepository.lastUpdate?.readAt != null) shouldBe true
        }
    }

    @Test
    fun `initial progress callback at saved index does not update chapter`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = 3L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 3, totalItems = 10)
            yield()

            chapterRepo.lastUpdate shouldBe null
        }
    }

    @Test
    fun `repeated callback at saved index does not mark chapter read`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = 8L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            // Initial callback and dispose callback with unchanged index should not update.
            screenModel.updateReadingProgress(currentIndex = 8, totalItems = 10)
            screenModel.updateReadingProgress(currentIndex = 8, totalItems = 10)
            yield()
            chapterRepo.lastUpdate shouldBe null

            // Actual progress change can mark chapter as read near the end.
            screenModel.updateReadingProgress(currentIndex = 9, totalItems = 10)
            yield()
            chapterRepo.lastUpdate?.read shouldBe true
            chapterRepo.lastUpdate?.lastPageRead shouldBe 9L
        }
    }

    @Test
    fun `native progress update at same index persists changed scroll offset`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                lastPageRead = encodeNativeScrollProgress(index = 0, offsetPx = 0),
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val updatedProgress = encodeNativeScrollProgress(index = 0, offsetPx = 420)
            screenModel.updateReadingProgress(
                currentIndex = 0,
                totalItems = 10,
                persistedProgress = updatedProgress,
            )
            yield()

            chapterRepo.lastUpdate?.read shouldBe false
            chapterRepo.lastUpdate?.lastPageRead shouldBe updatedProgress
        }
    }

    @Test
    fun `computes previous and next chapter ids from source order`() {
        runBlocking {
            val novel = Novel.create().copy(id = 991001L, source = 991010L, title = "Novel")
            val chapter1 = NovelChapter.create().copy(
                id = 991101L,
                novelId = novel.id,
                name = "Chapter 1",
                url = "https://example.org/ch1-prefetch-threshold",
                sourceOrder = 0L,
            )
            val chapter2 = NovelChapter.create().copy(
                id = 991102L,
                novelId = novel.id,
                name = "Chapter 2",
                url = "https://example.org/ch2-prefetch-threshold",
                sourceOrder = 1L,
            )
            val chapter3 = NovelChapter.create().copy(
                id = 3L,
                novelId = 1L,
                name = "Chapter 3",
                url = "https://example.org/ch3",
                sourceOrder = 2L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter2, listOf(chapter1, chapter2, chapter3))

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter2.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.previousChapterId shouldBe chapter1.id
            state.nextChapterId shouldBe chapter3.id
            chapterRepo.lastApplyScanlatorFilter shouldBe true
        }
    }

    @Test
    fun `computes previous and next chapter ids from the active branch subset`() {
        runBlocking {
            val novel = Novel.create().copy(id = 992001L, source = 992010L, title = "Novel")
            val selectedBranchChapter1 = NovelChapter.create().copy(
                id = 992101L,
                novelId = novel.id,
                name = "Chapter 1 - SeRa",
                url = "https://example.org/ch1-sera",
                sourceOrder = 10L,
                chapterNumber = 1.0,
                scanlator = "SeRa",
            )
            val selectedBranchChapter2 = NovelChapter.create().copy(
                id = 992102L,
                novelId = novel.id,
                name = "Chapter 2 - SeRa",
                url = "https://example.org/ch2-sera",
                sourceOrder = 20L,
                chapterNumber = 2.0,
                scanlator = "SeRa",
            )
            val chapterRepo = FakeNovelChapterRepository(
                chapter = selectedBranchChapter1,
                chaptersByNovel = listOf(selectedBranchChapter1, selectedBranchChapter2),
            )

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = selectedBranchChapter1.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.previousChapterId shouldBe null
            state.nextChapterId shouldBe selectedBranchChapter2.id
            chapterRepo.lastApplyScanlatorFilter shouldBe true
        }
    }

    @Test
    fun `prefetches next chapter after reaching 50 percent progress when enabled`() {
        runBlocking {
            val novel = Novel.create().copy(id = 991001L, source = 991010L, title = "Novel")
            val chapter1 = NovelChapter.create().copy(
                id = 991101L,
                novelId = novel.id,
                name = "Chapter 1",
                url = "https://example.org/ch1-prefetch-threshold",
                sourceOrder = 0L,
            )
            val chapter2 = NovelChapter.create().copy(
                id = 991102L,
                novelId = novel.id,
                name = "Chapter 2",
                url = "https://example.org/ch2-prefetch-threshold",
                sourceOrder = 1L,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter1, listOf(chapter1, chapter2))
            val requestedUrls = Collections.synchronizedList(mutableListOf<String>())
            val prefs = createNovelReaderPreferences().also {
                it.prefetchNextChapter().set(true)
            }
            prefs.prefetchNextChapter().get() shouldBe true

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter1.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(
                    sourceId = novel.source,
                    chapterHtml = "<p>fallback</p>",
                    chapterHtmlByUrl = mapOf(
                        chapter1.url to "<p>One</p>",
                        chapter2.url to "<p>Two</p>",
                    ),
                    requestedChapterUrls = requestedUrls,
                ),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = prefs,
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }
            screenModel.state.value
                .shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                .readerSettings
                .prefetchNextChapter shouldBe true

            requestedUrls.size shouldBe 1
            requestedUrls.contains(chapter2.url) shouldBe false

            screenModel.updateReadingProgress(currentIndex = 4, totalItems = 10)
            yield()

            withTimeout(1_000) {
                while (!requestedUrls.contains(chapter2.url)) {
                    yield()
                }
            }
            requestedUrls.contains(chapter1.url) shouldBe true
            requestedUrls.contains(chapter2.url) shouldBe true
        }
    }

    @Test
    fun `toggle chapter bookmark updates repository and state`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
                bookmark = false,
            )
            val chapterRepo = FakeNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.toggleChapterBookmark()

            // Wait for the async repository update to complete
            withTimeout(1_000) {
                while (chapterRepo.lastUpdate == null) {
                    yield()
                }
            }

            withTimeout(1_000) {
                while ((screenModel.state.value as? NovelReaderScreenModel.State.Success)?.chapter?.bookmark != true) {
                    yield()
                }
            }

            chapterRepo.lastUpdate?.bookmark shouldBe true
            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.chapter.bookmark shouldBe true
        }
    }

    @Test
    fun `rapid progress updates coalesce while repository write is in flight`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = BlockingNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 1, totalItems = 10)
            withTimeout(1_000) {
                while (chapterRepo.startedUpdates.isEmpty()) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 2, totalItems = 10)
            screenModel.updateReadingProgress(currentIndex = 3, totalItems = 10)
            yield()

            chapterRepo.startedUpdates.size shouldBe 1

            chapterRepo.allowNextUpdate()
            withTimeout(1_000) {
                while (chapterRepo.startedUpdates.size < 2) {
                    yield()
                }
            }

            chapterRepo.allowNextUpdate()
            withTimeout(1_000) {
                while (chapterRepo.completedUpdates.size < 2) {
                    yield()
                }
            }

            chapterRepo.completedUpdates.size shouldBe 2
            chapterRepo.completedUpdates[0].lastPageRead shouldBe 1L
            chapterRepo.completedUpdates[1].lastPageRead shouldBe 3L
        }
    }

    @Test
    fun `flush pending progress waits for the in flight chapter update`() {
        runBlocking {
            val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
            val chapter = NovelChapter.create().copy(
                id = 5L,
                novelId = 1L,
                name = "Chapter 1",
                url = "https://example.org/ch1",
            )
            val chapterRepo = BlockingNovelChapterRepository(chapter)

            val screenModel = trackedNovelReaderScreenModel(
                chapterId = chapter.id,
                novelChapterRepository = chapterRepo,
                getNovel = GetNovel(FakeNovelRepository(novel)),
                sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
                pluginStorage = FakeNovelPluginStorage(emptyList()),
                novelReaderPreferences = createNovelReaderPreferences(),
                isSystemDark = { false },
            )

            withTimeout(1_000) {
                while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                    yield()
                }
            }

            screenModel.updateReadingProgress(currentIndex = 1, totalItems = 10)
            withTimeout(1_000) {
                while (chapterRepo.startedUpdates.isEmpty()) {
                    yield()
                }
            }

            val flushJob = async { screenModel.awaitPendingProgressPersistence() }
            yield()
            flushJob.isCompleted shouldBe false

            chapterRepo.allowNextUpdate()
            flushJob.await()

            chapterRepo.completedUpdates.size shouldBe 1
            chapterRepo.completedUpdates.single().lastPageRead shouldBe 1L
        }
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private inline fun <reified T> getPrivateField(target: Any, name: String): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T
    }

    private inline fun <reified T> getPrivateFieldOrNull(target: Any, name: String): T? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T?
    }

    private fun invokePrivateClearChapterTransientState(target: Any) {
        val method = target.javaClass.getDeclaredMethod("clearChapterTransientState")
        method.isAccessible = true
        method.invoke(target)
    }

    private fun invokePrivateUpdateContent(
        target: Any,
        settings: NovelReaderSettings,
    ) {
        val method = target.javaClass.getDeclaredMethod("updateContent", NovelReaderSettings::class.java)
        method.isAccessible = true
        method.invoke(target, settings)
    }

    private fun invokePrivateCurrentParsedTextBlocks(target: Any): List<String> {
        val method = target.javaClass.getDeclaredMethod("currentParsedTextBlocks")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(target) as List<String>
    }

    private fun invokePrivateResolveTranslatedTtsChapterModel(
        target: Any,
        chapterId: Long,
        chapterTitle: String,
        originalContentBlocks: List<NovelReaderScreenModel.ContentBlock>,
        richContentBlocks: List<NovelRichContentBlock>,
        settings: NovelReaderSettings,
    ): eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModel? {
        val method = target.javaClass.getDeclaredMethod(
            "resolveTranslatedTtsChapterModel",
            Long::class.javaPrimitiveType,
            String::class.java,
            List::class.java,
            List::class.java,
            NovelReaderSettings::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            target,
            chapterId,
            chapterTitle,
            originalContentBlocks,
            richContentBlocks,
            settings,
        ) as eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterModel?
    }

    private class FakeNovelChapterRepository(
        private val chapter: NovelChapter?,
        private val chaptersByNovel: List<NovelChapter> = emptyList(),
    ) : NovelChapterRepository {
        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        var lastUpdate: NovelChapterUpdate? = null
        var lastApplyScanlatorFilter: Boolean? = null
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) {
            lastUpdate = chapterUpdate
        }
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean): List<NovelChapter> {
            lastApplyScanlatorFilter = applyScanlatorFilter
            return chaptersByNovel
        }
        override suspend fun getScanlatorsByNovelId(novelId: Long) = chaptersByNovel.mapNotNull { it.scanlator }
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long) = emptyList<NovelChapter>()
        override suspend fun getChapterById(id: Long): NovelChapter? = chapter?.takeIf { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
    }

    private class BlockingNovelChapterRepository(
        private val chapter: NovelChapter?,
    ) : NovelChapterRepository {
        val startedUpdates = mutableListOf<NovelChapterUpdate>()
        val completedUpdates = mutableListOf<NovelChapterUpdate>()
        private val updatePermits = Channel<Unit>(capacity = Channel.UNLIMITED)

        fun allowNextUpdate() {
            updatePermits.trySend(Unit)
        }

        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters

        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) {
            startedUpdates += chapterUpdate
            updatePermits.receive()
            completedUpdates += chapterUpdate
        }

        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit

        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit

        override suspend fun getChapterByNovelId(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): List<NovelChapter> = emptyList()

        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()

        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> = MutableStateFlow(emptyList())

        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()

        override suspend fun getChapterById(id: Long): NovelChapter? = chapter?.takeIf { it.id == id }

        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(emptyList())

        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
    }

    private class FakeNovelPluginStorage(
        private val packages: List<NovelPluginPackage>,
    ) : NovelPluginStorage {
        override suspend fun save(pkg: NovelPluginPackage) = Unit
        override suspend fun get(id: String): NovelPluginPackage? =
            packages.firstOrNull { it.entry.id == id }
        override suspend fun getAll(): List<NovelPluginPackage> = packages
    }

    private class FakeNovelHistoryRepository : NovelHistoryRepository {
        val updates = mutableListOf<NovelHistoryUpdate>()
        val lastUpdate: NovelHistoryUpdate?
            get() = updates.lastOrNull()

        override fun getNovelHistory(query: String): Flow<List<NovelHistoryWithRelations>> =
            MutableStateFlow(emptyList())

        override suspend fun getLastNovelHistory(): NovelHistoryWithRelations? = null

        override suspend fun getTotalReadDuration(): Long = 0L

        override suspend fun getHistoryByNovelId(novelId: Long): List<NovelHistory> = emptyList()

        override suspend fun resetNovelHistory(historyId: Long) = Unit

        override suspend fun resetHistoryByNovelId(novelId: Long) = Unit

        override suspend fun deleteAllNovelHistory(): Boolean = true

        override suspend fun upsertNovelHistory(historyUpdate: NovelHistoryUpdate) {
            updates += historyUpdate
        }
    }

    private fun createNovelReaderPreferences(
        selectedTextTranslationEnabled: Boolean = false,
        ttsEnabled: Boolean = false,
    ): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = ReactivePreferenceStore(),
            json = Json { encodeDefaults = true },
        ).also { prefs ->
            // Unit tests run without Android Application in Injekt; avoid touching disk cache store.
            prefs.cacheReadChapters().set(false)
            prefs.cacheReadChaptersUnlimited().set(false)
            prefs.selectedTextTranslationEnabled().set(selectedTextTranslationEnabled)
            prefs.ttsEnabled().set(ttsEnabled)
        }
    }

    private fun trackedNovelReaderScreenModel(
        chapterId: Long,
        novelChapterRepository: NovelChapterRepository,
        getNovel: GetNovel,
        sourceManager: NovelSourceManager,
        pluginStorage: NovelPluginStorage,
        novelReaderPreferences: NovelReaderPreferences,
        isSystemDark: () -> Boolean,
        historyRepository: NovelHistoryRepository = FakeNovelHistoryRepository(),
        googleTranslationService: GoogleTranslationService = this.googleTranslationService,
        translationQueueManager: TranslationQueueManager = this.translationQueueManager,
    ): NovelReaderScreenModel {
        ensureReaderScreenModelDependencies()
        return NovelReaderScreenModel(
            chapterId = chapterId,
            novelChapterRepository = novelChapterRepository,
            syncNovelChaptersWithSource = syncNovelChaptersWithSource,
            getNovel = getNovel,
            sourceManager = sourceManager,
            pluginStorage = pluginStorage,
            historyRepository = historyRepository,
            novelReaderPreferences = novelReaderPreferences,
            isSystemDark = isSystemDark,
            geminiTranslationService = geminiTranslationService,
            openRouterTranslationService = openRouterTranslationService,
            openRouterModelsService = openRouterModelsService,
            deepSeekTranslationService = deepSeekTranslationService,
            deepSeekModelsService = deepSeekModelsService,
            googleTranslationService = googleTranslationService,
            translationQueueManager = translationQueueManager,
        ).also(activeScreenModels::add)
    }

    private fun ensureReaderScreenModelDependencies() {
        val filesDir = java.io.File(System.getProperty("java.io.tmpdir"), "novel-reader-test-files")
            .apply { mkdirs() }
        val cacheDir = java.io.File(System.getProperty("java.io.tmpdir"), "novel-reader-test-cache")
            .apply { mkdirs() }
        val application = mockk<Application>(relaxed = true)
        every { application.filesDir } returns filesDir
        every { application.cacheDir } returns cacheDir
        Injekt.addSingleton(fullType<Application>(), application)

        val networkHelper = mockk<NetworkHelper>()
        every { networkHelper.client } returns OkHttpClient()
        Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)

        Injekt.addSingleton(fullType<Json>(), Json { encodeDefaults = true })

        val testTranslationQueueManager = mockk<TranslationQueueManager>(relaxed = true)
        every {
            testTranslationQueueManager.activeTranslation
        } returns MutableStateFlow<TranslationQueueItem?>(null)
        Injekt.addSingleton(fullType<TranslationQueueManager>(), testTranslationQueueManager)

        every { getNovelSeriesWithEntries.subscribe(any()) } returns MutableStateFlow(null)
        Injekt.addSingleton(fullType<GetNovelSeriesWithEntries>(), getNovelSeriesWithEntries)
    }

    private class ReactivePreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()
        private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

        override fun getString(key: String, defaultValue: String): Preference<String> = createPreference(
            key,
            defaultValue,
        )

        override fun getLong(key: String, defaultValue: Long): Preference<Long> = createPreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> = createPreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = createPreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = createPreference(
            key,
            defaultValue,
        )

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            createPreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = createPreference(key, defaultValue)

        override fun getAll(): Map<String, *> = values.toMap()

        @Suppress("UNCHECKED_CAST")
        private fun <T> createPreference(key: String, defaultValue: T): Preference<T> {
            val stateFlow = flows.getOrPut(key) {
                MutableStateFlow(values[key] ?: defaultValue)
            }
            return object : Preference<T> {
                override fun key(): String = key

                override fun get(): T = (values[key] as T?) ?: (stateFlow.value as T?) ?: defaultValue

                override fun set(value: T) {
                    values[key] = value
                    stateFlow.value = value
                }

                override fun isSet(): Boolean = values.containsKey(key)

                override fun delete() {
                    values.remove(key)
                    stateFlow.value = defaultValue
                }

                override fun defaultValue(): T = defaultValue

                override fun changes(): Flow<T> = stateFlow.map { (it as T?) ?: defaultValue }

                override fun stateIn(scope: CoroutineScope) =
                    changes().stateIn(scope, SharingStarted.Eagerly, get())
            }
        }
    }

    private class FakeNovelRepository(
        private val novel: Novel,
    ) : NovelRepository {
        override suspend fun getNovelById(id: Long): Novel = novel
        override suspend fun getNovelByIdAsFlow(id: Long) = MutableStateFlow(novel)
        override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? = null
        override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long) = MutableStateFlow<Novel?>(null)
        override suspend fun getNovelFavorites(): List<Novel> = emptyList()
        override suspend fun getReadNovelNotInLibrary(): List<Novel> = emptyList()
        override suspend fun getLibraryNovel(): List<LibraryNovel> = emptyList()
        override fun getLibraryNovelAsFlow() = MutableStateFlow(emptyList<LibraryNovel>())
        override fun getNovelFavoritesBySourceId(sourceId: Long) = MutableStateFlow(emptyList<Novel>())
        override suspend fun insertNovel(novel: Novel): Long? = null
        override suspend fun updateNovel(update: NovelUpdate): Boolean = true
        override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean = true
        override suspend fun resetNovelViewerFlags(): Boolean = true
    }

    private class FakeNovelSourceManager(
        private val sourceId: Long,
        private val chapterHtml: String,
        private val chapterHtmlByUrl: Map<String, String> = emptyMap(),
        private val requestedChapterUrls: MutableList<String>? = null,
        private val chapterWebUrlResolver: ((String, String?) -> String?)? = null,
        private val onGetChapterText: (() -> Unit)? = null,
    ) : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources =
            MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())
        override fun get(sourceKey: Long): NovelSource? =
            if (sourceKey == sourceId) {
                FakeNovelSource(
                    id = sourceId,
                    chapterHtml = chapterHtml,
                    chapterHtmlByUrl = chapterHtmlByUrl,
                    requestedChapterUrls = requestedChapterUrls,
                    chapterWebUrlResolver = chapterWebUrlResolver,
                    onGetChapterText = onGetChapterText,
                )
            } else {
                null
            }
        override fun getOrStub(sourceKey: Long): NovelSource =
            get(sourceKey) ?: object : NovelSource {
                override val id: Long = sourceKey
                override val name: String = "Stub"
            }
        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource>()
        override fun getCatalogueSources() = emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>()
        override fun getStubSources() = emptyList<StubNovelSource>()
    }

    private class FakeNovelSource(
        override val id: Long,
        private val chapterHtml: String,
        private val chapterHtmlByUrl: Map<String, String> = emptyMap(),
        private val requestedChapterUrls: MutableList<String>? = null,
        private val chapterWebUrlResolver: ((String, String?) -> String?)? = null,
        private val onGetChapterText: (() -> Unit)? = null,
    ) : NovelSource, NovelWebUrlSource {
        override val name: String = "NovelSource"

        override suspend fun getChapterText(chapter: SNovelChapter): String {
            onGetChapterText?.invoke()
            requestedChapterUrls?.add(chapter.url)
            return chapterHtmlByUrl[chapter.url] ?: chapterHtml
        }

        override suspend fun getNovelWebUrl(novelPath: String): String? = null

        override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? {
            return chapterWebUrlResolver?.invoke(chapterPath, novelPath)
        }
    }
}
