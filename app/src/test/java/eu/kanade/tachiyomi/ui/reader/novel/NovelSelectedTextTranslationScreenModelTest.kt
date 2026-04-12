package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.translation.TranslationQueueItem
import eu.kanade.tachiyomi.data.translation.TranslationQueueManager
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginPackage
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginStorage
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.AirforceTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.DeepSeekTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationService
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationProviderOutcome
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelSelectedTextTranslationRequest
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterModelsService
import eu.kanade.tachiyomi.ui.reader.novel.translation.OpenRouterTranslationService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

class NovelSelectedTextTranslationScreenModelTest {
    private class TestApplication : Application()

    private val activeScreenModels = mutableListOf<NovelReaderScreenModel>()
    private val syncNovelChaptersWithSource = mockk<SyncNovelChaptersWithSource>(relaxed = true)
    private val geminiTranslationService = mockk<GeminiTranslationService>(relaxed = true)
    private val airforceTranslationService = mockk<AirforceTranslationService>(relaxed = true)
    private val airforceModelsService = mockk<AirforceModelsService>(relaxed = true)
    private val openRouterTranslationService = mockk<OpenRouterTranslationService>(relaxed = true)
    private val openRouterModelsService = mockk<OpenRouterModelsService>(relaxed = true)
    private val deepSeekTranslationService = mockk<DeepSeekTranslationService>(relaxed = true)
    private val deepSeekModelsService = mockk<DeepSeekModelsService>(relaxed = true)

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupInjektApplication() {
            runCatching { Injekt.get<Application>() }
                .getOrElse {
                    val cacheDir = java.io.File(System.getProperty("java.io.tmpdir"), "novel-translation-test-cache")
                        .apply { mkdirs() }
                    val application = mockk<Application>(relaxed = true)
                    every { application.cacheDir } returns cacheDir
                    Injekt.addSingleton(fullType<Application>(), application)
                }
            runCatching { Injekt.get<TranslationQueueManager>() }
                .getOrElse {
                    val translationQueueManager = mockk<TranslationQueueManager>(relaxed = true)
                    every {
                        translationQueueManager.activeTranslation
                    } returns MutableStateFlow<TranslationQueueItem?>(null)
                    Injekt.addSingleton(fullType<TranslationQueueManager>(), translationQueueManager)
                }
        }

        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }
    }

    @Test
    fun `selection update exposes button and card state`() {
        runBlocking {
            val provider = ScriptedSelectedTextTranslationProvider()
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
            )

            awaitLoaded(screenModel)

            val selection = selectedTextSelection()
            screenModel.updateSelectedTextSelection(selection)
            yield()

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.selectedTextTranslationSelection shouldBe selection
            state.selectedTextTranslationUiState.shouldBeInstanceOf<
                NovelSelectedTextTranslationUiState.SelectionAvailable,
                >()
        }
    }

    @Test
    fun `disabled selection does not expose translation ui or request translation`() {
        runBlocking {
            val provider = ScriptedSelectedTextTranslationProvider()
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
                selectedTextTranslationEnabled = false,
            )

            awaitLoaded(screenModel)

            screenModel.updateSelectedTextSelection(selectedTextSelection())
            screenModel.translateSelectedText()
            yield()

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.selectedTextTranslationSelection shouldBe null
            state.selectedTextTranslationUiState shouldBe NovelSelectedTextTranslationUiState.Idle
            provider.requests shouldBe emptyList()
        }
    }

    @Test
    fun `translate action enters loading and success states`() {
        runBlocking {
            val deferred = CompletableDeferred<NovelSelectedTextTranslationProviderOutcome>()
            val provider = ScriptedSelectedTextTranslationProvider(
                onTranslate = { deferred.await() },
            )
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
            )

            awaitLoaded(screenModel)

            val selection = selectedTextSelection()
            screenModel.updateSelectedTextSelection(selection)
            screenModel.translateSelectedText()

            withTimeout(1_000) {
                while (provider.requests.isEmpty()) {
                    yield()
                }
            }

            screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                .selectedTextTranslationUiState.shouldBeInstanceOf<NovelSelectedTextTranslationUiState.Translating>()

            deferred.complete(
                NovelSelectedTextTranslationProviderOutcome.Success(
                    result = eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult(
                        translation = "Привет",
                        detectedSourceLanguage = "en",
                        providerFingerprint = provider.fingerprint,
                    ),
                ),
            )

            withTimeout(1_000) {
                while (true) {
                    val current = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                    if (current.selectedTextTranslationUiState is NovelSelectedTextTranslationUiState.Result) {
                        break
                    }
                    yield()
                }
            }

            val success = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            val resultState = success.selectedTextTranslationUiState.shouldBeInstanceOf<
                NovelSelectedTextTranslationUiState.Result,
                >()
            resultState.translationResult.translation shouldBe "Привет"
            provider.requests.size shouldBe 1
        }
    }

    @Test
    fun `dismissed stale response is ignored`() {
        runBlocking {
            val deferred = CompletableDeferred<NovelSelectedTextTranslationProviderOutcome>()
            val provider = ScriptedSelectedTextTranslationProvider(
                onTranslate = { deferred.await() },
            )
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
            )

            awaitLoaded(screenModel)

            screenModel.updateSelectedTextSelection(selectedTextSelection(sessionId = 7L))
            screenModel.translateSelectedText()

            withTimeout(1_000) {
                while (provider.requests.isEmpty()) {
                    yield()
                }
            }

            screenModel.dismissSelectedTextTranslation()
            deferred.complete(
                NovelSelectedTextTranslationProviderOutcome.Success(
                    result = eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult(
                        translation = "Привет",
                        detectedSourceLanguage = "en",
                        providerFingerprint = provider.fingerprint,
                    ),
                ),
            )
            yield()

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.selectedTextTranslationUiState shouldBe NovelSelectedTextTranslationUiState.Idle
            state.selectedTextTranslationSelection shouldBe null
        }
    }

    @Test
    fun `chapter reset clears selection and ignores late response`() {
        runBlocking {
            val deferred = CompletableDeferred<NovelSelectedTextTranslationProviderOutcome>()
            val provider = ScriptedSelectedTextTranslationProvider(
                onTranslate = { deferred.await() },
            )
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
            )

            awaitLoaded(screenModel)

            screenModel.updateSelectedTextSelection(selectedTextSelection(sessionId = 9L))
            screenModel.translateSelectedText()

            withTimeout(1_000) {
                while (provider.requests.isEmpty()) {
                    yield()
                }
            }

            screenModel.resetSelectedTextTranslationForChapter()
            deferred.complete(
                NovelSelectedTextTranslationProviderOutcome.Success(
                    result = eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult(
                        translation = "Привет",
                        detectedSourceLanguage = "en",
                        providerFingerprint = provider.fingerprint,
                    ),
                ),
            )
            yield()

            val state = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
            state.selectedTextTranslationUiState shouldBe NovelSelectedTextTranslationUiState.Idle
            state.selectedTextTranslationSelection shouldBe null
        }
    }

    @Test
    fun `repeated same text request hits session cache`() {
        runBlocking {
            val provider = ScriptedSelectedTextTranslationProvider(
                onTranslate = {
                    NovelSelectedTextTranslationProviderOutcome.Success(
                        result = eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult(
                            translation = "Привет",
                            detectedSourceLanguage = "en",
                            providerFingerprint = fingerprint,
                        ),
                    )
                },
            )
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
            )

            awaitLoaded(screenModel)

            val selection = selectedTextSelection(sessionId = 11L)
            screenModel.updateSelectedTextSelection(selection)
            screenModel.translateSelectedText()
            withTimeout(1_000) {
                while (
                    screenModel.state.value
                        .shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                        .selectedTextTranslationUiState !is NovelSelectedTextTranslationUiState.Result
                ) {
                    yield()
                }
            }

            screenModel.updateSelectedTextSelection(selection)
            screenModel.translateSelectedText()
            yield()

            provider.requests.size shouldBe 1
            screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                .selectedTextTranslationUiState.shouldBeInstanceOf<NovelSelectedTextTranslationUiState.Result>()
        }
    }

    @Test
    fun `cooldown state is surfaced without breaking reading`() {
        runBlocking {
            val provider = ScriptedSelectedTextTranslationProvider(
                onTranslate = {
                    NovelSelectedTextTranslationProviderOutcome.Unavailable(
                        reason = NovelSelectedTextTranslationErrorReason.Cooldown(60),
                    )
                },
            )
            val screenModel = trackedNovelReaderScreenModel(
                selectedTextTranslationProvider = provider,
            )

            awaitLoaded(screenModel)

            screenModel.updateSelectedTextSelection(selectedTextSelection(sessionId = 13L))
            screenModel.translateSelectedText()

            withTimeout(1_000) {
                while (true) {
                    val current = screenModel.state.value.shouldBeInstanceOf<NovelReaderScreenModel.State.Success>()
                    if (current.selectedTextTranslationUiState is NovelSelectedTextTranslationUiState.Unavailable) {
                        current.contentBlocks.isNotEmpty() shouldBe true
                        break
                    }
                    yield()
                }
            }
        }
    }

    private suspend fun awaitLoaded(screenModel: NovelReaderScreenModel) {
        withTimeout(1_000) {
            while (screenModel.state.value is NovelReaderScreenModel.State.Loading) {
                yield()
            }
        }
    }

    private class ScriptedSelectedTextTranslationProvider(
        override val fingerprint: String = "google-unofficial-test",
        private val onTranslate: suspend NovelSelectedTextTranslationProvider.(
            NovelSelectedTextTranslationRequest,
        ) -> NovelSelectedTextTranslationProviderOutcome = {
            NovelSelectedTextTranslationProviderOutcome.Success(
                result = eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationResult(
                    translation = "Привет",
                    detectedSourceLanguage = "en",
                    providerFingerprint = fingerprint,
                ),
            )
        },
    ) : NovelSelectedTextTranslationProvider {
        val requests = mutableListOf<NovelSelectedTextTranslationRequest>()

        override suspend fun translate(
            request: NovelSelectedTextTranslationRequest,
        ): NovelSelectedTextTranslationProviderOutcome {
            requests += request
            return onTranslate(request)
        }
    }

    private fun selectedTextSelection(
        sessionId: Long = 1L,
    ): NovelSelectedTextSelection {
        return NovelSelectedTextSelection(
            sessionId = sessionId,
            renderer = NovelSelectedTextRenderer.PAGE_READER,
            text = "Hello",
            anchor = NovelSelectedTextAnchor(
                leftPx = 10,
                topPx = 20,
                rightPx = 30,
                bottomPx = 40,
            ),
        )
    }

    private fun trackedNovelReaderScreenModel(
        selectedTextTranslationProvider: NovelSelectedTextTranslationProvider,
        selectedTextTranslationEnabled: Boolean = true,
    ): NovelReaderScreenModel {
        val novel = Novel.create().copy(id = 1L, source = 10L, title = "Novel")
        val chapter = NovelChapter.create().copy(
            id = 5L,
            novelId = 1L,
            name = "Chapter 1",
            url = "https://example.org/ch1",
        )

        val model = NovelReaderScreenModel(
            chapterId = chapter.id,
            novelChapterRepository = FakeNovelChapterRepository(chapter),
            syncNovelChaptersWithSource = syncNovelChaptersWithSource,
            getNovel = GetNovel(FakeNovelRepository(novel)),
            sourceManager = FakeNovelSourceManager(sourceId = novel.source, chapterHtml = "<p>Hello</p>"),
            pluginStorage = FakeNovelPluginStorage(emptyList()),
            historyRepository = null,
            novelReaderPreferences = createNovelReaderPreferences(
                selectedTextTranslationEnabled = selectedTextTranslationEnabled,
            ),
            isSystemDark = { false },
            geminiTranslationService = geminiTranslationService,
            airforceTranslationService = airforceTranslationService,
            airforceModelsService = airforceModelsService,
            openRouterTranslationService = openRouterTranslationService,
            openRouterModelsService = openRouterModelsService,
            deepSeekTranslationService = deepSeekTranslationService,
            deepSeekModelsService = deepSeekModelsService,
            selectedTextTranslationProvider = selectedTextTranslationProvider,
        )
        activeScreenModels += model
        return model
    }

    private fun createNovelReaderPreferences(
        selectedTextTranslationEnabled: Boolean = true,
    ): NovelReaderPreferences {
        return NovelReaderPreferences(
            preferenceStore = ReactivePreferenceStore(),
            json = Json { encodeDefaults = true },
        ).also { prefs ->
            prefs.cacheReadChapters().set(false)
            prefs.cacheReadChaptersUnlimited().set(false)
            prefs.selectedTextTranslationEnabled().set(selectedTextTranslationEnabled)
        }
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

    private class FakeNovelChapterRepository(
        private val chapter: NovelChapter?,
    ) : NovelChapterRepository {
        override suspend fun addAllChapters(chapters: List<NovelChapter>): List<NovelChapter> = chapters
        override suspend fun updateChapter(chapterUpdate: NovelChapterUpdate) = Unit
        override suspend fun updateAllChapters(chapterUpdates: List<NovelChapterUpdate>) = Unit
        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = Unit
        override suspend fun getChapterByNovelId(novelId: Long, applyScanlatorFilter: Boolean): List<NovelChapter> =
            listOfNotNull(chapter)
        override suspend fun getScanlatorsByNovelId(novelId: Long): List<String> = emptyList()
        override fun getScanlatorsByNovelIdAsFlow(novelId: Long): Flow<List<String>> =
            MutableStateFlow(emptyList())
        override suspend fun getBookmarkedChaptersByNovelId(novelId: Long): List<NovelChapter> = emptyList()
        override suspend fun getChapterById(id: Long): NovelChapter? = chapter?.takeIf { it.id == id }
        override suspend fun getChapterByNovelIdAsFlow(
            novelId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<NovelChapter>> = MutableStateFlow(listOfNotNull(chapter))
        override suspend fun getChapterByUrlAndNovelId(url: String, novelId: Long): NovelChapter? = null
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

    private class FakeNovelPluginStorage(
        private val packages: List<NovelPluginPackage>,
    ) : NovelPluginStorage {
        override suspend fun save(pkg: NovelPluginPackage) = Unit
        override suspend fun get(id: String): NovelPluginPackage? = packages.firstOrNull { it.entry.id == id }
        override suspend fun getAll(): List<NovelPluginPackage> = packages
    }

    private class FakeNovelSourceManager(
        private val sourceId: Long,
        private val chapterHtml: String,
    ) : NovelSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources =
            MutableStateFlow(emptyList<eu.kanade.tachiyomi.novelsource.NovelCatalogueSource>())
        override fun get(sourceKey: Long): NovelSource? =
            if (sourceKey == sourceId) {
                FakeNovelSource(id = sourceId, chapterHtml = chapterHtml)
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
    ) : NovelSource, NovelWebUrlSource {
        override val name: String = "NovelSource"
        override suspend fun getChapterText(chapter: SNovelChapter): String = chapterHtml
        override suspend fun getNovelWebUrl(novelPath: String): String? = null
        override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? = null
    }
}
